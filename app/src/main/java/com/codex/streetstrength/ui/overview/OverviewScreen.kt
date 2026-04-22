package com.codex.streetstrength.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.local.CalendarDaySummary
import com.codex.streetstrength.data.local.PeriodOverviewSummary
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.ui.formatCalendarStatus
import com.codex.streetstrength.ui.formatLoadKg
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

private enum class OverviewScope {
    WEEK,
    MONTH,
}

private data class OverviewRange(
    val start: LocalDate,
    val end: LocalDate,
)

data class OverviewUiState(
    val scope: String = OverviewScope.WEEK.name,
    val periodTitle: String = "",
    val periodSummary: PeriodOverviewSummary = PeriodOverviewSummary(),
    val daySummaries: List<CalendarDaySummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(
    private val repository: TrainingRepository,
) : ViewModel() {
    private val selectedScope = MutableStateFlow(OverviewScope.WEEK)
    private val anchorDate = MutableStateFlow(LocalDate.now())

    private val rangeFlow = combine(selectedScope, anchorDate) { scope, anchor ->
        when (scope) {
            OverviewScope.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                OverviewRange(start = start, end = start.plusDays(6))
            }

            OverviewScope.MONTH -> {
                val month = YearMonth.from(anchor)
                OverviewRange(start = month.atDay(1), end = month.atEndOfMonth())
            }
        }
    }

    val uiState = combine(
        selectedScope,
        rangeFlow,
        rangeFlow.flatMapLatest { range ->
            repository.observePeriodOverview(range.start.toString(), range.end.toString())
        },
        rangeFlow.flatMapLatest { range ->
            repository.observeMonthSummary(range.start.toString(), range.end.toString())
        },
    ) { scope, range, summary, days ->
        OverviewUiState(
            scope = scope.name,
            periodTitle = when (scope) {
                OverviewScope.WEEK -> {
                    "${formatShortDate(range.start)} - ${formatShortDate(range.end)}"
                }

                OverviewScope.MONTH -> formatMonth(YearMonth.from(range.start))
            },
            periodSummary = summary,
            daySummaries = days
                .filter { it.hasPlan || it.completedSetCount > 0 }
                .sortedByDescending { it.date },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewUiState(),
    )

    fun selectWeek() {
        selectedScope.value = OverviewScope.WEEK
    }

    fun selectMonth() {
        selectedScope.value = OverviewScope.MONTH
    }

    fun previousPeriod() {
        anchorDate.value = when (selectedScope.value) {
            OverviewScope.WEEK -> anchorDate.value.minusWeeks(1)
            OverviewScope.MONTH -> anchorDate.value.minusMonths(1)
        }
    }

    fun nextPeriod() {
        anchorDate.value = when (selectedScope.value) {
            OverviewScope.WEEK -> anchorDate.value.plusWeeks(1)
            OverviewScope.MONTH -> anchorDate.value.plusMonths(1)
        }
    }
}

@Composable
fun OverviewScreenRoute(
    app: StreetStrengthApp,
    onOpenPlanner: (String) -> Unit,
) {
    val viewModel = rememberAppViewModel { OverviewViewModel(app.trainingRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    OverviewScreen(
        uiState = uiState,
        onSelectWeek = viewModel::selectWeek,
        onSelectMonth = viewModel::selectMonth,
        onPreviousPeriod = viewModel::previousPeriod,
        onNextPeriod = viewModel::nextPeriod,
        onOpenPlanner = onOpenPlanner,
    )
}

@Composable
fun OverviewScreen(
    uiState: OverviewUiState,
    onSelectWeek: () -> Unit,
    onSelectMonth: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onOpenPlanner: (String) -> Unit,
) {
    val summary = uiState.periodSummary
    val weekSelected = uiState.scope == OverviewScope.WEEK.name

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        text = "\u603b\u89c8",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = weekSelected,
                            onClick = onSelectWeek,
                            label = { Text("\u5468") },
                        )
                        FilterChip(
                            selected = !weekSelected,
                            onClick = onSelectMonth,
                            label = { Text("\u6708") },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPreviousPeriod) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                        }
                        Text(
                            text = uiState.periodTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(onClick = onNextPeriod) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }

        item {
            OverviewMetricRow(
                leftTitle = "\u8ba1\u5212\u65e5",
                leftValue = summary.plannedDays.toString(),
                rightTitle = "\u8bad\u7ec3\u65e5",
                rightValue = summary.trainedDays.toString(),
            )
        }

        item {
            OverviewMetricRow(
                leftTitle = "\u5b8c\u6210\u65e5",
                leftValue = summary.completedDays.toString(),
                rightTitle = "\u5b8c\u6210\u7ec4",
                rightValue = "${summary.completedSets} / ${summary.plannedSets}",
            )
        }

        item {
            OverviewMetricRow(
                leftTitle = "\u8bad\u7ec3\u6b21\u6570",
                leftValue = summary.completedSessions.toString(),
                rightTitle = "\u603b\u6b21\u6570",
                rightValue = summary.totalReps.toString(),
            )
        }

        item {
            OverviewMetricRow(
                leftTitle = "\u603b\u9759\u6b62\u65f6\u957f",
                leftValue = "${summary.totalHoldSec}\u79d2",
                rightTitle = "\u8d1f\u91cd\u603b\u548c",
                rightValue = formatLoadKg(summary.totalExternalLoadKg),
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "\u5468\u671f\u660e\u7ec6",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.daySummaries.isEmpty()) {
                        Text(
                            text = "\u8fd9\u4e2a\u5468\u671f\u8fd8\u6ca1\u6709\u8ba1\u5212\u6216\u8bad\u7ec3\u8bb0\u5f55\u3002",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.daySummaries.forEach { item ->
                            OverviewDayRow(
                                item = item,
                                onClick = { onOpenPlanner(item.date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricRow(
    leftTitle: String,
    leftValue: String,
    rightTitle: String,
    rightValue: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewMetricCard(
            modifier = Modifier.weight(1f),
            title = leftTitle,
            value = leftValue,
        )
        OverviewMetricCard(
            modifier = Modifier.weight(1f),
            title = rightTitle,
            value = rightValue,
        )
    }
}

@Composable
private fun OverviewMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OverviewDayRow(
    item: CalendarDaySummary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val date = LocalDate.parse(item.date)
            Text(
                text = "${formatShortDate(date)} ${formatWeekdayCn(date)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatCalendarStatus(item.completionStatus),
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "\u9879\u76ee ${item.taskCount} \u4e2a \u00b7 \u5b8c\u6210\u7ec4 ${item.completedSetCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.primaryLabel?.let { label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OverviewPreview() {
    StreetStrengthTheme {
        OverviewScreen(
            uiState = OverviewUiState(
                periodTitle = "04/21 - 04/27",
                periodSummary = PeriodOverviewSummary(
                    plannedDays = 4,
                    trainedDays = 3,
                    completedDays = 2,
                    plannedSets = 24,
                    completedSets = 16,
                    completedSessions = 3,
                    totalReps = 52,
                    totalHoldSec = 38,
                    totalExternalLoadKg = 40.0,
                ),
                daySummaries = listOf(
                    CalendarDaySummary(
                        date = LocalDate.now().toString(),
                        hasPlan = true,
                        taskCount = 2,
                        completedSetCount = 5,
                        completionStatus = "DONE",
                        primaryLabel = "\u5f15\u4f53\u5411\u4e0a \u00b7 \u6b63\u624b\u5bbd\u63e1",
                    ),
                ),
            ),
            onSelectWeek = {},
            onSelectMonth = {},
            onPreviousPeriod = {},
            onNextPeriod = {},
            onOpenPlanner = {},
        )
    }
}
