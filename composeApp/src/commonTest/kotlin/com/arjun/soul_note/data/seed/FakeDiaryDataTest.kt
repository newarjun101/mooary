package com.arjun.soul_note.data.seed

import com.arjun.soul_note.domain.model.EmotionCatalog
import com.arjun.soul_note.domain.model.Mood
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeDiaryDataTest {

    private val today = LocalDate(2026, 9, 27)
    private val zone = TimeZone.UTC
    private val entries = FakeDiaryData.diaryEntries("local_user", today, zone)
    private val moods = FakeDiaryData.moodEntries("local_user", today, zone)

    @Test
    fun `produces at least thirty diary entries`() {
        assertTrue(
            entries.size >= 30,
            "expected at least 30 demo entries, got ${entries.size}",
        )
        assertEquals(FakeDiaryData.ENTRY_COUNT, entries.size)
    }

    @Test
    fun `produces a useful number of mood check-ins`() {
        assertTrue(moods.size >= 15, "expected check-ins too, got ${moods.size}")
    }

    @Test
    fun `is deterministic so screenshots stay comparable between runs`() {
        val again = FakeDiaryData.diaryEntries("local_user", today, zone)
        assertEquals(entries, again)
        assertEquals(moods, FakeDiaryData.moodEntries("local_user", today, zone))
    }

    @Test
    fun `entry ids are unique`() {
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }

    @Test
    fun `mood ids are unique and cannot collide with entry ids`() {
        assertEquals(moods.size, moods.map { it.id }.toSet().size)
        val overlap = moods.map { it.id }.toSet() intersect entries.map { it.id }.toSet()
        assertTrue(overlap.isEmpty(), "seed ids must not collide: $overlap")
    }

    @Test
    fun `all entries are scoped to the requested user`() {
        assertTrue(entries.all { it.userId == "local_user" })
        assertTrue(moods.all { it.userId == "local_user" })
    }

    @Test
    fun `no entry lands in the future`() {
        assertTrue(
            entries.all { it.entryDate <= today },
            "seed data must never be dated after today",
        )
        assertTrue(moods.all { it.date <= today })
    }

    @Test
    fun `dates span several months so streaks and the month tile are non-zero`() {
        val distinctDates = entries.map { it.entryDate }.toSet()
        assertTrue(distinctDates.size >= 25, "expected varied dates, got ${distinctDates.size}")
        val span = entries.minOf { it.entryDate }.daysUntil(entries.maxOf { it.entryDate })
        assertTrue(span >= 60, "expected a multi-month history, got $span days")
    }

    @Test
    fun `createdAt is consistent with entryDate`() {
        val mismatched = entries.filter { it.createdAt.toLocalDateTime(zone).date != it.entryDate }
        assertTrue(mismatched.isEmpty(), "timestamps drifted from entry dates")
    }

    @Test
    fun `updatedAt equals createdAt because demo rows are never edited`() {
        assertTrue(entries.all { it.updatedAt == it.createdAt })
    }

    @Test
    fun `every mood is a real catalogue value`() {
        val known = Mood.entries.toSet()
        val unknown = entries.map { it.mood }.toSet() - known
        assertTrue(unknown.isEmpty(), "unknown moods: $unknown")
    }

    @Test
    fun `every emotion resolves to the catalogue so no record shows a raw id`() {
        val unknown = entries.flatMap { it.emotions }
            .map { it.id }
            .filterNot { id -> EmotionCatalog.byId.containsKey(id) }
            .toSet()
        assertTrue(unknown.isEmpty(), "unknown emotion ids: $unknown")

        val unknownMoods = moods.flatMap { it.emotions }
            .map { it.id }
            .filterNot { id -> EmotionCatalog.byId.containsKey(id) }
            .toSet()
        assertTrue(unknownMoods.isEmpty(), "unknown check-in emotion ids: $unknownMoods")
    }

    @Test
    fun `no single emotion dominates so the filter chips stay useful`() {
        val total = entries.size
        val counts = entries.flatMap { it.emotions }.groupingBy { it.id }.eachCount()
        val overused = counts.filterValues { it > total / 3 }
        assertTrue(overused.isEmpty(), "over-used emotions: $overused")
    }

    @Test
    fun `no entry repeats the same emotion twice`() {
        val repeated = entries
            .map { entry -> entry.emotions.map { it.id } }
            .filter { ids -> ids.size != ids.toSet().size }
        assertTrue(repeated.isEmpty(), "entries with duplicate emotions: $repeated")
    }

    @Test
    fun `content is substantive enough for a preview to be worth reading`() {
        assertTrue(entries.all { it.content.length >= 80 }, "demo content reads as a stub")
        assertTrue(entries.all { it.title.isNotBlank() })
        assertTrue(entries.all { it.wordCount >= 15 })
    }

    @Test
    fun `preview truncates long content instead of dumping the whole entry`() {
        val longest = entries.maxBy { it.content.length }
        assertTrue(longest.preview().length <= 141)
    }

    @Test
    fun `tags are lowercase and comma-free so filtering works`() {
        val bad = entries.flatMap { it.tags }.filter { it != it.lowercase() || it.contains(',') }
        assertTrue(bad.isEmpty(), "unusable tags: $bad")
    }

    @Test
    fun `seed rows are stored as synced so they never enter the sync queue`() {
        assertTrue(entries.all { it.syncStatus == com.arjun.soul_note.domain.model.SyncStatus.SYNCED })
        assertTrue(moods.all { it.syncStatus == com.arjun.soul_note.domain.model.SyncStatus.SYNCED })
    }

    @Test
    fun `some entries are favourites so that filter is not empty`() {
        assertTrue(entries.count { it.isFavorite } >= 3)
    }

    @Test
    fun `every entry is visible and nothing is tombstoned`() {
        assertTrue(entries.all { it.isVisible() })
        assertTrue(entries.all { it.deletedAt == null })
    }

    @Test
    fun `search matches seeded text so the search field can be exercised`() {
        val hit = entries.first { it.matches("dentist") }
        assertTrue(hit.matches("DENTIST"), "search should be case-insensitive")
        assertTrue(entries.any { it.matches("running") }, "tag search should hit")
    }

    @Test
    fun `isSeedId identifies generated rows and nothing else`() {
        assertTrue(FakeDiaryData.isSeedId(FakeDiaryData.seedEntryId(0)))
        assertTrue(FakeDiaryData.isSeedId(FakeDiaryData.seedMoodId(33)))
        assertTrue(!FakeDiaryData.isSeedId("550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `seed ids are zero padded and stable`() {
        assertEquals("seed-entry-01", FakeDiaryData.seedEntryId(0))
        assertEquals("seed-entry-34", FakeDiaryData.seedEntryId(33))
        assertEquals("seed-mood-01", FakeDiaryData.seedMoodId(0))
        assertEquals("seed-entry-01", FakeDiaryData.seedEntryId(0))
    }
}
