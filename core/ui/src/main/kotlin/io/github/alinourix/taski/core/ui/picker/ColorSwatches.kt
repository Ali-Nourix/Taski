package io.github.alinourix.taski.core.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.model.ColorToken
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.theme.roles

@Composable
fun colorName(token: ColorToken): String = stringResource(
    when (token) {
        ColorToken.Palette.Red -> R.string.color_red
        ColorToken.Palette.Orange -> R.string.color_orange
        ColorToken.Palette.Yellow -> R.string.color_yellow
        ColorToken.Palette.Green -> R.string.color_green
        ColorToken.Palette.Cyan -> R.string.color_cyan
        ColorToken.Palette.Blue -> R.string.color_blue
        ColorToken.Palette.Purple -> R.string.color_purple
        ColorToken.Palette.Pink -> R.string.color_pink
        ColorToken.Palette.Gray -> R.string.color_gray
        is ColorToken.Custom -> R.string.color_custom
    },
)

/**
 * The plugin's palette as swatches, painted exactly as a chip in this theme
 * will look, plus one custom colour. The chosen swatch morphs into a cookie.
 */
@Composable
fun ColorSwatches(selected: ColorToken, onPick: (ColorToken) -> Unit, modifier: Modifier = Modifier) {
    var customOpen by remember { mutableStateOf(false) }
    val chosenShape = MaterialShapes.Cookie6Sided.toShape()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (token in ColorToken.Palette.entries) {
            Swatch(token, selected == token, chosenShape) { onPick(token) }
        }
        val custom = selected as? ColorToken.Custom
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(if (custom != null) chosenShape else CircleShape)
                .background(custom?.roles()?.accent ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, if (custom != null) chosenShape else CircleShape)
                .semantics {
                    this.selected = custom != null
                }
                .clickable(role = Role.RadioButton) { customOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Palette,
                contentDescription = stringResource(R.string.color_custom),
                tint = if (custom != null) custom.roles().onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (customOpen) {
        CustomColorDialog(
            initial = (selected as? ColorToken.Custom)?.let { Color(0xFF000000.toInt() or it.rgb) } ?: Color(0xFF7A5AF8),
            onDismiss = { customOpen = false },
            onPick = { customOpen = false; onPick(ColorToken.Custom(it.toArgb() and 0xFFFFFF)) },
        )
    }
}

@Composable
private fun Swatch(token: ColorToken, chosen: Boolean, chosenShape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val roles = token.roles()
    val name = colorName(token)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(if (chosen) chosenShape else CircleShape)
            .background(roles.accent)
            .semantics {
                this.selected = chosen
                contentDescription = name
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) Icon(Icons.Rounded.Check, contentDescription = null, tint = roles.onAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CustomColorDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1].coerceIn(0.25f, 1f)) }
    val color = Color.hsv(hue, saturation, 0.8f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(color))
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0.25f..1f)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A small filled circle in a tag or project colour. */
@Composable
fun ColorDot(token: ColorToken, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).clip(CircleShape).background(token.roles().accent))
}
