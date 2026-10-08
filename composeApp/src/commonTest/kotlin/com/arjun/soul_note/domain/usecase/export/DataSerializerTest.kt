package com.arjun.soul_note.domain.usecase.export

import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.ExportBundle
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DataSerializerTest {

    private val created = Instant.parse("2024-03-14T20:15:00Z")
    private val date = LocalDate(2024, 3, 14)

    private val profile = UserProfile(
        id = "u1",
        email = "reader@example.com",
        displayName = "A Reader",
        photoUrl = null,
        createdAt = created,
        updatedAt = created,
    )

    private fun bundle(
        content: String = "A plain day",
        title: String = "Tuesday",
        note: String? = null,
        includeEntry: Boolean = true,
    ) = ExportBundle(
        profile = profile,
        diaryEntries = if (includeEntry) {
            listOf(
                com.arjun.soul_note.domain.model.DiaryEntry(
                    id = "e1",
                    userId = "u1",
                    title = title,
                    content = content,
                    entryDate = date,
                    mood = Mood.HAPPY,
                    emotions = listOf(Emotion.fromId("calm"), Emotion.fromId("joy")),
                    tags = listOf("travel", "food"),
                    isFavorite = true,
                    createdAt = created,
                    updatedAt = created,
                ),
            )
        } else {
            emptyList()
        },
        moodEntries = listOf(
            com.arjun.soul_note.domain.model.MoodEntry(
                id = "m1",
                userId = "u1",
                mood = Mood.GOOD,
                note = note,
                emotions = listOf(Emotion.fromId("calm")),
                date = date,
                createdAt = created,
                updatedAt = created,
            ),
        ),
        exportedAt = created,
    )

    @Test
    fun `json is valid and carries the schema version and profile`() {
        val root = Json.parseToJsonElement(DataSerializer.toJson(bundle())).jsonObject

        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("2024-03-14T20:15:00.000Z", root.getValue("exportedAt").jsonPrimitive.content)

        val profileJson = root.getValue("profile").jsonObject
        assertEquals("u1", profileJson.getValue("id").jsonPrimitive.content)
        assertEquals("reader@example.com", profileJson.getValue("email").jsonPrimitive.content)
    }

    @Test
    fun `json keeps entry text intact including newlines and quotes`() {
        val content = "He said \"hello\".\nThen left."
        val root = Json.parseToJsonElement(DataSerializer.toJson(bundle(content = content))).jsonObject
        val entry = root.getValue("diaryEntries").jsonArray.single().jsonObject

        assertEquals(content, entry.getValue("content").jsonPrimitive.content)
        assertEquals("happy", entry.getValue("mood").jsonPrimitive.content)
        assertEquals(listOf("calm", "joy"), entry.getValue("emotions").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("travel", "food"), entry.getValue("tags").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(entry.getValue("isFavorite").jsonPrimitive.boolean)
        // The word count is derived on the way out, so the export cannot disagree with it.
        assertEquals(5, entry.getValue("wordCount").jsonPrimitive.int)
    }

    @Test
    fun `json serialises an empty account without dropping the tables`() {
        val root = Json.parseToJsonElement(DataSerializer.toJson(bundle(includeEntry = false))).jsonObject

        assertTrue(root.getValue("diaryEntries").jsonArray.isEmpty())
        assertEquals(1, root.getValue("moods").jsonArray.size)
    }

    @Test
    fun `csv contains the three marker tables in a fixed order`() {
        val csv = DataSerializer.toCsv(bundle())
        val markers = csv.lines().filter { it.startsWith("# table:") }

        assertEquals(
            listOf("# table: profile", "# table: diaryEntries", "# table: moods"),
            markers,
        )
    }

    @Test
    fun `csv flattens list columns with a pipe so a row stays one line`() {
        val csv = DataSerializer.toCsv(bundle())
        val entryRow = csv.lines().first { it.startsWith("e1,") }

        assertTrue(entryRow.contains("calm|joy"), "expected pipe-joined emotions in $entryRow")
        assertTrue(entryRow.contains("travel|food"), "expected pipe-joined tags in $entryRow")
        assertEquals("2024-03-14", entryRow.split(",")[3])
    }

    @Test
    fun `csv quotes fields containing a comma a quote or a newline`() {
        assertEquals("plain", csvField("plain"))
        assertEquals("\"a,b\"", csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", csvField("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", csvField("line1\r\nline2"))
    }

    /** [DataSerializer.toCsvField] is a member extension, so it needs a receiver in scope. */
    private fun csvField(value: String): String = DataSerializer.run { value.toCsvField() }

    @Test
    fun `csv escaping keeps a multi-line entry on a single parseable row`() {
        val csv = DataSerializer.toCsv(bundle(content = "first line\nsecond, \"quoted\""))

        // The newline and the comma stay inside one quoted field, so the embedded quotes
        // are doubled and no extra row is produced.
        assertTrue(csv.contains("\"first line\nsecond, \"\"quoted\"\"\""))
        assertEquals(1, csv.lines().count { it.startsWith("e1,") })
    }

    @Test
    fun `csv emits empty cells for absent optional values rather than null`() {
        val csv = DataSerializer.toCsv(bundle(note = null))
        val moodRow = csv.lines().first { it.startsWith("m1,") }

        assertFalse(moodRow.contains("null"), "absent values must not be the string null: $moodRow")
        val columns = moodRow.split(",")
        // id, mood, note, emotions, date, createdAt, updatedAt
        assertEquals(7, columns.size)
        assertEquals("", columns[2])
        assertEquals("calm", columns[3])
    }

    @Test
    fun `csv and json agree on the number of records`() {
        val subject = bundle()
        val csvEntries = DataSerializer.toCsv(subject).lines().count { it.startsWith("e1,") }
        val jsonEntries = Json
            .parseToJsonElement(DataSerializer.toJson(subject))
            .jsonObject.getValue("diaryEntries").jsonArray.size

        assertEquals(subject.diaryEntryCount, csvEntries)
        assertEquals(subject.diaryEntryCount, jsonEntries)
        assertEquals(subject.moodEntryCount, 1)
    }
}
