package io.github.alinourix.taski.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** An icon inside one of the Material 3 Expressive shapes (cookie, clover, sunny…). */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.5f,
    contentDescription: String? = null,
) {
    val shape = polygon.toShape()
    Box(
        modifier = modifier.size(size).clip(shape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = contentColor, modifier = Modifier.size(iconSize))
    }
}

/** A letter (a project's initial) inside an expressive shape. */
@Composable
fun ShapeLetter(
    letter: String,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie6Sided,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    val shape = polygon.toShape()
    Box(modifier = modifier.size(size).clip(shape).background(containerColor), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.titleMediumEmphasized, color = contentColor)
    }
}

private val ProjectShapes = listOf(
    MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon,
    MaterialShapes.Cookie9Sided, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst,
)

/** A project keeps its shape for good: it comes from the id, not from where the project sits in a list. */
fun projectShape(projectId: String): RoundedPolygon = ProjectShapes[(projectId.hashCode() and Int.MAX_VALUE) % ProjectShapes.size]
