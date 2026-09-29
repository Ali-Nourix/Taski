package io.github.alinourix.taski.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
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
