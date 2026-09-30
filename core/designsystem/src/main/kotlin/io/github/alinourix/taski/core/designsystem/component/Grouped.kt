package io.github.alinourix.taski.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Segmented list shapes: items in a group sit 2dp apart, with outer corners
 * on the group and near-square inner ones, so a group reads as one quiet block.
 */
object GroupedShapes {
    val Outer: Dp = 12.dp
    val Inner: Dp = 3.dp
    val Gap: Dp = 2.dp

    @Stable
    fun forIndex(index: Int, count: Int, outer: Dp = Outer, inner: Dp = Inner): Shape = when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/**
 * A section as one large rounded container, one tonal step above the page. In a
 * lazy list each row draws its own slice: the first rounds the top, the last the
 * bottom, the ones between are square, and the side margin keeps it off the edge.
 */
object Sections {
    val Radius: Dp = 28.dp
    val Margin: Dp = 16.dp

    @Stable
    fun shape(index: Int, count: Int): Shape = when {
        count <= 1 -> RoundedCornerShape(Radius)
        index == 0 -> RoundedCornerShape(topStart = Radius, topEnd = Radius)
        index == count - 1 -> RoundedCornerShape(bottomStart = Radius, bottomEnd = Radius)
        else -> RectangleShape
    }
}

/** Places a row in its section container: margin, clipped slice, tonal fill. */
@Composable
fun Modifier.sectionRow(index: Int, count: Int, color: Color = MaterialTheme.colorScheme.surfaceContainerLow): Modifier =
    padding(horizontal = Sections.Margin).clip(Sections.shape(index, count)).background(color)
