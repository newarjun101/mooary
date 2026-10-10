package com.arjun.mooary.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.action_delete
import com.arjun.mooary.resources.action_edit
import org.jetbrains.compose.resources.painterResource

enum class EntryActionIcon { EDIT, DELETE }

@Composable
fun ActionImageIcon(
    action: EntryActionIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "${action.name}ActionIcon")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${action.name}ActionLift",
    )
    Image(
        painter = painterResource(
            if (action == EntryActionIcon.EDIT) Res.drawable.action_edit else Res.drawable.action_delete,
        ),
        contentDescription = contentDescription,
        modifier = modifier
            .size(24.dp)
            .graphicsLayer { translationY = lift },
    )
}
