package io.github.alinourix.taski.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.ui.R

/**
 * Notion's "+ New" at the foot of a group: a quiet line that turns into a
 * text field where it stands. Done adds the task and keeps the field open for
 * the next one; leaving it empty folds it back.
 */
@Composable
fun NewTaskRow(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 16.dp, end = 4.dp),
    iconGap: Dp = 14.dp,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!open) {
        Row(
            modifier = modifier.fillMaxWidth().clickable { onOpenChange(true) }.padding(padding).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(iconGap),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.task_new_in_group), style = MaterialTheme.typography.bodyLarge, color = muted)
        }
        return
    }
    var text by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = modifier.fillMaxWidth().padding(padding).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(iconGap),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isBlank()) onOpenChange(false) else { onAdd(text); text = "" }
            }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(stringResource(R.string.task_new_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    field()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused) focused = true else if (focused && text.isBlank()) onOpenChange(false)
                },
        )
        IconButton(onClick = { text = ""; onOpenChange(false) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel), tint = muted, modifier = Modifier.size(18.dp))
        }
    }
}
