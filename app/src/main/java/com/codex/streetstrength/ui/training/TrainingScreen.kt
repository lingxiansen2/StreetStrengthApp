package com.codex.streetstrength.ui.training

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SessionStatus
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.SetCompletionInput
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.domain.calculateTrainingProgress
import com.codex.streetstrength.timer.RestTimerService
import com.codex.streetstrength.ui.formatLoadKg
import com.codex.streetstrength.ui.formatMetric
import com.codex.streetstrength.ui.formatRestClock
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.components.ValueAdjuster
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TrainingEvent {
    data class StartRestTimer(val timerId: Long, val endElapsedMs: Long) : TrainingEvent
    data object StopRestTimer : TrainingEvent
}

data class TrainingUiState(
    val dayId: Long? = null,
    val sessionId: Long? = null,
    val sessionStatus: SessionStatus? = null,
    val dayTitle: String = "",
    val currentTaskId: Long? = null,
    val currentTaskName: String = "",
    val currentVariantName: String = "",
    val metricType: MetricType = MetricType.REPS,
    val currentSetIndex: Int = 0,
    val totalSets: Int = 0,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val targetLoadKg: Double = 0.0,
    val supportsLoad: Boolean = false,
    val restSeconds: Int = 0,
    val activeTimerId: Long? = null,
    val remainingMs: Long = 0L,
    val isResting: Boolean = false,
    val isFinished: Boolean = false,
    val isEmpty: Boolean = false,
    val isLastOverallSet: Boolean = false,
    val keepScreenOn: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingViewModel(
    private val repository: TrainingRepository,
    private val preferencesRepository: PreferencesRepository,
    private val date: String,
) : ViewModel() {
    private val dayId = MutableStateFlow<Long?>(null)
    private val sessionId = MutableStateFlow<Long?>(null)
    private val eventsFlow = MutableSharedFlow<TrainingEvent>()
    private val finalizedSessionIds = mutableSetOf<Long>()

    val events: Flow<TrainingEvent> = eventsFlow

    private val dayPlanFlow = repository.observeDayPlan(date)

    private val sessionFlow = dayId.filterNotNull().flatMapLatest { repository.observeLatestSession(it) }

    private val logsFlow = sessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeSetLogs(id)
    }

    private val timerFlow = sessionId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeActiveRestTimer(id)
    }

    private val tickerFlow = flow {
        while (true) {
            emit(android.os.SystemClock.elapsedRealtime())
            delay(250L)
        }
    }

    private data class TrainingBaseState(
        val dayPlan: com.codex.streetstrength.data.local.PlanDayWithTasks?,
        val session: com.codex.streetstrength.data.local.WorkoutSessionEntity?,
        val logs: List<com.codex.streetstrength.data.local.SetLogEntity>,
        val timer: com.codex.streetstrength.data.local.ActiveRestTimerEntity?,
        val preferences: com.codex.streetstrength.data.preferences.UserPreferences,
    )

    private val trainingBaseFlow = combine(
        dayPlanFlow,
        sessionFlow,
        logsFlow,
        timerFlow,
        preferencesRepository.preferencesFlow,
    ) { dayPlan, session, logs, timer, prefs ->
        TrainingBaseState(
            dayPlan = dayPlan,
            session = session,
            logs = logs,
            timer = timer,
            preferences = prefs,
        )
    }

    val uiState = combine(
        trainingBaseFlow,
        tickerFlow,
    ) { base, now ->
        val progress = calculateTrainingProgress(base.dayPlan, base.logs)
        val cursor = progress.cursor
        TrainingUiState(
            dayId = base.dayPlan?.day?.id,
            sessionId = base.session?.id,
            sessionStatus = base.session?.status,
            dayTitle = date,
            currentTaskId = cursor?.task?.task?.id,
            currentTaskName = cursor?.task?.template?.name.orEmpty(),
            currentVariantName = cursor?.task?.variant?.name.orEmpty(),
            metricType = cursor?.task?.variant?.metricType ?: MetricType.REPS,
            currentSetIndex = cursor?.setPlan?.setIndex ?: 0,
            totalSets = cursor?.task?.setPlans?.size ?: 0,
            targetReps = cursor?.setPlan?.targetReps,
            targetHoldSec = cursor?.setPlan?.targetHoldSec,
            targetLoadKg = cursor?.setPlan?.targetLoadKg ?: cursor?.task?.task?.plannedLoadKg ?: 0.0,
            supportsLoad = cursor?.task?.template?.supportsExternalLoad ?: false,
            restSeconds = cursor?.task?.task?.restSec ?: 0,
            activeTimerId = base.timer?.id,
            remainingMs = base.timer?.let { (it.endElapsedRealtimeMs - now).coerceAtLeast(0L) } ?: 0L,
            isResting = base.timer != null,
            isFinished = progress.isFinished,
            isEmpty = base.dayPlan?.tasks?.isEmpty() != false,
            isLastOverallSet = cursor?.isLastOverallSet ?: false,
            keepScreenOn = base.preferences.keepScreenOn,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TrainingUiState(),
    )

    init {
        viewModelScope.launch {
            val (day, session) = repository.ensureSessionForDate(date)
            dayId.value = day.id
            sessionId.value = session?.id
        }

        viewModelScope.launch {
            uiState.collect { state ->
                val activeSessionId = state.sessionId
                val activeDayId = state.dayId
                if (
                    state.isFinished &&
                    activeSessionId != null &&
                    activeDayId != null &&
                    state.sessionStatus == SessionStatus.IN_PROGRESS &&
                    finalizedSessionIds.add(activeSessionId)
                ) {
                    repository.completeSession(activeDayId)
                    eventsFlow.emit(TrainingEvent.StopRestTimer)
                }
            }
        }
    }

    fun completeCurrentSet(
        actualReps: Int?,
        actualHoldSec: Int?,
        actualLoadKg: Double,
    ) {
        val state = uiState.value
        val sessionId = state.sessionId ?: return
        val taskId = state.currentTaskId ?: return
        viewModelScope.launch {
            val timer = repository.completeSet(
                SetCompletionInput(
                    sessionId = sessionId,
                    taskId = taskId,
                    setIndex = state.currentSetIndex,
                    actualReps = actualReps,
                    actualHoldSec = actualHoldSec,
                    actualLoadKg = actualLoadKg,
                    restSec = state.restSeconds,
                    shouldStartRest = !state.isLastOverallSet,
                ),
            )
            sessionId.let { this@TrainingViewModel.sessionId.value = it }
            timer?.let {
                eventsFlow.emit(TrainingEvent.StartRestTimer(timerId = it.id, endElapsedMs = it.endElapsedRealtimeMs))
            }
        }
    }

    fun skipCurrentSet() {
        val state = uiState.value
        val sessionId = state.sessionId ?: return
        val taskId = state.currentTaskId ?: return
        viewModelScope.launch {
            repository.skipSet(sessionId = sessionId, taskId = taskId, setIndex = state.currentSetIndex)
        }
    }

    fun skipRest() {
        viewModelScope.launch {
            repository.clearRestTimer(uiState.value.activeTimerId)
            eventsFlow.emit(TrainingEvent.StopRestTimer)
        }
    }

    fun endWorkout() {
        val dayId = uiState.value.dayId ?: return
        viewModelScope.launch {
            repository.abandonSession(dayId)
            eventsFlow.emit(TrainingEvent.StopRestTimer)
        }
    }
}

