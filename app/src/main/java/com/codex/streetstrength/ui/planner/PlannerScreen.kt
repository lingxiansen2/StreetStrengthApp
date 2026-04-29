package com.codex.streetstrength.ui.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.debug.PlanTestingSwitch
import com.codex.streetstrength.data.local.ExerciseCategoryWithTemplates
import com.codex.streetstrength.data.local.DayTaskWithDetails
import com.codex.streetstrength.data.local.GoalEntity
import com.codex.streetstrength.data.local.PlanCycleEntity
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.model.CalendarCompletionStatus
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.TrainingOrderMode
import com.codex.streetstrength.data.model.parseTrainingOrderModeNote
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.CustomExerciseDraft
import com.codex.streetstrength.data.repository.DayTaskEditDraft
import com.codex.streetstrength.data.repository.DayTaskDraft
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.domain.PlanDayActionPolicy
import com.codex.streetstrength.domain.PlanDateTiming
import com.codex.streetstrength.domain.resolvePlanDayActionPolicy
import com.codex.streetstrength.ui.formatLoadKg
import com.codex.streetstrength.ui.formatMetric
import com.codex.streetstrength.ui.formatShortDate
import com.codex.streetstrength.ui.formatWeekdayCn
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.components.ValueAdjuster
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MIN_SETS = 1
private const val MAX_SETS = 99
private const val MIN_REPS = 1
private const val MAX_REPS = 999
private const val MIN_HOLD_SEC = 1
private const val MAX_HOLD_SEC = 3600
private const val MIN_REST_SEC = 0
private const val MAX_REST_SEC = 3600
private const val MIN_LOAD_KG = 0.0
private const val MAX_LOAD_KG = 500.0

private enum class EditablePlannerField {
    SETS,
    REPS,
    HOLD_SEC,
    DROP_PER_SET,
    LOAD,
    REST,
}

private data class PlanPreset(
    val title: String,
    val subtitle: String,
    val mode: TrainingOrderMode,
    val tasks: List<PlanPresetTaskSpec>,
)

private data class PlanPresetTaskSpec(
    val templateName: String,
    val variantName: String? = null,
    val sets: Int,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val targetDropPerSet: Int = 0,
    val loadKg: Double = 0.0,
    val restSec: Int,
)

data class PlannerPresetTaskInput(
    val templateId: Long,
    val variantId: Long,
    val sets: Int,
    val targetReps: Int?,
    val targetHoldSec: Int?,
    val targetDropPerSet: Int,
    val loadKg: Double,
    val restSec: Int,
)

