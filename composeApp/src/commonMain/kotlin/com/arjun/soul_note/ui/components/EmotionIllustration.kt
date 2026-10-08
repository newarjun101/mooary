package com.arjun.soul_note.ui.components

import androidx.compose.foundation.Image
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.emotion_confident
import com.arjun.soul_note.resources.emotion_confused
import com.arjun.soul_note.resources.emotion_grateful
import com.arjun.soul_note.resources.emotion_lonely
import com.arjun.soul_note.resources.emotion_love
import com.arjun.soul_note.resources.emotion_stressed
import com.arjun.soul_note.resources.mood_angry
import com.arjun.soul_note.resources.mood_anxious
import com.arjun.soul_note.resources.mood_excited
import com.arjun.soul_note.resources.mood_good
import com.arjun.soul_note.resources.mood_happy
import com.arjun.soul_note.resources.mood_neutral
import com.arjun.soul_note.resources.mood_sad
import com.arjun.soul_note.resources.mood_tired
import com.arjun.soul_note.resources.mood_very_happy
import com.arjun.soul_note.resources.mood_very_sad
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Supplied 3D illustrations, shared so every mood and emotion has the same visual language. */
@Composable
fun MoodIllustration(
    mood: Mood,
    modifier: Modifier = Modifier,
    motionSeed: String = mood.id,
) {
    Illustration(mood.drawable(), motionSeed, modifier)
}

@Composable
fun EmotionIllustration(emotion: Emotion, modifier: Modifier = Modifier) {
    Illustration(emotion.drawable(), emotion.id, modifier)
}

@Composable
private fun Illustration(drawable: DrawableResource, motionSeed: String, modifier: Modifier) {
    val floatDistance = with(LocalDensity.current) { 1.5.dp.toPx() }
    val phaseDelay = (motionSeed.hashCode() and Int.MAX_VALUE) % 700
    val floatTransition = rememberInfiniteTransition(label = "emojiFloat")
    val floatOffset by floatTransition.animateFloat(
        initialValue = -floatDistance,
        targetValue = floatDistance,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_450,
                delayMillis = phaseDelay,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emojiFloatOffset",
    )
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer { translationY = floatOffset },
    )
}

private fun Mood.drawable(): DrawableResource = when (this) {
    Mood.VERY_HAPPY -> Res.drawable.mood_very_happy
    Mood.HAPPY -> Res.drawable.mood_happy
    Mood.GOOD -> Res.drawable.mood_good
    Mood.NEUTRAL -> Res.drawable.mood_neutral
    Mood.SAD -> Res.drawable.mood_sad
    Mood.VERY_SAD -> Res.drawable.mood_very_sad
    Mood.ANGRY -> Res.drawable.mood_angry
    Mood.ANXIOUS -> Res.drawable.mood_anxious
    Mood.TIRED -> Res.drawable.mood_tired
    Mood.EXCITED -> Res.drawable.mood_excited
}

private fun Emotion.drawable(): DrawableResource = when (id) {
    "joy" -> Res.drawable.mood_very_happy
    "calm" -> Res.drawable.mood_good
    "love" -> Res.drawable.emotion_love
    "excited" -> Res.drawable.mood_excited
    "grateful" -> Res.drawable.emotion_grateful
    "confident" -> Res.drawable.emotion_confident
    "motivated", "hopeful" -> Res.drawable.mood_very_happy
    "tired" -> Res.drawable.mood_tired
    "bored" -> Res.drawable.mood_neutral
    "confused" -> Res.drawable.emotion_confused
    "sad" -> Res.drawable.mood_very_sad
    "lonely" -> Res.drawable.emotion_lonely
    "angry" -> Res.drawable.mood_angry
    "anxious" -> Res.drawable.mood_anxious
    "stressed" -> Res.drawable.emotion_stressed
    else -> Res.drawable.mood_neutral
}
