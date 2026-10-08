package com.arjun.soul_note.ui.labels

import androidx.compose.runtime.Composable
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.EmotionCatalog
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodCatalog
import com.arjun.soul_note.domain.model.MoodDescriptor
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.emotion_angry
import com.arjun.soul_note.resources.emotion_anxious
import com.arjun.soul_note.resources.emotion_bored
import com.arjun.soul_note.resources.emotion_calm
import com.arjun.soul_note.resources.emotion_confident
import com.arjun.soul_note.resources.emotion_confused
import com.arjun.soul_note.resources.emotion_excited
import com.arjun.soul_note.resources.emotion_grateful
import com.arjun.soul_note.resources.emotion_group_difficult
import com.arjun.soul_note.resources.emotion_group_neutral
import com.arjun.soul_note.resources.emotion_group_positive
import com.arjun.soul_note.resources.emotion_hopeful
import com.arjun.soul_note.resources.emotion_joy
import com.arjun.soul_note.resources.emotion_lonely
import com.arjun.soul_note.resources.emotion_love
import com.arjun.soul_note.resources.emotion_motivated
import com.arjun.soul_note.resources.emotion_sad
import com.arjun.soul_note.resources.emotion_stressed
import com.arjun.soul_note.resources.emotion_tired
import com.arjun.soul_note.resources.emotion_unknown
import com.arjun.soul_note.resources.mood_angry
import com.arjun.soul_note.resources.mood_anxious
import com.arjun.soul_note.resources.mood_excited
import com.arjun.soul_note.resources.mood_good
import com.arjun.soul_note.resources.mood_happy
import com.arjun.soul_note.resources.mood_neutral
import com.arjun.soul_note.resources.mood_sad
import com.arjun.soul_note.resources.mood_tired
import com.arjun.soul_note.resources.mood_unknown
import com.arjun.soul_note.resources.mood_very_happy
import com.arjun.soul_note.resources.mood_very_sad
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Turns catalogue keys into localised display text.
 *
 * The domain holds keys rather than text on purpose: `MoodDescriptor.nameKey` and the
 * emotion group keys are stable identifiers, and the presentation layer decides what they
 * read as. That only works if something resolves them, and until now nothing did — labels
 * were built by `id.replace('_', ' ')` and by `Emotion.name`, so the UI was English-only
 * and untranslatable no matter what was added to `strings.xml`.
 *
 * The maps below are the whole mapping. Compose resources are resolved at compile time, so a
 * key with no entry cannot be looked up by name; it has to be listed here, which is exactly
 * the friction that keeps the domain and the translations from drifting apart.
 * `CatalogLabelsTest` asserts the two sides stay in step.
 */
private val MOOD_NAME_RESOURCES: Map<String, StringResource> = mapOf(
    "mood_very_happy" to Res.string.mood_very_happy,
    "mood_excited" to Res.string.mood_excited,
    "mood_happy" to Res.string.mood_happy,
    "mood_good" to Res.string.mood_good,
    "mood_neutral" to Res.string.mood_neutral,
    "mood_tired" to Res.string.mood_tired,
    "mood_sad" to Res.string.mood_sad,
    "mood_anxious" to Res.string.mood_anxious,
    "mood_angry" to Res.string.mood_angry,
    "mood_very_sad" to Res.string.mood_very_sad,
)

private val EMOTION_NAME_RESOURCES: Map<String, StringResource> = mapOf(
    "joy" to Res.string.emotion_joy,
    "calm" to Res.string.emotion_calm,
    "love" to Res.string.emotion_love,
    "excited" to Res.string.emotion_excited,
    "grateful" to Res.string.emotion_grateful,
    "confident" to Res.string.emotion_confident,
    "motivated" to Res.string.emotion_motivated,
    "hopeful" to Res.string.emotion_hopeful,
    "tired" to Res.string.emotion_tired,
    "bored" to Res.string.emotion_bored,
    "confused" to Res.string.emotion_confused,
    "sad" to Res.string.emotion_sad,
    "lonely" to Res.string.emotion_lonely,
    "angry" to Res.string.emotion_angry,
    "anxious" to Res.string.emotion_anxious,
    "stressed" to Res.string.emotion_stressed,
)

private val EMOTION_GROUP_RESOURCES: Map<String, StringResource> = mapOf(
    "emotion_group_positive" to Res.string.emotion_group_positive,
    "emotion_group_neutral" to Res.string.emotion_group_neutral,
    "emotion_group_difficult" to Res.string.emotion_group_difficult,
)

/** The localised name of [descriptor]'s mood. */
@Composable
fun moodName(descriptor: MoodDescriptor): String =
    stringResource(moodNameResource(descriptor))

/** The localised name of [mood], resolving through the catalogue. */
@Composable
fun moodName(mood: Mood): String = moodName(MoodCatalog.descriptorFor(mood))

/** The localised name of [emotion]. */
@Composable
fun emotionName(emotion: Emotion): String = stringResource(emotionNameResource(emotion))

/** The localised heading for an emotion group key, such as `emotion_group_positive`. */
@Composable
fun emotionGroupName(groupKey: String): String = stringResource(emotionGroupResource(groupKey))

/** Text counterpart of the mood illustration, used for labels and assistive technology. */
@Composable
fun moodLabel(mood: Mood): String = moodName(mood)

@Composable
fun emotionLabel(emotion: Emotion): String = emotionName(emotion)

/**
 * The resource for [descriptor]'s name, falling back to the neutral mood rather than
 * throwing: a descriptor for a mood added by a newer client still has to render.
 */
fun moodNameResource(descriptor: MoodDescriptor): StringResource =
    MOOD_NAME_RESOURCES[descriptor.nameKey] ?: Res.string.mood_unknown

/**
 * The resource for [emotion]'s name.
 *
 * An id that is not in the catalogue is one written by a newer client, so it gets the
 * "unrecognised" label rather than the raw id — the id is data, and data is not a label.
 */
fun emotionNameResource(emotion: Emotion): StringResource =
    EMOTION_NAME_RESOURCES[emotion.id] ?: Res.string.emotion_unknown

fun emotionGroupResource(groupKey: String): StringResource =
    EMOTION_GROUP_RESOURCES[groupKey] ?: Res.string.emotion_unknown

/** Exposed for the drift test; not part of the UI contract. */
internal val catalogLabelMappings: Map<String, Map<String, StringResource>>
    get() = mapOf(
        "mood" to MOOD_NAME_RESOURCES,
        "emotion" to EMOTION_NAME_RESOURCES,
        "emotion_group" to EMOTION_GROUP_RESOURCES,
    )
