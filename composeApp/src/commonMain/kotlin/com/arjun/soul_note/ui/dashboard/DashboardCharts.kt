package com.arjun.soul_note.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arjun.soul_note.domain.model.EmotionShare
import com.arjun.soul_note.domain.model.Emotion
import com.arjun.soul_note.domain.model.Mood
import com.arjun.soul_note.domain.model.MoodDistribution
import com.arjun.soul_note.domain.model.TimeRange
import com.arjun.soul_note.domain.model.TrendPoint
import com.arjun.soul_note.ui.labels.emotionName
import com.arjun.soul_note.ui.labels.moodName
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.Elevation
import com.arjun.soul_note.ui.theme.PaperCard
import com.arjun.soul_note.ui.theme.Spacing
import com.arjun.soul_note.ui.theme.moodColors
import com.arjun.soul_note.ui.theme.luminanceIsDark
import kotlinx.datetime.LocalDate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.chart_emotions_summary
import com.arjun.soul_note.resources.chart_frequency_summary
import com.arjun.soul_note.resources.chart_mood_mix_summary
import com.arjun.soul_note.resources.chart_mood_trend_summary
import com.arjun.soul_note.resources.chart_emotion_share
import com.arjun.soul_note.resources.chart_mood_share
import com.arjun.soul_note.resources.dashboard_no_emotions
import com.arjun.soul_note.resources.dashboard_no_entries
import com.arjun.soul_note.resources.dashboard_no_moods
import com.arjun.soul_note.ui.labels.emotionLabel
import com.arjun.soul_note.ui.labels.monthNameRes
import com.arjun.soul_note.ui.labels.moodLabel
import com.arjun.soul_note.ui.components.EmotionIllustration
import com.arjun.soul_note.ui.components.MoodIllustration
import kotlinx.datetime.number
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Hand-drawn charts for the dashboard.
 *
 * No charting library: every shape here is a handful of `drawArc`/`drawLine` calls, which
 * keeps the binary small and the colours on the app's own theme. The trade-off is that
 * axes, ticks and legends are hand-built, so each chart keeps its labelling to a minimum
 * rather than pretending to be a full charting surface.
 *
 * Two rules hold across all four:
 * - Colour is never the only signal. Every slice and bar is also named in text beside the
 *   shape, which is what the accessibility descriptions report.
 * - A missing value is drawn as a gap, never as a zero. Flat-lining a gap to the floor
 *   would claim the user felt terrible on days they simply did not record.
 */

private val MaxMoodScore = 5.0

