package com.arjun.mooary.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.arjun.mooary.domain.model.Mood

/**
 * Presentation-only mood colours.
 *
 * Colour is never the only signal: every place these are used also renders the mood's
 * supplied illustration and label, so the scale stays readable for colour-blind users and in
 * high-contrast mode. The pairs are hand-picked per mood rather than generated from
 * [Mood.score], because the scale is not linear (e.g. [Mood.ANGRY] scores 2 yet must
 * read as alarming, not neutral).
 */
data class MoodColors(
    val container: Color,
    val onContainer: Color,
    /**
     * A mid-tone that reads as a filled shape on both a light and a dark background.
     *
     * Separate from [container] because the container colours are deliberately pale
     * surfaces for text, and a chart slice wants the opposite: enough saturation to tell
     * two adjacent segments apart. Using `container` as a fill made neighbouring segments
     * merge into one indistinguishable blob.
     */
    val accent: Color,
)

@Composable
fun moodColors(mood: Mood): MoodColors {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    return when (mood) {
        Mood.VERY_HAPPY, Mood.EXCITED ->
            if (dark) MoodColors(Color(0xFF4A3B00), Color(0xFFFFE08A), Color(0xFFFFC107)) else MoodColors(Color(0xFFFFE9A8), Color(0xFF4A3B00), Color(0xFFF2A900))

        Mood.HAPPY ->
            if (dark) MoodColors(Color(0xFF3B4A00), Color(0xFFCFE58A), Color(0xFF9CCC65)) else MoodColors(Color(0xFFE5F3B8), Color(0xFF2E3A00), Color(0xFF7CB342))

        Mood.GOOD ->
            if (dark) MoodColors(Color(0xFF14403A), Color(0xFF9FE6DA), Color(0xFF4DB6AC)) else MoodColors(Color(0xFFCDEEE7), Color(0xFF0C3A33), Color(0xFF26A69A))

        Mood.NEUTRAL ->
            if (dark) MoodColors(Color(0xFF3A3A3F), Color(0xFFE2E2E6), Color(0xFFBDBDBD)) else MoodColors(Color(0xFFE8E8EC), Color(0xFF2C2C31), Color(0xFF8E8E93))

        Mood.TIRED ->
            if (dark) MoodColors(Color(0xFF2F3B52), Color(0xFFBFD0EE), Color(0xFF90A4AE)) else MoodColors(Color(0xFFDDE5F5), Color(0xFF24314A), Color(0xFF78909C))

        Mood.SAD, Mood.VERY_SAD ->
            if (dark) MoodColors(Color(0xFF2A3A52), Color(0xFFB8CBE8), Color(0xFF8C9EFF)) else MoodColors(Color(0xFFDCE5F2), Color(0xFF1F2C40), Color(0xFF5C6BC0))

        Mood.ANXIOUS ->
            if (dark) MoodColors(Color(0xFF3E2A4A), Color(0xFFE4BEF0), Color(0xFFCE93D8)) else MoodColors(Color(0xFFF0DEF7), Color(0xFF3A2044), Color(0xFFAB47BC))

        Mood.ANGRY ->
            if (dark) MoodColors(Color(0xFF5A1F1A), Color(0xFFFFC0B8), Color(0xFFEF9A9A)) else MoodColors(Color(0xFFFBDAD5), Color(0xFF4A1611), Color(0xFFD32F2F))
    }
}
