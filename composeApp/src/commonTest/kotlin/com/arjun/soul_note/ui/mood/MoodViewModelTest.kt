package com.arjun.soul_note.ui.mood

import com.arjun.soul_note.core.analytics.AnalyticsEventCall
import com.arjun.soul_note.core.analytics.AnalyticsSink
import com.arjun.soul_note.core.analytics.AnalyticsTracker
import com.arjun.soul_note.domain.model.DayMoodSummary
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodEntry
import com.arjun.soul_note.domain.model.MoodFilter
import com.arjun.soul_note.domain.model.Page
import com.arjun.soul_note.domain.model.SyncStatus
import com.arjun.soul_note.domain.repository.MoodRepository
import com.arjun.soul_note.domain.usecase.mood.DeleteMoodUseCase
import com.arjun.soul_note.domain.usecase.mood.GetMoodHistoryUseCase
import com.arjun.soul_note.domain.usecase.mood.RecordMoodUseCase
import com.arjun.soul_note.domain.usecase.mood.UpdateMoodUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Editing and deleting a mood check-in is the one place this screen can destroy a user's own
 * words, and both operations sit on a card full of other controls. Three things are worth
 * pinning: an edit must write the row that was tapped rather than recording a second one, a
 * cancel must not leave the sheet pointing at a row the user decided not to change, and a
 * failed delete must reach the user rather than closing silently.
 */
class MoodViewModelTest {

    private val now = Instant.parse("2024-04-01T12:00:00Z")
    private val today = LocalDate(2024, 4, 1)
    private val userId = "user-1"

