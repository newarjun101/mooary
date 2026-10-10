package com.arjun.mooary.data.seed

import com.arjun.mooary.core.datetime.LocalDates
import com.arjun.mooary.domain.model.DiaryEntry
import com.arjun.mooary.domain.model.Emotion
import com.arjun.mooary.domain.model.EmotionCatalog
import com.arjun.mooary.domain.model.Mood
import com.arjun.mooary.domain.model.MoodEntry
import com.arjun.mooary.domain.model.SyncStatus
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * Demo content for design review and manual testing.
 *
 * Deliberately **deterministic**: nothing here calls a random source, so relaunching the
 * app produces byte-identical entries. That keeps screenshots and manual QA comparable
 * between runs, and lets the test suite assert on the output instead of on a shape.
 *
 * Everything is scoped to a caller-supplied [userId] and is written as [SyncStatus.SYNCED]
 * so demo content never queues up as unsent work for the sync manager to push at a
 * backend that does not exist.
 */
object FakeDiaryData {

    /** Enough to fill a diary list, a month of calendar cells and the analytics roll-up. */
    const val ENTRY_COUNT: Int = 35

    /** Fewer than the entries: a check-in and a diary entry on the same day is common. */
    const val MOOD_COUNT: Int = 22

    /** Longest look-back, so streaks and the "entries this month" tile are non-zero. */
    private const val HISTORY_DAYS: Int = 118

    private data class Story(
        val daysAgo: Int,
        val title: String,
        val content: String,
        val mood: Mood,
        val emotions: List<String>,
        val tags: List<String>,
        val isFavorite: Boolean = false,
        val location: String? = null,
        val weather: String? = null,
        val hour: Int = 21,
        val minute: Int = 15,
    )

    private data class CheckIn(
        val daysAgo: Int,
        val mood: Mood,
        val note: String?,
        val emotions: List<String>,
        val hour: Int = 8,
        val minute: Int = 30,
    )