data class PlannerUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val weekDates: List<LocalDate> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val cycles: List<PlanCycleEntity> = emptyList(),
    val activeGoalId: Long? = null,
    val activeCycleId: Long? = null,
    val catalog: List<ExerciseCategoryWithTemplates> = emptyList(),
    val dayPlan: PlanDayWithTasks? = null,
    val completionStatus: String = CalendarCompletionStatus.EMPTY.name,
    val trainingOrderMode: TrainingOrderMode = TrainingOrderMode.CIRCUIT,
    val defaultRestSeconds: Int = 90,
    val recentLoadKg: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModel(
    private val repository: TrainingRepository,
    private val preferencesRepository: PreferencesRepository,
    initialDate: String,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.parse(initialDate))

    private val preferencesFlow = preferencesRepository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = com.codex.streetstrength.data.preferences.UserPreferences(),
    )

    private val goalsFlow = repository.observeGoals().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val activeGoalIdFlow = combine(goalsFlow, preferencesFlow) { goals, prefs ->
        prefs.activeGoalId ?: goals.firstOrNull()?.id
    }

    private val cyclesFlow = activeGoalIdFlow.flatMapLatest { goalId ->
        if (goalId == null) {
            flowOf(emptyList())
        } else {
            repository.observeCycles(goalId)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val plannerBaseFlow = combine(
        selectedDate,
        goalsFlow,
        cyclesFlow,
        preferencesFlow,
        repository.observeExerciseCatalog(),
    ) { date, goals, cycles, prefs, catalog ->
        PlannerUiState(
            selectedDate = date,
            weekDates = weekFor(date),
            goals = goals,
            cycles = cycles,
            activeGoalId = prefs.activeGoalId ?: goals.firstOrNull()?.id,
            activeCycleId = prefs.activeCycleId ?: cycles.firstOrNull()?.id,
            catalog = catalog,
            defaultRestSeconds = prefs.defaultRestSeconds,
            recentLoadKg = prefs.recentLoadKg,
        )
    }

    val uiState = combine(
        plannerBaseFlow,
        selectedDate.flatMapLatest { repository.observeDayPlan(it.toString()) },
        selectedDate.flatMapLatest { date -> repository.observeMonthSummary(date.toString(), date.toString()) },
    ) { base, dayPlan, summaries ->
        base.copy(
            dayPlan = dayPlan,
            trainingOrderMode = parseTrainingOrderModeNote(dayPlan?.day?.note),
            completionStatus = summaries.firstOrNull()?.completionStatus ?: CalendarCompletionStatus.EMPTY.name,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlannerUiState(),
    )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun addTask(
        templateId: Long,
        variantId: Long,
        sets: Int,
        targetReps: Int?,
        targetHoldSec: Int?,
        targetDropPerSet: Int,
        loadKg: Double,
        restSec: Int,
    ) {
        viewModelScope.launch {
            repository.createOrUpdateDayTask(
                DayTaskDraft(
                    date = selectedDate.value.toString(),
                    cycleId = uiState.value.activeCycleId,
                    templateId = templateId,
                    variantId = variantId,
                    sets = sets,
                    targetReps = targetReps,
                    targetHoldSec = targetHoldSec,
                    targetDropPerSet = targetDropPerSet,
                    plannedLoadKg = loadKg,
                    restSec = restSec,
                ),
            )
        }
    }

    fun applyPreset(
        mode: TrainingOrderMode,
        tasks: List<PlannerPresetTaskInput>,
    ) {
        viewModelScope.launch {
            repository.createDayTasks(
                inputs = tasks.map { task ->
                    DayTaskDraft(
                        date = selectedDate.value.toString(),
                        cycleId = uiState.value.activeCycleId,
                        templateId = task.templateId,
                        variantId = task.variantId,
                        sets = task.sets,
                        targetReps = task.targetReps,
                        targetHoldSec = task.targetHoldSec,
                        targetDropPerSet = task.targetDropPerSet,
                        plannedLoadKg = task.loadKg,
                        restSec = task.restSec,
                    )
                },
                mode = mode,
            )
        }
    }

    fun updateTask(
        taskId: Long,
        templateId: Long,
        variantId: Long,
        sets: Int,
        targetReps: Int?,
        targetHoldSec: Int?,
        targetDropPerSet: Int,
        loadKg: Double,
        restSec: Int,
    ) {
        viewModelScope.launch {
            repository.updateDayTask(
                DayTaskEditDraft(
                    taskId = taskId,
                    templateId = templateId,
                    variantId = variantId,
                    sets = sets,
                    targetReps = targetReps,
                    targetHoldSec = targetHoldSec,
                    targetDropPerSet = targetDropPerSet,
                    plannedLoadKg = loadKg,
                    restSec = restSec,
                ),
            )
        }
    }

    fun duplicateTask(taskId: Long) {
        viewModelScope.launch {
            repository.duplicateDayTask(taskId)
        }
    }

    fun moveTask(taskId: Long, direction: Int) {
        viewModelScope.launch {
            repository.moveDayTask(taskId, direction)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteDayTask(taskId)
        }
    }

    fun duplicateToTomorrow() {
        viewModelScope.launch {
            repository.duplicatePlanDay(
                sourceDate = selectedDate.value.toString(),
                targetDate = selectedDate.value.plusDays(1).toString(),
            )
        }
    }

    fun duplicatePreviousWeekToCurrent() {
        viewModelScope.launch {
            repository.duplicatePreviousWeekToCurrent(selectedDate.value.toString())
        }
    }

    fun createGoal(title: String, targetDate: String?) {
        viewModelScope.launch {
            repository.createGoal(title = title, targetDate = targetDate)
        }
    }

    fun createCycle(name: String, startDate: String, endDate: String?) {
        viewModelScope.launch {
            val goalId = uiState.value.activeGoalId ?: return@launch
            repository.createCycle(goalId = goalId, name = name, startDate = startDate, endDate = endDate)
        }
    }

    fun setActiveGoal(goalId: Long) {
        viewModelScope.launch {
            repository.setActiveGoal(goalId)
        }
    }

    fun setActiveCycle(cycleId: Long) {
        viewModelScope.launch {
            repository.setActiveCycle(cycleId)
        }
    }

    fun setTrainingOrderMode(mode: TrainingOrderMode) {
        viewModelScope.launch {
            repository.setTrainingOrderMode(
                date = selectedDate.value.toString(),
                cycleId = uiState.value.activeCycleId,
                mode = mode,
            )
        }
    }
}

@Composable
fun PlannerScreenRoute(
    app: StreetStrengthApp,
    initialDate: String,
    onOpenDate: (String) -> Unit,
    onStartTraining: (String) -> Unit,
) {
    val viewModel = rememberAppViewModel(key = "planner:$initialDate") {
        PlannerViewModel(
            repository = app.trainingRepository,
            preferencesRepository = app.preferencesRepository,
            initialDate = initialDate,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlannerScreen(
        uiState = uiState,
        onSelectDate = { date ->
            viewModel.selectDate(date)
            onOpenDate(date.toString())
        },
        onAddTask = viewModel::addTask,
        onApplyPreset = viewModel::applyPreset,
        onUpdateTask = viewModel::updateTask,
        onDuplicateTask = viewModel::duplicateTask,
        onMoveTask = viewModel::moveTask,
        onDeleteTask = viewModel::deleteTask,
        onDuplicateToTomorrow = viewModel::duplicateToTomorrow,
        onDuplicatePreviousWeek = viewModel::duplicatePreviousWeekToCurrent,
        onCreateGoal = viewModel::createGoal,
        onCreateCycle = viewModel::createCycle,
        onSelectGoal = viewModel::setActiveGoal,
        onSelectCycle = viewModel::setActiveCycle,
        onSetTrainingOrderMode = viewModel::setTrainingOrderMode,
        onStartTraining = onStartTraining,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    uiState: PlannerUiState,
    onSelectDate: (LocalDate) -> Unit,
    onAddTask: (Long, Long, Int, Int?, Int?, Int, Double, Int) -> Unit,
    onApplyPreset: (TrainingOrderMode, List<PlannerPresetTaskInput>) -> Unit,
    onUpdateTask: (Long, Long, Long, Int, Int?, Int?, Int, Double, Int) -> Unit,
    onDuplicateTask: (Long) -> Unit,
    onMoveTask: (Long, Int) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onDuplicateToTomorrow: () -> Unit,
    onDuplicatePreviousWeek: () -> Unit,
    onCreateGoal: (String, String?) -> Unit,
    onCreateCycle: (String, String, String?) -> Unit,
    onSelectGoal: (Long) -> Unit,
    onSelectCycle: (Long) -> Unit,
    onSetTrainingOrderMode: (TrainingOrderMode) -> Unit,
    onStartTraining: (String) -> Unit,
) {
    var showTaskSheet by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showCycleDialog by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<Long?>(null) }
    val tasks = uiState.dayPlan?.tasks?.sortedBy { it.task.orderInDay }.orEmpty()
    val editingTask = tasks.firstOrNull { it.task.id == editingTaskId }
    val actionPolicy = resolvePlanDayActionPolicy(
        selectedDate = uiState.selectedDate,
        hasTasks = tasks.isNotEmpty(),
        completionStatus = uiState.completionStatus,
        allowCompletedTodayTesting = PlanTestingSwitch.enabled,
    )
    val today = LocalDate.now()
    val canCopyToTomorrow = tasks.isNotEmpty() && !uiState.selectedDate.plusDays(1).isBefore(today)
    val canCopyPreviousWeek = !uiState.selectedDate
        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        .isBefore(today)

    LaunchedEffect(uiState.selectedDate) {
        showTaskSheet = false
        editingTaskId = null
    }

    if (showTaskSheet) {
        AddTaskBottomSheet(
            catalog = uiState.catalog,
            defaultRestSeconds = uiState.defaultRestSeconds,
            recentLoadKg = uiState.recentLoadKg,
            initialTask = null,
            title = "\u6dfb\u52a0\u8bad\u7ec3\u9879\u76ee",
            saveText = "\u4fdd\u5b58\u5230\u672c\u65e5\u8ba1\u5212",
            onDismiss = { showTaskSheet = false },
            onSave = { templateId, variantId, sets, reps, holdSec, targetDrop, loadKg, restSec ->
                onAddTask(templateId, variantId, sets, reps, holdSec, targetDrop, loadKg, restSec)
                showTaskSheet = false
            },
        )
    }

    if (editingTask != null) {
        AddTaskBottomSheet(
            catalog = uiState.catalog,
            defaultRestSeconds = uiState.defaultRestSeconds,
            recentLoadKg = uiState.recentLoadKg,
            initialTask = editingTask,
            title = "\u7f16\u8f91\u8bad\u7ec3\u9879\u76ee",
            saveText = "\u4fdd\u5b58\u4fee\u6539",
            onDismiss = { editingTaskId = null },
            onSave = { templateId, variantId, sets, reps, holdSec, targetDrop, loadKg, restSec ->
                onUpdateTask(editingTask.task.id, templateId, variantId, sets, reps, holdSec, targetDrop, loadKg, restSec)
                editingTaskId = null
            },
        )
    }

    if (showGoalDialog) {
        GoalDialog(
            onDismiss = { showGoalDialog = false },
            onConfirm = { title, targetDate ->
                onCreateGoal(title, targetDate)
                showGoalDialog = false
            },
        )
    }

    if (showCycleDialog) {
        CycleDialog(
            initialStartDate = uiState.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
            onDismiss = { showCycleDialog = false },
            onConfirm = { name, startDate, endDate ->
                onCreateCycle(name, startDate, endDate)
                showCycleDialog = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "本周计划",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${formatShortDate(uiState.selectedDate)} ${formatWeekdayCn(uiState.selectedDate)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = plannerDateStateText(actionPolicy),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(uiState.weekDates) { date ->
                            FilterChip(
                                selected = date == uiState.selectedDate,
                                onClick = { onSelectDate(date) },
                                label = { Text("${date.dayOfMonth} ${formatWeekdayCn(date)}") },
                            )
                        }
                    }
                }
            }
        }

        item {
            PlannerGoalCard(
                uiState = uiState,
                onCreateGoal = { showGoalDialog = true },
                onCreateCycle = { showCycleDialog = true },
                onSelectGoal = onSelectGoal,
                onSelectCycle = onSelectCycle,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showTaskSheet = true },
                    enabled = actionPolicy.canEditPlan,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加训练项目")
                }
                OutlinedButton(
                    onClick = onDuplicateToTomorrow,
                    enabled = canCopyToTomorrow,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("复制到次日")
                }
            }
        }

        item {
            PresetPlanCard(
                catalog = uiState.catalog,
                recentLoadKg = uiState.recentLoadKg,
                enabled = actionPolicy.canEditPlan,
                onApplyPreset = onApplyPreset,
            )
        }

        item {
            OutlinedButton(
                onClick = onDuplicatePreviousWeek,
                enabled = canCopyPreviousWeek,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("复制上周到本周")
            }
        }

        item {
            TrainingOrderModeCard(
                selectedMode = uiState.trainingOrderMode,
                enabled = actionPolicy.canEditPlan,
                onSelectMode = onSetTrainingOrderMode,
            )
        }

        if (tasks.isEmpty()) {
            item {
                EmptyPlannerState(
                    enabled = actionPolicy.canEditPlan,
                    onOpenTaskSheet = { showTaskSheet = true },
                )
            }
        } else {
            itemsIndexed(tasks, key = { _, task -> task.task.id }) { index, task ->
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "\u7b2c${index + 1}\u9879",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = task.template.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = task.variant.name,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = { editingTaskId = task.task.id },
                                    enabled = actionPolicy.canEditPlan,
                                ) {
                                    Text("\u7f16\u8f91")
                                }
                                TextButton(
                                    onClick = { onDuplicateTask(task.task.id) },
                                    enabled = actionPolicy.canEditPlan,
                                ) {
                                    Text("\u590d\u5236")
                                }
                                IconButton(
                                    onClick = { onDeleteTask(task.task.id) },
                                    enabled = actionPolicy.canEditPlan,
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除")
                                }
                            }
                        }
                        Text(
                            text = "${task.setPlans.size}组 · ${formatMetric(task.setPlans.firstOrNull()?.targetReps, task.setPlans.firstOrNull()?.targetHoldSec)} · ${task.task.restSec}s",
                        )
                        if (task.task.plannedLoadKg > 0.0) {
                            Text(text = "负重 ${formatLoadKg(task.task.plannedLoadKg)}")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { onMoveTask(task.task.id, -1) },
                                enabled = actionPolicy.canEditPlan && index > 0,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("\u4e0a\u79fb")
                            }
                            OutlinedButton(
                                onClick = { onMoveTask(task.task.id, 1) },
                                enabled = actionPolicy.canEditPlan && index < tasks.lastIndex,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("\u4e0b\u79fb")
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onStartTraining(uiState.selectedDate.toString()) },
                enabled = actionPolicy.canStartTraining,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(startTrainingButtonText(actionPolicy))
            }
        }
    }
}

