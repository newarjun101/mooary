package com.arjun.soul_note.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arjun.soul_note.core.validation.Limits
import com.arjun.soul_note.ui.theme.DiaryShapes
import com.arjun.soul_note.ui.theme.SectionLabelStyle
import com.arjun.soul_note.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Full-screen progress indicator with an optional caption.
 *
 * A spinner is replaced by three dots breathing in sequence. It is the same "something is
 * happening" signal, but it sits in the page's own language: a rotating arc is a control
 * metaphor, and three drifting points are closer to ink settling on paper.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    val transition = rememberInfiniteTransition(label = "loading")
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            // Staggered by a third of the cycle so the three read as a
                            // travelling motion rather than three blinking at once.
                            animation = tween(durationMillis = 900, delayMillis = index * 300),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(accent.copy(alpha = alpha), CircleShape),
                    )
                }
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shared empty/error placeholder so every list reports its state the same way.
 * [onRetry] is optional: when absent the retry affordance is hidden.
 */
@Composable
fun MessageState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            // The icon sits in a tinted disc rather than floating on the page. A 40dp glyph
            // alone is a loose shape in the middle of a lot of space; inside a disc it reads
            // as a deliberate mark, and it gives the eye somewhere to land first.
            Box(
                // A quiet outlined disc, not a filled coloured badge. The filled version
                // pulled the eye straight off the words, which is backwards on a screen whose
                // whole job is to explain that there is nothing here yet.
                Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedIcon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xSmall))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = Spacing.medium)) {
                    AnimatedIcon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Text(
                        text = actionLabel,
                        modifier = Modifier.padding(start = Spacing.small),
                    )
                }
            }
        }
    }
}

/**
 * A titled block with optional trailing content, used to break screens into sections.
 *
 * Set as a widely tracked small label rather than a title, with a rule running out to the
 * trailing content. On a page this is mostly prose, a heading that shouts louder than the
 * text under it is the thing that makes a long screen tiring to read.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (trailing == null) Modifier else Modifier.weight(1f),
        )
        // The rule is what makes a label read as a section divider rather than as a caption
        // that happens to sit above something.
        Box(
            Modifier
                .then(if (trailing == null) Modifier.weight(1f) else Modifier.width(Spacing.xSmall))
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        )
        trailing?.invoke()
    }
}

/**
 * Trailing indicator for a paged list, rendered whenever the server — or here, the local
 * database — says more rows exist.
 *
 * It is shown before the next read is actually triggered as well as while one is in flight:
 * the trigger is a distance from the end, so on a screen shorter than the prefetch distance
 * the footer is already on screen with nothing loading yet. Hiding it in that gap would make
 * the list visibly grow by a whole page instead.
 *
 * It deliberately renders nothing but the indicator: the next page is fetched from a local
 * database, so there is no meaningful retry or error state to offer here, and a fixed-height
 * spinner that never resolves would be worse than none.
 */
@Composable
fun PagingFooter(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.large),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

/**
 * Fetches the next page when the list gets within [Limits.PAGE_PREFETCH_DISTANCE] rows of the
 * end, so the trailing [PagingFooter] has usually resolved before the user scrolls to it.
 *
 * The trigger is derived from [layoutInfo] rather than a scroll offset because the footer row
 * itself participates in the index space: counting from `itemCount` keeps the threshold
 * correct whether or not the footer is currently composed.
 *
 * [enabled] must reflect `hasMore`; without it the effect re-arms on every layout pass at the
 * end of the list and calls [onLoadMore] forever.
 */
@Composable
fun LazyListState.RequestNextPageOnApproach(
    enabled: Boolean,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(this, enabled, itemCount) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= itemCount - 1 - Limits.PAGE_PREFETCH_DISTANCE
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
}
