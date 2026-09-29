package io.github.alinourix.taski.feature.timer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alinourix.taski.core.designsystem.component.ConnectedToggleGroup
import io.github.alinourix.taski.core.designsystem.component.EmptyState
import io.github.alinourix.taski.core.designsystem.component.LoadingState
import io.github.alinourix.taski.core.domain.timer.FocusTimerState
import io.github.alinourix.taski.core.ui.LocalClock
import io.github.alinourix.taski.core.ui.LocalUiConfig
import io.github.alinourix.taski.core.ui.format.localizeDigits
import io.github.alinourix.taski.core.ui.picker.TaskPickerSheet
import kotlinx.coroutines.delay
import io.github.alinourix.taski.core.ui.R as UiR

private val PRESETS = listOf(15, 25, 45, 60)

@Composable
fun FocusTimerScreen(onBack: () -> Unit, onOpenTask: (String) -> Unit, viewModel: FocusTimerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableIntStateOf(0) }
    val chosenMinutes = if (minutes == 0) state.defaultMinutes else minutes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(UiR.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val timer = state.timer
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when {
                state.loading -> LoadingState()
                timer == null -> {
                    EmptyState(Icons.Rounded.Timer, stringResource(R.string.timer_pick_task), body = stringResource(R.string.timer_pick_hint))
                    MinutePresets(chosenMinutes) { minutes = it }
                    Button(onClick = { picking = true }, modifier = Modifier.widthIn(min = 200.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Text(stringResource(R.string.timer_pick_task), Modifier.padding(start = 8.dp))
                    }
                }
                else -> Running(state, timer, viewModel, onOpenTask, onChangeTask = { picking = true })
            }
        }
    }

    if (picking) {
        TaskPickerSheet(
            title = stringResource(R.string.timer_pick_task),
            searchHint = stringResource(R.string.timer_search),
            tasks = state.candidates,
            onDismiss = { picking = false },
        ) { viewModel.start(it, chosenMinutes); picking = false }
    }
}

@Composable
private fun MinutePresets(selected: Int, onSelect: (Int) -> Unit) {
    val persian = LocalUiConfig.current.persian
    val labels = PRESETS.associateWith { stringResource(R.string.timer_minutes, it).localizeDigits(persian) }
    ConnectedToggleGroup(PRESETS, selected.takeIf { it in PRESETS }, onSelect, labels::getValue)
}

@Composable
private fun Running(state: TimerUiState, timer: FocusTimerState, viewModel: FocusTimerViewModel, onOpenTask: (String) -> Unit, onChangeTask: () -> Unit) {
    val persian = LocalUiConfig.current.persian
    val clock = LocalClock.current
    var now by remember { mutableLongStateOf(clock.nowMillis()) }
    LaunchedEffect(timer) {
        now = clock.nowMillis()
        while (timer.isRunning) {
            delay(1_000 - now % 1_000)
            now = clock.nowMillis()
        }
    }
    val fraction = timer.fraction(now)
    val animated by animateFloatAsState(fraction, MaterialTheme.motionScheme.slowEffectsSpec(), label = "progress")

    Surface(onClick = { state.task?.let { onOpenTask(it.id) } }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.task?.task?.title.orEmpty(), style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.timer_open_task), Modifier.size(18.dp))
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        CircularWavyProgressIndicator(
            progress = { animated },
            modifier = Modifier.size(300.dp),
            stroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            trackStroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            amplitude = { if (timer.isRunning) WavyProgressIndicatorDefaults.indicatorAmplitude(it) else 0f },
            wavelength = 48.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                FocusTimerState.formatClock(timer.remainingSeconds(now)).localizeDigits(persian),
                style = MaterialTheme.typography.displayLargeEmphasized,
            )
            if (!timer.isRunning) Text(stringResource(R.string.timer_paused), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    PlayPauseButton(running = timer.isRunning, onClick = { if (timer.isRunning) viewModel.pause() else viewModel.resume() })

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { viewModel.add(5) }) { Text(stringResource(R.string.timer_add, 5).localizeDigits(persian)) }
        FilledTonalButton(onClick = { viewModel.add(15) }) { Text(stringResource(R.string.timer_add, 15).localizeDigits(persian)) }
        FilledTonalIconButton(onClick = viewModel::reset, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.RestartAlt, stringResource(R.string.timer_reset)) }
        FilledTonalIconButton(onClick = viewModel::stop, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Stop, stringResource(R.string.timer_stop)) }
    }
    TextButton(onClick = onChangeTask) {
        Icon(Icons.Rounded.SwapHoriz, null)
        Text(stringResource(R.string.timer_change_task), Modifier.padding(start = 8.dp))
    }
    if (state.focusedSeconds > 0) {
        Text(
            stringResource(R.string.timer_focused_total, FocusTimerState.formatClock(state.focusedSeconds)).localizeDigits(persian),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A large play/pause button whose shape morphs: a cookie while paused, a softer square while running. */
@Composable
private fun PlayPauseButton(running: Boolean, onClick: () -> Unit) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square) }
    val progress by animateFloatAsState(if (running) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "morph")
    val shape = remember(progress) { MorphShape(morph, progress) }
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(104.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (running) R.string.timer_pause else R.string.timer_resume),
                Modifier.size(44.dp),
            )
        }
    }
}

private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
