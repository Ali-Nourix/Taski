package io.github.alinourix.taski.feature.board

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A card that answers a touch by changing shape: its corners tighten while pressed and spring
 * back on release, the way Material 3 Expressive buttons do. Big rest radius, small pressed one.
 */
@Composable
internal fun MorphCard(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    restRadius: Dp = 28.dp,
    pressedRadius: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) pressedRadius else restRadius, MaterialTheme.motionScheme.fastSpatialSpec(), label = "radius")
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(shape)
            .background(color, shape)
            .combinedClickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick, onLongClick = onLongClick),
    ) { content() }
}
