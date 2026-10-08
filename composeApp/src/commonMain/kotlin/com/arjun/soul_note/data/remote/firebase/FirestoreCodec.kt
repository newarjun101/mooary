package com.arjun.soul_note.data.remote.firebase

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * The Firestore REST v1 wire format.
 *
 * Firestore's JSON API wraps every value in a typed object (`stringValue`,
 * `timestampValue`, …). Encoding and decoding live in one place so the diary and mood
 * documents stay plain, strongly-typed domain models and the wire format can be changed
 * without touching the data layer.
 */
object FirestoreCodec {


    fun string(value: String): JsonObject = buildJsonObject {
        put("stringValue", value)
    }

    fun nullableString(value: String?): JsonElement =
        if (value == null) nullValue() else string(value)

    /**
     * A Firestore `Value` is a typed oneof; a field holding null must be sent as
     * `{"nullValue": null}`. A bare JSON `null` is not a `Value` at all, and Firestore
     * rejects the whole commit with 400 `Cannot convert firestore.v1.Value with type
     * unset` — so one unset optional field silently fails the entire batch.
     */
    fun nullValue(): JsonObject = buildJsonObject { put("nullValue", JsonNull) }

    fun long(value: Long): JsonObject = buildJsonObject {
        put("integerValue", value.toString())
    }

    fun double(value: Double): JsonObject = buildJsonObject {
        put("doubleValue", value)
    }

    fun bool(value: Boolean): JsonObject = buildJsonObject {
        put("booleanValue", value)
    }

    fun timestamp(value: String): JsonObject = buildJsonObject {
        put("timestampValue", value)
    }

    fun nullableTimestamp(value: String?): JsonElement =
        if (value == null) nullValue() else timestamp(value)

    fun stringArray(values: List<String>): JsonObject = buildJsonObject {
        put(
            "arrayValue",
            buildJsonObject {
                put("values", buildJsonArray { values.forEach { add(string(it)) } })
            },
        )
    }

    fun map(fields: Map<String, JsonElement>): JsonObject = buildJsonObject {
        put("mapValue", buildJsonObject { put("fields", buildJsonObject { fields.forEach { (k, v) -> put(k, v) } }) })
    }


    fun fieldMap(document: JsonObject): Map<String, JsonElement> =
        (document["fields"] as? JsonObject)?.toMap() ?: emptyMap()

    fun asString(element: JsonElement?): String? =
        (element as? JsonObject)?.get("stringValue")?.jsonPrimitiveOrNull()

    /** A `timestampValue`, which is a string on the wire but is not a `stringValue` field. */
    fun asTimestamp(element: JsonElement?): String? =
        (element as? JsonObject)?.get("timestampValue")?.jsonPrimitiveOrNull()

    fun asNullableString(element: JsonElement?): String? =
        if (element == null || isNullValue(element)) null else asString(element)

    /** Matches the encoding of [nullValue]; the two must stay symmetric. */
    fun isNullValue(element: JsonElement?): Boolean =
        element is JsonNull || (element as? JsonObject)?.containsKey("nullValue") == true

    fun asLong(element: JsonElement?): Long? =
        (element as? JsonObject)?.get("integerValue")?.jsonPrimitiveOrNull()?.toLongOrNull()
            ?: (element as? JsonObject)?.get("doubleValue")?.jsonPrimitiveOrNull()?.toDoubleOrNull()?.toLong()

    fun asBool(element: JsonElement?): Boolean? =
        (element as? JsonObject)?.get("booleanValue")?.jsonPrimitiveOrNull()?.toBooleanStrictOrNull()

    fun asStringList(element: JsonElement?): List<String> {
        val values = (element as? JsonObject)?.get("arrayValue") as? JsonObject
            ?: return emptyList()
        val array = values["values"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { asString(it) }
    }

    /** `"users/abc/diaryEntries/xyz"`, or `null` when the document has no parent. */
    fun documentPath(document: JsonObject): String? =
        (document["name"] as? JsonPrimitive)?.content

    /** The document id, i.e. the last path segment. */
    fun documentId(document: JsonObject): String? =
        documentPath(document)?.substringAfterLast('/')

    /** `createTime` of a committed write, used as the authoritative sync timestamp. */
    fun createTime(document: JsonObject): String? =
        ((document["createTime"] ?: document["updateTime"]) as? JsonPrimitive)?.content

    fun updateTime(document: JsonObject): String? =
        (document["updateTime"] as? JsonPrimitive)?.content

    private fun JsonElement.jsonPrimitiveOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}
