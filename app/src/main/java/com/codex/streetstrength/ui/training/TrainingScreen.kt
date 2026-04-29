package com.codex.streetstrength.ui.training

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SessionStatus
import com.codex.streetstrength.data.model.TimerState
import com.codex.streetstrength.data.model.TrainingOrderMode
import com.codex.streetstrength.data.model.parseTrainingOrderModeNote
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.SetCompletionInput
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.domain.calculateTrainingProgress
import com.codex.streetstrength.timer.RestTimerService
import com.codex.streetstrength.ui.components.ValueAdjuster
import com.codex.streetstrength.ui.formatLoadKg
import com.codex.streetstrength.ui.formatMetric
import com.codex.streetstrength.ui.formatRestClock
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
    data object WorkoutEnded : TrainingEvent
}

data class TrainingUiState(
    val dayId: Long? = null,
    val sessionId: Long? = null,
    val sessionStatus: SessionStatus? = null,
    val dayTitle: String = "",
    val currentTaskId: Long? = null,
    val currentTaskName: String = "",
    val currentVariantName: String = "",
    val trainingOrderMode: TrainingOrderMode = TrainingOrderMode.CIRCUIT,
    val roundIndex: Int = 0,
    val totalRounds: Int = 0,
    val positionInRound: Int = 0,
    val tasksInRound: Int = 0,
    val metricType: MetricType = MetricType.REPS,
    val currentSetIndex: Int = 0,
    val totalSets: Int = 0,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val targetLoadKg: Double = 0.0,
    val supportsLoad: Boolean = false,
    val restSeconds: Int = 0,
    val activeTimerId: Long? = null,
    val activeTimerEndElapsedMs: Long? = null,
    val remainingMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val isResting: Boolean = false,
    val isRestComplete: Boolean = false,
    val isFinished: Boolean = false,
    val isEmpty: Boolean = false,
    val isLastOverallSet: Boolean = false,
    val keepScreenOn: Boolean = true,
    val trainingTips: String? = null,
    val tutorialUrl: String? = null,
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
        val orderMode = parseTrainingOrderModeNote(base.dayPlan?.day?.note)
        val progress = calculateTrainingProgress(base.dayPlan, base.logs, orderMode)
        val cursor = progress.cursor
        val activeTimer = base.timer
        val remainingMs = activeTimer?.let { (it.endElapsedRealtimeMs - now).coerceAtLeast(0L) } ?: 0L
        val isTimerExpired = activeTimer?.endElapsedRealtimeMs?.let { it <= now } == true
        val isRestComplete = activeTimer != null && (activeTimer.state == TimerState.FIRED || isTimerExpired)
        val isResting = activeTimer != null && !isRestComplete
        val elapsedMs = base.session?.startedAt?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L

        TrainingUiState(
            dayId = base.dayPlan?.day?.id,
            sessionId = base.session?.id,
            sessionStatus = base.session?.status,
            dayTitle = date,
            currentTaskId = cursor?.task?.task?.id,
            currentTaskName = cursor?.task?.template?.name.orEmpty(),
            currentVariantName = cursor?.task?.variant?.name.orEmpty(),
            trainingOrderMode = orderMode,
            roundIndex = cursor?.roundIndex ?: 0,
            totalRounds = cursor?.totalRounds ?: 0,
            positionInRound = cursor?.positionInRound ?: 0,
            tasksInRound = cursor?.tasksInRound ?: 0,
            metricType = cursor?.task?.variant?.metricType ?: MetricType.REPS,
            currentSetIndex = cursor?.setPlan?.setIndex ?: 0,
            totalSets = cursor?.task?.setPlans?.size ?: 0,
            targetReps = cursor?.setPlan?.targetReps,
            targetHoldSec = cursor?.setPlan?.targetHoldSec,
            targetLoadKg = cursor?.setPlan?.targetLoadKg ?: cursor?.task?.task?.plannedLoadKg ?: 0.0,
            supportsLoad = cursor?.task?.template?.supportsExternalLoad ?: false,
            restSeconds = cursor?.task?.task?.restSec ?: 0,
            activeTimerId = activeTimer?.id,
            activeTimerEndElapsedMs = activeTimer?.endElapsedRealtimeMs,
            remainingMs = remainingMs,
            elapsedMs = elapsedMs,
            isResting = isResting,
            isRestComplete = isRestComplete,
            isFinished = progress.isFinished,
            isEmpty = base.dayPlan?.tasks?.isEmpty() != false,
            isLastOverallSet = cursor?.isLastOverallSet ?: false,
            keepScreenOn = base.preferences.keepScreenOn,
            trainingTips = mergeTrainingTips(
                templateCue = cursor?.task?.template?.cue,
                variantCue = cursor?.task?.variant?.cue,
            ),
            tutorialUrl = buildTutorialUrl(
                taskName = cursor?.task?.template?.name,
                variantName = cursor?.task?.variant?.name,
            ),
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
        val activeSessionId = state.sessionId ?: return
        val taskId = state.currentTaskId ?: return
        viewModelScope.launch {
            val timer = repository.completeSet(
                SetCompletionInput(
                    sessionId = activeSessionId,
                    taskId = taskId,
                    setIndex = state.currentSetIndex,
                    actualReps = actualReps,
                    actualHoldSec = actualHoldSec,
                    actualLoadKg = actualLoadKg,
                    restSec = state.restSeconds,
                    shouldStartRest = !state.isLastOverallSet,
                ),
            )
            sessionId.value = activeSessionId
            timer?.let {
                eventsFlow.emit(TrainingEvent.StartRestTimer(timerId = it.id, endElapsedMs = it.endElapsedRealtimeMs))
            }
        }
    }

    fun skipCurrentSet() {
        val state = uiState.value
        val activeSessionId = state.sessionId ?: return
        val taskId = state.currentTaskId ?: return
        viewModelScope.launch {
            repository.skipSet(sessionId = activeSessionId, taskId = taskId, setIndex = state.currentSetIndex)
        }
    }

    fun skipRest() {
        viewModelScope.launch {
            repository.clearRestTimer(uiState.value.activeTimerId)
            eventsFlow.emit(TrainingEvent.StopRestTimer)
        }
    }

    fun endWorkout() {
        viewModelScope.launch {
            uiState.value.dayId?.let { activeDayId ->
                repository.abandonSession(activeDayId)
            }
            eventsFlow.emit(TrainingEvent.StopRestTimer)
            eventsFlow.emit(TrainingEvent.WorkoutEnded)
        }
    }
}