/** A titled card wrapping one chart. */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    PaperCard(
        modifier = modifier.fillMaxWidth(),
        shape = DiaryShapes.large,
        elevation = Elevation.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/**
 * Mood split as a paper-cut donut, with a labelled legend and a tappable slice.
 *
 * Flat, not extruded. A pie's job is proportion, and an oblique projection distorts exactly
 * that: the slices nearest the viewer measure wider than the slices at the back, so the chart
 * argues with its own legend. Depth comes instead from a quiet contact shadow, a recessed
 * centre and the warm rim between slices. Those details make it feel tactile without making
 * one mood look more important merely because it is closest to the viewer.
 *
 * Tapping a slice selects it. The selection has to be visible in the shape as well as in the
 * caption, because a caption that changes on its own is easy to miss; so the chosen slice
 * moves out along its own mid-angle, the rest step back, and the caption names it. Tapping it
 * again, or tapping the middle, clears the selection.
 */
@Composable
fun MoodPieChart(
    distribution: List<MoodDistribution>,
    totalRecords: Int,
    caption: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 168.dp,
) {
    val accents = moodAccents(distribution)
    val scheme = MaterialTheme.colorScheme
    val extents = shareOfCircle(distribution.map { it.count })
    val description = pieDescription(distribution, totalRecords)
    // Keyed on the moods themselves, so a range change resets the selection whether it drops a
    // mood or merely swaps one for another -- and the derived read below is what the drawing
    // uses, so a stale index degrades to "nothing selected" rather than to a crash.
    val selectedIndexState = remember(distribution.map { it.mood }) { mutableStateOf(-1) }
    val selectedIndex by selectedIndexState
    val selected = if (selectedIndex in distribution.indices) selectedIndex else -1

    // A single sweep angle drives the whole entrance, so the pie fills like a clock hand
    // rather than every slice growing at once from its final offset -- which reads as a pie
    // losing pieces, not as one being drawn.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(620, easing = FastOutSlowInEasing),
        label = "pieEnter",
    )
    val shown = visibleSweeps(extents, enter)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .semantics { contentDescription = description }
                // Keyed on the moods, not the count. Two ranges can both hold eight moods and
                // be entirely different ones, and a selection left pointing at index 3 would
                // then highlight and caption the wrong mood with nothing to show it had moved.
                // `selectedIndex` is a holder rather than a plain `by` delegate read inside the
                // detector. A gesture block is only rebuilt when its key changes, and the key
                // here is the extents -- so a plain capture would freeze the selection as it was
                // when the detector started, `hit == selected` would be false forever, and
                // tapping a selected slice would re-select it instead of clearing it.
                .pointerInput(extents, distribution.map { it.mood }) {
                    detectTapGestures { tap ->
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        // Same inset as the draw path, and it has to be the same number: a
                        // hit radius that disagrees with the drawn radius accepts taps on
                        // empty page outside the pie and rejects taps on its edge.
                        val outer = minOf(size.width, size.height) / 2f - 4.dp.toPx()
                        val dx = tap.x - centre.x
                        val dy = tap.y - centre.y
                        val distance = hypot(dx, dy)
                        val hit = sliceAt(clockDegreesFromCentre(dx, dy), extents)
                        // A dead zone around the middle, and outside the rim, clears rather than
                        // guessing. The centre needs this to be an explicit radius rather than a
                        // by-product of the angle: `atan2(0, 0)` is 180 degrees, so a dead-centre
                        // tap resolves to the *last* slice and selects whatever mood happens to
                        // sit at six o'clock. A pie's centre belongs to no slice, and the caption
                        // is right there saying so.
                        val inside = outer > 0f &&
                            distance <= outer &&
                            distance >= CENTRE_DEAD_ZONE.toPx()
                        val current = selectedIndexState.value
                        selectedIndexState.value =
                            if (!inside || hit < 0) -1 else if (hit == current) -1 else hit
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Density-independent for the same reason as the pop: the hairline and the
                // inset are drawn, not composed, so a pixel constant silently thins the gap
                // and lets slices touch on a high-density screen.
                val outer = size.minDimension / 2f - 4.dp.toPx()
                val centre = Offset(size.width / 2f, size.height / 2f)
                // The small centre opening is a deliberate visual well rather than a fake 3D
                // extrusion: proportions remain truthful, while the raised ring feels like a
                // physical printed chart resting on the paper card.
                val inner = outer * 0.34f
                val rim = scheme.surfaceContainerHigh
                val rimOutline = scheme.outlineVariant
                val shadowOffset = 3.dp.toPx()
                contactShadow(
                    centre = centre + Offset(0f, shadowOffset),
                    radiusX = outer * 0.88f,
                    radiusY = 5.dp.toPx(),
                    alpha = if (scheme.background.luminanceIsDark()) 0.26f else 0.12f,
                )
                // Painting a rim first means the hairline gaps reveal a warm, deliberate
                // separator rather than the empty canvas behind them.
                drawCircle(color = rim, radius = outer, center = centre)
                // Density-independent. The distance is part of how a selection reads, so a raw
                // pixel constant is a different pie on every screen: 7px is 4% of a 168dp pie
                // at mdpi and under 2% on a 420dpi phone, where the slice would barely move and
                // the caption would be the only evidence anything had been selected.
                val pop = 7.dp.toPx()
                // A hairline between slices, but never on a single full-circle slice:
                // subtracting a gap from 360 degrees would cut a notch out of the pie.
                val gap = if (distribution.size > 1) 2f else 0f
                var startAngle = 0f

                distribution.forEachIndexed { index, slice ->
                    val extent = extents[index]
                    val sweep = shown[index]
                    val mid = startAngle + extent / 2f
                    val isSelected = index == selected
                    // Draw the chosen slice last so it sits above its neighbours instead of
                    // being overlapped by the slice that follows it round the circle.
                    if (sweep > 0f && !isSelected) {
                        drawArc(
                            color = accents.getValue(slice.mood)
                                .copy(alpha = if (selected >= 0) 0.42f else 1f),
                            startAngle = startAngle + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.4f),
                            useCenter = true,
                            topLeft = Offset(centre.x - outer, centre.y - outer),
                            size = Size(outer * 2f, outer * 2f),
                        )
                    }
                    if (sweep > 0f && isSelected) {
                        // Pulled straight out along its own mid-angle, which is why the
                        // offset needs the angle rather than a scale: scaling would grow the
                        // slice and make its area lie about its share.
                        val radians = mid * PI / 180.0
                        val push = pop * enter
                        drawArc(
                            color = accents.getValue(slice.mood),
                            startAngle = startAngle + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.4f),
                            useCenter = true,
                            topLeft = Offset(
                                centre.x - outer + (cos(radians) * push).toFloat(),
                                centre.y - outer + (sin(radians) * push).toFloat(),
                            ),
                            size = Size(outer * 2f, outer * 2f),
                        )
                    }
                    startAngle += extent
                }

                // A recessed centre creates room for the live label and makes the otherwise
                // flat chart read as a single crafted object. The outline stays subtle enough
                // that it never competes with the mood colours.
                drawCircle(color = scheme.surface, radius = inner, center = centre)
                drawCircle(
                    color = rimOutline,
                    radius = inner,
                    center = centre,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    color = rimOutline.copy(alpha = 0.68f),
                    radius = outer,
                    center = centre,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            val chosen = distribution.getOrNull(selected)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = chosen?.let { (it.count * 100 / totalRecords.coerceAtLeast(1)).toString() + "%" }
                        ?: totalRecords.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = chosen?.let { moodName(it.mood) } ?: caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                chosen?.let { MoodIllustration(it.mood, Modifier.size(30.dp)) }
            }
        }

        distribution.forEachIndexed { index, slice ->
            MoodPieLegendRow(
                mood = slice.mood,
                name = moodName(slice.mood),
                accent = accents.getValue(slice.mood),
                percent = (slice.ratio * 100).toInt(),
                count = slice.count,
                selected = index == selected,
                onClick = { selectedIndexState.value = if (index == selected) -1 else index },
            )
        }
    }
}

