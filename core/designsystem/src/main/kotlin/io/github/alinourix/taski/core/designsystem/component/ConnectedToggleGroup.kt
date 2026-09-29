package io.github.alinourix.taski.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single-choice connected button group: the Material 3 Expressive
 * replacement for segmented buttons. The selected button morphs to its
 * checked shape; the ends keep their outer corners.
 */
@Composable
fun <T> ConnectedToggleGroup(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    showLabels: Boolean = true,
    fillWidth: Boolean = true,
    /** A shorter group, for toolbars where the buttons are chrome rather than content. */
    height: Dp? = null,
) {
    Row(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .then(if (height != null) Modifier.height(height) else Modifier)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = ToggleButtonDefaults.contentPaddingFor(ToggleButtonDefaults.size, hasStartIcon = icon != null)
                    .let { if (showLabels) it else androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp) },
            ) {
                icon?.let {
                    Icon(it(option), contentDescription = if (showLabels) null else label(option), modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                }
                if (showLabels) {
                    if (icon != null) androidx.compose.foundation.layout.Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
