package com.arjun.soul_note.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Palette: warm paper and ink.
//
// The app is a journal, so the surfaces are paper rather than white and the text is a warm
// near-black rather than #000000 — pure black on pure white is the one pairing that reliably
// tires the eye over a long writing session. Every neutral here carries a little red and
// yellow so the greys never drift cold, which is what makes a warm scheme read as paper
// instead of as a slightly-tinted default.
//
// The accents are earth pigments — clay, sage, ochre — all low-chroma enough to sit behind
// black text. The old indigo/amber pairing was cool against warm body copy and fought it.
internal val Violet40 = Color(0xFF6245A6)
internal val Violet80 = Color(0xFFD9C8FF)
internal val Coral40 = Color(0xFFB4644B)
internal val Coral80 = Color(0xFFFFCBBB)
internal val Sand40 = Color(0xFF8B6846)
internal val Sand80 = Color(0xFFF1D6B3)
internal val Rose40 = Color(0xFFAA4D5C)
internal val Rose80 = Color(0xFFFFB8C2)

// Warm paper, brightening toward the top of the page.
internal val Paper = Color(0xFFF5F2EF)
internal val PaperBright = Color(0xFFFFFCF9)
internal val Ink = Color(0xFF220F09)
internal val InkMuted = Color(0xFF685952)
internal val PaperOutline = Color(0xFF9C8A82)
internal val PaperOutlineSoft = Color(0xFFE6D8D2)

internal val LightColorScheme = lightColorScheme(
    primary = Coral40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE0D6),
    onPrimaryContainer = Color(0xFF3D170D),
    secondary = Sand40,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE5C4),
    onSecondaryContainer = Color(0xFF32200E),
    tertiary = Rose40,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DE),
    onTertiaryContainer = Color(0xFF43000F),
    error = Rose40,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9DE),
    onErrorContainer = Color(0xFF43000F),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECE5E1),
    onSurfaceVariant = InkMuted,
    outline = PaperOutline,
    // Material derives the tonal surface roles from its own baseline purple unless they are
    // set explicitly, which made every Card a different hue from the rest of the app. Each
    // step here is warmer and a little darker than the one below it, so stacked cards read
    // as layers of paper rather than as unrelated boxes.
    surfaceDim = Color(0xFFE4DDDA),
    surfaceBright = PaperBright,
    // Bright paper, not `#FFFFFF`. This is the one slot the baseline tonal palette would fill
    // with pure white, and pure white is the only neutral in the light scheme carrying neither
    // the red nor the yellow every other neutral keeps, so the range pills and the nav bar read
    // colder than the page they sit on. Dark's counterpart is already a warm near-black.
    surfaceContainerLowest = PaperBright,
    surfaceContainerLow = Color(0xFFFAF6F3),
    surfaceContainer = Color(0xFFF4ECE8),
    surfaceContainerHigh = Color(0xFFEDE5E1),
    surfaceContainerHighest = Color(0xFFE6DDDA),
    inverseSurface = Color(0xFF311C15),
    inverseOnSurface = Color(0xFFFFEEE8),
    inversePrimary = Coral80,
    outlineVariant = PaperOutlineSoft,
    surfaceTint = Coral40,
    scrim = Color(0xFF000000),
)

// Espresso rather than black: the same paper idea, inverted.
// A muted cocoa-charcoal base avoids the hard black/white jump that makes a dark screen feel
// bright at night. Surfaces stay close enough together that depth comes from shape and a soft
// border, not from stacked blocks of contrast.
internal val Espresso = Color(0xFF383334)
internal val EspressoBright = Color(0xFF3B3637)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD09A87),
    onPrimary = Color(0xFF3E2822),
    primaryContainer = Color(0xFF4C3934),
    onPrimaryContainer = Color(0xFFE1BBB0),
    secondary = Color(0xFFD0BA98),
    onSecondary = Color(0xFF393027),
    secondaryContainer = Color(0xFF484036),
    onSecondaryContainer = Color(0xFFDBC9AD),
    tertiary = Color(0xFFD0A6AD),
    onTertiary = Color(0xFF3D3032),
    tertiaryContainer = Color(0xFF4D3B3F),
    onTertiaryContainer = Color(0xFFDFC0C5),
    error = Color(0xFFD8AAB3),
    onError = Color(0xFF41292E),
    errorContainer = Color(0xFF513A3F),
    onErrorContainer = Color(0xFFE3C2C8),
    background = Espresso,
    // Keep content comfortably legible even though the surrounding surfaces deliberately
    // stay very close together. Reducing surface contrast must not turn body copy grey.
    onBackground = Color(0xFFE5DEDA),
    surface = Espresso,
    onSurface = Color(0xFFE5DEDA),
    surfaceVariant = Color(0xFF3E393A),
    onSurfaceVariant = Color(0xFFD4C9C5),
    outline = Color(0xFF6B6261),
    surfaceDim = Color(0xFF353031),
    surfaceBright = Color(0xFF403A3B),
    surfaceContainerLowest = Color(0xFF353031),
    surfaceContainerLow = Color(0xFF393435),
    surfaceContainer = EspressoBright,
    surfaceContainerHigh = Color(0xFF3D3839),
    surfaceContainerHighest = Color(0xFF413C3D),
    inverseSurface = Color(0xFFE3D9D5),
    inverseOnSurface = Color(0xFF393132),
    inversePrimary = Coral40,
    outlineVariant = Color(0xFF565051),
    surfaceTint = Color(0xFFD09A87),
    scrim = Color(0xFF000000),
)
