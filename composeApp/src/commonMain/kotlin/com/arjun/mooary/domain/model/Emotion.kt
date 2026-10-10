package com.arjun.mooary.domain.model

import kotlinx.serialization.Serializable

/**
 * A predefined emotion tag.
 *
 * Like [Mood], emotions are persisted by a stable [id] so that the catalogue can grow
 * without a migration. A record with an id this build does not know still renders using a
 * neutral illustration and a localised fallback label.
 */
@Serializable
data class Emotion(
    val id: String,
    val name: String,
) {
    companion object {
        fun fromId(id: String): Emotion =
            EmotionCatalog.byId[id] ?: Emotion(id = id, name = id)
    }
}

/** The predefined emotion catalogue, grouped for the picker UI. */
object EmotionCatalog {

    val positive: List<Emotion> = listOf(
        Emotion("joy", "Joy"),
        Emotion("calm", "Calm"),
        Emotion("love", "Love"),
        Emotion("excited", "Excited"),
        Emotion("grateful", "Grateful"),
        Emotion("confident", "Confident"),
        Emotion("motivated", "Motivated"),
        Emotion("hopeful", "Hopeful"),
    )

    val neutral: List<Emotion> = listOf(
        Emotion("tired", "Tired"),
        Emotion("bored", "Bored"),
        Emotion("confused", "Confused"),
    )

    val difficult: List<Emotion> = listOf(
        Emotion("sad", "Sad"),
        Emotion("lonely", "Lonely"),
        Emotion("angry", "Angry"),
        Emotion("anxious", "Anxious"),
        Emotion("stressed", "Stressed"),
    )

    val all: List<Emotion> = positive + neutral + difficult

    val byId: Map<String, Emotion> = all.associateBy { it.id }

    /** Grouped for rendering; the group keys map to localisation resources. */
    val groups: List<Pair<String, List<Emotion>>> = listOf(
        "emotion_group_positive" to positive,
        "emotion_group_neutral" to neutral,
        "emotion_group_difficult" to difficult,
    )

    fun descriptorFor(emotion: Emotion): Emotion = byId[emotion.id] ?: emotion

    fun namesFor(ids: Collection<String>): List<String> = ids.mapNotNull { byId[it]?.name }
}
