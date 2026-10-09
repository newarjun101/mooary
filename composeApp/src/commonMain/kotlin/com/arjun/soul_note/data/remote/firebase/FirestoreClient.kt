package com.arjun.soul_note.data.remote.firebase

import com.arjun.soul_note.core.error.AppError
import com.arjun.soul_note.core.error.AppException
import com.arjun.soul_note.core.error.AuthenticationReason
import com.arjun.soul_note.core.error.ErrorKeys
import com.arjun.soul_note.core.error.toAppError
import com.arjun.soul_note.core.logging.AppLogger
import com.arjun.soul_note.core.platform.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A single Firestore document, as returned by the REST API.
 */
data class RemoteDocument(
    val path: String,
    val id: String,
    val fields: Map<String, JsonElement>,
    val createTime: String?,
    val updateTime: String?,
)

/** A structured Firestore query, translated into `StructuredQuery` JSON. */
/**
 * A filter on one field.
 *
 * [ValueType] is part of the filter rather than inferred from [value] because Firestore
 * compares *typed* values: a `stringValue` filter never matches a `timestamp` field, no
 * matter how the two are spelled. The wire type has to be stated where the filter is built,
 * next to the knowledge of what the document actually stores.
 */
data class FirestoreFilter(
    val field: String,
    val operator: Operator,
    val value: String,
    val valueType: ValueType = ValueType.STRING,
) {
    enum class ValueType { STRING, TIMESTAMP }

    enum class Operator(val wire: String) {
        EQUAL("EQUAL"),
        NOT_EQUAL("NOT_EQUAL"),
        LESS_THAN("LESS_THAN"),
        LESS_THAN_OR_EQUAL("LESS_THAN_OR_EQUAL"),
        GREATER_THAN("GREATER_THAN"),
        GREATER_THAN_OR_EQUAL("GREATER_THAN_OR_EQUAL"),
        ARRAY_CONTAINS("ARRAY_CONTAINS"),
    }
}

data class FirestoreQuery(
    val collectionPath: String,
    val filters: List<FirestoreFilter> = emptyList(),
    val ordersBy: List<FirestoreOrder> = emptyList(),
    val limit: Int? = null,
)

data class FirestoreOrder(
    val field: String,
    val direction: Direction,
) {
    /**
     * The documents-list `orderBy` query parameter wants the short proto direction,
     * lower case. The enum names `ASCENDING`/`DESCENDING` are what the `runQuery`
     * structured query used, and the list endpoint answers
     * "Invalid order by clause" to them, so the two wire formats cannot be shared.
     */
    enum class Direction(val wire: String) { ASCENDING("asc"), DESCENDING("desc") }
}

/** A batched write, submitted atomically via the `:commit` endpoint. */
sealed interface FirestoreWrite {
    val path: String

    data class Set(override val path: String, val fields: Map<String, JsonElement>) : FirestoreWrite
    data class Update(override val path: String, val fields: Map<String, JsonElement>) : FirestoreWrite
    data class Delete(override val path: String) : FirestoreWrite
}

/**
 * Cloud Firestore over its public REST v1 API.
 *
 * There is no official Firebase SDK for Kotlin Multiplatform, so the app talks to the
 * documented REST endpoints instead. This is a real client, not a stub: the same code
 * path serves production and the Firestore emulator, which is what lets the integration
 * tests in `commonTest` run against a genuine Firestore implementation.
 *
 * Responsibilities are deliberately narrow — HTTP, auth header, typed values, error
 * translation. Every caller already holds a domain model.
 */
class FirestoreClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val tokenProvider: suspend () -> String?,
    private val logger: AppLogger,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = true; isLenient = true }
    private val documentsRoot = config.documentsRoot


    /**
     * Lists the documents of [query]'s collection, newest-watermark first, and returns them
     * in `updatedAt` ascending order.
     *
     * Deliberately the `documents/{path}` list endpoint rather than `:runQuery`. A
     * `runQuery` `structuredQuery` cannot address a subcollection on this API: `From` has
     * no `parent` field (rejected as an unknown name) and more than one `from` selector is
     * rejected outright, so `users/{uid}/diaryEntries` is simply unreachable that way. The
     * list endpoint takes the collection path directly and supports `orderBy` plus
     * `pageSize`, which is all an incremental pull needs.
     *
     * The consequence is that the watermark filter is applied here rather than server-side.
     * Ordering ascending by the same field makes this cheap: page until the first document
     * at or below the watermark, then stop.
     */
    suspend fun query(query: FirestoreQuery): List<RemoteDocument> {
        val order = query.ordersBy.singleOrNull()
            ?: error("the list endpoint takes a single orderBy; got ${query.ordersBy.size}")
        val watermark = query.watermarkOrNull()
        val pageSize = minOf(query.limit ?: DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE)

        val documents = mutableListOf<RemoteDocument>()
        var pageToken: String? = null
        do {
            val response = get(listUrl(query.collectionPath, order, pageSize, pageToken))
            val page = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: break

            val batch = (page["documents"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.toRemoteDocument() }
            if (batch.isEmpty()) break

            for (document in batch) {
                if (watermark != null && !isAfterWatermark(document, order.field, watermark)) return documents
                documents += document
            }
            pageToken = (page["nextPageToken"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        } while (pageToken != null && documents.size < pageSize)

        return if (query.limit != null) documents.take(query.limit) else documents
    }

    private fun listUrl(
        collectionPath: String,
        order: FirestoreOrder,
        pageSize: Int,
        pageToken: String?,
    ): String = buildListUrl(documentsRoot, collectionPath, order, pageSize, pageToken)

    /** Fetches a single document, or `null` when it does not exist. */
    suspend fun getDocument(path: String): RemoteDocument? {
        val response = get("$documentsRoot/$path")
        if (response.status == HttpStatusCode.NotFound) return null
        ensureSuccess(response, "$documentsRoot/$path")
        val document = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        return document?.toRemoteDocument()
    }


    /**
     * Applies a batch atomically.
     *
     * Batching is not just an optimisation: it is the only way an entry and its mood can
     * be reconciled together, and it means a partially-applied sync is impossible.
     */
    suspend fun commit(writes: List<FirestoreWrite>): List<RemoteDocument> {
        if (writes.isEmpty()) return emptyList()
        val payload = buildJsonObject {
            put(
                "writes",
                buildJsonArray {
                    writes.forEach { write ->
                        add(
                            buildJsonObject {
                                when (write) {
                                    is FirestoreWrite.Set -> put(
                                        "update",
                                        buildJsonObject {
                                            put("name", "${config.documentsResourceRoot}/${write.path}")
                                            put(
                                                "fields",
                                                buildJsonObject { write.fields.forEach { (k, v) -> put(k, v) } },
                                            )
                                        },
                                    )
                                    is FirestoreWrite.Update -> put(
                                        "update",
                                        buildJsonObject {
                                            put("name", "${config.documentsResourceRoot}/${write.path}")
                                            put(
                                                "fields",
                                                buildJsonObject { write.fields.forEach { (k, v) -> put(k, v) } },
                                            )
                                        },
                                    )
                                    is FirestoreWrite.Delete -> put(
                                        "delete",
                                        "${config.documentsResourceRoot}/${write.path}",
                                    )
                                }
                            },
                        )
                    }
                },
            )
        }

        val response = post("$documentsRoot:commit", payload)
        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: return emptyList()
        val results = root["writeResults"] as? JsonArray ?: return emptyList()
        val updateTime = (root["commitTime"] as? JsonPrimitive)?.content
        return results.mapNotNull { result ->
            (result as? JsonObject)?.get("updateTime")?.jsonPrimitive?.content
        }.map { RemoteDocument(path = "", id = "", fields = emptyMap(), createTime = null, updateTime = updateTime) }
    }

    /**
     * Deletes every document under [collectionPath].
     *
     * Uses the list endpoint rather than a structured prefix query, for the same reason
     * [query] does: `runQuery` cannot address a subcollection on this API. Deleting the
     * page that was just read invalidates its page token, so this always restarts from the
     * first page and drains until one comes back empty -- the list shrinks underneath it.
     *
     * A pass that reads documents but deletes nothing is the one way this cannot drain, and
     * a delete of a path that does not exist succeeds without touching anything, so that
     * case is a silent infinite loop unless it is caught here. It is the symptom of a
     * relative path that still carries the documents root; see [documentRelativePath].
     */
    suspend fun deleteCollection(collectionPath: String, batchSize: Int = 400): Int {
        var count = 0
        var previousPass: Set<String>? = null
        while (true) {
            val response = get(
                "$documentsRoot/$collectionPath?pageSize=${minOf(batchSize, MAX_PAGE_SIZE)}",
            )
            val page = json.parseToJsonElement(response.bodyAsText()) as? JsonObject ?: break
            val relative = (page["documents"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.get("name") as? JsonPrimitive }
                .map { documentRelativePath(it.content, documentsRoot, config.documentsResourceRoot) }
                .toSet()
            if (relative.isEmpty()) break

            // The same first page coming back means the deletes were addressed somewhere
            // that does not exist. Firestore accepts a delete of a missing document, so
            // there is no error to surface and the only way out is to notice the stall.
            if (relative == previousPass) {
                logger.warn("FirestoreClient") {
                    "delete made no progress on $collectionPath after $count documents, stopping"
                }
                break
            }
            previousPass = relative

            relative.chunked(batchSize).forEach { chunk ->
                commit(chunk.map { FirestoreWrite.Delete(it) })
            }
            count += relative.size
        }
        return count
    }

    private fun JsonObject.toRemoteDocument(): RemoteDocument? {
        val name = (this["name"] as? JsonPrimitive)?.content ?: return null
        return RemoteDocument(
            path = documentRelativePath(name, documentsRoot, config.documentsResourceRoot),
            id = name.substringAfterLast('/'),
            fields = FirestoreCodec.fieldMap(this),
            createTime = (this["createTime"] as? JsonPrimitive)?.content,
            updateTime = (this["updateTime"] as? JsonPrimitive)?.content,
        )
    }


    /**
     * Converts a non-2xx response into a typed [AppException].
     *
     * Firestore reports rule rejections and missing documents as ordinary 4xx responses
     * with a structured body, so the body is inspected here — the one place in the app
     * where an HTTP error payload is read, and the reason [toAppError] itself only needs
     * the status code.
     */
    private suspend fun ensureSuccess(response: HttpResponse, url: String) {
        if (response.status.isSuccess()) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        // Logged for *every* non-2xx, not only the 400-range one. The 400 branch used to be
        // the only one that spoke, so a 401 or 403 -- the two statuses a refused write
        // actually comes back as -- surfaced as a bare AppException with nothing attached,
        // and a dead feature was indistinguishable from one that had never been wired up.
        // Both parts are scrubbed on the way out: the url to a collection path, and the
        // reason to its vocabulary with every quoted value masked, because a fieldViolation
        // quotes the value it rejected and those values are diary entries.
        logger.warn("FirestoreClient") {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            val err = root?.get("error") as? JsonObject
            val reasons = buildList {
                err?.get("message")?.let { add(it.redacted()) }
                err?.get("details").asArray().forEach { detail ->
                    (detail as? JsonObject)?.get("fieldViolations").asArray().forEach { v ->
                        (v as? JsonObject)?.get("description")?.let { add(it.redacted()) }
                    }
                }
            }
            val endpoint = endpointOf(url)
            "rejected ${response.status.value} on $endpoint :: ${reasons.joinToString(" / ")}"
        }
        throw AppException(
            when (response.status) {
                HttpStatusCode.Unauthorized ->
                    AppError.Authentication(AuthenticationReason.SESSION_EXPIRED)
                HttpStatusCode.Forbidden -> AppError.PermissionDenied
                HttpStatusCode.NotFound -> AppError.NotFound
                HttpStatusCode.Conflict -> AppError.Conflict(ErrorKeys.WRITE_CONFLICT)
                HttpStatusCode.TooManyRequests ->
                    AppError.Authentication(AuthenticationReason.TOO_MANY_REQUESTS)
                in HttpStatusCode.BadRequest..HttpStatusCode.InternalServerError ->
                    AppError.Validation(
                        REGEX_FIELD.find(body)?.groupValues?.getOrNull(1),
                        ErrorKeys.VALIDATION_REQUIRED,
                    )
                else -> AppError.Unexpected(ErrorKeys.UNEXPECTED)
            },
        )
    }

    private suspend fun post(url: String, payload: JsonObject): HttpResponse = try {
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            credential()
            setBody(payload.toString())
        }
        ensureSuccess(response, url)
        response
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (app: AppException) {
        throw app
    } catch (throwable: Throwable) {
        throw AppException(throwable.toAppError(logger))
    }

    private suspend fun get(url: String): HttpResponse = try {
        val response = http.get(url) {
            credential()
        }
        if (response.status == HttpStatusCode.NotFound) return response
        ensureSuccess(response, url)
        response
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (app: AppException) {
        throw app
    } catch (throwable: Throwable) {
        throw AppException(throwable.toAppError(logger))
    }

    private fun JsonElement?.asArray(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

    /**
     * Keeps Firestore's reason vocabulary while masking anything that could be user
     * text. A fieldViolation `description` quotes the value it rejected, so quoted
     * spans, timestamps and long numbers are all replaced before the string can
     * reach a log sink.
     */
    private fun JsonElement.redacted(): String = (this as? JsonPrimitive)?.content.orEmpty()
        .replace(QUOTED, "?")
        .replace(STAMP, "<ts>")
        .replace(LONG_NUMBER, "<n>")

    private val QUOTED = Regex("'''.*?'''|\".*?\"")
    private val STAMP = Regex("""\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(\.\d+)?Z?""")
    private val LONG_NUMBER = Regex("""\b\d{5,}\b""")


    /** The collection path with the account id replaced, so a failure names the endpoint. */
    /**
     * The identifying part of a URL for a log line.
     *
     * `:commit` addresses no document — the paths are in the body — and without the special
     * case below [substringAfter] misses and the host name is the longest unbroken segment in
     * the string, so it would be reported as `<uid>`: a "collection path" that is really the
     * domain, which reads as a masked identity and is worse than saying nothing.
     */
    private fun endpointOf(url: String): String =
        if (url.contains(":commit")) "commit"
        else url.substringAfter("documents/").substringBefore('?').replace(Regex("/[^/]{20,}"), "/<uid>")

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    /**
     * Attaches the caller's id token, or reports that there was not one.
     *
     * A request with no credential is refused by every rule in the file with the same
     * "Missing or insufficient permissions" a rule-level denial produces, so without this
     * line the two are the same single line in logcat. The presence of a token is logged;
     * the token itself never is.
     */
    private suspend fun io.ktor.client.request.HttpRequestBuilder.credential() {
        val token = tokenProvider()
        if (token == null) logger.warn("FirestoreClient") { "sending a request with no id token" }
        token?.let { bearer(it) }
    }

    private companion object {
        val REGEX_FIELD = Regex("\"field\"\\s*:\\s*\"([^\"]+)\"")

        /** What the list endpoint returns when the caller expresses no page size. */
        const val DEFAULT_PAGE_SIZE = 400

        /** Firestore refuses a `pageSize` above 1000. */
        const val MAX_PAGE_SIZE = 1000
    }
}

/**
 * The `documents/{collectionPath}` list URL for one page of [order]ed documents.
 *
 * Top level rather than a [FirestoreClient] member so it can be tested directly. The
 * ordering is load-bearing rather than cosmetic: the watermark compare in [isAfterWatermark]
 * is a string compare, and it is only correct because the list comes back ordered by the
 * very field it compares.
 */
/**
 * The path a caller can hand back to this client, from either shape a document `name`
 * arrives in: the list and get responses use a resource name
 * `projects/{p}/databases/(default)/documents/{path}`, while an error body quotes a full
 * URL. Getting this wrong is silent rather than loud -- [commit] prefixes the resource
 * root onto whatever it is given, so a name that still carries the root becomes
 * `projects/.../documents/projects/.../documents/...`, and a delete of that path succeeds
 * without touching anything. [deleteCollection] would then list, delete, and re-list the
 * same documents forever.
 */
internal fun documentRelativePath(
    name: String,
    documentsRoot: String,
    documentsResourceRoot: String,
): String = name.removePrefix("$documentsRoot/").removePrefix("$documentsResourceRoot/")

internal fun buildListUrl(    documentsRoot: String,
    collectionPath: String,
    order: FirestoreOrder,
    pageSize: Int,
    pageToken: String?,
): String = buildString {
    append(documentsRoot).append('/').append(collectionPath)
    append("?pageSize=").append(pageSize)
    append("&orderBy=").append(order.field).append("%20").append(order.direction.wire)
    if (pageToken != null) append("&pageToken=").append(pageToken.encodeUrlParameter())
}

/**
 * The one server-side filter the pull needs, recovered from [FirestoreQuery] so the call
 * sites keep describing their intent rather than hand-rolling a compare.
 */
internal fun FirestoreQuery.watermarkOrNull(): String? = filters
    .singleOrNull { it.operator == FirestoreFilter.Operator.GREATER_THAN }
    ?.takeIf { it.valueType == FirestoreFilter.ValueType.TIMESTAMP }
    ?.value

/**
 * Both sides come from [Iso8601.formatTimestamp] -- UTC, fixed width, millisecond
 * precision -- so this is a plain string compare rather than an instant parse. Falls back
 * to the server-assigned `updateTime` when the document has no such field, since that is
 * also a UTC RFC 3339 string and orders consistently against the same watermark.
 */
internal fun isAfterWatermark(document: RemoteDocument, field: String, watermark: String): Boolean {
    val remote = FirestoreCodec.asTimestamp(document.fields[field]) ?: document.updateTime
    return remote != null && remote > watermark
}

private fun String.encodeUrlParameter(): String = buildString {
    this@encodeUrlParameter.encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        // RFC 3986 unreserved set. `+` matters in particular: left bare it decodes as a
        // space, which silently truncates the page token rather than failing loudly.
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") {
            append(char)
        } else {
            append('%').append(value.toString(16).uppercase().padStart(2, '0'))
        }
    }
}