@Composable
private fun PlannerGoalCard(
    uiState: PlannerUiState,
    onCreateGoal: () -> Unit,
    onCreateCycle: () -> Unit,
    onSelectGoal: (Long) -> Unit,
    onSelectCycle: (Long) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("目标与周期", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCreateGoal) { Text("新建目标") }
                    TextButton(onClick = onCreateCycle) { Text("新建周期") }
                }
            }

            if (uiState.goals.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.goals, key = { it.id }) { goal ->
                        FilterChip(
                            selected = goal.id == uiState.activeGoalId,
                            onClick = { onSelectGoal(goal.id) },
                            label = { Text(goal.title) },
                        )
                    }
                }
            }

            if (uiState.cycles.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.cycles, key = { it.id }) { cycle ->
                        FilterChip(
                            selected = cycle.id == uiState.activeCycleId,
                            onClick = { onSelectCycle(cycle.id) },
                            label = { Text(cycle.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingOrderModeCard(
    selectedMode: TrainingOrderMode,
    enabled: Boolean,
    onSelectMode: (TrainingOrderMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "\u8bad\u7ec3\u6a21\u5f0f",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedMode == TrainingOrderMode.CIRCUIT,
                    onClick = { onSelectMode(TrainingOrderMode.CIRCUIT) },
                    enabled = enabled,
                    label = { Text("\u5faa\u73af\u7ec4") },
                )
                FilterChip(
                    selected = selectedMode == TrainingOrderMode.SEQUENTIAL,
                    onClick = { onSelectMode(TrainingOrderMode.SEQUENTIAL) },
                    enabled = enabled,
                    label = { Text("\u6309\u52a8\u4f5c\u5b8c\u6210") },
                )
            }
            Text(
                text = when (selectedMode) {
                    TrainingOrderMode.CIRCUIT -> "\u6309\u7b2c1\u8f6e\uff1a\u52a8\u4f5cA + \u52a8\u4f5cB + \u52a8\u4f5cC\uff0c\u518d\u8fdb\u5165\u7b2c2\u8f6e\u3002"
                    TrainingOrderMode.SEQUENTIAL -> "\u5148\u5b8c\u6210\u52a8\u4f5cA\u7684\u5168\u90e8\u7ec4\u6570\uff0c\u518d\u8fdb\u5165\u52a8\u4f5cB\u3002"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PresetPlanCard(
    catalog: List<ExerciseCategoryWithTemplates>,
    recentLoadKg: Double,
    enabled: Boolean,
    onApplyPreset: (TrainingOrderMode, List<PlannerPresetTaskInput>) -> Unit,
) {
    val presets = remember(recentLoadKg) { marketInspiredPresets(recentLoadKg) }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "\u5feb\u6377\u8bad\u7ec3\u6a21\u677f",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "\u53c2\u8003\u6210\u719f App \u7684\u65e5\u63a8\u8350\u548c\u9884\u8bbe\u8ba1\u5212\u5165\u53e3\uff0c\u4e00\u952e\u8ffd\u52a0\u5230\u672c\u65e5\u8ba1\u5212\u3002",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(presets) { preset ->
                    val resolvedTasks = resolvePresetTasks(preset, catalog)
                    PresetPlanItem(
                        preset = preset,
                        resolvedCount = resolvedTasks.size,
                        enabled = enabled && resolvedTasks.isNotEmpty(),
                        onApply = { onApplyPreset(preset.mode, resolvedTasks) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetPlanItem(
    preset: PlanPreset,
    resolvedCount: Int,
    enabled: Boolean,
    onApply: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = preset.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = preset.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(
                onClick = { },
                enabled = false,
                label = {
                    Text(
                        when (preset.mode) {
                            TrainingOrderMode.CIRCUIT -> "\u5faa\u73af\u7ec4"
                            TrainingOrderMode.SEQUENTIAL -> "\u6309\u52a8\u4f5c\u5b8c\u6210"
                        },
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
            Text(
                text = if (enabled) {
                    "\u5c06\u8ffd\u52a0 $resolvedCount \u4e2a\u52a8\u4f5c"
                } else {
                    "\u52a8\u4f5c\u5e93\u6682\u7f3a\u5339\u914d\u52a8\u4f5c"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onApply,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\u5957\u7528\u5230\u672c\u65e5")
            }
        }
    }
}

@Composable
private fun EmptyPlannerState(
    enabled: Boolean,
    onOpenTaskSheet: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "这一天还没有训练项目",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "从训练库挑动作、变式、组数和间歇后保存到今天。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenTaskSheet, enabled = enabled) {
                Text("添加第一项")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskBottomSheet(
    catalog: List<ExerciseCategoryWithTemplates>,
    defaultRestSeconds: Int,
    recentLoadKg: Double,
    initialTask: DayTaskWithDetails?,
    title: String,
    saveText: String,
    onDismiss: () -> Unit,
    onSave: (Long, Long, Int, Int?, Int?, Int, Double, Int) -> Unit,
) {
    val initialSetPlans = initialTask?.setPlans?.sortedBy { it.setIndex }.orEmpty()
    val initialFirstPlan = initialSetPlans.firstOrNull()
    var selectedCategoryId by remember(catalog, initialTask?.task?.id) {
        mutableStateOf(initialTask?.template?.categoryId ?: catalog.firstOrNull()?.category?.id)
    }
    val selectedCategory = catalog.firstOrNull { it.category.id == selectedCategoryId } ?: catalog.firstOrNull()
    var selectedTemplateId by remember(selectedCategory, initialTask?.task?.id) {
        mutableStateOf(initialTask?.template?.id ?: selectedCategory?.templates?.firstOrNull()?.template?.id)
    }
    val selectedTemplate = selectedCategory?.templates?.firstOrNull { it.template.id == selectedTemplateId }
        ?: selectedCategory?.templates?.firstOrNull()
    var selectedVariantId by remember(selectedTemplate, initialTask?.task?.id) {
        mutableStateOf(initialTask?.variant?.id ?: selectedTemplate?.variants?.firstOrNull()?.id)
    }
    val selectedVariant = selectedTemplate?.variants?.firstOrNull { it.id == selectedVariantId } ?: selectedTemplate?.variants?.firstOrNull()

    var sets by remember(initialTask?.task?.id) { mutableIntStateOf(initialSetPlans.size.takeIf { it > 0 } ?: 5) }
    var reps by remember(initialTask?.task?.id) { mutableIntStateOf(initialFirstPlan?.targetReps ?: 5) }
    var holdSec by remember(initialTask?.task?.id) { mutableIntStateOf(initialFirstPlan?.targetHoldSec ?: 10) }
    var targetDropPerSet by remember(initialTask?.task?.id) { mutableIntStateOf(inferTargetDropPerSet(initialTask)) }
    var restSec by remember(initialTask?.task?.id) { mutableIntStateOf(initialTask?.task?.restSec ?: defaultRestSeconds) }
    var loadKg by remember(initialTask?.task?.id) {
        mutableStateOf((initialTask?.task?.plannedLoadKg ?: recentLoadKg).coerceAtLeast(0.0))
    }
    var editingField by remember { mutableStateOf<EditablePlannerField?>(null) }
    val selectedMetric = selectedVariant?.metricType ?: selectedTemplate?.template?.defaultMetricType

    LaunchedEffect(selectedCategory?.category?.id) {
        if (selectedCategory?.templates?.none { it.template.id == selectedTemplateId } == true) {
            val firstTemplate = selectedCategory.templates.firstOrNull()
            selectedTemplateId = firstTemplate?.template?.id
            selectedVariantId = firstTemplate?.variants?.firstOrNull()?.id
        }
    }
    LaunchedEffect(selectedTemplate?.template?.id) {
        if (selectedTemplate?.variants?.none { it.id == selectedVariantId } == true) {
            selectedVariantId = selectedTemplate.variants.firstOrNull()?.id
        }
    }
    LaunchedEffect(selectedMetric, reps, holdSec) {
        targetDropPerSet = targetDropPerSet.coerceAtMost(maxTargetDrop(selectedMetric, reps, holdSec))
    }

    editingField?.let { field ->
        val maxDrop = maxTargetDrop(selectedMetric, reps, holdSec)
        NumberInputDialog(
            title = when (field) {
                EditablePlannerField.SETS -> "\u8f93\u5165\u7ec4\u6570"
                EditablePlannerField.REPS -> "\u8f93\u5165\u6bcf\u7ec4\u76ee\u6807"
                EditablePlannerField.HOLD_SEC -> "\u8f93\u5165\u6bcf\u7ec4\u65f6\u957f"
                EditablePlannerField.DROP_PER_SET -> "\u8f93\u5165\u6bcf\u7ec4\u9012\u51cf"
                EditablePlannerField.LOAD -> "\u8f93\u5165\u8d1f\u91cd"
                EditablePlannerField.REST -> "\u8f93\u5165\u95f4\u6b47"
            },
            initialValue = when (field) {
                EditablePlannerField.SETS -> sets.toString()
                EditablePlannerField.REPS -> reps.toString()
                EditablePlannerField.HOLD_SEC -> holdSec.toString()
                EditablePlannerField.DROP_PER_SET -> targetDropPerSet.toString()
                EditablePlannerField.LOAD -> formatManualLoadKg(loadKg)
                EditablePlannerField.REST -> restSec.toString()
            },
            suffix = when (field) {
                EditablePlannerField.SETS -> "\u7ec4"
                EditablePlannerField.REPS -> "\u6b21"
                EditablePlannerField.HOLD_SEC -> "\u79d2"
                EditablePlannerField.DROP_PER_SET -> if (selectedMetric == MetricType.REPS) "\u6b21/\u7ec4" else "\u79d2/\u7ec4"
                EditablePlannerField.LOAD -> "kg"
                EditablePlannerField.REST -> "\u79d2"
            },
            allowDecimal = field == EditablePlannerField.LOAD,
            minValue = when (field) {
                EditablePlannerField.SETS -> MIN_SETS.toDouble()
                EditablePlannerField.REPS -> MIN_REPS.toDouble()
                EditablePlannerField.HOLD_SEC -> MIN_HOLD_SEC.toDouble()
                EditablePlannerField.DROP_PER_SET -> 0.0
                EditablePlannerField.LOAD -> MIN_LOAD_KG
                EditablePlannerField.REST -> MIN_REST_SEC.toDouble()
            },
            maxValue = when (field) {
                EditablePlannerField.SETS -> MAX_SETS.toDouble()
                EditablePlannerField.REPS -> MAX_REPS.toDouble()
                EditablePlannerField.HOLD_SEC -> MAX_HOLD_SEC.toDouble()
                EditablePlannerField.DROP_PER_SET -> maxDrop.toDouble()
                EditablePlannerField.LOAD -> MAX_LOAD_KG
                EditablePlannerField.REST -> MAX_REST_SEC.toDouble()
            },
            onDismiss = { editingField = null },
            onConfirm = { value ->
                when (field) {
                    EditablePlannerField.SETS -> sets = value.toInt().coerceIn(MIN_SETS, MAX_SETS)
                    EditablePlannerField.REPS -> reps = value.toInt().coerceIn(MIN_REPS, MAX_REPS)
                    EditablePlannerField.HOLD_SEC -> holdSec = value.toInt().coerceIn(MIN_HOLD_SEC, MAX_HOLD_SEC)
                    EditablePlannerField.DROP_PER_SET -> targetDropPerSet = value.toInt().coerceIn(0, maxDrop)
                    EditablePlannerField.LOAD -> loadKg = value.coerceIn(MIN_LOAD_KG, MAX_LOAD_KG)
                    EditablePlannerField.REST -> restSec = value.toInt().coerceIn(MIN_REST_SEC, MAX_REST_SEC)
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\u70b9\u51fb\u4e2d\u95f4\u6570\u503c\u53ef\u624b\u52a8\u8f93\u5165\uff0c+/- \u7528\u4e8e\u5c0f\u6b65\u5fae\u8c03\u3002",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Text("分类", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catalog, key = { it.category.id }) { category ->
                        FilterChip(
                            selected = category.category.id == selectedCategory?.category?.id,
                            onClick = {
                                selectedCategoryId = category.category.id
                                val firstTemplate = category.templates.firstOrNull()
                                selectedTemplateId = firstTemplate?.template?.id
                                selectedVariantId = firstTemplate?.variants?.firstOrNull()?.id
                            },
                            label = { Text(category.category.name) },
                        )
                    }
                }
            }

            item {
                Text("项目", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedCategory?.templates.orEmpty(), key = { it.template.id }) { template ->
                        FilterChip(
                            selected = template.template.id == selectedTemplate?.template?.id,
                            onClick = {
                                selectedTemplateId = template.template.id
                                selectedVariantId = template.variants.firstOrNull()?.id
                            },
                            label = { Text(template.template.name) },
                        )
                    }
                }
            }

            item {
                Text("变式", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedTemplate?.variants.orEmpty(), key = { it.id }) { variant ->
                        FilterChip(
                            selected = variant.id == selectedVariant?.id,
                            onClick = { selectedVariantId = variant.id },
                            label = { Text(variant.name) },
                        )
                    }
                }
            }

            item {
                ValueAdjuster(
                    label = "组数",
                    valueText = "${sets}组",
                    onDecrease = { sets = (sets - 1).coerceAtLeast(MIN_SETS) },
                    onIncrease = { sets = (sets + 1).coerceAtMost(MAX_SETS) },
                    onValueClick = { editingField = EditablePlannerField.SETS },
                )
            }

            when (selectedMetric) {
                MetricType.REPS -> item {
                    ValueAdjuster(
                        label = "每组目标",
                        valueText = "${reps}次",
                        onDecrease = { reps = (reps - 1).coerceAtLeast(MIN_REPS) },
                        onIncrease = { reps = (reps + 1).coerceAtMost(MAX_REPS) },
                        onValueClick = { editingField = EditablePlannerField.REPS },
                    )
                }

                MetricType.HOLD_SECONDS, MetricType.HOLD_SECONDS_PLUS_ECCENTRIC -> item {
                    ValueAdjuster(
                        label = "每组时长",
                        valueText = "${holdSec}秒",
                        onDecrease = { holdSec = (holdSec - 1).coerceAtLeast(MIN_HOLD_SEC) },
                        onIncrease = { holdSec = (holdSec + 1).coerceAtMost(MAX_HOLD_SEC) },
                        onValueClick = { editingField = EditablePlannerField.HOLD_SEC },
                    )
                }

                MetricType.HOLD_TO_FAILURE, null -> item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("目标方式", style = MaterialTheme.typography.titleMedium)
                            Text("记录为力竭，不预设固定秒数。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                val maxDrop = maxTargetDrop(selectedMetric, reps, holdSec)
                ValueAdjuster(
                    label = "\u6bcf\u7ec4\u9012\u51cf",
                    valueText = when {
                        targetDropPerSet == 0 -> "\u4e0d\u9012\u51cf"
                        selectedMetric == MetricType.REPS -> "${targetDropPerSet}\u6b21/\u7ec4"
                        else -> "${targetDropPerSet}\u79d2/\u7ec4"
                    },
                    onDecrease = { targetDropPerSet = (targetDropPerSet - 1).coerceAtLeast(0) },
                    onIncrease = { targetDropPerSet = (targetDropPerSet + 1).coerceAtMost(maxDrop) },
                    onValueClick = { editingField = EditablePlannerField.DROP_PER_SET },
                )
            }

            if (selectedTemplate?.template?.supportsExternalLoad == true) {
                item {
                    ValueAdjuster(
                        label = "负重",
                        valueText = formatLoadKg(loadKg),
                        onDecrease = { loadKg = (loadKg - 2.5).coerceAtLeast(0.0) },
                        onIncrease = { loadKg = (loadKg + 2.5).coerceAtMost(MAX_LOAD_KG) },
                        onValueClick = { editingField = EditablePlannerField.LOAD },
                    )
                }
            }

            item {
                ValueAdjuster(
                    label = "间歇",
                    valueText = "${restSec}s",
                    onDecrease = { restSec = (restSec - 15).coerceAtLeast(MIN_REST_SEC) },
                    onIncrease = { restSec = (restSec + 15).coerceAtMost(MAX_REST_SEC) },
                    onValueClick = { editingField = EditablePlannerField.REST },
                )
            }

            item {
                Button(
                    onClick = {
                        val template = selectedTemplate ?: return@Button
                        val variant = selectedVariant ?: return@Button
                        val metric = variant.metricType
                        onSave(
                            template.template.id,
                            variant.id,
                            sets,
                            if (metric == MetricType.REPS) reps else null,
                            when (metric) {
                                MetricType.HOLD_SECONDS, MetricType.HOLD_SECONDS_PLUS_ECCENTRIC -> holdSec

                                else -> null
                            },
                            targetDropPerSet,
                            loadKg,
                            restSec,
                        )
                    },
                    enabled = selectedTemplate != null && selectedVariant != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(saveText)
                }
            }
        }
    }
}

@Composable
private fun NumberInputDialog(
    title: String,
    initialValue: String,
    suffix: String,
    allowDecimal: Boolean,
    minValue: Double,
    maxValue: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var rawValue by remember(title, initialValue) { mutableStateOf(initialValue) }
    var hasError by remember(title, initialValue) { mutableStateOf(false) }
    val rangeText = "\u8303\u56f4\uff1a${formatNumberBound(minValue, allowDecimal)}-${formatNumberBound(maxValue, allowDecimal)} $suffix"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawValue,
                    onValueChange = {
                        rawValue = it
                        hasError = false
                    },
                    label = { Text("\u6570\u503c") },
                    singleLine = true,
                    isError = hasError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    ),
                )
                Text(
                    text = if (hasError) {
                        "\u8bf7\u8f93\u5165\u6709\u6548\u6570\u5b57\uff0c$rangeText"
                    } else {
                        rangeText
                    },
                    color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleaned = rawValue.trim().replace(",", ".")
                    val parsed = if (allowDecimal) cleaned.toDoubleOrNull() else cleaned.toIntOrNull()?.toDouble()
                    if (parsed == null || parsed < minValue || parsed > maxValue) {
                        hasError = true
                    } else {
                        onConfirm(parsed)
                        onDismiss()
                    }
                },
            ) {
                Text("\u786e\u5b9a")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u53d6\u6d88")
            }
        },
    )
}

private fun maxTargetDrop(
    metric: MetricType?,
    reps: Int,
    holdSec: Int,
): Int = when (metric) {
    MetricType.REPS -> (reps - MIN_REPS).coerceAtLeast(0)
    MetricType.HOLD_SECONDS, MetricType.HOLD_SECONDS_PLUS_ECCENTRIC, MetricType.HOLD_TO_FAILURE -> {
        (holdSec - MIN_HOLD_SEC).coerceAtLeast(0)
    }

    null -> 0
}

private fun inferTargetDropPerSet(task: DayTaskWithDetails?): Int {
    val plans = task?.setPlans?.sortedBy { it.setIndex }.orEmpty()
    if (plans.size < 2) return 0
    val first = plans[0]
    val second = plans[1]
    return when (task?.variant?.metricType) {
        MetricType.REPS -> ((first.targetReps ?: return 0) - (second.targetReps ?: return 0)).coerceAtLeast(0)
        MetricType.HOLD_SECONDS, MetricType.HOLD_SECONDS_PLUS_ECCENTRIC, MetricType.HOLD_TO_FAILURE -> {
            ((first.targetHoldSec ?: return 0) - (second.targetHoldSec ?: return 0)).coerceAtLeast(0)
        }

        null -> 0
    }
}

private fun formatManualLoadKg(value: Double): String =
    if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

private fun formatNumberBound(value: Double, allowDecimal: Boolean): String =
    if (!allowDecimal || value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

@Composable
private fun GoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var title by remember { mutableStateOf("基础力量提升") }
    var targetDate by remember { mutableStateOf(LocalDate.now().plusWeeks(12).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("目标标题") })
                OutlinedTextField(value = targetDate, onValueChange = { targetDate = it }, label = { Text("目标日期 yyyy-MM-dd") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, targetDate.ifBlank { null }) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun CycleDialog(
    initialStartDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("当前周期") }
    var startDate by remember { mutableStateOf(initialStartDate) }
    var endDate by remember { mutableStateOf(LocalDate.parse(initialStartDate).plusWeeks(8).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建周期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("周期名称") })
                OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("开始日期") })
                OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("结束日期") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, startDate, endDate.ifBlank { null }) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private fun weekFor(date: LocalDate): List<LocalDate> {
    val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return List(7) { start.plusDays(it.toLong()) }
}

private fun plannerDateStateText(policy: PlanDayActionPolicy): String = when {
    policy.isCompletedTodayTestingAllowed -> "Debug 测试：今日已完成仍可继续新增和开始"
    policy.hasRecordedTraining -> "已有训练记录，计划已锁定"
    policy.timing == PlanDateTiming.PAST -> "已过期，仅保留历史计划"
    policy.timing == PlanDateTiming.FUTURE -> "未来计划，可提前编辑，当日才能开始"
    policy.hasTasks -> "今日计划可开始"
    else -> "今日还没有安排训练"
}

private fun startTrainingButtonText(policy: PlanDayActionPolicy): String = when {
    policy.isCompletedTodayTestingAllowed -> "Debug 再次开始训练"
    policy.completionStatus == CalendarCompletionStatus.DONE.name -> "\u8bad\u7ec3\u5df2\u5b8c\u6210"
    policy.completionStatus == CalendarCompletionStatus.PARTIAL.name -> "继续当天训练"
    !policy.hasTasks -> "添加计划后可开始"
    policy.timing == PlanDateTiming.PAST -> "计划已过期"
    policy.timing == PlanDateTiming.FUTURE -> "未到开始日期"
    else -> "\u5f00\u59cb\u5f53\u5929\u8bad\u7ec3"
}

private fun marketInspiredPresets(recentLoadKg: Double): List<PlanPreset> {
    val weightedLoad = recentLoadKg.takeIf { it > 0.0 } ?: 10.0
    return listOf(
        PlanPreset(
            title = "\u57fa\u7840\u8857\u5065\u5168\u8eab",
            subtitle = "\u62c9\u3001\u63a8\u3001\u6838\u5fc3\u4ea4\u66ff\uff0c\u9002\u5408\u60f3\u76f4\u63a5\u5f00\u7ec3\u7684\u65e5\u5e38\u8bfe\u3002",
            mode = TrainingOrderMode.CIRCUIT,
            tasks = listOf(
                PlanPresetTaskSpec("引体向上", "标准正握", sets = 4, targetReps = 5, restSec = 90),
                PlanPresetTaskSpec("腰间俯卧撑", "标准", sets = 4, targetReps = 8, restSec = 90),
                PlanPresetTaskSpec("Hollow Body Hold", "标准", sets = 4, targetHoldSec = 20, restSec = 90),
            ),
        ),
        PlanPreset(
            title = "\u62c9\u529b\u5bb9\u91cf\u65e5",
            subtitle = "\u5148\u5b8c\u6210\u4e3b\u9879\uff0c\u518d\u8865\u5212\u8239\u548c\u80a9\u80db\u63a7\u5236\u3002",
            mode = TrainingOrderMode.SEQUENTIAL,
            tasks = listOf(
                PlanPresetTaskSpec("引体向上", "标准正握", sets = 5, targetReps = 5, restSec = 120),
                PlanPresetTaskSpec("水平划船", "标准正握", sets = 4, targetReps = 10, restSec = 90),
                PlanPresetTaskSpec("肩胛引体", "标准", sets = 3, targetReps = 10, restSec = 60),
            ),
        ),
        PlanPreset(
            title = "\u63a8\u529b\u6280\u5de7\u65e5",
            subtitle = "\u5012\u7acb\u6491\u3001\u8170\u95f4\u4fef\u5367\u6491\u3001\u51b2\u80a9\uff0c\u9762\u5411\u80a9\u90e8\u548c\u652f\u6491\u80fd\u529b\u3002",
            mode = TrainingOrderMode.CIRCUIT,
            tasks = listOf(
                PlanPresetTaskSpec("倒立撑", "靠墙", sets = 5, targetReps = 4, restSec = 120),
                PlanPresetTaskSpec("腰间俯卧撑", "标准", sets = 5, targetReps = 8, restSec = 90),
                PlanPresetTaskSpec("冲肩", "标准", sets = 5, targetHoldSec = 12, restSec = 90),
            ),
        ),
        PlanPreset(
            title = "\u6838\u5fc3\u652f\u6491\u65e5",
            subtitle = "\u60ac\u5782\u3001L-Sit \u548c\u5e73\u677f\u652f\u6491\uff0c\u8865\u9f50\u8857\u5065\u57fa\u7840\u7a33\u5b9a\u3002",
            mode = TrainingOrderMode.SEQUENTIAL,
            tasks = listOf(
                PlanPresetTaskSpec("悬垂举腿", "屈膝", sets = 4, targetReps = 8, restSec = 90),
                PlanPresetTaskSpec("L-Sit支撑", "屈膝", sets = 4, targetHoldSec = 12, restSec = 90),
                PlanPresetTaskSpec("平板支撑", "前平板", sets = 3, targetHoldSec = 45, restSec = 60),
            ),
        ),
        PlanPreset(
            title = "\u8d1f\u91cd\u5f3a\u5ea6\u65e5",
            subtitle = "\u504f\u5411\u5f3a\u5ea6\u8fdb\u9636\uff0c\u4f7f\u7528\u4f60\u6700\u8fd1\u7684\u8d1f\u91cd\u4f5c\u4e3a\u8d77\u70b9\u3002",
            mode = TrainingOrderMode.SEQUENTIAL,
            tasks = listOf(
                PlanPresetTaskSpec("引体向上", "标准正握", sets = 5, targetReps = 3, targetDropPerSet = 0, loadKg = weightedLoad, restSec = 150),
                PlanPresetTaskSpec("双杠臂屈伸", "标准", sets = 5, targetReps = 5, targetDropPerSet = 0, loadKg = weightedLoad, restSec = 150),
                PlanPresetTaskSpec("Hollow Body Hold", "标准", sets = 3, targetHoldSec = 25, restSec = 75),
            ),
        ),
    )
}

private fun resolvePresetTasks(
    preset: PlanPreset,
    catalog: List<ExerciseCategoryWithTemplates>,
): List<PlannerPresetTaskInput> {
    val templates = catalog.flatMap { it.templates }
    return preset.tasks.mapNotNull { spec ->
        val template = templates.firstOrNull { it.template.name == spec.templateName } ?: return@mapNotNull null
        val variant = spec.variantName?.let { variantName ->
            template.variants.firstOrNull { it.name == variantName }
        } ?: template.variants.firstOrNull()
        variant ?: return@mapNotNull null

        PlannerPresetTaskInput(
            templateId = template.template.id,
            variantId = variant.id,
            sets = spec.sets,
            targetReps = if (variant.metricType == MetricType.REPS) spec.targetReps else null,
            targetHoldSec = if (variant.metricType != MetricType.REPS) spec.targetHoldSec else null,
            targetDropPerSet = spec.targetDropPerSet,
            loadKg = if (template.template.supportsExternalLoad) spec.loadKg else 0.0,
            restSec = spec.restSec,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlannerPreview() {
    StreetStrengthTheme {
        PlannerScreen(
            uiState = PlannerUiState(
                selectedDate = LocalDate.now(),
                weekDates = weekFor(LocalDate.now()),
            ),
            onSelectDate = {},
            onAddTask = { _, _, _, _, _, _, _, _ -> },
            onApplyPreset = { _, _ -> },
            onUpdateTask = { _, _, _, _, _, _, _, _, _ -> },
            onDuplicateTask = {},
            onMoveTask = { _, _ -> },
            onDeleteTask = {},
            onDuplicateToTomorrow = {},
            onDuplicatePreviousWeek = {},
            onCreateGoal = { _, _ -> },
            onCreateCycle = { _, _, _ -> },
            onSelectGoal = {},
            onSelectCycle = {},
            onSetTrainingOrderMode = {},
            onStartTraining = {},
        )
    }
}
