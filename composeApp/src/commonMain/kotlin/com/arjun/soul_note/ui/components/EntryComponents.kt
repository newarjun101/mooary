package com.arjun.soul_note.ui.components

import com.arjun.soul_note.ui.labels.emotionGroupName
import com.arjun.soul_note.ui.labels.emotionLabel
import com.arjun.soul_note.ui.labels.moodLabel
import androidx.compose.foundation.clickable
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.common_favourite_add
import com.arjun.soul_note.resources.common_favourite_remove
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arjun.soul_note.domain.model.DiaryEntry
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodCatalog
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.Elevation
import com.arjun.soul_note.ui.theme.PaperCard
import com.arjun.soul_note.ui.theme.pressScale
import com.arjun.soul_note.ui.theme.Spacing
import com.arjun.soul_note.ui.theme.moodColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Single-select mood picker.
 *
 * The full [MoodCatalog] is offered rather than a short list, because a diary that
 * forces a "primary" mood loses information the user took effort to record.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodPicker(
    selected: Mood,
    onSelect: (Mood) -> Unit,
    modifier: Modifier = Modifier,
    moods: List<Mood> = MoodCatalog.all.map { it.mood },
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
    ) {
        moods.forEach { mood ->
            val isSelected = mood == selected
            val colors = moodColors(mood)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(mood) },
                label = {
                    // The dot keeps the mood legible when the chip is unselected and its
                    // container is plain, and it stays a second signal alongside the label
                    // and supplied illustration.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall + 2.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(colors.accent, CircleShape),
                        )
                        MoodIllustration(mood, Modifier.size(26.dp))
                        Text(text = moodLabel(mood))
                    }
                },
                colors = if (isSelected) {
                    FilterChipDefaults.filterChipColors(
                        containerColor = colors.container,
                        labelColor = colors.onContainer,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
                border = if (isSelected) {
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = true,
                        borderColor = colors.accent,
                        selectedBorderColor = colors.accent,
                        selectedBorderWidth = 1.5.dp,
                    )
                } else {
                    null
                },
            )
        }
    }
}

/** Multi-select emotion picker, grouped so the catalogue stays scannable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotionPicker(
    groups: List<Pair<String, List<Emotion>>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        groups.forEach { (groupName, emotions) ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
                Text(
                    text = emotionGroupName(groupName),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                ) {
                    emotions.forEach { emotion ->
                        FilterChip(
                            selected = emotion.id in selected,
                            onClick = { onToggle(emotion.id) },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                                ) {
                                    EmotionIllustration(emotion, Modifier.size(24.dp))
                                    Text(text = emotionLabel(emotion))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * List row for a diary entry.
 *
 * The mood is a thin coloured spine down the leading edge rather than a filled circle, for
 * the same reason a book has a ribbon: it marks the row without competing with the title,
 * and it stays legible at a glance when scanning a month. The illustration and date beside it
 * keep the colour from being the only signal.
 */
@Composable
fun DiaryEntryCard(
    entry: DiaryEntry,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = moodColors(entry.mood)
    // IntrinsicSize.Min so the spine can run the full height of the row; the row's height is
    // set by its tallest child, and the preview can wrap to one line or two.
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The favourite button is a child, so its own click still wins over the row's.
            .pressScale(onClick = onClick)
            .padding(end = Spacing.xSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaperCard(
            modifier = Modifier.weight(1f),
            shape = DiaryShapes.extraLarge,
            elevation = Elevation.raised,
            color = colors.container,
        ) {
            Row(
                // IntrinsicSize.Min is what lets the spine fill the row's height while the
                // row's height is still set by its tallest child. Without it the spine is
                // measured first and collapses to nothing.
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(end = Spacing.small, top = Spacing.medium, bottom = Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(colors.accent, DiaryShapes.extraSmall),
                )
                Spacer(Modifier.width(Spacing.medium - 3.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(42.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                            CircleShape,
                        )
                        .padding(vertical = Spacing.xSmall),
                ) {
                    MoodIllustration(entry.mood, Modifier.size(30.dp))
                    Text(
                        text = entry.entryDate.day.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.medium))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
                ) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.preview(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    if (entry.tags.isNotEmpty()) {
                        Text(
                            text = entry.tags.joinToString("  ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                IconToggleButton(
                    checked = entry.isFavorite,
                    onCheckedChange = { onToggleFavorite() },
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(
                            if (entry.isFavorite) Res.string.common_favourite_remove
                            else Res.string.common_favourite_add,
                        ),
                        tint = if (entry.isFavorite) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** A single labelled number in the Home and Profile summaries. */
data class Stat(val label: String, val value: String)

/**
 * Small labelled number used by the Home and Profile summaries.
 *
 * Rendered as a raised tile rather than bare text: a flat row of numbers had no grouping,
 * so the eye could not tell where one metric ended and the next began.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    PaperCard(
        modifier = modifier,
        shape = DiaryShapes.large,
        elevation = Elevation.card,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.medium - 2.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xSmall),
        ) {
            Text(
                text = value,
                // Serif at display scale: the number is the loudest thing on the screen for
                // a moment and then settles back, which is what a headline does.
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/**
 * Lays stat tiles out on a grid with a fixed column count and one consistent gutter.
 *
 * Four tiles stretched across a phone left each label ~95dp, so longer labels such as
 * "Entries this month" wrapped and the tiles touched with no gap at all. Letting each
 * screen pick its column count keeps every tile the same width on every screen.
 */
@Composable
fun StatTileGrid(
    stats: List<Stat>,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        stats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                row.forEach { stat ->
                    StatTile(
                        label = stat.label,
                        value = stat.value,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short final row aligned to the same columns as the full ones.
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Formats an entry date for list rows without pulling in a date library. */
fun LocalDate.toShortLabel(): String {
    val monthDigits = month.number.toString().padStart(2, '0')
    val dayDigits = day.toString().padStart(2, '0')
    return "$dayDigits/$monthDigits/$year"
}
