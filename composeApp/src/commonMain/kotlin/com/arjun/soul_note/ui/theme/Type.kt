package com.arjun.soul_note.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Serif for anything the user reads, sans for anything the user operates.
//
// The split is the whole editorial trick: an entry title or a run of diary prose in a serif
// reads like a page, while buttons, counters and captions in sans stay quiet and legible at
// small sizes where a serif's thin strokes disappear.
//
// These are the *platform* serifs (Georgia/Roboto Serif/Times) reached through
// [FontFamily.Serif], not a bundled typeface. Bundling a display face would mean shipping
// font binaries, picking a licence, and adding weight to the binary for every platform, and
// a diary gains nothing from a novelty face — the serif is doing the work, not a personality.
private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

/**
 * Material 3 type scale, set as an editorial page.
 *
 * Two deliberate departures from the stock scale:
 *
 *  - Body copy is serif and runs at a taller line height. Diary entries are long-form prose
 *    rather than UI labels, and 1.6x is about where serif stays comfortable without the
 *    leading starting to look loose.
 *  - Tracking tightens as size grows. Large serif defaults to wide tracking, which makes a
 *    headline look like a label; pulling it in by a few hundredths reads as intentional.
 */
internal val DiaryTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.4).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        // Small caps-ish: wide tracking makes an 11sp sans label read as a deliberate
        // caption instead of shrunken body text.
        letterSpacing = 0.6.sp,
    ),
)

/**
 * Small-caps-ish styling for the section labels that sit above a block of content.
 *
 * Applied as a separate style rather than folded into [DiaryTypography] so the one place
 * that labels a section and the places that label a chip cannot drift apart.
 */
internal val SectionLabelStyle = TextStyle(
    fontFamily = Sans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.2.sp,
)