@Composable
fun TrainingScreenRoute(
    app: StreetStrengthApp,
    date: String,
    onBack: () -> Unit,
    onWorkoutEnded: () -> Unit = onBack,
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
                TrainingEvent.WorkoutEnded -> onWorkoutEnded()
            }
        }
    }

    LaunchedEffect(uiState.activeTimerId, uiState.activeTimerEndElapsedMs, uiState.isResting) {
        val timerId = uiState.activeTimerId
        val endElapsedMs = uiState.activeTimerEndElapsedMs
        if (uiState.isResting && timerId != null && endElapsedMs != null) {
            RestTimerService.start(context, timerId, endElapsedMs)
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
    val uriHandler = LocalUriHandler.current
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

    val isActiveTraining = !uiState.isEmpty && !uiState.isFinished && uiState.sessionId != null
    var showDetails by remember(uiState.sessionId) { mutableStateOf(false) }
    var showEndWorkoutConfirm by remember(uiState.sessionId) { mutableStateOf(false) }
    val completeSetWithCurrentValues = {
        onCompleteSet(
            if (uiState.metricType == MetricType.REPS) actualReps else null,
            if (uiState.metricType != MetricType.REPS) actualHoldSec else null,
            actualLoad,
        )
    }
    val requestEndWorkout = { showEndWorkoutConfirm = true }

    if (showEndWorkoutConfirm) {
        EndWorkoutConfirmDialog(
            onDismiss = { showEndWorkoutConfirm = false },
            onConfirm = {
                showEndWorkoutConfirm = false
                onEndWorkout()
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (isActiveTraining && !showDetails) {
            TrainingFocusScreen(
                uiState = uiState,
                onBack = onBack,
                onCompleteSet = completeSetWithCurrentValues,
                onSkipSet = onSkipSet,
                onSkipRest = onSkipRest,
                onEndWorkout = requestEndWorkout,
                onOpenDetails = { showDetails = true },
            )
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                    Text(uiState.dayTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!uiState.isEmpty && !uiState.isFinished && uiState.sessionId != null) {
                    TrainingStatusCard(uiState)
                    ReminderPermissionCard()
                    OutlinedButton(
                        onClick = { showDetails = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("\u8fd4\u56de\u4e13\u6ce8\u9875")
                    }
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
                            text = trainingProgressLabel(uiState),
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

                        if (!uiState.trainingTips.isNullOrBlank() || !uiState.tutorialUrl.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "训练要点",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    uiState.trainingTips?.takeIf { it.isNotBlank() }?.let { tips ->
                                        Text(
                                            text = tips,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    uiState.tutorialUrl?.takeIf { it.isNotBlank() }?.let { tutorialUrl ->
                                        OutlinedButton(
                                            onClick = { runCatching { uriHandler.openUri(tutorialUrl) } },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("查看教程视频")
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.isResting || uiState.isRestComplete) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (uiState.isRestComplete) "休息结束" else "休息中",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = if (uiState.isRestComplete) "00:00" else formatRestClock(uiState.remainingMs),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (uiState.isRestComplete) {
                                        Text(
                                            text = "手动进入下一项后继续",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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

                    uiState.isRestComplete -> {
                        Button(onClick = onSkipRest, modifier = Modifier.fillMaxWidth()) {
                            Text("开始下一项")
                        }
                    }

                    uiState.isResting -> {
                        Button(onClick = onSkipRest, modifier = Modifier.fillMaxWidth()) {
                            Text("跳过休息")
                        }
                    }

                    else -> {
                        Button(
                            onClick = completeSetWithCurrentValues,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 18.dp),
                        ) {
                            Text("完成本组")
                        }
                    }
                }

                if (!uiState.isFinished && !uiState.isEmpty) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onSkipSet,
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isResting && !uiState.isRestComplete,
                        ) {
                            Text("跳过本组")
                        }
                        OutlinedButton(onClick = requestEndWorkout, modifier = Modifier.weight(1f)) {
                            Text("结束训练")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndWorkoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("结束当前训练？") },
        text = {
            Text("当前训练会退出并停止休息提醒。已记录的组会保留，未完成的组不会自动补记。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "结束训练",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("继续训练")
            }
        },
    )
}

@Composable
private fun TrainingFocusScreen(
    uiState: TrainingUiState,
    onBack: () -> Unit,
    onCompleteSet: () -> Unit,
    onSkipSet: () -> Unit,
    onSkipRest: () -> Unit,
    onEndWorkout: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "\u8fd4\u56de")
                }
                Text(uiState.dayTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = when {
                            uiState.isRestComplete -> "\u4f11\u606f\u7ed3\u675f"
                            uiState.isResting -> "\u4f11\u606f\u4e2d"
                            else -> "\u8bad\u7ec3\u4e2d"
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = when {
                            uiState.isRestComplete -> "\u7b49\u5f85\u624b\u52a8\u7ee7\u7eed"
                            uiState.isResting -> formatRestClock(uiState.remainingMs)
                            else -> "\u5df2\u5f00\u59cb ${formatElapsedClock(uiState.elapsedMs)}"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ReminderPermissionCard(onlyIfNeedsAttention = true)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = trainingProgressLabel(uiState),
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
                Text(
                    text = buildString {
                        append("\u76ee\u6807 ")
                        append(formatMetric(uiState.targetReps, uiState.targetHoldSec))
                        if (uiState.supportsLoad) {
                            append(" \u00b7 \u8d1f\u91cd ")
                            append(formatLoadKg(uiState.targetLoadKg))
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = when {
                    uiState.isRestComplete -> onSkipRest
                    uiState.isResting -> onSkipRest
                    else -> onCompleteSet
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) {
                Text(
                    text = when {
                        uiState.isRestComplete -> "\u5f00\u59cb\u4e0b\u4e00\u9879"
                        uiState.isResting -> "\u8df3\u8fc7\u4f11\u606f"
                        else -> "\u5b8c\u6210\u672c\u7ec4"
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onSkipSet,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isResting && !uiState.isRestComplete,
                ) {
                    Text("\u8df3\u8fc7\u672c\u7ec4")
                }
                OutlinedButton(onClick = onEndWorkout, modifier = Modifier.weight(1f)) {
                    Text("\u7ed3\u675f\u8bad\u7ec3")
                }
            }

            OutlinedButton(
                onClick = onOpenDetails,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\u8fdb\u5165\u8be6\u60c5\u9875")
            }
        }
    }
}

@Composable
private fun TrainingStatusCard(uiState: TrainingUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = when {
                    uiState.isRestComplete -> "\u4f11\u606f\u7ed3\u675f\uff0c\u7b49\u5f85\u624b\u52a8\u7ee7\u7eed"
                    uiState.isResting -> "\u4f11\u606f\u4e2d \u00b7 ${formatRestClock(uiState.remainingMs)}"
                    else -> "\u8bad\u7ec3\u4e2d"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "\u5df2\u5f00\u59cb ${formatElapsedClock(uiState.elapsedMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReminderPermissionCard(
    onlyIfNeedsAttention: Boolean = false,
) {
    val context = LocalContext.current
    val notificationReady = rememberReminderNotificationReady(context)
    val exactAlarmReady = rememberExactAlarmReady(context)
    if (onlyIfNeedsAttention && notificationReady && exactAlarmReady) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "\u540e\u53f0\u63d0\u9192",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    notificationReady && exactAlarmReady -> "\u901a\u77e5\u548c\u7cbe\u786e\u95f9\u949f\u6743\u9650\u6b63\u5e38\uff0c\u5207\u5230\u540e\u53f0\u540e\u4f9d\u7136\u4f1a\u5c1d\u8bd5\u53d1\u51fa\u4f11\u606f\u7ed3\u675f\u9707\u52a8\u3002"
                    !notificationReady && !exactAlarmReady -> "\u901a\u77e5\u548c\u7cbe\u786e\u95f9\u949f\u90fd\u672a\u5b8c\u6574\u5f00\u542f\uff0c\u540e\u53f0\u63d0\u9192\u53ef\u80fd\u5ef6\u8fdf\u6216\u4e0d\u663e\u793a\u3002"
                    !notificationReady -> "\u901a\u77e5\u6743\u9650\u672a\u5f00\u542f\uff0c\u4f11\u606f\u7ed3\u675f\u53ef\u80fd\u65e0\u6cd5\u5f39\u51fa\u63d0\u9192\u3002"
                    else -> "\u7cbe\u786e\u95f9\u949f\u6743\u9650\u672a\u5f00\u542f\uff0c\u4f11\u606f\u7ed3\u675f\u5728\u540e\u53f0\u53ef\u80fd\u5ef6\u8fdf\u3002"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!notificationReady) {
                    OutlinedButton(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("\u901a\u77e5\u8bbe\u7f6e")
                    }
                }
                if (!exactAlarmReady) {
                    OutlinedButton(
                        onClick = { openExactAlarmSettings(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("\u95f9\u949f\u6743\u9650")
                    }
                }
                if (notificationReady && exactAlarmReady) {
                    OutlinedButton(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("\u901a\u77e5\u8bbe\u7f6e")
                    }
                    OutlinedButton(
                        onClick = { openExactAlarmSettings(context) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("\u95f9\u949f\u8bbe\u7f6e")
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
                currentVariantName = "标准正握",
                roundIndex = 1,
                totalRounds = 4,
                positionInRound = 2,
                tasksInRound = 3,
                currentSetIndex = 1,
                totalSets = 4,
                targetReps = 5,
                targetLoadKg = 20.0,
                supportsLoad = true,
                currentTaskId = 1L,
                sessionId = 1L,
                dayId = 1L,
                trainingTips = "全程收紧核心，避免摆动。",
            ),
            onBack = {},
            onCompleteSet = { _, _, _ -> },
            onSkipSet = {},
            onSkipRest = {},
            onEndWorkout = {},
        )
    }
}

private fun mergeTrainingTips(templateCue: String?, variantCue: String?): String? {
    val tips = listOfNotNull(templateCue?.trim(), variantCue?.trim())
        .filter { it.isNotBlank() }
        .distinct()
    if (tips.isEmpty()) return null
    return tips.joinToString("\n")
}

private fun buildTutorialUrl(taskName: String?, variantName: String?): String? {
    val keyword = listOfNotNull(taskName?.trim(), variantName?.trim())
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (keyword.isBlank()) return null
    val query = "$keyword 街健 动作教程"
    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
    return "https://search.bilibili.com/all?keyword=$encoded"
}

private fun trainingProgressLabel(uiState: TrainingUiState): String = when (uiState.trainingOrderMode) {
    TrainingOrderMode.CIRCUIT -> {
        "\u7b2c${uiState.roundIndex}/${uiState.totalRounds}\u8f6e \u00b7 \u672c\u8f6e${uiState.positionInRound}/${uiState.tasksInRound}\u9879"
    }
    TrainingOrderMode.SEQUENTIAL -> {
        "\u7b2c${uiState.currentSetIndex}/${uiState.totalSets}\u7ec4 \u00b7 \u52a8\u4f5c${uiState.positionInRound}/${uiState.tasksInRound}\u9879"
    }
}

private fun formatElapsedClock(elapsedMs: Long): String {
    val totalSeconds = elapsedMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun rememberReminderNotificationReady(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun rememberExactAlarmReady(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        true
    } else {
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openExactAlarmSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
