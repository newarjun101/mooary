package com.arjun.mooary.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arjun.mooary.core.datetime.TimeProvider
import com.arjun.mooary.core.datetime.TimeZoneProvider
import com.arjun.mooary.core.logging.AppLogger
import com.arjun.mooary.data.local.db.DiaryDatabase
import com.arjun.mooary.data.seed.FakeDiaryData
import com.arjun.mooary.data.seed.SeedDemoDataUseCase
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.UserProfile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Upsert semantics against a real SQLite database.
 *
 * This guards a crash on every second launch of a debug build: `upsertDiary` was named as an
 * upsert but issued a bare `INSERT`, so re-seeding the demo rows with their stable ids threw
 * `SQLITE_CONSTRAINT_PRIMARYKEY` and killed the process before the first screen appeared.
 * Nothing about that is visible to the compiler or to a mocked repository, which is why it
 * needs an actual database rather than a fake.
 */
class LocalStoreUpsertTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val database = DiaryDatabase(driver).also { DiaryDatabase.Schema.create(driver) }
    private val store = LocalStore(database, UnconfinedTestDispatcher(), SilentLogger)

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun entry(id: String, title: String = "title", favorite: Boolean = false) = DiaryEntry(
        id = id,
        userId = USER,
        title = title,
        content = "content",
        entryDate = DATE,
        mood = Mood.HAPPY,
        emotions = emptyList(),
        tags = emptyList(),
        isFavorite = favorite,
        createdAt = EPOCH,
        updatedAt = EPOCH,
    )

    private fun mood(id: String, mood: Mood = Mood.HAPPY) = MoodEntry(
        id = id,
        userId = USER,
        mood = mood,
        note = null,
        emotions = emptyList(),
        date = DATE,
        createdAt = EPOCH,
        updatedAt = EPOCH,
    )

    private suspend fun seeder() = SeedDemoDataUseCase(
        local = store,
        timeProvider = FixedTimeProvider,
        timeZoneProvider = TimeZoneProvider(),
    )

    @Test
    fun `writing the same diary id twice replaces rather than throwing`() = runTest {
        store.upsertDiary(entry("a", title = "first"))
        store.upsertDiary(entry("a", title = "second"))

        val rows = store.observeVisibleDiary(USER).first()
        assertEquals(1, rows.size, "the second write must replace, not duplicate")
        assertEquals("second", rows.single().title, "the second write must win")
    }

    @Test
    fun `writing the same mood id twice replaces rather than throwing`() = runTest {
        store.upsertMood(mood("m1", Mood.HAPPY))
        store.upsertMood(mood("m1", Mood.SAD))

        val rows = store.observeRecentMoods(USER, 10).first()
        assertEquals(1, rows.size)
        assertEquals(Mood.SAD, rows.single().mood)
    }

    @Test
    fun `replace writes every column rather than resetting the rest to defaults`() = runTest {
        // OR REPLACE deletes the old row before inserting, so a column left out of the
        // statement would silently come back as its default. All columns are named; this is
        // what would notice if one ever were not.
        store.upsertDiary(entry("a", title = "first", favorite = true))
        store.upsertDiary(entry("a", title = "second"))

        val row = store.observeVisibleDiary(USER).first().single()
        assertEquals("second", row.title)
        assertEquals(false, row.isFavorite, "the second write's own value is applied")
        assertEquals(USER, row.userId)
        assertEquals(DATE, row.entryDate)
        assertEquals(Mood.HAPPY, row.mood)
    }

    @Test
    fun `seeding twice is a no-op rather than a crash`() = runTest {
        val useCase = seeder()

        val first = useCase(USER)
        val afterFirst = store.observeVisibleDiary(USER).first().size
        // This second call is the one that used to kill the app on the next launch.
        val second = useCase(USER)
        val afterSecond = store.observeVisibleDiary(USER).first().size

        assertTrue(first > 0, "the first run should have written demo rows")
        assertEquals(0, second, "a diary that already holds rows is left alone")
        assertEquals(afterFirst, afterSecond, "the second run must not duplicate rows")
    }

    @Test
    fun `re-seeding does not revert an edit to a demo row`() = runTest {
        val useCase = seeder()
        val seeded = useCase(USER)
        assertTrue(seeded > 0)

        // Editing a demo row and relaunching used to lose the edit: the old guard allowed a
        // diary of nothing but seed rows, and the re-seed upserted the demo text over the
        // top. It presented as "the edit did not save" rather than as seeding.
        val target = store.observeVisibleDiary(USER).first().first()
        store.upsertDiary(target.copy(title = "my own words"))

        useCase(USER)

        val titles = store.observeVisibleDiary(USER).first().map { it.title }
        assertTrue(
            "my own words" in titles,
            "the edit must survive a relaunch; titles were $titles",
        )
    }

    @Test
    fun `seeding is skipped once the user has written an entry of their own`() = runTest {
        store.upsertDiary(entry("mine", title = "not seed data"))

        assertEquals(0, seeder()(USER), "demo rows must never be mixed into a real journal")
        assertEquals(1, store.observeVisibleDiary(USER).first().size)
    }

    @Test
    fun `every generated seed id is recognised by isSeedId`() {
        // The re-seed guard is `existing.any { !isSeedId(it.id) }`, so an id the guard failed to
        // recognise would make the app treat its own demo rows as the user's and never refresh.
        val ids = FakeDiaryData.diaryEntries(USER, DATE, TimeZone.UTC).map { it.id } +
            FakeDiaryData.moodEntries(USER, DATE, TimeZone.UTC).map { it.id }

        assertTrue(ids.isNotEmpty())
        assertTrue(ids.all { FakeDiaryData.isSeedId(it) }, "unrecognised seed id in $ids")
    }

    @Test
    fun `seed ids are distinct across entries and moods`() {
        val entryIds = FakeDiaryData.diaryEntries(USER, DATE, TimeZone.UTC).map { it.id }
        val moodIds = FakeDiaryData.moodEntries(USER, DATE, TimeZone.UTC).map { it.id }

        assertEquals(entryIds.size, entryIds.distinct().size, "duplicate entry ids would collide on re-seed")
        assertEquals(moodIds.size, moodIds.distinct().size, "duplicate mood ids would collide on re-seed")
        assertTrue(entryIds.intersect(moodIds.toSet()).isEmpty(), "entry and mood ids must not overlap")
    }

    @Test
    fun `the profile survives the first sync pull re-applying what sign-up stored`() = runTest {
        // The pull applies the remote profile over the row sign-up already wrote, and it
        // does so for every account that had ever synced. A bare INSERT aborted the whole
        // pass here, so the entry push stayed pending and the counters never moved.
        store.upsertProfile(profile(email = "one@example.invalid"))
        store.upsertProfile(profile(email = "two@example.invalid", displayName = "Two"))

        val stored = store.getProfile(USER)

        assertEquals("two@example.invalid", stored?.email)
        assertEquals("Two", stored?.displayName)
    }

    private fun profile(email: String, displayName: String? = null) = UserProfile(
        id = USER,
        email = email,
        displayName = displayName,
        photoUrl = null,
        createdAt = EPOCH,
        updatedAt = EPOCH,
    )

    private object SilentLogger : AppLogger {
        override fun debug(tag: String, message: () -> String) = Unit
        override fun info(tag: String, message: () -> String) = Unit
        override fun warn(tag: String, throwable: Throwable?, message: () -> String) = Unit
        override fun error(tag: String, throwable: Throwable?, message: () -> String) = Unit
    }

    private object FixedTimeProvider : TimeProvider {
        override fun now(): Instant = Instant.parse("2024-05-01T09:00:00Z")
    }

    private companion object {
        const val USER = "test-user"
        val DATE = LocalDate(2024, 5, 1)
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
