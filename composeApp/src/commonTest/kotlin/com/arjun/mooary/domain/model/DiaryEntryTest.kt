package com.arjun.mooary.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class DiaryEntryTest {

    private val date = LocalDate(2024, 3, 14)
    private val created = Instant.parse("2024-03-14T20:15:00Z")

    private fun entry(
        id: String = "e1",
        title: String = "Title",
        content: String = "Some content",
        emotions: List<Emotion> = emptyList(),
        tags: List<String> = emptyList(),
        deletedAt: Instant? = null,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ) = DiaryEntry(
        id = id,
        userId = "local_user",
        title = title,
        content = content,
        entryDate = date,
        mood = Mood.HAPPY,
        emotions = emotions,
        tags = tags,
        isFavorite = false,
        createdAt = created,
        updatedAt = created,
        deletedAt = deletedAt,
        syncStatus = syncStatus,
    )

    @Test
    fun `word count splits on any whitespace and ignores runs`() {
        assertEquals(2, "two words".countWords())
        assertEquals(2, "  spaced \n\t out  ".countWords())
        assertEquals(0, "   ".countWords())
        assertEquals(1, "single".countWords())
    }

    @Test
    fun `word count is derived from content and never stored`() {
        assertEquals(3, entry(content = "one two three").wordCount)
    }

    @Test
    fun `preview flattens newlines and truncates with an ellipsis`() {
        assertEquals("first second", entry(content = "first\nsecond").preview())
        val long = entry(content = "x".repeat(200)).preview(maxLength = 10)
        assertEquals("x".repeat(10) + "…", long)
        assertEquals("short", entry(content = "  short  ").preview(maxLength = 10))
    }

    @Test
    fun `search matches title content tags and emotions and ignores case`() {
        val subject = entry(
            title = "Coast trip",
            content = "Long drive back",
            emotions = listOf(Emotion.fromId("grateful")),
            tags = listOf("travel"),
        )

        assertTrue(subject.matches("coast"))
        assertTrue(subject.matches("DRIVE"))
        assertTrue(subject.matches("trav"))
        assertTrue(subject.matches("grateful"))
        assertTrue(subject.matches(""))
        assertTrue(subject.matches("   "))
        assertFalse(subject.matches("mountain"))
    }

    @Test
    fun `an unknown emotion id still matches on the raw id`() {
        val subject = entry(emotions = listOf(Emotion.fromId("ufo")))
        assertEquals("ufo", subject.emotions.single().id)
        assertTrue(subject.matches("ufo"))
    }

    @Test
    fun `soft-deleted and pending-delete rows are invisible`() {
        assertTrue(entry().isVisible())
        assertFalse(entry(deletedAt = created).isVisible())
        assertFalse(entry(syncStatus = SyncStatus.PENDING_DELETE).isVisible())
        assertFalse(entry(deletedAt = created, syncStatus = SyncStatus.PENDING_DELETE).isVisible())
    }
}

class MoodEntryTest {

    private val date = LocalDate(2024, 3, 14)
    private val first = Instant.parse("2024-03-14T08:00:00Z")
    private val second = Instant.parse("2024-03-14T20:00:00Z")

    private fun mood(
        id: String,
        mood: Mood,
        at: Instant,
        note: String? = null,
        emotions: List<Emotion> = emptyList(),
    ) = MoodEntry(
        id = id,
        userId = "local_user",
        mood = mood,
        note = note,
        emotions = emotions,
        date = date,
        createdAt = at,
        updatedAt = at,
    )

    @Test
    fun `several check-ins on one day collapse into a summary`() {
        val summary = DayMoodSummary(
            date = date,
            entries = listOf(
                mood("m1", Mood.SAD, first, emotions = listOf(Emotion.fromId("tired"))),
                mood("m2", Mood.VERY_HAPPY, second, emotions = listOf(Emotion.fromId("joy"), Emotion.fromId("tired"))),
            ),
        )

        assertEquals(2, summary.count)
        assertEquals(3.0, summary.averageScore)
        // The most recent check-in represents the day.
        assertEquals(Mood.VERY_HAPPY, summary.dominantMood)
        // Emotions are de-duplicated by id across the day's records.
        assertEquals(listOf("tired", "joy"), summary.emotions.map { it.id })
    }

    @Test
    fun `an empty day has no average and no dominant mood`() {
        val summary = DayMoodSummary(date = date, entries = emptyList())
        assertNull(summary.averageScore)
        assertNull(summary.dominantMood)
        assertEquals(0, summary.count)
    }

    @Test
    fun `a draft is only empty when nothing at all was typed`() {
        val base = DiaryDraft(
            userId = "local_user",
            entryId = null,
            title = "",
            content = "",
            entryDate = date,
            mood = Mood.NEUTRAL,
            emotions = emptyList(),
            tags = emptyList(),
            updatedAt = Instant.parse("2024-03-14T20:15:00Z"),
        )

        assertTrue(base.isEmpty)
        // A space is still blank, so a draft with only a stray keypress stays empty.
        assertTrue(base.copy(content = " ").isEmpty)
        assertFalse(base.copy(content = "a").isEmpty)
        assertFalse(base.copy(title = "t").isEmpty)
        assertFalse(base.copy(tags = listOf("x")).isEmpty)
        assertFalse(base.copy(emotions = listOf(Emotion.fromId("joy"))).isEmpty)
    }
}

class MoodTest {

    @Test
    fun `ids survive a round trip and unknown ids degrade to the sentinel`() {
        Mood.entries.forEach { assertEquals(it, Mood.fromId(it.id)) }
        assertEquals(Mood.UNKNOWN, Mood.fromId("from_the_future"))
        assertEquals(Mood.UNKNOWN, Mood.fromId(null))
    }

    @Test
    fun `mood ids are unique so a document cannot resolve to two moods`() {
        assertEquals(Mood.entries.size, Mood.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `valence helpers follow the score`() {
        assertTrue(Mood.VERY_HAPPY.hasPositiveValence)
        assertTrue(Mood.EXCITED.hasPositiveValence)
        assertFalse(Mood.NEUTRAL.hasPositiveValence)
        assertTrue(Mood.VERY_SAD.isNeutralOrWorse)
        assertTrue(Mood.ANXIOUS.isNeutralOrWorse)
        assertFalse(Mood.GOOD.isNeutralOrWorse)
    }

    @Test
    fun `moods are ordered best to worst`() {
        val scores = Mood.byScoreDescending.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }
}

class EmotionTest {

    @Test
    fun `the catalogue resolves known ids`() {
        assertEquals("Joy", Emotion.fromId("joy").name)
    }

    @Test
    fun `an unknown id falls back to the raw id`() {
        val unknown = Emotion.fromId("brand_new_emotion")
        assertEquals("brand_new_emotion", unknown.id)
        assertEquals("brand_new_emotion", unknown.name)
    }

    @Test
    fun `catalogue ids are unique across every group`() {
        val all = EmotionCatalog.positive + EmotionCatalog.neutral + EmotionCatalog.difficult
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }
}