@Composable
private fun MoodPieLegendRow(
    mood: Mood,
    name: String,
    accent: Color,
    percent: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(Res.string.chart_mood_share, name, percent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DiaryShapes.small)
            .background(
                if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
            )
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = Spacing.small, vertical = Spacing.xSmall)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(accent, CircleShape),
        )
        MoodIllustration(mood, Modifier.size(24.dp))
        Text(
            text = name,
            style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (selected) "$count" else "$percent%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Sweeps to actually draw for a pie part-way through its entrance.
 *
 * One shared angle budget rather than scaling every slice's own sweep, because scaling each
 * slice independently leaves a visible gap between the last slice and the one before it while
 * the pie is still filling. The sum is always [progress] of 360.
 */
internal fun visibleSweeps(extents: List<Float>, progress: Float): List<Float> {
    if (extents.isEmpty()) return emptyList()
    var remaining = 360f * progress.coerceIn(0f, 1f)
    return extents.map { extent ->
        val drawn = minOf(extent, remaining)
        remaining -= drawn
        drawn.coerceAtLeast(0f)
    }
}

/**
 * Which slice a tap at [angleDegrees] lands in, or -1 for none.
 *
 * [angleDegrees] is the same 0-at-twelve-o'clock, clockwise measure the arcs are drawn with, so
 * this agrees with the drawing by construction rather than by two conversions agreeing.
 *
 * Half-open intervals, deliberately: a closed test at 360 would hand the last slice a
 * boundary it shares with the first, and a gap between two slices has to belong to neither.
 */
internal fun sliceAt(angleDegrees: Float, extents: List<Float>): Int {
    if (extents.isEmpty()) return -1
    val angle = ((angleDegrees % 360f) + 360f) % 360f
    var start = 0f
    extents.forEachIndexed { index, extent ->
        if (extent > 0f && angle >= start && angle < start + extent) return index
        start += extent
    }
    return -1
}

/**
 * Radius around the middle of the pie that belongs to no slice.
 *
 * Small enough that it never eats a real slice -- it covers the centre label, which is where a
 * thumb lands by accident when trying to dismiss -- and large enough to be a comfortable target
 * rather than a few stray pixels.
 */
private val CENTRE_DEAD_ZONE = 20.dp

/** Twelve o'clock is 0 and the angle grows clockwise, matching how the arcs are drawn. */
internal fun clockDegreesFromCentre(dx: Float, dy: Float): Float =
    (atan2(dx, -dy) * 180.0 / PI).toFloat()

/**
 * Average mood per bucket as a raised 3D ribbon over a receding floor.
 *
 * Y is the fixed 0-5 mood scale rather than a fitted range, so the shape is comparable between
 * the 7-day and 1-year views: a flat line means "unchanged", not "rescaled".
 *
 * The ribbon is drawn as a band with a lit top edge and a shadowed underside rather than a
 * plain stroke, because a 1px line at 2.5dp wide cannot take a shadow and a mood trend is the
 * one chart on this screen that people look at longest.
 */
@Composable
fun MoodTrendChart(
    points: List<TrendPoint>,
    range: TimeRange,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val lowMood = MaterialTheme.colorScheme.error
    val steadyMood = MaterialTheme.colorScheme.tertiary
    val brightMood = MaterialTheme.colorScheme.secondary
    val pointFill = MaterialTheme.colorScheme.surface
    val description = trendDescription(points)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
        ) {
            val floor = size.height - 3f
            val yFor: (Double) -> Float = { score ->
                val fraction = (score / MaxMoodScore).coerceIn(0.0, 1.0).toFloat()
                floor * (1f - fraction)
            }

            // Three very quiet score bands make the line easier to read before the user has
            // traced it point-by-point: difficult, steady and bright. They are flat colours,
            // not a gradient, so the chart remains part of the paper system.
            drawRect(
                color = brightMood.copy(alpha = 0.055f),
                size = Size(size.width, yFor(3.0)),
            )
            drawRect(
                color = steadyMood.copy(alpha = 0.045f),
                topLeft = Offset(0f, yFor(3.0)),
                size = Size(size.width, yFor(1.0) - yFor(3.0)),
            )
            drawRect(
                color = lowMood.copy(alpha = 0.045f),
                topLeft = Offset(0f, yFor(1.0)),
                size = Size(size.width, floor - yFor(1.0)),
            )

            listOf(1.0, 3.0, 5.0).forEach { score ->
                val y = yFor(score)
                drawLine(
                    color = grid,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            val segments = trendSegments(points, size.width, yFor)
            segments.forEach { segment ->
                if (segment.size == 1) {
                    val p = segment.first()
                    drawCircle(
                        color = shade(line, 0f, 0f, 1f),
                        radius = 3.5f,
                        center = Offset(p.x, p.y + 2f),
                    )
                    drawCircle(color = line, radius = 3.5f, center = p)
                } else {
                    // Three passes over the same path with different weights is what turns a
                    // stroke into something with a body; the offsets are one depth unit. The
                    // middle pass is a flat translucent fill -- it used to be a vertical wash
                    // that faded out toward the floor, which was doing nothing a single
                    // alpha does not do better and cost a brush per frame.
                    val path = Path().apply {
                        moveTo(segment.first().x, segment.first().y)
                        cubicSmoothTo(segment)
                    }
                    val area = Path().apply {
                        moveTo(segment.first().x, floor)
                        lineTo(segment.first().x, segment.first().y)
                        cubicSmoothTo(segment)
                        lineTo(segment.last().x, floor)
                        close()
                    }
                    drawPath(path = area, color = line.copy(alpha = 0.10f))
                    drawPath(
                        path = path,
                        color = Color(red = 0f, green = 0f, blue = 0f, alpha = 0.5f),
                        style = Stroke(width = 5f, cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = path,
                        color = shade(line, 0.2f, 0.4f, 0.8f),
                        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                    )
                    drawPath(
                        path = path,
                        color = line,
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round),
                    )
                    segment.forEach { point ->
                        drawCircle(color = pointFill, radius = 4f, center = point)
                        drawCircle(color = line, radius = 2.25f, center = point)
                    }
                    // The newest mood is the one readers most often look for, so it gets a
                    // restrained halo rather than a label that would collide with the axis.
                    drawCircle(
                        color = line.copy(alpha = 0.18f),
                        radius = 7f,
                        center = segment.last(),
                    )
                    drawCircle(color = line, radius = 3f, center = segment.last())
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = axisLabel(points.firstOrNull()?.bucketStart, range),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = axisLabel(points.lastOrNull()?.bucketStart, range),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Diary entries per bucket, so writing frequency is visible next to how it felt. */
@Composable
fun EntryFrequencyChart(
    points: List<TrendPoint>,
    range: TimeRange,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp,
) {
    val barColors = listOf(
        Color(0xFF26A69A), // teal
        Color(0xFF5C6BC0), // indigo
        Color(0xFFF2A900), // ochre
        Color(0xFFAB47BC), // violet
        Color(0xFFE57373), // coral
    )
    val floorTint = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    val peak = (points.maxOfOrNull { it.entryCount } ?: 0).coerceAtLeast(1)
    val description = frequencyDescription(points)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description },
        ) {
            val floor = size.height - 2f
            val slot = size.width / points.size.coerceAtLeast(1)
            // 30 buckets in a phone width leaves ~11dp a slot, so the bar takes a bit over
            // half of it and never collapses to a hairline.
            val barWidth = (slot * 0.42f).coerceAtLeast(2f)

            // A soft floor under the whole series, so the bars stand on something instead of
            // hanging off a hairline.
            drawOval(
                color = floorTint.copy(alpha = 0.06f),
                topLeft = Offset(-slot * 0.2f, floor - 2f),
                size = Size(size.width + slot * 0.4f, 6f),
            )

            points.forEachIndexed { index, point ->
                if (point.entryCount <= 0) return@forEachIndexed
                val left = slot * index + (slot - barWidth) / 2f
                val fraction = point.entryCount.toFloat() / peak
                val barHeight = (size.height * fraction * 0.82f).coerceAtLeast(barWidth * 0.6f)
                contactShadow(
                    centre = Offset(left + barWidth / 2f, floor + 1f),
                    radiusX = barWidth * 0.5f,
                    radiusY = 2.5f,
                    alpha = 0.55f,
                )
                // A capsule gives every bar a friendly rounded crown. The shared baseline is
                // painted afterwards, visually anchoring the lower cap so the chart retains a
                // clear zero line instead of looking like floating pills.
                drawRoundRect(
                    // Cycling a small editorial palette distinguishes adjacent days without
                    // inventing a second data dimension. Height remains the only magnitude.
                    color = barColors[index % barColors.size],
                    topLeft = Offset(left, floor - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }

            drawLine(
                color = baseline,
                start = Offset(0f, floor),
                end = Offset(size.width, floor),
                strokeWidth = 1.5f,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = axisLabel(points.firstOrNull()?.bucketStart, range),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = axisLabel(points.lastOrNull()?.bucketStart, range),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The most frequent emotions as labelled horizontal bars.
 *
 * Built from layout rather than a canvas: each row is mostly text, and text in a canvas
 * cannot wrap or scale with the user's font size. The track and its filled bar are drawn on a
 * canvas so their caps stay perfectly round at every density. Bars are scaled against the
 * largest share, not against 100%, because a record can carry
 * several emotions and the shares therefore do not sum to one.
 */
@Composable
fun EmotionBreakdownChart(
    shares: List<EmotionShare>,
    modifier: Modifier = Modifier,
) {
    val peak = (shares.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val description = emotionDescription(shares)

    Column(
        modifier = modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        shares.forEachIndexed { index, share ->
            val fill = emotionBarColor(share.emotion)
            // A bar owns its entrance by emotion id, so opening the dashboard plays the
            // frequency story once. A later sync changing a count keeps the bar present and
            // lets `animateFloatAsState` adjust its width rather than replaying from zero.
            var revealed by remember(share.emotion.id) { mutableStateOf(false) }
            LaunchedEffect(share.emotion.id) {
                delay(index * 70L)
                revealed = true
            }
            val fraction by animateFloatAsState(
                targetValue = if (revealed) share.count.toFloat() / peak else 0f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                label = "emotionFrequencyBar",
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xSmall)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmotionIllustration(share.emotion, Modifier.size(24.dp))
                    Spacer(Modifier.width(Spacing.small))
                    Text(
                        text = emotionName(share.emotion),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = share.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    val radius = size.height / 2f
                    // A full-width pill track gives the short bars a deliberate endpoint. The
                    // fill uses the same radius but clamps it to its own width, so a one-count
                    // emotion remains a neat rounded capsule rather than a clipped oval.
                    drawRoundRect(
                        color = track,
                        size = size,
                        cornerRadius = CornerRadius(radius, radius),
                    )
                    val barWidth = size.width * fraction
                    if (barWidth < 1f) return@Canvas
                    drawRoundRect(
                        color = fill,
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(minOf(radius, barWidth / 2f), radius),
                    )
                }
            }
        }
    }
}

/** Emotion colours group the bars at a glance while text and illustrations keep them accessible. */
@Composable
private fun emotionBarColor(emotion: Emotion): Color = when (emotion.id) {
    "joy", "excited", "grateful", "motivated" -> Color(0xFFF2A900)
    "calm", "hopeful" -> Color(0xFF26A69A)
    "love" -> Color(0xFFE57373)
    "confident" -> Color(0xFF7CB342)
    "tired", "bored", "confused" -> Color(0xFF5C6BC0)
    "sad", "lonely", "angry", "stressed" -> Color(0xFFD32F2F)
    "anxious" -> Color(0xFFAB47BC)
    else -> MaterialTheme.colorScheme.primary
}

/**
 * Maps every mood in [distribution] to its accent colour.
 *
 * `associate` cannot be used because [moodColors] reads the theme and so only composes
 * inside an inline lambda.
 */
@Composable
private fun moodAccents(distribution: List<MoodDistribution>): Map<Mood, Color> {
    val accents = mutableMapOf<Mood, Color>()
    distribution.forEach { accents[it.mood] = moodColors(it.mood).accent }
    return accents
}

/**
 * Splits the series into runs of consecutive recorded buckets.
 *
 * A run boundary is where the next point is missing, so the line breaks instead of
 * interpolating across days the user never wrote about.
 */
private fun DrawScope.trendSegments(
    points: List<TrendPoint>,
    width: Float,
    yFor: (Double) -> Float,
): List<List<Offset>> {
    if (points.isEmpty()) return emptyList()
    val lastIndex = (points.size - 1).coerceAtLeast(1)
    val segments = mutableListOf<MutableList<Offset>>()
    var current = mutableListOf<Offset>()

    points.forEachIndexed { index, point ->
        val average = point.averageMood
        if (average == null) {
            if (current.isNotEmpty()) segments += current
            current = mutableListOf()
        } else {
            val x = if (points.size == 1) width / 2f else width * index / lastIndex
            current += Offset(x, yFor(average))
        }
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

/**
 * Adds a Catmull-Rom-like cubic curve through already plotted points.
 *
 * The data dots remain on the true values; the curve only softens the visual journey between
 * them. End controls are clamped to their endpoint so a short series never hooks past its
 * first or last recorded day.
 */
private fun Path.cubicSmoothTo(points: List<Offset>) {
    for (index in 0 until points.lastIndex) {
        val before = points.getOrElse(index - 1) { points[index] }
        val start = points[index]
        val end = points[index + 1]
        val after = points.getOrElse(index + 2) { end }
        cubicTo(
            start.x + (end.x - before.x) / 6f,
            start.y + (end.y - before.y) / 6f,
            end.x - (after.x - start.x) / 6f,
            end.y - (after.y - start.y) / 6f,
            end.x,
            end.y,
        )
    }
}

/** Compact tick text; numeric for days, the month's own name for the yearly view. */
@Composable
private fun axisLabel(date: LocalDate?, range: TimeRange): String {
    if (date == null) return ""
    return when (range) {
        TimeRange.Last7Days, TimeRange.Last30Days, TimeRange.Last12Weeks -> date.day.toString()
        TimeRange.Last12Months -> stringResource(monthNameRes(date.month.number))
    }
}

@Composable
private fun pieDescription(distribution: List<MoodDistribution>, total: Int): String {
    if (distribution.isEmpty()) return stringResource(Res.string.dashboard_no_moods)
    val parts = mutableListOf<String>()
    for (slice in distribution) {
        parts += stringResource(
            Res.string.chart_mood_share,
            moodLabel(slice.mood),
            (slice.ratio * 100).toInt(),
        )
    }
    return stringResource(Res.string.chart_mood_mix_summary, total, parts.joinToString(", "))
}

@Composable
private fun trendDescription(points: List<TrendPoint>): String {
    val recorded = points.count { it.averageMood != null }
    return if (recorded == 0) {
        stringResource(Res.string.dashboard_no_moods)
    } else {
        stringResource(Res.string.chart_mood_trend_summary, points.size, recorded)
    }
}

@Composable
private fun frequencyDescription(points: List<TrendPoint>): String {
    val total = points.sumOf { it.entryCount }
    val busiest = points.maxOf { it.entryCount }
    return if (total == 0) {
        stringResource(Res.string.dashboard_no_entries)
    } else {
        stringResource(Res.string.chart_frequency_summary, total, busiest)
    }
}

@Composable
private fun emotionDescription(shares: List<EmotionShare>): String {
    if (shares.isEmpty()) return stringResource(Res.string.dashboard_no_emotions)
    val parts = mutableListOf<String>()
    for (share in shares) {
        parts += stringResource(Res.string.chart_emotion_share, emotionLabel(share.emotion), share.count)
    }
    return stringResource(Res.string.chart_emotions_summary, parts.joinToString(", "))
}