    private val stories: List<Story> = listOf(
        Story(
            daysAgo = 0, title = "Slow morning, no alarm",
            content = "Woke up before the alarm for once and just lay there listening to the " +
                "street. Made coffee properly instead of drinking it over the sink. Started the " +
                "day an hour late and lost nothing at all.",
            mood = Mood.GOOD, emotions = listOf("calm", "grateful"), tags = listOf("daily", "rest"),
            isFavorite = true, weather = "Clear", hour = 8, minute = 40,
        ),
        Story(
            daysAgo = 1, title = "Shipped the thing",
            content = "Three weeks of off-and-on work finally went out. The moment it merged I " +
                "sat very still for a minute, waiting for the panic to arrive. It didn't.",
            mood = Mood.VERY_HAPPY, emotions = listOf("excited", "confident", "joy"),
            tags = listOf("work", "milestone"), isFavorite = true, location = "Office",
        ),
        Story(
            daysAgo = 2, title = "Ran three kilometres, hated it, will do it again",
            content = "It is never pleasant at the start. Legs complained the whole way and I " +
                "stopped twice. Still finished, and the cold air afterwards was worth it.",
            mood = Mood.GOOD, emotions = listOf("motivated"), tags = listOf("health", "running"),
            weather = "Cold, windy",
        ),
        Story(
            daysAgo = 4, title = "Called Mum",
            content = "Forty minutes, mostly about the neighbour's roof. She sounded lighter " +
                "than last time. I should call before it drifts to a monthly thing again.",
            mood = Mood.HAPPY, emotions = listOf("love", "grateful"), tags = listOf("family"),
            hour = 19, minute = 5,
        ),
        Story(
            daysAgo = 5, title = "Rain all day",
            content = "Genuinely grey from morning to night. Sat by the window and did almost " +
                "nothing, which I think I needed. Read the same page four times.",
            mood = Mood.NEUTRAL, emotions = listOf("tired", "calm"), tags = listOf("rest"),
            weather = "Heavy rain",
        ),
        Story(
            daysAgo = 7, title = "Sunday cooking",
            content = "Made the aubergine thing from the paper, the one I had cut out months " +
                "ago and never tried. It worked, mostly. Next time with less lemon.",
            mood = Mood.HAPPY, emotions = listOf("joy"), tags = listOf("cooking", "weekend"),
            isFavorite = true, location = "Home", hour = 18, minute = 30,
        ),
        Story(
            daysAgo = 9, title = "Bad review in the morning",
            content = "Woke up to something that stung. Spent two hours drafting a reply I did " +
                "not send. Wrote the technical facts instead and that felt better than the " +
                "rebuttal would have.",
            mood = Mood.ANXIOUS, emotions = listOf("stressed", "sad"), tags = listOf("work"),
            hour = 7, minute = 20,
        ),
        Story(
            daysAgo = 11, title = "Ten years today",
            content = "Ten years in this city. I moved here with one suitcase and genuinely " +
                "did not know anyone. Walked past the old flat on the way home.",
            mood = Mood.NEUTRAL, emotions = listOf("confused", "hopeful"), tags = listOf("milestone"),
            isFavorite = true, location = "Old town", weather = "Overcast",
        ),
        Story(
            daysAgo = 13, title = "Sorted the attic",
            content = "Found a box of letters and the camera from university. Sat on the floor " +
                "of the attic for an hour going through them. Kept far less than I expected " +
                "to and felt oddly lighter.",
            mood = Mood.GOOD, emotions = listOf("calm", "grateful"), tags = listOf("home", "family"),
            hour = 15, minute = 45,
        ),
        Story(
            daysAgo = 15, title = "Dentist, as feared",
            content = "Two hours of my life in a reclined chair listening to someone's radio. " +
                "No cavities, which is the only news I wanted.",
            mood = Mood.TIRED, emotions = listOf("tired"), tags = listOf("health"),
            hour = 11, minute = 0,
        ),
        Story(
            daysAgo = 17, title = "Learned to actually parallel park",
            content = "Twelve attempts. The trick is the third turn of the wheel, not the " +
                "first. Drove away feeling like I had done something difficult and small.",
            mood = Mood.EXCITED, emotions = listOf("confident", "motivated"), tags = listOf("driving", "milestone"),
            location = "Car park", hour = 16, minute = 20,
        ),
        Story(
            daysAgo = 19, title = "Argument I did not want to have",
            content = "Said the thing too fast and too loud. Knew it halfway through and kept " +
                "going, which is the part I actually mind. Apologised properly afterwards, " +
                "which I think is most of it.",
            mood = Mood.ANGRY, emotions = listOf("angry", "anxious"), tags = listOf("family"),
            hour = 22, minute = 10,
        ),
        Story(
            daysAgo = 21, title = "First proper frost",
            content = "Grass was white at seven. Made tea and stood at the window doing nothing " +
                "useful, which is a good use of a Saturday.",
            mood = Mood.HAPPY, emotions = listOf("calm", "joy"), tags = listOf("weather", "rest"),
            isFavorite = true, weather = "Frost",
        ),
        Story(
            daysAgo = 24, title = "Two hours, uninterrupted",
            content = "No meetings, no notifications, no interrupting anyone. Got more done " +
                "in this block than in the previous three days combined. Guarding this time " +
                "is going to be the hard part.",
            mood = Mood.GOOD, emotions = listOf("motivated", "confident"), tags = listOf("work"),
            hour = 9, minute = 0,
        ),
        Story(
            daysAgo = 27, title = "Flights cancelled, hotel voucher",
            content = "Four hours in an airport and then a hotel with a bath, which was a " +
                "strange combination of misery and luxury. Read sixty pages.",
            mood = Mood.TIRED, emotions = listOf("stressed", "bored"), tags = listOf("travel"),
            location = "Airport hotel", weather = "Storm",
        ),
        Story(
            daysAgo = 30, title = "Cooked for six people",
            content = "Nobody asked me to, I just did it. Loud kitchen, too many glasses, " +
                "someone spilled red wine on the good tablecloth. I did not mind at all.",
            mood = Mood.VERY_HAPPY, emotions = listOf("joy", "love", "confident"),
            tags = listOf("friends", "cooking"), isFavorite = true, location = "Home",
            hour = 20, minute = 30,
        ),
        Story(
            daysAgo = 33, title = "Start of the month, feeling behind",
            content = "Listed everything outstanding and it was not as bad as it felt, which " +
                "is annoying because I would have liked to keep feeling behind for another day.",
            mood = Mood.SAD, emotions = listOf("stressed", "lonely"), tags = listOf("work", "planning"),
            hour = 23, minute = 0,
        ),
        Story(
            daysAgo = 36, title = "Half marathon, no time goal",
            content = "Finished in two hours forty, which is slower than last year and better " +
                "than the plan. Stopped twice to look at the river. Ate everything.",
            mood = Mood.GOOD, emotions = listOf("confident", "tired"), tags = listOf("running", "health"),
            isFavorite = true, hour = 10, minute = 30,
        ),
        Story(
            daysAgo = 39, title = "Power cut",
            content = "Four hours of candles and no heating. We ended up talking far more than " +
                "we have in months, which is the argument for owning no screens.",
            mood = Mood.HAPPY, emotions = listOf("love", "joy"), tags = listOf("home", "friends"),
            hour = 21, minute = 40,
        ),
        Story(
            daysAgo = 42, title = "Therapy, awkward",
            content = "Talked about the thing I have been circling for months. Did not resolve " +
                "anything. Was still thinking about it on the bus home, which apparently counts.",
            mood = Mood.NEUTRAL, emotions = listOf("anxious", "hopeful"), tags = listOf("health", "personal"),
            hour = 16, minute = 0,
        ),
        Story(
            daysAgo = 46, title = "New notebook",
            content = "Thirty pages, unlined, cheap. Starting this one because the last was " +
                "nearly full and I kept writing in the margins like a student.",
            mood = Mood.HAPPY, emotions = listOf("excited", "hopeful"), tags = listOf("writing"),
            hour = 12, minute = 15,
        ),
        Story(
            daysAgo = 50, title = "The car broke down",
            content = "Stranded at the motorway services for three hours in the rain. A family " +
                "in the next car lent us a phone charger, which was somehow the worst and best " +
                "part. £180.",
            mood = Mood.ANGRY, emotions = listOf("stressed", "angry"), tags = listOf("driving", "money"),
            hour = 18, minute = 50,
        ),
        Story(
            daysAgo = 54, title = "Nothing much",
            content = "Worked, ate, walked, slept. Writing this down because I notice I only " +
                "record the days that feel like something, and most of them do not.",
            mood = Mood.NEUTRAL, emotions = listOf("bored"), tags = listOf("daily"),
            hour = 22, minute = 15,
        ),
        Story(
            daysAgo = 58, title = "Museum with Dad",
            content = "He wanted to see one specific room and we spent forty minutes in it. " +
                "Then coffee, then he went home, then I walked back slowly. Good day.",
            mood = Mood.HAPPY, emotions = listOf("grateful", "love"), tags = listOf("family"),
            isFavorite = true, location = "City museum", weather = "Bright",
            hour = 17, minute = 0,
        ),
        Story(
            daysAgo = 62, title = "Deadline moved, sigh of relief",
            content = "A week bought back. Used about two hours of it on admin and the rest " +
                "walking around the lake being unconvincingly relaxed.",
            mood = Mood.GOOD, emotions = listOf("calm", "confident"), tags = listOf("work", "rest"),
            hour = 15, minute = 20,
        ),
        Story(
            daysAgo = 67, title = "Lost my keys",
            content = "Hour and a half in the rain, then the locksmith, then a very expensive " +
                "lesson. Set three alarms for the morning.",
            mood = Mood.ANXIOUS, emotions = listOf("stressed", "anxious"), tags = listOf("daily"),
            hour = 23, minute = 20,
        ),
        Story(
            daysAgo = 71, title = "First proper heatwave day",
            content = "Thirty-four degrees. The flat was unbearable by two in the afternoon. " +
                "Drank water like it was a job and slept through the hottest part of it.",
            mood = Mood.TIRED, emotions = listOf("bored"), tags = listOf("weather", "rest"),
            weather = "Heatwave", hour = 20, minute = 45,
        ),
        Story(
            daysAgo = 76, title = "Finished the book",
            content = "The one I had been carrying around for three months. Sat down after " +
                "dinner and did not move for two hundred pages. Sad in the good way.",
            mood = Mood.EXCITED, emotions = listOf("joy", "grateful"), tags = listOf("reading"),
            isFavorite = true, hour = 22, minute = 30,
        ),
        Story(
            daysAgo = 81, title = "Said no to something",
            content = "Turned down the thing I did not want to do, the one I would have said " +
                "yes to out of habit. Nothing terrible happened. Writing this down as evidence.",
            mood = Mood.GOOD, emotions = listOf("confident", "hopeful"), tags = listOf("work", "personal"),
            hour = 14, minute = 10,
        ),
        Story(
            daysAgo = 86, title = "Snow, properly",
            content = "Rare enough to be worth the walk. The whole street was quiet and every " +
                "car had snow on the roof. Made cocoa when I got back.",
            mood = Mood.VERY_HAPPY, emotions = listOf("joy", "grateful"), tags = listOf("weather", "rest"),
            isFavorite = true, weather = "Snow", hour = 18, minute = 0,
        ),
        Story(
            daysAgo = 91, title = "Restructured the whole week",
            content = "Moved everything into fewer, longer blocks. Looked much better on " +
                "paper. Will discover tomorrow whether it survives contact with reality.",
            mood = Mood.GOOD, emotions = listOf("motivated", "confident"), tags = listOf("planning", "work"),
            hour = 21, minute = 0,
        ),
        Story(
            daysAgo = 97, title = "Friend cancelled again",
            content = "Third time. Stopped making an excuse and just said that I had noticed. " +
                "They apologised, genuinely, and suggested a different day. Keeping that in " +
                "mind rather than assuming the worst.",
            mood = Mood.SAD, emotions = listOf("lonely", "sad"), tags = listOf("friends", "personal"),
            hour = 19, minute = 30,
        ),
        Story(
            daysAgo = 103, title = "Plant survived the winter",
            content = "The one I nearly killed twice. New growth on the side, which feels " +
                "disproportionately meaningful for a plant on a windowsill.",
            mood = Mood.HAPPY, emotions = listOf("hopeful", "joy"), tags = listOf("home"),
            hour = 11, minute = 30,
        ),
        Story(
            daysAgo = 109, title = "Big argument, then fine",
            content = "Said things I have been holding for months. It went badly for about " +
                "an hour and then we actually talked. Not sure I would have managed it a year ago.",
            mood = Mood.ANGRY, emotions = listOf("angry", "confident"),
            tags = listOf("family", "personal"), hour = 20, minute = 30,
        ),
        Story(
            daysAgo = 115, title = "New year, same person",
            content = "No resolutions, on purpose. One thing: write here more often, because I " +
                "can already tell this year will be full of things worth finding again.",
            mood = Mood.GOOD, emotions = listOf("hopeful", "motivated"), tags = listOf("milestone", "planning"),
            isFavorite = true, hour = 0, minute = 5,
        ),
    )

