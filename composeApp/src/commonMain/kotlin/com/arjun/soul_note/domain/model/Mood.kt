package com.arjun.soul_note.domain.model

import kotlinx.serialization.Serializable

/**
 * The predefined mood scale.
 *
 * Extension without a migration is an explicit product requirement, so moods are stored
 * as a stable [id] string plus a [score] rather than as an ordinal. Adding a mood means
 * appending a constant here: existing documents keep resolving, unknown ids are rendered
 * through [Mood.UNKNOWN] instead of crashing the list, and no database or rules change
 * is required.
 */
@Serializable
enum class Mood(val id: String, val score: Int) {

    VERY_HAPPY("very_happy", 5),
    HAPPY("happy", 4),
    GOOD("good", 3),
    NEUTRAL("neutral", 2),
    SAD("sad", 1),
    VERY_SAD("very_sad", 0),
    ANGRY("angry", 2),
    ANXIOUS("anxious", 1),
    TIRED("tired", 1),
    EXCITED("excited", 5);

    /** Accessibility: colour is never the only signal, so every mood ships a label too. */
    val hasPositiveValence: Boolean get() = score >= 3

    val isNeutralOrWorse: Boolean get() = score < 2

    companion object {
        /** Sentinel for a mood id written by a newer client. */
        val UNKNOWN: Mood = NEUTRAL

        val byId: Map<String, Mood> = entries.associateBy { it.id }

        /** Ordered best → worst, used by the analytics distribution chart. */
        val byScoreDescending: List<Mood> =
            entries.sortedWith(compareByDescending<Mood> { it.score }.thenBy { it.ordinal })

        fun fromId(id: String?): Mood = id?.let { byId[it] } ?: UNKNOWN

        /** The canonical mood for a valence score, used when normalising mood aggregates. */
        fun fromScore(score: Int): Mood? =
            entries.firstOrNull { it.score == score && it.ordinal <= Mood.VERY_SAD.ordinal }
    }
}

/** Localisable mood metadata, kept out of the enum so the domain stays string-free. */
data class MoodDescriptor(
    val mood: Mood,
    val nameKey: String,
    val descriptionKey: String,
    val score: Int = mood.score,
) {
    val id: String get() = mood.id
}

/** Every mood plus its localisation keys, in display order. */
object MoodCatalog {
    val all: List<MoodDescriptor> = listOf(
        MoodDescriptor(Mood.VERY_HAPPY, "mood_very_happy", "mood_very_happy_description"),
        MoodDescriptor(Mood.EXCITED, "mood_excited", "mood_excited_description"),
        MoodDescriptor(Mood.HAPPY, "mood_happy", "mood_happy_description"),
        MoodDescriptor(Mood.GOOD, "mood_good", "mood_good_description"),
        MoodDescriptor(Mood.NEUTRAL, "mood_neutral", "mood_neutral_description"),
        MoodDescriptor(Mood.TIRED, "mood_tired", "mood_tired_description"),
        MoodDescriptor(Mood.SAD, "mood_sad", "mood_sad_description"),
        MoodDescriptor(Mood.ANXIOUS, "mood_anxious", "mood_anxious_description"),
        MoodDescriptor(Mood.ANGRY, "mood_angry", "mood_angry_description"),
        MoodDescriptor(Mood.VERY_SAD, "mood_very_sad", "mood_very_sad_description"),
    )

    /** Best → worst, matching the analytics distribution. */
    val byScore: List<MoodDescriptor> =
        all.sortedWith(compareByDescending<MoodDescriptor> { it.score }.thenBy { it.mood.ordinal })

    val quickPicker: List<MoodDescriptor> = all.take(5)

    fun descriptorFor(mood: Mood): MoodDescriptor =
        all.firstOrNull { it.mood == mood }
            ?: MoodDescriptor(mood, "mood_neutral", "mood_neutral_description")
}

/** JSON field names, centralised so the rules file and the code cannot drift apart. */
object DiaryFields {
    const val TITLE = "title"
    const val CONTENT = "content"
    const val ENTRY_DATE = "entryDate"
    const val MOOD = "mood"
    const val EMOTIONS = "emotions"
    const val TAGS = "tags"
    const val IS_FAVORITE = "isFavorite"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val DELETED_AT = "deletedAt"
    const val LOCATION = "location"
    const val WEATHER = "weather"
    const val WORD_COUNT = "wordCount"
}

object MoodFields {
    const val MOOD = "mood"
    const val NOTE = "note"
    const val EMOTIONS = "emotions"
    const val DATE = "date"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val DELETED_AT = "deletedAt"
}

object UserFields {
    const val EMAIL = "email"
    const val DISPLAY_NAME = "displayName"
    const val PHOTO_URL = "photoUrl"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
}

/** Firestore collection paths, relative to the authenticated user. */
object FirestorePaths {
    const val USERS = "users"
    const val DIARY_ENTRIES = "diaryEntries"
    const val MOODS = "moods"
    const val DRAFTS = "drafts"
    const val DEVICES = "devices"

    fun user(userId: String) = "$USERS/$userId"
    fun diaryEntries(userId: String) = "$USERS/$userId/$DIARY_ENTRIES"
    fun moods(userId: String) = "$USERS/$userId/$MOODS"
    fun entry(userId: String, entryId: String) = "$USERS/$userId/$DIARY_ENTRIES/$entryId"
    fun mood(userId: String, moodId: String) = "$USERS/$userId/$MOODS/$moodId"

    /**
     * One document per registered push target, under the owning user.
     *
     * Keyed by a hash of the token rather than the token itself. A registration token is a
     * credential — it authorises nothing on its own, but it does let whoever holds it address
     * this device, and Firestore rules are not the only thing guarding a document. Putting it
     * in a document id would also make it appear in every log line and error message that
     * mentions a path, which is the opposite of what the privacy rules want.
     */
    fun device(userId: String, tokenHash: String) = "$USERS/$userId/$DEVICES/$tokenHash"
}