@Composable
fun TrainingScreenRoute(
    app: StreetStrengthApp,
    date: String,
    onBack: () -> Unit,
) {
    val viewModel = rememberAppViewModel(key = "training:$date") {
        TrainingViewModel(
            repository = app.trainingRepository,
            preferencesRepository = app.preferencesRepository,
            date = date,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TrainingEvent.StartRestTimer -> RestTimerService.start(context, event.timerId, event.endElapsedMs)
                TrainingEvent.StopRestTimer -> RestTimerService.stop(context)
            }
        }
    }

    TrainingScreen(
        uiState = uiState,
        onBack = onBack,
        onCompleteSet = viewModel::completeCurrentSet,
        onSkipSet = viewModel::skipCurrentSet,
        onSkipRest = viewModel::skipRest,
        onEndWorkout = viewModel::endWorkout,
    )
}

@Composable
fun TrainingScreen(
    uiState: TrainingUiState,
    onBack: () -> Unit,
    onCompleteSet: (Int?, Int?, Double) -> Unit,
    onSkipSet: () -> Unit,
    onSkipRest: () -> Unit,
    onEndWorkout: () -> Unit,
) {
    val view = LocalView.current
    BackHandler(onBack = onBack)

    DisposableEffect(uiState.keepScreenOn) {
        view.keepScreenOn = uiState.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var actualReps by remember { mutableIntStateOf(uiState.targetReps ?: 0) }
    var actualHoldSec by remember { mutableIntStateOf(uiState.targetHoldSec ?: 0) }
    var actualLoad by remember { mutableDoubleStateOf(uiState.targetLoadKg) }

    LaunchedEffect(uiState.currentTaskId, uiState.currentSetIndex) {
        actualReps = uiState.targetReps ?: 0
        actualHoldSec = uiState.targetHoldSec ?: 0
        actualLoad = uiState.targetLoadKg
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Text(uiState.dayTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                when {
                    uiState.isEmpty -> {
                        Text("今天还没有安排训练", style = MaterialTheme.typography.headlineMedium)
                        Text("先去计划页添加动作。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    uiState.isFinished -> {
                        Text("训练完成", style = MaterialTheme.typography.headlineLarge)
                        Text("今天的所有动作已经记录完成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    else -> {
                        Text(
                            text = "第 ${uiState.currentSetIndex} / ${uiState.totalSets} 组",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = uiState.currentTaskName,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = uiState.currentVariantName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("目标", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = formatMetric(
                                        targetReps = uiState.targetReps,
                                        targetHoldSec = uiState.targetHoldSec,
                                    ),
                                    style = MaterialTheme.typography.headlineLarge,
                                )
                                if (uiState.supportsLoad) {
                                    Text(
                                        text = "负重 ${formatLoadKg(uiState.targetLoadKg)}",
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                            }
                        }

                        if (uiState.isResting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("休息中", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = formatRestClock(uiState.remainingMs),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        } else {
                            when (uiState.metricType) {
                                MetricType.REPS -> {
                                    ValueAdjuster(
                                        label = "实际次数",
                                        valueText = "${actualReps}次",
                                        onDecrease = { actualReps = (actualReps - 1).coerceAtLeast(0) },
                                        onIncrease = { actualReps = (actualReps + 1).coerceAtMost(50) },
                                    )
                                }

                                MetricType.HOLD_SECONDS, MetricType.HOLD_SECONDS_PLUS_ECCENTRIC -> {
                                    ValueAdjuster(
                                        label = "实际时长",
                                        valueText = "${actualHoldSec}秒",
                                        onDecrease = { actualHoldSec = (actualHoldSec - 1).coerceAtLeast(0) },
                                        onIncrease = { actualHoldSec = (actualHoldSec + 1).coerceAtMost(180) },
                                    )
                                }

                                MetricType.HOLD_TO_FAILURE -> {
                                    ValueAdjuster(
                                        label = "记录时长",
                                        valueText = "${actualHoldSec}秒",
                                        onDecrease = { actualHoldSec = (actualHoldSec - 1).coerceAtLeast(0) },
                                        onIncrease = { actualHoldSec = (actualHoldSec + 1).coerceAtMost(180) },
                                    )
                                }
                            }
                            if (uiState.supportsLoad) {
                                ValueAdjuster(
                                    label = "实际负重",
                                    valueText = formatLoadKg(actualLoad),
                                    onDecrease = { actualLoad = (actualLoad - 2.5).coerceAtLeast(0.0) },
                                    onIncrease = { actualLoad = (actualLoad + 2.5).coerceAtMost(100.0) },
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    uiState.isEmpty || uiState.isFinished -> {
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("返回")
                        }
                    }

                    uiState.isResting -> {
                        Button(onClick = onSkipRest, modifier = Modifier.fillMaxWidth()) {
                            Text("跳过休息")
                        }
                    }

                    else -> {
                        Button(
                            onClick = {
                                onCompleteSet(
                                    if (uiState.metricType == MetricType.REPS) actualReps else null,
                                    if (uiState.metricType != MetricType.REPS) actualHoldSec else null,
                                    actualLoad,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 18.dp),
                        ) {
                            Text("完成本组")
                        }
                    }
                }

                if (!uiState.isFinished && !uiState.isEmpty) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSkipSet, modifier = Modifier.weight(1f), enabled = !uiState.isResting) {
                            Text("跳过本组")
                        }
                        OutlinedButton(onClick = onEndWorkout, modifier = Modifier.weight(1f)) {
                            Text("结束训练")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TrainingPreview() {
    StreetStrengthTheme {
        TrainingScreen(
            uiState = TrainingUiState(
                dayTitle = "2026-04-22",
                currentTaskName = "引体向上",
                currentVariantName = "正手宽握",
                currentSetIndex = 2,
                totalSets = 5,
                targetReps = 5,
                targetLoadKg = 20.0,
                supportsLoad = true,
                currentTaskId = 1L,
                sessionId = 1L,
                dayId = 1L,
            ),
            onBack = {},
            onCompleteSet = { _, _, _ -> },
            onSkipSet = {},
            onSkipRest = {},
            onEndWorkout = {},
        )
    }
}
