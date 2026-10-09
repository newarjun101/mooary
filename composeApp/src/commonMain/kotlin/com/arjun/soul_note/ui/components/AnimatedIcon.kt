package com.arjun.soul_note.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * What `Icon` gives a vector for free and what a bitmap asset has to be told: 24.dp.
 *
 * Named rather than inlined so the two overloads of [AnimatedIcon] are visibly interchangeable
 * at a call site — a vector and the asset that replaces it occupy the same box.
 */
private val IconSize = 24.dp

/** A shared, quiet motion treatment for operational icons throughout the app. */
@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val lift = rememberIconLift()
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer { translationY = lift.value * density },
    )
}

/**
 * The same motion for a bitmap asset rather than a vector.
 *
 * Deliberately takes no tint. These assets are multi-coloured, so flattening them into one
 * colour would replace the artwork rather than recolour it; a vector icon is drawn in the
 * content colour, and these are drawn as they were authored.
 *
 * [IconSize] is applied here for the reason it does not have to be on the vector overload:
 * `Icon` already gives a vector a default of 24.dp, while an `Image` takes its intrinsic size
 * and these assets are 512px — which lays out as 512dp tall. Placed after [modifier] so a
 * caller's own `size` still wins: the outer size constrains the inner one.
 */
@Composable
fun AnimatedIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val lift = rememberIconLift()
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier
            .size(IconSize)
            .graphicsLayer { translationY = lift.value * density },
    )
}

/**
 * The shared bob. One transition for both overloads so a vector and a bitmap icon side by side
 * never drift apart, and so the motion is defined in exactly one place.
 *
 * The numbers are the navigation bar's, not invented here: the tab icons were the first thing
 * to move in this app and everything else has to read as the same gesture. The previous ±0.45
 * was legible only next to a still neighbour — at 24dp it is a fifth of a pixel per frame and
 * the icon simply looks static, which is what it looked like on the date-picker button.
 */
@Composable
private fun rememberIconLift(): State<Float> {
    val transition = rememberInfiniteTransition(label = "icon-motion")
    return transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "icon-lift",
    )
}