    private val checkIns: List<CheckIn> = listOf(
        CheckIn(0, Mood.GOOD, "Slow start, fine once it began.", listOf("calm", "grateful"), 8, 15),
        CheckIn(1, Mood.VERY_HAPPY, "Genuinely good day.", listOf("joy", "excited"), 21, 5),
        CheckIn(2, Mood.TIRED, "Legs still complaining.", listOf("tired"), 7, 45),
        CheckIn(3, Mood.ANXIOUS, "Too much in my head about work.", listOf("stressed"), 22, 30),
        CheckIn(4, Mood.NEUTRAL, "Flat but not unhappy.", listOf("bored"), 12, 20),
        CheckIn(6, Mood.HAPPY, "Good food, good company.", listOf("joy", "grateful"), 19, 40),
        CheckIn(8, Mood.SAD, "Replied to the review. Should not have read the comments.", listOf("sad"), 9, 10),
        CheckIn(10, Mood.GOOD, "Steady.", listOf("calm"), 18, 0),
        CheckIn(12, Mood.EXCITED, "The parallel parking finally clicked.", listOf("confident"), 17, 25),
        CheckIn(14, Mood.VERY_SAD, "Bad day, no particular reason.", listOf("sad", "lonely"), 21, 50),
        CheckIn(16, Mood.GOOD, "Long walk.", listOf("calm", "hopeful"), 16, 15),
        CheckIn(18, Mood.ANGRY, "Still annoyed about the car.", listOf("angry", "stressed"), 8, 30),
        CheckIn(20, Mood.HAPPY, "Cooked for six, everyone came.", listOf("joy", "love"), 22, 45),
        CheckIn(22, Mood.NEUTRAL, "Got through it.", listOf("tired"), 13, 0),
        CheckIn(25, Mood.GOOD, "Two uninterrupted hours.", listOf("motivated", "confident"), 15, 30),
        CheckIn(28, Mood.TIRED, "Airport all day.", listOf("tired", "stressed"), 20, 10),
        CheckIn(31, Mood.SAD, "The list was longer than I wanted.", listOf("stressed", "anxious"), 22, 50),
        CheckIn(34, Mood.GOOD, "Slow river, good legs.", listOf("confident"), 11, 40),
        CheckIn(38, Mood.VERY_HAPPY, "Power cut was the best thing that week.", listOf("joy", "love"), 21, 0),
        CheckIn(45, Mood.NEUTRAL, "Therapy. Still processing.", listOf("confused", "hopeful"), 17, 20),
        CheckIn(55, Mood.HAPPY, "Dad in the museum.", listOf("grateful", "calm"), 18, 30),
        CheckIn(72, Mood.TIRED, "Too hot to do anything.", listOf("bored", "tired"), 14, 45),
    )

