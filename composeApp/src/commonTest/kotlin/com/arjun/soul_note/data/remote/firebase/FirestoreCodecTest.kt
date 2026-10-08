package com.arjun.soul_note.data.remote.firebase

import com.arjun.soul_note.domain.model.DiaryFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Wire-format tests for [FirestoreCodec].
 *
 * A Firestore `Value` is a typed oneof, so a field holding null has to be encoded as
 * `{"nullValue": null}`. A bare JSON `null` is not a `Value`, and Firestore refuses the
 * whole commit with 400 `Cannot convert firestore.v1.Value with type unset`. That failure is
 * invisible in unit tests and only shows up as a sync pass that pushes nothing, so the
 * encoders are asserted directly here rather than only through a decoded round trip.
 */
class FirestoreCodecTest {

    private val instant = Instant.parse("2026-09-29T12:00:00Z")

    /**
     * Every value Firestore would reject, found by walking the tree rather than by listing
     * keys. A typed `Value` wrapper ends the walk: the `null` inside `{"nullValue": null}` is
     * the required payload, so descending into it would flag the correct encoding.
     */
    private fun bareNullsIn(element: JsonElement, path: String = ""): List<String> = when {
        element is JsonNull -> listOf(path.ifEmpty { "<root>" })
        element is JsonObject && element.keys.any { it in VALUE_KEYS } -> emptyList()
        element is JsonArray -> element.flatMapIndexed { i, v -> bareNullsIn(v, "$path[$i]") }
        element is JsonObject ->
            element.entries.flatMap { (k, v) -> bareNullsIn(v, "$path.$k") }
        else -> emptyList()
    }

    private companion object {
        /** The oneof members of `firestore.v1.Value`; each wraps the field's real payload. */
        val VALUE_KEYS = setOf(
            "stringValue", "nullValue", "integerValue", "doubleValue", "booleanValue",
            "timestampValue", "bytesValue", "referenceValue", "geoPointValue",
        )
    }

    @Test
    fun `an absent optional is sent as a nullValue object and not as a bare json null`() {
        val location = FirestoreCodec.nullableString(null)
        val deletedAt = FirestoreCodec.nullableTimestamp(null)

        assertEquals(setOf("nullValue"), location.jsonKeys())
        assertEquals(setOf("nullValue"), deletedAt.jsonKeys())
        assertEquals(JsonNull, (location as JsonObject)["nullValue"])
    }

    @Test
    fun `a present optional is sent as its usual typed value`() {
        assertEquals(setOf("stringValue"), FirestoreCodec.nullableString("Lisbon").jsonKeys())
        assertEquals(
            setOf("timestampValue"),
            FirestoreCodec.nullableTimestamp("2026-09-29T12:00:00.000Z").jsonKeys(),
        )
    }

    @Test
    fun `a diary entry with no location or weather and no deletion builds a committable payload`() {
        val fields = RemoteMappers.diaryToFields(
            title = "E2E Sync Check",
            content = "Written while signed in to prove the write path reaches Firestore.",
            entryDate = "2026-09-29",
            moodId = "happy",
            emotions = emptyList(),
            tags = emptyList(),
            isFavorite = false,
            location = null,
            weather = null,
            wordCount = 11,
            createdAt = instant,
            updatedAt = instant,
            deletedAt = null,
        )

        assertEquals(emptyList(), bareNullsIn(FirestoreCodec.map(fields)))
    }

    @Test
    fun `a mood check-in with no note and no deletion builds a committable payload`() {
        val fields = RemoteMappers.moodToFields(
            moodId = "good",
            note = null,
            emotions = listOf("calm"),
            date = "2026-09-29",
            createdAt = instant,
            updatedAt = instant,
            deletedAt = null,
        )

        assertEquals(emptyList(), bareNullsIn(FirestoreCodec.map(fields)))
    }

    @Test
    fun `a populated diary entry survives a round trip through the wire format`() {
        val encoded = FirestoreCodec.map(
            RemoteMappers.diaryToFields(
                title = "Museum with Dad",
                content = "He wanted to see one specific room.",
                entryDate = "2026-09-29",
                moodId = "happy",
                emotions = listOf("calm", "grateful"),
                tags = listOf("family"),
                isFavorite = true,
                location = "Lisbon",
                weather = "sunny",
                wordCount = 7,
                createdAt = instant,
                updatedAt = instant,
                deletedAt = null,
            ),
        )
        val decoded = FirestoreCodec.fieldMap(
            JsonObject(mapOf("fields" to (encoded["mapValue"] as JsonObject)["fields"]!!)),
        )

        assertEquals("Museum with Dad", FirestoreCodec.asString(decoded[DiaryFields.TITLE]))
        assertEquals("Lisbon", FirestoreCodec.asNullableString(decoded[DiaryFields.LOCATION]))
        assertEquals("sunny", FirestoreCodec.asNullableString(decoded[DiaryFields.WEATHER]))
        assertEquals(null, FirestoreCodec.asNullableString(decoded[DiaryFields.DELETED_AT]))
        assertEquals(7L, FirestoreCodec.asLong(decoded[DiaryFields.WORD_COUNT]))
        assertTrue(FirestoreCodec.asBool(decoded[DiaryFields.IS_FAVORITE]) == true)
    }

    @Test
    fun `an explicit remote null reads back as absent`() {
        assertEquals(null, FirestoreCodec.asNullableString(FirestoreCodec.nullValue()))
        assertEquals(null, FirestoreCodec.asString(FirestoreCodec.nullValue()))
        assertTrue(FirestoreCodec.isNullValue(FirestoreCodec.nullValue()))
        assertTrue(FirestoreCodec.isNullValue(JsonNull))
        assertFalse(FirestoreCodec.isNullValue(FirestoreCodec.string("x")))
    }

    private fun JsonElement.jsonKeys(): Set<String> =
        ((this as? JsonObject)?.keys ?: emptySet()).toSet()
}
