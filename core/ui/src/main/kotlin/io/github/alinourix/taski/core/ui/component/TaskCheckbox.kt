package io.github.alinourix.taski.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.alinourix.taski.core.domain.model.Priority
import io.github.alinourix.taski.core.domain.model.TaskStatus
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.statusLabel
import io.github.alinourix.taski.core.ui.theme.roles

/**
 * The plugin's four checkbox states, drawn so they read at a glance: an open
 * ring (tinted by priority), a ring half filled while in progress, and on
 * completion the circle morphs into a filled cookie with a tick, or a clover
 * with "!" for not done. Tap toggles done; long-press opens the status menu.
 */
@Composable
fun TaskCheckbox(
    status: TaskStatus,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    priority: Priority? = null,
    onLongPress: (() -> Unit)? = null,
    size: Dp = 26.dp,
) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val closed = status == TaskStatus.Done || status == TaskStatus.NotDone
    val fill by animateFloatAsState(if (closed) 1f else 0f, motion.fastSpatialSpec(), label = "fill")
    val half by animateFloatAsState(if (status == TaskStatus.InProgress) 1f else 0f, motion.defaultEffectsSpec(), label = "half")

    val doneMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val notDoneMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Clover4Leaf) }
    val ringColor = priority?.roles()?.accent ?: scheme.outline
    val fillColor = if (status == TaskStatus.NotDone) scheme.error else scheme.primary
    val iconColor = if (status == TaskStatus.NotDone) scheme.onError else scheme.onPrimary
    val stateText = stringResource(R.string.task_checkbox_state, statusLabel(status))
    val toggleText = stringResource(R.string.task_checkbox_toggle)

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics {
                contentDescription = toggleText
                stateDescription = stateText
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Checkbox,
                onLongClick = onLongPress?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                onClick = {
                    haptics.performHapticFeedback(if (closed) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggle()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val morph = if (status == TaskStatus.NotDone) notDoneMorph else doneMorph
        Canvas(Modifier.size(size)) {
            val stroke = 2.dp.toPx()
            val radius = this.size.minDimension / 2 - stroke / 2
            if (fill < 1f) {
                drawCircle(ringColor.copy(alpha = 1f - fill), radius = radius, style = Stroke(stroke))
            }
            if (half > 0f && fill < 1f) {
                drawArc(
                    color = scheme.primary.copy(alpha = half),
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(stroke * 2, stroke * 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke * 4, this.size.height - stroke * 4),
                )
            }
            if (fill > 0f) {
                val path = morph.toPath(fill)
                path.transform(Matrix().apply { scale(this@Canvas.size.width, this@Canvas.size.height) })
                drawPath(path, fillColor.copy(alpha = fill.coerceIn(0f, 1f)))
            }
        }
        if (fill > 0.01f) {
            Icon(
                imageVector = if (status == TaskStatus.NotDone) Icons.Rounded.PriorityHigh else Icons.Rounded.Check,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(size * 0.62f).graphicsLayer {
                    scaleX = fill
                    scaleY = fill
                },
            )
        }
    }
}