    private class FakeMoodRepository(
        initial: List<MoodEntry> = emptyList(),
        private val failUpdateWith: Throwable? = null,
        private val failDeleteWith: Throwable? = null,
    ) : MoodRepository {
        val entries = MutableStateFlow(initial)
        var updateCalls = 0
        var recordCalls = 0
        var deletedIds = mutableListOf<String>()
        var lastUpdate: MoodEntry? = null

        override fun observeEntries(userId: String): Flow<List<MoodEntry>> = entries

        override fun observeFilteredEntries(userId: String, filter: MoodFilter): Flow<List<MoodEntry>> = entries

        override fun observeEntriesPage(
            userId: String,
            filter: MoodFilter,
            limit: Int,
            offset: Int,
        ): Flow<Page<MoodEntry>> = entries.map { list ->
            Page(
                items = list.drop(offset).take(limit),
                totalCount = list.size,
                hasMore = offset + limit < list.size,
            )
        }

        override fun observeEntry(userId: String, moodId: String): Flow<MoodEntry?> =
            entries.map { list -> list.firstOrNull { it.id == moodId } }

        override fun observeRecentEntries(userId: String, limit: Int): Flow<List<MoodEntry>> =
            entries.map { it.take(limit) }

        override fun observeEntriesForDate(userId: String, date: LocalDate): Flow<List<MoodEntry>> =
            entries.map { list -> list.filter { it.date == date } }

        override fun observeDaySummaries(userId: String): Flow<List<DayMoodSummary>> =
            MutableStateFlow(emptyList())

        override fun observeEntriesInRange(
            userId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<MoodEntry>> = entries.map { list -> list.filter { it.date in from..to } }

        override fun observeCount(userId: String): Flow<Int> = entries.map { it.size }

        override suspend fun getEntry(userId: String, moodId: String): Result<MoodEntry?> =
            Result.success(entries.value.firstOrNull { it.id == moodId })

        override suspend fun recordMood(entry: MoodEntry): Result<MoodEntry> {
            recordCalls++
            entries.value = listOf(entry) + entries.value
            return Result.success(entry)
        }

        override suspend fun updateMood(entry: MoodEntry): Result<MoodEntry> {
            failUpdateWith?.let { return Result.failure(it) }
            updateCalls++
            lastUpdate = entry
            entries.value = entries.value.map { if (it.id == entry.id) entry else it }
            return Result.success(entry)
        }

        override suspend fun deleteMood(userId: String, moodId: String): Result<Unit> {
            failDeleteWith?.let { return Result.failure(it) }
            deletedIds += moodId
            entries.value = entries.value.filterNot { it.id == moodId }
            return Result.success(Unit)
        }

        override suspend fun getAllForExport(userId: String): Result<List<MoodEntry>> =
            Result.success(entries.value)

        override suspend fun getAllInRange(
            userId: String,
            from: LocalDate,
            to: LocalDate,
        ): Result<List<MoodEntry>> = Result.success(entries.value.filter { it.date in from..to })
    }

    private fun analytics() = AnalyticsTracker(
        object : AnalyticsSink {
            override fun send(call: AnalyticsEventCall) = Unit
        },
    )

    /**
     * Builds the view model with a collector attached.
     *
     * `state` is a `WhileSubscribed` flow, so with nothing collecting it the combination never
     * runs and every read returns the initial value -- which would make these tests pass for
     * reasons unrelated to the behaviour under test.
     */
    private fun TestScope.viewModel(repository: FakeMoodRepository): MoodViewModel {
        val model = MoodViewModel(
            observeHistory = GetMoodHistoryUseCase(repository),
            recordMood = RecordMoodUseCase(
                repository = repository,
                analytics = analytics(),
                now = { now },
                newId = { "generated-id" },
            ),
            updateMood = UpdateMoodUseCase(repository, analytics()) { now },
            deleteMood = DeleteMoodUseCase(repository, analytics()),
            userId = userId,
            today = today,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { }
        }
        return model
    }

    /**
     * Waits for the state to satisfy [predicate].
     *
     * `BaseViewModel` runs its scope on the default dispatcher rather than the test
     * scheduler, so both the combine behind [MoodViewModel.state] and the `launch` inside
     * `record`/`delete` complete on another thread. Reading `state.value` straight after an
     * action therefore races both, and these tests would assert on a stale value.
     */
    private suspend fun await(
        model: MoodViewModel,
        predicate: (MoodState) -> Boolean,
    ): MoodState = model.state.first(predicate)

    /**
     * Waits for the stored history to reach the state.
     *
     * `stateIn`'s initial value is `MoodState(date = today)`, which is `isLoading = true` with
     * an empty `recent` and no `editing`. Every "after this action" predicate below is true of
     * that value -- an empty list contains no deleted id and no editing entry -- so a test that
     * skips this passes against a state the action never touched.
     */
    private suspend fun loaded(model: MoodViewModel) =
        await(model) { !it.isLoading && it.recent.isNotEmpty() }

    private fun entry(
        id: String,
        mood: Mood = Mood.GOOD,
        note: String? = "first note",
        date: LocalDate = LocalDate(2024, 3, 30),
    ) = MoodEntry(
        id = id,
        userId = userId,
        mood = mood,
        note = note,
        emotions = emptyList(),
        date = date,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `tapping edit loads that entry into the sheet rather than the sheet defaults`() = runTest {
        val stored = entry("mood-1", mood = Mood.ANXIOUS, note = "restless")
        val model = viewModel(FakeMoodRepository(listOf(stored)))
        loaded(model)

        model.onEdit(stored)
        val state = await(model) { it.isEditing }

        assertEquals("mood-1", assertNotNull(state.editing).id)
        // Not Mood.NEUTRAL and not a blank note: an edit starts from what was recorded.
        assertEquals(Mood.ANXIOUS, state.selectedMood)
        assertEquals("restless", state.note)
        assertEquals(stored.date, state.date)
    }

    @Test
    fun `selecting a check-in date is retained by the view model`() = runTest {
        val model = viewModel(FakeMoodRepository(listOf(entry("mood-1"))))
        loaded(model)
        val selectedDate = LocalDate(2024, 3, 14)

        model.onDateChanged(selectedDate)

        assertEquals(selectedDate, await(model) { it.date == selectedDate }.date)
    }

    @Test
    fun `saving an edit updates the tapped row instead of recording another one`() = runTest {
        val repository = FakeMoodRepository(listOf(entry("mood-1")))
        val model = viewModel(repository)
        loaded(model)

        model.onEdit(assertNotNull(repository.entries.value.firstOrNull()))
        await(model) { it.isEditing }
        model.onMoodSelected(Mood.HAPPY)
        model.onNoteChanged("calmer now")
        model.record()
        await(model) { !it.isEditing }

        assertEquals(1, repository.updateCalls)
        assertEquals(0, repository.recordCalls)
        val written = assertNotNull(repository.lastUpdate)
        assertEquals("mood-1", written.id)
        assertEquals(Mood.HAPPY, written.mood)
        assertEquals("calmer now", written.note)
        // The SQL update sets every mutable column, so created-at has to survive the write.
        assertEquals(now, written.createdAt)
        assertEquals(SyncStatus.PENDING_UPDATE, written.syncStatus)
    }

    @Test
    fun `a saved edit clears the sheet so a second tap records a new check-in`() = runTest {
        val repository = FakeMoodRepository(listOf(entry("mood-1")))
        val model = viewModel(repository)
        loaded(model)

        model.onEdit(assertNotNull(repository.entries.value.firstOrNull()))
        await(model) { it.isEditing }
        model.record()
        await(model) { !it.isEditing }

        model.record()
        // Waiting on the history growing, because `!isRecording` is true of the state that
        // existed before this tap and would pass without the second save ever running.
        await(model) { it.recent.size == 2 }

        assertEquals(1, repository.updateCalls)
        assertEquals(1, repository.recordCalls)
    }

    @Test
    fun `cancelling an edit returns the sheet to a blank entry for today`() = runTest {
        val stored = entry("mood-1", mood = Mood.SAD, date = LocalDate(2024, 1, 5))
        val model = viewModel(FakeMoodRepository(listOf(stored)))
        loaded(model)

        model.onEdit(stored)
        await(model) { it.isEditing }
        model.cancelEdit()
        val state = await(model) { !it.isEditing }

        assertNull(state.editing)
        assertEquals("", state.note)
        assertEquals(Mood.NEUTRAL, state.selectedMood)
        // Back to the session's today, not the edited row's day: otherwise a cancel would
        // silently leave the sheet recording into someone else's date.
        assertEquals(today, state.date)
    }

    @Test
    fun `a failed update is reported and keeps the entry open for another attempt`() = runTest {
        val repository = FakeMoodRepository(
            listOf(entry("mood-1")),
            failUpdateWith = IllegalStateException("disk full"),
        )
        val model = viewModel(repository)
        loaded(model)

        model.onEdit(assertNotNull(repository.entries.value.firstOrNull()))
        await(model) { it.isEditing }
        model.record()
        val failed = await(model) { it.error != null }

        assertNotNull(failed.error)
        assertEquals(0, repository.updateCalls)
        // Losing the edit on a failure would throw away the user's retyped note.
        assertTrue(failed.isEditing)
    }

    @Test
    fun `deleting removes the row and clears the sheet if that row was being edited`() = runTest {
        val repository = FakeMoodRepository(listOf(entry("mood-1"), entry("mood-2")))
        val model = viewModel(repository)
        loaded(model)

        val target = assertNotNull(repository.entries.value.firstOrNull { it.id == "mood-1" })
        model.onEdit(target)
        await(model) { it.isEditing }
        model.delete(target)
        await(model) { it.recent.none { e -> e.id == "mood-1" } }

        assertEquals(listOf("mood-1"), repository.deletedIds)
        assertTrue(repository.entries.value.none { it.id == "mood-1" })
        // The sheet must not keep pointing at a row that no longer exists.
        await(model) { !it.isEditing }
    }

    @Test
    fun `a failed delete is reported and leaves the entry in place`() = runTest {
        val repository = FakeMoodRepository(
            listOf(entry("mood-1")),
            failDeleteWith = IllegalStateException("network down"),
        )
        val model = viewModel(repository)
        loaded(model)

        model.delete(assertNotNull(repository.entries.value.firstOrNull()))
        val failed = await(model) { it.error != null }

        assertNotNull(failed.error)
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(1, repository.entries.value.size)

        model.dismissError()
        await(model) { it.error == null }
    }
}
