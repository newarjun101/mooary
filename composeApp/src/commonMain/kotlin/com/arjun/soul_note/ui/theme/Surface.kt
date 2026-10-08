package com.arjun.soul_note.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Card treatment used across the app.
 *
 * Two things stack here, and the shadow is the one that does the work:
 *
 *  - a **shadow** in two parts — a wide soft ambient one and a tighter, slightly denser one
 *    under the edge — which is what gives a card the sense of floating at a distance instead
 *    of being drawn next to the page;
 *  - a **flat fill, lifted a little off the page colour**, because on a flat scheme depth has
 *    to come from the shadow alone. The lift is what keeps a card from reading as a hole
 *    punched in the page; it is a single colour, not a wash, so nothing here depends on a
 *    gradient being renderable.
 *
 * A single flat colour, deliberately. A gradient fill and a top-edge highlight that faded
 * toward the bottom were both here for a while, and both were doing real work -- but a card
 * that catches the light is a different object from one that is lit, and the second one is
 * what the rest of the app is drawn as.
 */
@Composable
fun Modifier.paperCard(
    shape: Shape = DiaryShapes.large,
    elevation: Dp = Elevation.card,
    color: Color = MaterialTheme.colorScheme.surface,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    // Two shadow alphas, not one: a single value is either too weak to lift the card or too
    // strong to sit under text, and it cannot be both. The ambient one spreads wide and faint,
    // the spot one stays close and slightly denser.
    val ambient = Color.Black.copy(alpha = if (dark) 0.05f else 0.045f)
    val contact = Color.Black.copy(alpha = if (dark) 0.08f else 0.07f)
    // Lift toward white so the card separates from the page in both themes instead of only in
    // the one where the page is light. The base has to be the page colour for that to mean
    // anything: `surfaceContainerLowest` is already pure white, so lifting it toward white is
    // a no-op and the card collapses to a cold white rectangle -- the one neutral in the light
    // scheme carrying neither the red nor the yellow every other neutral keeps.
    val fill = lerp(color, Color.White, if (dark) 0.012f else 0.30f)
    val edge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (dark) 0.08f else 0.48f)
    return this
        .shadow(
            elevation = elevation * 3f,
            shape = shape,
            clip = false,
            ambientColor = ambient,
            spotColor = ambient,
        )
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = contact,
            spotColor = contact,
        )
        .clip(shape)
        .background(fill)
        .border(width = 1.dp, color = edge, shape = shape)
}

/** A card with the app's default fill, for the many places that do not need a custom one. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    shape: Shape = DiaryShapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = Elevation.card,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.paperCard(shape = shape, elevation = elevation, color = color), content = content)
}

/**
 * Drop-in replacement for Material's [Card] in this app: same ColumnScope content, the app's
 * own paper treatment instead of a tonal overlay.
 *
 * Every grouped block in the app goes through this rather than `Card`, so "a block of
 * content" looks the same on the Mood, Calendar, Profile and Settings screens instead of
 * each one picking its own elevation and filling.
 */
@Composable
fun JournalCard(
    modifier: Modifier = Modifier,
    shape: Shape = DiaryShapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = Elevation.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.paperCard(shape = shape, elevation = elevation, color = color),
        content = content,
    )
}

/**
 * The app's one emphatic fill: solid primary, for the handful of things meant to be the
 * brightest object on screen — the new-entry button, the selected range pill.
 *
 * Renamed from `accentGradient` when gradients came out. It was primary into tertiary, which
 * cost a brush to animate and, being two hues, read as decoration rather than as emphasis. A
 * flat accent says "this is the button" on its own, and it can be cross-faded with
 * `animateColor` instead of rebuilt.
 */
@Composable
fun Modifier.accentFill(shape: Shape = DiaryShapes.large): Modifier = this
    .clip(shape)
    .background(MaterialTheme.colorScheme.primary, shape)

/**
 * Scales a row down a touch while it is held.
 *
 * Diary rows are the app's main tap target and nothing about them moves when pressed, so a
 * mis-tap stays invisible and the entry opens before you notice you missed. Two percent is
 * enough to confirm the touch and small enough not to feel like a game.
 *
 * [indication] is off because the press already reports the touch; Material's ripple on top
 * of a moving card reads as two separate pieces of feedback fighting each other.
 */
@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    shape: Shape = DiaryShapes.large,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
}

/**
 * The page behind everything.
 *
 * One flat field plus **grain and fibres** — a fixed-seed scatter of faint specks and a
 * handful of long hairlines, which is what makes the result read as paper rather than as a
 * flat colour. The vertical wash and the three corner blooms that used to sit underneath are
 * gone with the rest of the gradients; the blooms were the only reason the page did not read
 * as a fill colour, and a page that reads as a fill is a page whose edges you start to see.
 *
 * Everything is deterministic off a fixed seed. A background that reshuffles its speckle on
 * every recomposition is the kind of detail nobody can name but everybody notices as
 * something wrong, and it also means every frame is a fresh draw of 260 circles.
 *
 * Mounted once in `DiaryApp` behind the navigation chrome, so every screen inherits it and
 * none of them has to remember to ask for it.
 */
@Composable
fun PaperSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminanceIsDark()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val speck = Color.Black.copy(alpha = if (dark) 0.055f else 0.030f)
            val fibre = Color.Black.copy(alpha = if (dark) 0.030f else 0.016f)
            val rng = Random(7)
            repeat(260) {
                drawCircle(
                    color = speck,
                    radius = rng.nextFloat() * 0.9f + 0.35f,
                    center = Offset(rng.nextFloat() * w, rng.nextFloat() * h),
                )
            }
            // A handful of long fibres. Without these the specks read as noise; with them they
            // read as a surface.
            repeat(18) {
                val x = rng.nextFloat() * w
                val y = rng.nextFloat() * h
                val len = (0.03f + rng.nextFloat() * 0.09f) * w
                drawLine(
                    color = fibre,
                    start = Offset(x, y),
                    end = Offset(x + len, y + (rng.nextFloat() - 0.5f) * 5f),
                    strokeWidth = 1f,
                )
            }
        }
        content()
    }
}

/**
 * Perceived lightness, used to pick between a light and a dark treatment.
 *
 * The weighted luma approximation rather than a real relative-luminance calculation: it
 * only has to separate "paper" from "espresso", and both are far enough from the midpoint
 * that a cheap formula is not the deciding factor.
 */
internal fun Color.luminanceIsDark(): Boolean =
    (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f
