package com.arjun.soul_note.data.remote.firebase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Wire-format tests for the list-based read path: [buildListUrl], [FirestoreQuery.watermarkOrNull]
 * and [isAfterWatermark].
 *
 * All three encode a constraint that Firestore enforces but the type system cannot. A wrong
 * ordering, a missing `orderBy`, or an off-by-one on the watermark each produce a request the
 * server accepts and silently returns the wrong rows for, so the assertions are about the exact
 * query shape and the boundary, not about the happy path returning something.
 */
class FirestoreListQueryTest {

    private val root = "https://firestore.googleapis.com/v1/projects/p/databases/(default)/documents"
    private val order = FirestoreOrder("updatedAt", FirestoreOrder.Direction.ASCENDING)

    private fun document(updatedAt: String?, updateTime: String? = null) = RemoteDocument(
        path = "users/u/diaryEntries/e",
        id = "e",
        fields = updatedAt?.let { mapOf("updatedAt" to buildJsonObject { put("timestampValue", it) }) }
            ?: emptyMap(),
        createTime = null,
        updateTime = updateTime,
    )

    @Test
    fun `the url addresses the subcollection directly rather than through a query`() {
        val url = buildListUrl(root, "users/u/diaryEntries", order, 400, null)

        assertEquals("$root/users/u/diaryEntries?pageSize=400&orderBy=updatedAt%20asc", url)
    }

    @Test
    fun `a page token is percent encoded so it cannot break out of the query string`() {
        val url = buildListUrl(root, "users/u/diaryEntries", order, 400, "a+b/c=d&e")

        assertTrue(url.endsWith("&pageToken=a%2Bb%2Fc%3Dd%26e"), url)
    }

    @Test
    fun `descending order is passed through rather than normalised away`() {
        val descending = FirestoreOrder("updatedAt", FirestoreOrder.Direction.DESCENDING)

        assertTrue(
            buildListUrl(root, "users/u/moods", descending, 400, null).endsWith("orderBy=updatedAt%20desc"),
        )
    }

    @Test
    fun `the order by direction uses the short lower case form the list endpoint accepts`() {
        // The list endpoint answers "Invalid order by clause" to the proto enum
        // names that the runQuery structured query needed.
        val names = FirestoreOrder.Direction.entries.map { it.wire }

        assertEquals(listOf("asc", "desc"), names)
    }

    @Test
    fun `a resource name from a list response keeps only the path under the database`() {
        // The list response names documents `projects/.../documents/...`, not by url, so
        // stripping the url prefix alone leaves the root in place and the commit API
        // prefixes it a second time.
        val name = "projects/p/databases/(default)/documents/users/u/diaryEntries/e"

        assertEquals("users/u/diaryEntries/e", documentRelativePath(name, root, "projects/p/databases/(default)/documents"))
    }

    @Test
    fun `a url from an error body is reduced to the same path`() {
        val name = "$root/users/u/diaryEntries/e"

        assertEquals("users/u/diaryEntries/e", documentRelativePath(name, root, "projects/p/databases/(default)/documents"))
    }

    @Test
    fun `the watermark is recovered only from a greater-than timestamp filter`() {
        val watermark = "2026-09-29T12:00:00.000Z"
        val pull = FirestoreQuery(
            collectionPath = "users/u/diaryEntries",
            filters = listOf(
                FirestoreFilter(
                    field = "updatedAt",
                    operator = FirestoreFilter.Operator.GREATER_THAN,
                    value = watermark,
                    valueType = FirestoreFilter.ValueType.TIMESTAMP,
                ),
            ),
        )

        assertEquals(watermark, pull.watermarkOrNull())
        assertNull(FirestoreQuery("users/u/diaryEntries").watermarkOrNull())
        assertNull(
            FirestoreQuery(
                collectionPath = "users/u/diaryEntries",
                filters = listOf(
                    FirestoreFilter("title", FirestoreFilter.Operator.EQUAL, "x"),
                ),
            ).watermarkOrNull(),
        )
    }

    @Test
    fun `the watermark compare is strict so a document at the watermark is not re-pulled`() {
        val watermark = "2026-09-29T12:00:00.000Z"

        assertTrue(isAfterWatermark(document("2026-09-29T12:00:00.001Z"), "updatedAt", watermark))
        assertFalse(isAfterWatermark(document("2026-09-29T12:00:00.000Z"), "updatedAt", watermark))
        assertFalse(isAfterWatermark(document("2026-09-29T11:59:59.999Z"), "updatedAt", watermark))
    }

    @Test
    fun `the compare falls back to the server update time when the field is absent`() {
        val watermark = "2026-09-29T12:00:00.000Z"

        assertTrue(isAfterWatermark(document(null, updateTime = "2026-09-29T12:00:05Z"), "updatedAt", watermark))
        assertFalse(isAfterWatermark(document(null, updateTime = "2026-09-29T11:00:00Z"), "updatedAt", watermark))
        assertFalse(isAfterWatermark(document(null), "updatedAt", watermark))
    }

    @Test
    fun `the compare ignores a field the document does not carry`() {
        val watermark = "2026-09-29T12:00:00.000Z"
        val renamed = RemoteDocument(
            path = "users/u/diaryEntries/e",
            id = "e",
            fields = mapOf("lastEdited" to buildJsonObject { put("timestampValue", "2026-09-30T00:00:00.000Z") }),
            createTime = null,
            updateTime = null,
        )

        assertFalse(isAfterWatermark(renamed, "updatedAt", watermark))
    }

    @Test
    fun `a string field is never read as a timestamp`() {
        val watermark = "2026-09-29T12:00:00.000Z"
        val wrongType = RemoteDocument(
            path = "users/u/diaryEntries/e",
            id = "e",
            fields = mapOf<String, kotlinx.serialization.json.JsonElement>(
                "updatedAt" to buildJsonObject { put("stringValue", "2026-12-01T00:00:00.000Z") },
            ),
            createTime = null,
            updateTime = null,
        )

        assertFalse(isAfterWatermark(wrongType, "updatedAt", watermark))
    }

    @Test
    fun `a field map survives the codec round trip the pull depends on`() {
        val fields = FirestoreCodec.fieldMap(
            JsonObject(
                mapOf(
                    "name" to kotlinx.serialization.json.JsonPrimitive("users/u/diaryEntries/e"),
                    "updateTime" to kotlinx.serialization.json.JsonPrimitive("2026-09-29T12:00:00.000Z"),
                    "fields" to buildJsonObject {
                        put("updatedAt", buildJsonObject { put("timestampValue", "2026-09-29T12:00:00.000Z") })
                    },
                ),
            ),
        )

        assertEquals(1, fields.size)
        assertEquals(
            "2026-09-29T12:00:00.000Z",
            FirestoreCodec.asTimestamp(fields["updatedAt"]),
        )
    }
}
