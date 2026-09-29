package io.github.alinourix.taski.core.ui.picker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.alinourix.taski.core.domain.CalendarSystem
import io.github.alinourix.taski.core.domain.time.JalaliCalendar
import io.github.alinourix.taski.core.domain.time.JalaliDate
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.R
import io.github.alinourix.taski.core.ui.format.CalendarText
import io.github.alinourix.taski.core.ui.format.localized
import java.time.LocalDate
import java.time.LocalTime

/** A month in either calendar: its first day as a Gregorian date, and its length. */
internal data class CalendarMonth(val year: Int, val month: Int, val calendar: CalendarSystem) {
    val first: LocalDate
        get() = when (calendar) {
            CalendarSystem.Gregorian -> LocalDate.of(year, month, 1)
            CalendarSystem.Jalali -> JalaliCalendar.toGregorian(JalaliDate(year, month, 1))
        }

    val length: Int
        get() = when (calendar) {
            CalendarSystem.Gregorian -> first.lengthOfMonth()
            CalendarSystem.Jalali -> JalaliCalendar.monthLength(year, month)
        }

    fun plus(months: Int): CalendarMonth {
        val index = year * 12 + (month - 1) + months
        return copy(year = Math.floorDiv(index, 12), month = Math.floorMod(index, 12) + 1)
    }

    companion object {
        fun of(date: LocalDate, calendar: CalendarSystem): CalendarMonth =
            CalendarText.yearMonth(date, calendar).let { (y, m) -> CalendarMonth(y, m, calendar) }
    }
}

/**
 * The deadline picker, in the Gregorian or Jalali calendar the user chose:
 * quick picks, a month grid, and an optional time of day.
 */
@Composable
fun DatePickerSheet(
    date: LocalDate?,
    time: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, LocalTime?) -> Unit,
) {
    val config = LocalUiConfig.current
    val today = config.today
    var selected by rememberSaveable { mutableStateOf(date) }
    var selectedTime by rememberSaveable { mutableStateOf(time) }
    var month by remember { mutableStateOf(CalendarMonth.of(date ?: today, config.calendar)) }
    var showTime by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.pick_date), style = MaterialTheme.typography.headlineSmallEmphasized)

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickDate(stringResource(R.string.due_today), today, selected) { selected = it; month = CalendarMonth.of(it, config.calendar) }
                QuickDate(stringResource(R.string.due_tomorrow), today.plusDays(1), selected) { selected = it; month = CalendarMonth.of(it, config.calendar) }
                QuickDate(stringResource(R.string.next_week), today.plusWeeks(1), selected) { selected = it; month = CalendarMonth.of(it, config.calendar) }
                AssistChip(onClick = { selected = null; selectedTime = null }, label = { Text(stringResource(R.string.no_date)) })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    CalendarText.monthTitle(month.year, month.month, config.calendar, config.persian),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plus(-1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.previous_month))
                }
                IconButton(onClick = { month = month.plus(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.next_month))
                }
            }

            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    val forward = targetState.year * 12 + targetState.month > initialState.year * 12 + initialState.month
                    slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
                },
                label = "month",
            ) { shown ->
                MonthGrid(shown, selected, today) { selected = it }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = selectedTime
                if (current == null) {
                    FilledTonalButton(onClick = { showTime = true }, enabled = selected != null) {
                        Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.add_time), Modifier.padding(start = 8.dp))
                    }
                } else {
                    InputChip(
                        selected = true,
                        onClick = { showTime = true },
                        label = { Text(CalendarText.time(current, config.persian)) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.remove_time),
                                modifier = Modifier.size(18.dp).clickable { selectedTime = null },
                            )
                        },
                    )
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onConfirm(selected, selected?.let { selectedTime }) }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }

    if (showTime) {
        TimeDialog(
            initial = selectedTime ?: LocalTime.of(9, 0),
            onDismiss = { showTime = false },
            onConfirm = { selectedTime = it; showTime = false },
        )
    }
}

@Composable
private fun QuickDate(label: String, date: LocalDate, selected: LocalDate?, onPick: (LocalDate) -> Unit) {
    val chosen = selected == date
    AssistChip(
        onClick = { onPick(date) },
        label = { Text(label) },
        colors = if (chosen) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        border = if (chosen) null else AssistChipDefaults.assistChipBorder(enabled = true),
    )
}

@Composable
private fun MonthGrid(month: CalendarMonth, selected: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val config = LocalUiConfig.current
    val firstDay = CalendarText.firstDayOfWeek(config.calendar, config.persian)
    val leading = Math.floorMod(month.first.dayOfWeek.value - firstDay.value, 7)
    val cells = leading + month.length
    val rows = (cells + 6) / 7
    val selectedShape = MaterialShapes.Cookie9Sided.toShape()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    CalendarText.weekdayShort(firstDay.plus(i.toLong()), config.persian),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col - leading
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (index in 0 until month.length) {
                            val day = month.first.plusDays(index.toLong())
                            val isSelected = day == selected
                            val isToday = day == today
                            Surface(
                                shape = if (isSelected) selectedShape else CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                border = if (isToday && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.size(40.dp).clip(if (isSelected) selectedShape else CircleShape)
                                    .semantics { this.selected = isSelected }
                                    .clickable(role = Role.Button) { onPick(day) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        (index + 1).localized(config.persian),
                                        style = if (isSelected || isToday) MaterialTheme.typography.bodyLargeEmphasized else MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
