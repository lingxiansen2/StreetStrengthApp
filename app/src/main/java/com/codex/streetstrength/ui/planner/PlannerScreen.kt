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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.local.ExerciseCategoryWithTemplates
import com.codex.streetstrength.data.local.GoalEntity
import com.codex.streetstrength.data.local.PlanCycleEntity
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.CustomExerciseDraft
import com.codex.streetstrength.data.repository.DayTaskDraft
import com.codex.streetstrength.data.repository.TrainingRepository
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

data class PlannerUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val weekDates: List<LocalDate> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val cycles: List<PlanCycleEntity> = emptyList(),
    val activeGoalId: Long? = null,
    val activeCycleId: Long? = null,
    val catalog: List<ExerciseCategoryWithTemplates> = emptyList(),
    val dayPlan: PlanDayWithTasks? = null,
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
    ) { base, dayPlan ->
        base.copy(
            dayPlan = dayPlan,
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
                    plannedLoadKg = loadKg,
                    restSec = restSec,
                ),
            )
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
        onDeleteTask = viewModel::deleteTask,
        onDuplicateToTomorrow = viewModel::duplicateToTomorrow,
        onDuplicatePreviousWeek = viewModel::duplicatePreviousWeekToCurrent,
        onCreateGoal = viewModel::createGoal,
        onCreateCycle = viewModel::createCycle,
        onSelectGoal = viewModel::setActiveGoal,
        onSelectCycle = viewModel::setActiveCycle,
        onStartTraining = onStartTraining,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    uiState: PlannerUiState,
    onSelectDate: (LocalDate) -> Unit,
    onAddTask: (Long, Long, Int, Int?, Int?, Double, Int) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onDuplicateToTomorrow: () -> Unit,
    onDuplicatePreviousWeek: () -> Unit,
    onCreateGoal: (String, String?) -> Unit,
    onCreateCycle: (String, String, String?) -> Unit,
    onSelectGoal: (Long) -> Unit,
    onSelectCycle: (Long) -> Unit,
    onStartTraining: (String) -> Unit,
) {
    var showTaskSheet by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showCycleDialog by remember { mutableStateOf(false) }

    if (showTaskSheet) {
        AddTaskBottomSheet(
            catalog = uiState.catalog,
            defaultRestSeconds = uiState.defaultRestSeconds,
            recentLoadKg = uiState.recentLoadKg,
            onDismiss = { showTaskSheet = false },
            onSave = { templateId, variantId, sets, reps, holdSec, loadKg, restSec ->
                onAddTask(templateId, variantId, sets, reps, holdSec, loadKg, restSec)
                showTaskSheet = false
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
                Button(onClick = { showTaskSheet = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加训练项目")
                }
                OutlinedButton(onClick = onDuplicateToTomorrow, modifier = Modifier.weight(1f)) {
                    Text("复制到次日")
                }
            }
        }

        item {
            OutlinedButton(onClick = onDuplicatePreviousWeek, modifier = Modifier.fillMaxWidth()) {
                Text("复制上周到本周")
            }
        }

        val tasks = uiState.dayPlan?.tasks?.sortedBy { it.task.orderInDay }.orEmpty()
        if (tasks.isEmpty()) {
            item {
                EmptyPlannerState(onOpenTaskSheet = { showTaskSheet = true })
            }
        } else {
            items(tasks, key = { it.task.id }) { task ->
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
                                    text = task.template.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = task.variant.name,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteTask(task.task.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除")
                            }
                        }
                        Text(
                            text = "${task.setPlans.size}组 · ${formatMetric(task.setPlans.firstOrNull()?.targetReps, task.setPlans.firstOrNull()?.targetHoldSec)} · ${task.task.restSec}s",
                        )
                        if (task.task.plannedLoadKg > 0.0) {
                            Text(text = "负重 ${formatLoadKg(task.task.plannedLoadKg)}")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onStartTraining(uiState.selectedDate.toString()) },
                enabled = tasks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("开始当天训练")
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
private fun EmptyPlannerState(
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
            Button(onClick = onOpenTaskSheet) {
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
    onDismiss: () -> Unit,
    onSave: (Long, Long, Int, Int?, Int?, Double, Int) -> Unit,
) {
    var selectedCategoryId by remember(catalog) { mutableStateOf(catalog.firstOrNull()?.category?.id) }
    val selectedCategory = catalog.firstOrNull { it.category.id == selectedCategoryId } ?: catalog.firstOrNull()
    var selectedTemplateId by remember(selectedCategory) { mutableStateOf(selectedCategory?.templates?.firstOrNull()?.template?.id) }
    val selectedTemplate = selectedCategory?.templates?.firstOrNull { it.template.id == selectedTemplateId }
        ?: selectedCategory?.templates?.firstOrNull()
    var selectedVariantId by remember(selectedTemplate) { mutableStateOf(selectedTemplate?.variants?.firstOrNull()?.id) }
    val selectedVariant = selectedTemplate?.variants?.firstOrNull { it.id == selectedVariantId } ?: selectedTemplate?.variants?.firstOrNull()

    var sets by remember { mutableIntStateOf(5) }
    var reps by remember { mutableIntStateOf(5) }
    var holdSec by remember { mutableIntStateOf(10) }
    var restSec by remember { mutableIntStateOf(defaultRestSeconds) }
    var loadKg by remember { mutableStateOf(recentLoadKg.coerceAtLeast(0.0)) }

    LaunchedEffect(selectedCategory?.category?.id) {
        selectedTemplateId = selectedCategory?.templates?.firstOrNull()?.template?.id
    }
    LaunchedEffect(selectedTemplate?.template?.id) {
        selectedVariantId = selectedTemplate?.variants?.firstOrNull()?.id
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
                    text = "添加训练项目",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Text("分类", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(catalog, key = { it.category.id }) { category ->
                        FilterChip(
                            selected = category.category.id == selectedCategory?.category?.id,
                            onClick = { selectedCategoryId = category.category.id },
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
                            onClick = { selectedTemplateId = template.template.id },
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
                    onDecrease = { sets = (sets - 1).coerceAtLeast(1) },
                    onIncrease = { sets = (sets + 1).coerceAtMost(10) },
                )
            }

            when (selectedVariant?.metricType ?: selectedTemplate?.template?.defaultMetricType) {
                MetricType.REPS -> item {
                    ValueAdjuster(
                        label = "每组目标",
                        valueText = "${reps}次",
                        onDecrease = { reps = (reps - 1).coerceAtLeast(1) },
                        onIncrease = { reps = (reps + 1).coerceAtMost(30) },
                    )
                }

                MetricType.HOLD_SECONDS, MetricType.HOLD_SECONDS_PLUS_ECCENTRIC -> item {
                    ValueAdjuster(
                        label = "每组时长",
                        valueText = "${holdSec}秒",
                        onDecrease = { holdSec = (holdSec - 1).coerceAtLeast(3) },
                        onIncrease = { holdSec = (holdSec + 1).coerceAtMost(120) },
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

            if (selectedTemplate?.template?.supportsExternalLoad == true) {
                item {
                    ValueAdjuster(
                        label = "负重",
                        valueText = formatLoadKg(loadKg),
                        onDecrease = { loadKg = (loadKg - 2.5).coerceAtLeast(0.0) },
                        onIncrease = { loadKg = (loadKg + 2.5).coerceAtMost(100.0) },
                    )
                }
            }

            item {
                ValueAdjuster(
                    label = "间歇",
                    valueText = "${restSec}s",
                    onDecrease = { restSec = (restSec - 15).coerceAtLeast(15) },
                    onIncrease = { restSec = (restSec + 15).coerceAtMost(240) },
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
                            loadKg,
                            restSec,
                        )
                    },
                    enabled = selectedTemplate != null && selectedVariant != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存到今天")
                }
            }
        }
    }
}

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
            onAddTask = { _, _, _, _, _, _, _ -> },
            onDeleteTask = {},
            onDuplicateToTomorrow = {},
            onDuplicatePreviousWeek = {},
            onCreateGoal = { _, _ -> },
            onCreateCycle = { _, _, _ -> },
            onSelectGoal = {},
            onSelectCycle = {},
            onStartTraining = {},
        )
    }
}
