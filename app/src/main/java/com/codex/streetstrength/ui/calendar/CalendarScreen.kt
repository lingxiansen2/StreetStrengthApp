package com.codex.streetstrength.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.debug.PlanTestingSwitch
import com.codex.streetstrength.data.local.CalendarDaySummary
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.model.CalendarCompletionStatus
import com.codex.streetstrength.domain.PlanDayActionPolicy
import com.codex.streetstrength.domain.PlanDateTiming
import com.codex.streetstrength.domain.resolvePlanDayActionPolicy
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.ui.formatCalendarStatus
import com.codex.streetstrength.ui.formatMetric
import com.codex.streetstrength.ui.formatMonth
import com.codex.streetstrength.ui.formatShortDate
import com.codex.streetstrength.ui.formatWeekdayCn
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val summaries: List<CalendarDaySummary> = emptyList(),
    val selectedPlan: PlanDayWithTasks? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    repository: TrainingRepository,
) : ViewModel() {
    private val currentMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState = combine(
        currentMonth,
        selectedDate,
        currentMonth.flatMapLatest { month ->
            repository.observeMonthSummary(
                from = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
                to = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString(),
            )
        },
        selectedDate.flatMapLatest { repository.observeDayPlan(it.toString()) },
    ) { month, date, summaries, plan ->
        CalendarUiState(
            currentMonth = month,
            selectedDate = date,
            summaries = summaries,
            selectedPlan = plan,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun previousMonth() {
        currentMonth.value = currentMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        currentMonth.value = currentMonth.value.plusMonths(1)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        currentMonth.value = YearMonth.from(date)
    }
}

@Composable
fun CalendarScreenRoute(
    app: StreetStrengthApp,
    onOpenPlanner: (String) -> Unit,
    onStartTraining: (String) -> Unit,
) {
    val viewModel = rememberAppViewModel {
        CalendarViewModel(app.trainingRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarScreen(
        uiState = uiState,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectDate = viewModel::selectDate,
        onOpenPlanner = onOpenPlanner,
        onStartTraining = onStartTraining,
    )
}

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenPlanner: (String) -> Unit,
    onStartTraining: (String) -> Unit,
) {
    val summaryMap = uiState.summaries.associateBy { it.date }
    val selectedSummary = summaryMap[uiState.selectedDate.toString()]
    val monthDays = rememberMonthCells(uiState.currentMonth)
    val selectedTasks = uiState.selectedPlan?.tasks
        ?.sortedBy { it.task.orderInDay }
        .orEmpty()
    val selectedStatus = selectedSummary?.completionStatus
        ?: if (selectedTasks.isNotEmpty()) CalendarCompletionStatus.PLANNED.name else CalendarCompletionStatus.EMPTY.name
    val actionPolicy = resolvePlanDayActionPolicy(
        selectedDate = uiState.selectedDate,
        hasTasks = selectedTasks.isNotEmpty(),
        completionStatus = selectedStatus,
        allowCompletedTodayTesting = PlanTestingSwitch.enabled,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月")
                    }
                    Text(
                        text = formatMonth(uiState.currentMonth),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月")
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                monthDays.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        week.forEach { cell ->
                            CalendarDayCell(
                                modifier = Modifier.weight(1f),
                                date = cell,
                                summary = cell?.let { summaryMap[it.toString()] },
                                isSelected = cell == uiState.selectedDate,
                                isCurrentMonth = cell?.month == uiState.currentMonth.month,
                                onClick = { cell?.let(onSelectDate) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "${calendarPlanTitle(actionPolicy)} ${formatShortDate(uiState.selectedDate)} ${formatWeekdayCn(uiState.selectedDate)}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (selectedTasks.isEmpty()) {
                        Text(
                            text = "这一天还没有排训练，去计划页添加动作。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        selectedTasks.take(3).forEach { task ->
                            Text(
                                text = buildString {
                                    append(task.template.name)
                                    append(" · ")
                                    append(task.variant.name)
                                    append("\n")
                                    append("${task.setPlans.size}组 × ")
                                    append(
                                        formatMetric(
                                            targetReps = task.setPlans.firstOrNull()?.targetReps,
                                            targetHoldSec = task.setPlans.firstOrNull()?.targetHoldSec,
                                        ),
                                    )
                                    append(" · ${task.task.restSec}s")
                                    if (task.task.plannedLoadKg > 0.0) append(" · ${task.task.plannedLoadKg}kg")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    Text(
                        text = "状态：${formatCalendarStatus(selectedStatus)} · ${calendarDateStateText(actionPolicy)}",
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onOpenPlanner(uiState.selectedDate.toString()) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("编辑计划")
                        }
                        Button(
                            onClick = { onStartTraining(uiState.selectedDate.toString()) },
                            enabled = actionPolicy.canStartTraining,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(startTrainingButtonText(actionPolicy))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    summary: CalendarDaySummary?,
    isSelected: Boolean,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = when (summary?.completionStatus) {
        "DONE" -> MaterialTheme.colorScheme.secondary
        "PARTIAL" -> MaterialTheme.colorScheme.tertiary
        "PLANNED" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val label = when {
        date == null -> ""
        date == LocalDate.now() -> "T"
        summary?.completionStatus == "DONE" -> "D"
        summary?.hasPlan == true -> "P"
        else -> ""
    }

    Surface(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = date != null, onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = date?.dayOfMonth?.toString().orEmpty(),
                color = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (summary?.hasPlan == true) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                } else {
                    Spacer(modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

private fun rememberMonthCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val gridStart = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastDay = month.atEndOfMonth()
    val gridEnd = lastDay.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = generateSequence(gridStart) { current ->
        current.takeIf { it < gridEnd }?.plusDays(1)
    }.takeWhile { it <= gridEnd }.toList()
    return days
}

private fun calendarPlanTitle(policy: PlanDayActionPolicy): String = when (policy.timing) {
    PlanDateTiming.PAST -> "过期计划"
    PlanDateTiming.TODAY -> "今日计划"
    PlanDateTiming.FUTURE -> "未来计划"
}

private fun calendarDateStateText(policy: PlanDayActionPolicy): String = when {
    policy.isCompletedTodayTestingAllowed -> "Debug 可再次测试"
    policy.hasRecordedTraining -> "已有训练记录"
    policy.timing == PlanDateTiming.PAST -> "不可开始"
    policy.timing == PlanDateTiming.FUTURE -> "未到开始日期"
    policy.hasTasks -> "可开始"
    else -> "未安排"
}

private fun startTrainingButtonText(policy: PlanDayActionPolicy): String = when {
    policy.isCompletedTodayTestingAllowed -> "Debug 再测"
    policy.completionStatus == CalendarCompletionStatus.DONE.name -> "\u8bad\u7ec3\u5df2\u5b8c\u6210"
    policy.completionStatus == CalendarCompletionStatus.PARTIAL.name -> "继续训练"
    !policy.hasTasks -> "暂无计划"
    policy.timing == PlanDateTiming.PAST -> "计划已过期"
    policy.timing == PlanDateTiming.FUTURE -> "未到日期"
    else -> "\u5f00\u59cb\u8bad\u7ec3"
}

@Preview(showBackground = true)
@Composable
private fun CalendarPreview() {
    StreetStrengthTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                currentMonth = YearMonth.now(),
                selectedDate = LocalDate.now(),
                summaries = listOf(
                    CalendarDaySummary(
                        date = LocalDate.now().toString(),
                        hasPlan = true,
                        taskCount = 2,
                        completedSetCount = 3,
                        completionStatus = "PARTIAL",
                        primaryLabel = "引体向上 · 正手宽握",
                    ),
                ),
                selectedPlan = null,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onSelectDate = {},
            onOpenPlanner = {},
            onStartTraining = {},
        )
    }
}