    /**
     * Demo diary entries for [userId], dated backwards from [today].
     *
     * Every entry is already [SyncStatus.SYNCED] so seeding does not fill the sync queue.
     */
    fun diaryEntries(
        userId: String,
        today: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DiaryEntry> = stories.mapIndexed { index, story ->
        val date = LocalDates.minusDays(today, story.daysAgo)
        val createdAt = story.instantOn(date, timeZone)
        DiaryEntry(
            id = seedEntryId(index),
            userId = userId,
            title = story.title,
            content = story.content,
            entryDate = date,
            mood = story.mood,
            emotions = story.emotions.map(::emotionById),
            tags = story.tags,
            isFavorite = story.isFavorite,
            location = story.location,
            weather = story.weather,
            createdAt = createdAt,
            updatedAt = createdAt,
            syncStatus = SyncStatus.SYNCED,
        )
    }

    /** Demo mood check-ins for [userId], dated backwards from [today]. */
    fun moodEntries(
        userId: String,
        today: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): List<MoodEntry> = checkIns.mapIndexed { index, checkIn ->
        val date = LocalDates.minusDays(today, checkIn.daysAgo)
        val createdAt = checkIn.instantOn(date, timeZone)
        MoodEntry(
            id = seedMoodId(index),
            userId = userId,
            mood = checkIn.mood,
            note = checkIn.note,
            emotions = checkIn.emotions.map(::emotionById),
            date = date,
            createdAt = createdAt,
            updatedAt = createdAt,
            syncStatus = SyncStatus.SYNCED,
        )
    }

    /** Stable id for seed entry [index], so re-seeding upserts instead of duplicating. */
    fun seedEntryId(index: Int): String = "seed-entry-" + (index + 1).padSeed()

    /** Stable id for seed check-in [index]. */
    fun seedMoodId(index: Int): String = "seed-mood-" + (index + 1).padSeed()

    /**
     * True when a row id belongs to the seed set.
     *
     * Lets a re-seed clear stale demo rows without touching anything the user wrote.
     */
    fun isSeedId(id: String): Boolean =
        id.startsWith("seed-entry-") || id.startsWith("seed-mood-")

    private fun Story.instantOn(date: LocalDate, timeZone: TimeZone): Instant =
        date.atStartOfDayIn(timeZone) + hour.hours + minute.minutes

    private fun CheckIn.instantOn(date: LocalDate, timeZone: TimeZone): Instant =
        date.atStartOfDayIn(timeZone) + hour.hours + minute.minutes

    /**
     * Resolves an emotion by id, accepting either a catalogue id or the display name.
     *
     * Falling back to [Emotion.fromId] keeps an unknown id renderable instead of dropping
     * the whole entry, which matches how a real record from a newer client is handled.
     */
    private fun Int.padSeed(): String = if (this < 10) "0$this" else toString()

    private fun emotionById(id: String): Emotion =
        EmotionCatalog.all.firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) }
            ?: Emotion.fromId(id)

    /** Upper bound on the look-back window, exposed for the seeder and for tests. */
    val historyWindowDays: Int get() = HISTORY_DAYS
}
