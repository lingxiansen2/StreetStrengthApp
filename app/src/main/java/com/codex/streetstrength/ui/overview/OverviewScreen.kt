package com.codex.streetstrength.ui.overview

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.backup.BackupExporter
import com.codex.streetstrength.data.local.CalendarDaySummary
import com.codex.streetstrength.data.local.ExerciseVolumeSummary
import com.codex.streetstrength.data.local.PeriodOverviewSummary
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.domain.OverviewTrendPoint
import com.codex.streetstrength.domain.OverviewTrendRange
import com.codex.streetstrength.domain.buildTrailingWeekRanges
import com.codex.streetstrength.domain.calculateTrendDeltaPercent
import com.codex.streetstrength.ui.formatCalendarStatus
import com.codex.streetstrength.ui.formatLoadKg
import com.codex.streetstrength.ui.formatMonth
import com.codex.streetstrength.ui.formatShortDate
import com.codex.streetstrength.ui.formatWeekdayCn
import com.codex.streetstrength.ui.rememberAppViewModel
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private enum class OverviewScope {
    WEEK,
    MONTH,
}

private data class OverviewRange(
    val start: LocalDate,
    val end: LocalDate,
)

private data class OverviewPeriodData(
    val summary: PeriodOverviewSummary,
    val days: List<CalendarDaySummary>,
    val exerciseVolumes: List<ExerciseVolumeSummary>,
    val trendPoints: List<OverviewTrendPoint>,
)

data class OverviewRhythmDay(
    val date: LocalDate,
    val summary: CalendarDaySummary? = null,
)

data class OverviewUiState(
    val scope: String = OverviewScope.WEEK.name,
    val periodTitle: String = "",
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    val periodSummary: PeriodOverviewSummary = PeriodOverviewSummary(),
    val rhythmDays: List<OverviewRhythmDay> = emptyList(),
    val recentDays: List<CalendarDaySummary> = emptyList(),
    val exerciseVolumes: List<ExerciseVolumeSummary> = emptyList(),
    val trendPoints: List<OverviewTrendPoint> = emptyList(),
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

    private val periodDataFlow = rangeFlow.flatMapLatest { range ->
        combine(
            repository.observePeriodOverview(range.start.toString(), range.end.toString()),
            repository.observeMonthSummary(range.start.toString(), range.end.toString()),
            repository.observeExerciseVolume(range.start.toString(), range.end.toString()),
            observeTrendPoints(range),
        ) { summary, days, exerciseVolumes, trendPoints ->
            OverviewPeriodData(
                summary = summary,
                days = days,
                exerciseVolumes = exerciseVolumes,
                trendPoints = trendPoints,
            )
        }
    }

    val uiState = combine(
        selectedScope,
        rangeFlow,
        periodDataFlow,
    ) { scope, range, periodData ->
        OverviewUiState(
            scope = scope.name,
            periodTitle = when (scope) {
                OverviewScope.WEEK -> "${formatShortDate(range.start)} - ${formatShortDate(range.end)}"
                OverviewScope.MONTH -> formatMonth(YearMonth.from(range.start))
            },
            rangeStart = range.start,
            rangeEnd = range.end,
            periodSummary = periodData.summary,
            rhythmDays = buildRhythmDays(range, periodData.days),
            recentDays = periodData.days
                .filter { it.hasPlan || it.completedSetCount > 0 }
                .sortedByDescending { it.date },
            exerciseVolumes = periodData.exerciseVolumes,
            trendPoints = periodData.trendPoints,
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

    private fun observeTrendPoints(range: OverviewRange) = buildTrailingWeekRanges(range.end).let { ranges ->
        combine(
            repository.observePeriodOverview(ranges[0].start.toString(), ranges[0].end.toString()),
            repository.observePeriodOverview(ranges[1].start.toString(), ranges[1].end.toString()),
            repository.observePeriodOverview(ranges[2].start.toString(), ranges[2].end.toString()),
            repository.observePeriodOverview(ranges[3].start.toString(), ranges[3].end.toString()),
        ) { first, second, third, fourth ->
            listOf(first, second, third, fourth).mapIndexed { index, summary ->
                OverviewTrendPoint(range = ranges[index], summary = summary)
            }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appVersionName = remember(context) { resolveAppVersionName(context) }
    var isExporting by remember { mutableStateOf(false) }
    var backupStatusText by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val exportResult = runCatching {
                val backupJson = app.trainingRepository.exportBackupJson(appVersionName)
                BackupExporter.writeJson(context, uri, backupJson)
            }
            isExporting = false
            exportResult.fold(
                onSuccess = {
                    backupStatusText = "最近备份：${formatBackupStatusTime(LocalDateTime.now())}"
                    Toast.makeText(context, "计划和训练记录已导出到本地文件。", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    backupStatusText = "导出失败，请重新选择本地保存位置。"
                    Toast.makeText(context, "导出备份失败。", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    OverviewScreen(
        uiState = uiState,
        onSelectWeek = viewModel::selectWeek,
        onSelectMonth = viewModel::selectMonth,
        onPreviousPeriod = viewModel::previousPeriod,
        onNextPeriod = viewModel::nextPeriod,
        onOpenPlanner = onOpenPlanner,
        onExportBackup = { exportLauncher.launch(buildBackupFileName(appVersionName)) },
        isExporting = isExporting,
        backupStatusText = backupStatusText,
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
    onExportBackup: () -> Unit,
    isExporting: Boolean,
    backupStatusText: String?,
) {
    val summary = uiState.periodSummary
    val isWeekSelected = uiState.scope == OverviewScope.WEEK.name
    val periodLabel = if (isWeekSelected) "本周" else "本月"
    val attendanceRate = percentage(summary.trainedDays, summary.plannedDays)
    val setCompletionRate = percentage(summary.completedSets, summary.plannedSets)
    val heroRate = when {
        summary.plannedSets > 0 -> setCompletionRate
        summary.plannedDays > 0 -> attendanceRate
        else -> 0
    }
    val heroRateLabel = if (summary.plannedSets > 0) "组完成度" else "训练出勤率"
    val heatmapCells = rememberRhythmCells(
        rangeStart = uiState.rangeStart,
        rhythmDays = uiState.rhythmDays,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            OverviewHeroCard(
                periodTitle = uiState.periodTitle,
                isWeekSelected = isWeekSelected,
                heroRate = heroRate,
                heroRateLabel = heroRateLabel,
                heroMessage = buildHeroMessage(periodLabel, summary),
                summary = summary,
                onSelectWeek = onSelectWeek,
                onSelectMonth = onSelectMonth,
                onPreviousPeriod = onPreviousPeriod,
                onNextPeriod = onNextPeriod,
                onExportBackup = onExportBackup,
                isExporting = isExporting,
                backupStatusText = backupStatusText,
            )
        }

        item {
            OverviewRhythmCard(
                periodLabel = periodLabel,
                isCompact = !isWeekSelected,
                cells = heatmapCells,
                onOpenPlanner = onOpenPlanner,
            )
        }

        item {
            OverviewStatsSection(
                periodLabel = periodLabel,
                summary = summary,
                attendanceRate = attendanceRate,
                setCompletionRate = setCompletionRate,
            )
        }

        item {
            OverviewTrendCard(
                trendPoints = uiState.trendPoints,
            )
        }

        item {
            OverviewVolumeCard(
                periodLabel = periodLabel,
                exerciseVolumes = uiState.exerciseVolumes,
            )
        }

        item {
            OverviewRecentDaysCard(
                daySummaries = uiState.recentDays,
                onOpenPlanner = onOpenPlanner,
            )
        }
    }
}

@Composable
private fun OverviewHeroCard(
    periodTitle: String,
    isWeekSelected: Boolean,
    heroRate: Int,
    heroRateLabel: String,
    heroMessage: String,
    summary: PeriodOverviewSummary,
    onSelectWeek: () -> Unit,
    onSelectMonth: () -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    onExportBackup: () -> Unit,
    isExporting: Boolean,
    backupStatusText: String?,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                        ),
                    ),
                )
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "街健总览",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "把训练节奏、执行度和输出放到一眼就能读懂的面板里。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isWeekSelected,
                            onClick = onSelectWeek,
                            label = { Text("周") },
                        )
                        FilterChip(
                            selected = !isWeekSelected,
                            onClick = onSelectMonth,
                            label = { Text("月") },
                        )
                    }
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
                        text = periodTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = onNextPeriod) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "$heroRate%",
                            fontSize = 52.sp,
                            lineHeight = 56.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = heroRateLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            text = "计划 ${summary.plannedTasks} 项",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Text(
                    text = heroMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                LinearProgressIndicator(
                    progress = { (heroRate / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OverviewQuickBadge(
                        modifier = Modifier.weight(1f),
                        label = "训练日",
                        value = if (summary.plannedDays > 0) {
                            "${summary.trainedDays}/${summary.plannedDays}"
                        } else {
                            "${summary.trainedDays}"
                        },
                    )
                    OverviewQuickBadge(
                        modifier = Modifier.weight(1f),
                        label = "完成组",
                        value = if (summary.plannedSets > 0) {
                            "${summary.completedSets}/${summary.plannedSets}"
                        } else {
                            "${summary.completedSets}"
                        },
                    )
                    OverviewQuickBadge(
                        modifier = Modifier.weight(1f),
                        label = "训练次",
                        value = summary.completedSessions.toString(),
                    )
                }

                OverviewBackupAction(
                    isExporting = isExporting,
                    backupStatusText = backupStatusText,
                    onExportBackup = onExportBackup,
                )
            }
        }
    }
}

@Composable
private fun OverviewQuickBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OverviewBackupAction(
    isExporting: Boolean,
    backupStatusText: String?,
    onExportBackup: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isExporting, onClick = onExportBackup),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (isExporting) "导出中..." else "导出备份",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "把计划、训练记录和关键设置保存为 JSON，本地留一份更安心。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            backupStatusText?.let { status ->
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun OverviewRhythmCard(
    periodLabel: String,
    isCompact: Boolean,
    cells: List<OverviewRhythmDay?>,
    onOpenPlanner: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "训练节奏",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$periodLabel 像热力图一样看每天的安排和完成状态，点日期可以直接回到计划页。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            OverviewWeekdayHeader()

            cells.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { day ->
                        OverviewRhythmCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            isCompact = isCompact,
                            onOpenPlanner = onOpenPlanner,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OverviewLegendItem("已完成", MaterialTheme.colorScheme.secondary)
                OverviewLegendItem("进行中", MaterialTheme.colorScheme.tertiary)
                OverviewLegendItem("已安排", MaterialTheme.colorScheme.primary)
                OverviewLegendItem("空白", MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable
private fun OverviewWeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun OverviewRhythmCell(
    day: OverviewRhythmDay?,
    isCompact: Boolean,
    onOpenPlanner: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Spacer(
            modifier = modifier
                .aspectRatio(if (isCompact) 0.95f else 1.02f),
        )
        return
    }

    val summary = day.summary
    val status = summary?.completionStatus.orEmpty().ifBlank { "EMPTY" }
    val isToday = day.date == LocalDate.now()
    val accentColor = statusAccentColor(status)
    val containerColor = statusContainerColor(status)
    val badgeText = buildDayBadgeText(day, isCompact)

    Surface(
        modifier = modifier
            .aspectRatio(if (isCompact) 0.95f else 1.02f)
            .clickable { onOpenPlanner(day.date.toString()) },
        shape = RoundedCornerShape(if (isCompact) 18.dp else 22.dp),
        color = containerColor,
        border = when {
            isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            status != "EMPTY" -> BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
            else -> null
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontWeight = FontWeight.SemiBold,
                style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = badgeText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(
                            color = if (status == "EMPTY") {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                accentColor
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun OverviewLegendItem(
    label: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun OverviewStatsSection(
    periodLabel: String,
    summary: PeriodOverviewSummary,
    attendanceRate: Int,
    setCompletionRate: Int,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "训练指标",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "保留真正和街健进步相关的四组核心信息，不把总览做成难读的表格。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewStatCard(
                    modifier = Modifier.weight(1f),
                    title = "出勤",
                    value = "${attendanceRate}%",
                    caption = if (summary.plannedDays > 0) {
                        "${summary.trainedDays}/${summary.plannedDays} 训练日"
                    } else {
                        "还没有安排 $periodLabel 训练"
                    },
                    accentColor = MaterialTheme.colorScheme.primary,
                )
                OverviewStatCard(
                    modifier = Modifier.weight(1f),
                    title = "完成组",
                    value = summary.completedSets.toString(),
                    caption = if (summary.plannedSets > 0) {
                        "计划 ${summary.plannedSets} 组 · ${setCompletionRate}%"
                    } else {
                        "当前没有设定组目标"
                    },
                    accentColor = MaterialTheme.colorScheme.secondary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverviewStatCard(
                    modifier = Modifier.weight(1f),
                    title = "总次数",
                    value = summary.totalReps.toString(),
                    caption = "${summary.completedSessions} 次训练输出",
                    accentColor = MaterialTheme.colorScheme.tertiary,
                )
                OverviewStatCard(
                    modifier = Modifier.weight(1f),
                    title = "静止 / 负重",
                    value = formatHoldDuration(summary.totalHoldSec),
                    caption = "负重 ${formatLoadKg(summary.totalExternalLoadKg)}",
                    accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun OverviewStatCard(
    title: String,
    value: String,
    caption: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = accentColor, shape = CircleShape),
                )
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OverviewTrendCard(
    trendPoints: List<OverviewTrendPoint>,
) {
    val hasTrendData = trendPoints.any { point ->
        point.summary.plannedDays > 0 ||
            point.summary.trainedDays > 0 ||
            point.summary.completedSets > 0
    }
    val maxCompletedSets = trendPoints.maxOfOrNull { it.summary.completedSets } ?: 0

    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "近 4 周趋势",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "按自然周对比训练日、完成组和输出量，避免只看单个周期造成误判。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (!hasTrendData) {
                Text(
                    text = "最近 4 周还没有计划或训练记录，完成第一组后这里会显示趋势。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                buildTrendDeltaLabel(trendPoints)?.let { label ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            text = label,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                trendPoints.forEach { point ->
                    OverviewTrendRow(
                        point = point,
                        maxCompletedSets = maxCompletedSets,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTrendRow(
    point: OverviewTrendPoint,
    maxCompletedSets: Int,
) {
    val summary = point.summary
    val setRate = percentage(summary.completedSets, summary.plannedSets)
    val progress = if (maxCompletedSets > 0) {
        summary.completedSets.toFloat() / maxCompletedSets.toFloat()
    } else {
        0f
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatShortDate(point.range.start)} - ${formatShortDate(point.range.end)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${summary.completedSets} 组",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            )

            Text(
                text = buildTrendCaption(summary, setRate),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OverviewVolumeCard(
    periodLabel: String,
    exerciseVolumes: List<ExerciseVolumeSummary>,
) {
    val categoryVolumes = buildCategoryVolumes(exerciseVolumes)
    val maxExerciseSets = exerciseVolumes.maxOfOrNull { it.completedSets } ?: 0

    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "动作输出",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "按实际完成的有效组聚合，跳过组不计入动作输出。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (exerciseVolumes.isEmpty()) {
                Text(
                    text = "$periodLabel 还没有有效完成组，完成训练后会按动作和部位拆出训练量。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "部位分布",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                categoryVolumes.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            OverviewCategoryVolumeChip(
                                modifier = Modifier.weight(1f),
                                item = item,
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Text(
                    text = "动作排行",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                exerciseVolumes.take(5).forEach { item ->
                    OverviewExerciseVolumeRow(
                        item = item,
                        maxCompletedSets = maxExerciseSets,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCategoryVolumeChip(
    item: OverviewCategoryVolume,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = item.categoryName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "${item.completedSets} 组",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = buildCategoryVolumeCaption(item),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OverviewExerciseVolumeRow(
    item: ExerciseVolumeSummary,
    maxCompletedSets: Int,
) {
    val progress = if (maxCompletedSets > 0) {
        item.completedSets.toFloat() / maxCompletedSets.toFloat()
    } else {
        0f
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.categoryName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "${item.completedSets} 组",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            )

            Text(
                text = buildExerciseVolumeCaption(item),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OverviewRecentDaysCard(
    daySummaries: List<CalendarDaySummary>,
    onOpenPlanner: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "训练记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (daySummaries.isEmpty()) {
                Text(
                    text = "这个周期还没有安排或训练记录，先去计划页把第一天排出来。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                daySummaries.forEach { item ->
                    OverviewDayRow(
                        item = item,
                        onClick = { onOpenPlanner(item.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewDayRow(
    item: CalendarDaySummary,
    onClick: () -> Unit,
) {
    val date = LocalDate.parse(item.date)
    val status = item.completionStatus
    val accentColor = statusAccentColor(status)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentColor.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
            ) {
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = formatShortDate(date),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatWeekdayCn(date),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.primaryLabel ?: "已安排训练",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.16f),
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            text = formatCalendarStatus(status),
                            color = accentColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text = "项目 ${item.taskCount} 个 · 完成组 ${item.completedSetCount}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class OverviewCategoryVolume(
    val categoryName: String,
    val exerciseCount: Int,
    val completedSets: Int,
    val totalReps: Int,
    val totalHoldSec: Int,
    val totalExternalLoadKg: Double,
)

private fun buildTrendDeltaLabel(trendPoints: List<OverviewTrendPoint>): String? {
    val current = trendPoints.lastOrNull()?.summary ?: return null
    val previous = trendPoints.dropLast(1).lastOrNull()?.summary ?: return null
    val delta = calculateTrendDeltaPercent(
        current = current.completedSets,
        previous = previous.completedSets,
    )
    return if (delta == null) {
        "上周暂无有效完成组，本周完成 ${current.completedSets} 组。"
    } else {
        "完成组较上周 ${formatSignedPercent(delta)}。"
    }
}

private fun formatSignedPercent(value: Int): String =
    if (value > 0) "+$value%" else "$value%"

private fun buildTrendCaption(
    summary: PeriodOverviewSummary,
    setRate: Int,
): String {
    val trainingDays = if (summary.plannedDays > 0) {
        "训练日 ${summary.trainedDays}/${summary.plannedDays}"
    } else {
        "训练日 ${summary.trainedDays}"
    }
    val setCompletion = if (summary.plannedSets > 0) {
        "组完成度 $setRate%"
    } else {
        "未设组目标"
    }
    return "$trainingDays · $setCompletion · ${formatVolumeOutput(summary.totalReps, summary.totalHoldSec)}"
}

private fun buildCategoryVolumes(
    exerciseVolumes: List<ExerciseVolumeSummary>,
): List<OverviewCategoryVolume> {
    return exerciseVolumes
        .groupBy { it.categoryName }
        .map { (categoryName, items) ->
            OverviewCategoryVolume(
                categoryName = categoryName,
                exerciseCount = items.size,
                completedSets = items.sumOf { it.completedSets },
                totalReps = items.sumOf { it.totalReps },
                totalHoldSec = items.sumOf { it.totalHoldSec },
                totalExternalLoadKg = items.sumOf { it.totalExternalLoadKg },
            )
        }
        .sortedWith(
            compareByDescending<OverviewCategoryVolume> { it.completedSets }
                .thenByDescending { it.totalReps }
                .thenBy { it.categoryName },
        )
}

private fun buildExerciseVolumeCaption(item: ExerciseVolumeSummary): String {
    val parts = mutableListOf<String>()
    parts += "${item.trainedDays} 个训练日"
    if (item.totalReps > 0) parts += "${item.totalReps} 次"
    if (item.totalHoldSec > 0) parts += "静止 ${formatHoldDuration(item.totalHoldSec)}"
    if (item.totalExternalLoadKg > 0.0) parts += "负重 ${formatLoadKg(item.totalExternalLoadKg)}"
    return parts.joinToString(" · ")
}

private fun buildCategoryVolumeCaption(item: OverviewCategoryVolume): String {
    val parts = mutableListOf("${item.exerciseCount} 个动作")
    if (item.totalReps > 0) parts += "${item.totalReps} 次"
    if (item.totalHoldSec > 0) parts += "静止 ${formatHoldDuration(item.totalHoldSec)}"
    if (item.totalExternalLoadKg > 0.0) parts += "负重 ${formatLoadKg(item.totalExternalLoadKg)}"
    return parts.joinToString(" · ")
}

private fun formatVolumeOutput(
    totalReps: Int,
    totalHoldSec: Int,
): String {
    val parts = mutableListOf<String>()
    if (totalReps > 0) parts += "${totalReps} 次"
    if (totalHoldSec > 0) parts += "静止 ${formatHoldDuration(totalHoldSec)}"
    return parts.ifEmpty { listOf("暂无次数或静止时长") }.joinToString(" · ")
}

private fun buildRhythmDays(
    range: OverviewRange,
    days: List<CalendarDaySummary>,
): List<OverviewRhythmDay> {
    val summaryByDate = days.associateBy { LocalDate.parse(it.date) }
    return generateSequence(range.start) { current ->
        current.takeIf { it < range.end }?.plusDays(1)
    }.takeWhile { it <= range.end }
        .map { date -> OverviewRhythmDay(date = date, summary = summaryByDate[date]) }
        .toList()
}

private fun rememberRhythmCells(
    rangeStart: LocalDate,
    rhythmDays: List<OverviewRhythmDay>,
): List<OverviewRhythmDay?> {
    val leadingBlankCount = rangeStart.dayOfWeek.value - 1
    val cells = List(leadingBlankCount) { null } + rhythmDays
    val trailingBlankCount = (7 - (cells.size % 7)) % 7
    return cells + List(trailingBlankCount) { null }
}

private fun percentage(value: Int, total: Int): Int {
    if (total <= 0) return 0
    return ((value.toFloat() / total.toFloat()) * 100f).roundToInt()
}

private fun buildHeroMessage(
    periodLabel: String,
    summary: PeriodOverviewSummary,
): String {
    return when {
        summary.plannedDays == 0 ->
            "$periodLabel 还没有安排训练，先把节奏排出来，再开始追踪输出。"

        summary.trainedDays == 0 ->
            "计划已经有了，但这段时间还没开练。先把第一练打出去，热力图就会亮起来。"

        summary.completedDays == summary.plannedDays && summary.plannedDays > 0 ->
            "这段周期已经全部打满，节奏很稳。接下来可以继续叠加下一周的输出。"

        summary.trainedDays < summary.plannedDays ->
            "已经完成 ${summary.trainedDays}/${summary.plannedDays} 个训练日，再补上剩下的安排，这周就很完整了。"

        else ->
            "训练日已经超过原计划，最近状态很足，继续把完成组和总次数顶上去。"
    }
}

private fun buildDayBadgeText(
    day: OverviewRhythmDay,
    isCompact: Boolean,
): String {
    val summary = day.summary
    return when {
        summary?.completedSetCount ?: 0 > 0 -> {
            if (isCompact) {
                summary?.completedSetCount?.toString().orEmpty()
            } else {
                "${summary?.completedSetCount ?: 0}组"
            }
        }

        summary?.hasPlan == true -> {
            if (isCompact) {
                "${summary.taskCount}"
            } else {
                "${summary.taskCount}项"
            }
        }

        day.date == LocalDate.now() -> "今"
        else -> ""
    }
}

@Composable
private fun statusAccentColor(status: String): Color = when (status) {
    "DONE" -> MaterialTheme.colorScheme.secondary
    "PARTIAL" -> MaterialTheme.colorScheme.tertiary
    "PLANNED" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
}

@Composable
private fun statusContainerColor(status: String): Color = when (status) {
    "DONE" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    "PARTIAL" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
    "PLANNED" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
}

private fun formatHoldDuration(totalHoldSec: Int): String {
    if (totalHoldSec <= 0) return "0秒"
    val minutes = totalHoldSec / 60
    val seconds = totalHoldSec % 60
    return if (minutes > 0) {
        "${minutes}分${seconds.toString().padStart(2, '0')}秒"
    } else {
        "${seconds}秒"
    }
}

private fun buildBackupFileName(appVersion: String): String {
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    return "streetstrength-backup-$timestamp-v$appVersion.json"
}

private fun formatBackupStatusTime(dateTime: LocalDateTime): String =
    dateTime.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))

private fun resolveAppVersionName(context: android.content.Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return packageInfo.versionName ?: "unknown"
}

@Preview(showBackground = true)
@Composable
private fun OverviewPreview() {
    StreetStrengthTheme {
        OverviewScreen(
            uiState = OverviewUiState(
                periodTitle = "04/21 - 04/27",
                rangeStart = LocalDate.of(2026, 4, 21),
                rangeEnd = LocalDate.of(2026, 4, 27),
                periodSummary = PeriodOverviewSummary(
                    plannedDays = 4,
                    trainedDays = 3,
                    completedDays = 2,
                    plannedTasks = 7,
                    plannedSets = 24,
                    completedSets = 16,
                    completedSessions = 3,
                    totalReps = 52,
                    totalHoldSec = 38,
                    totalExternalLoadKg = 40.0,
                ),
                rhythmDays = listOf(
                    OverviewRhythmDay(
                        date = LocalDate.of(2026, 4, 21),
                        summary = CalendarDaySummary(
                            date = "2026-04-21",
                            hasPlan = true,
                            taskCount = 2,
                            completedSetCount = 6,
                            completionStatus = "DONE",
                            primaryLabel = "引体向上 · 正手宽握",
                        ),
                    ),
                    OverviewRhythmDay(
                        date = LocalDate.of(2026, 4, 22),
                        summary = CalendarDaySummary(
                            date = "2026-04-22",
                            hasPlan = true,
                            taskCount = 2,
                            completedSetCount = 4,
                            completionStatus = "PARTIAL",
                            primaryLabel = "双杠臂屈伸 · 标准",
                        ),
                    ),
                    OverviewRhythmDay(
                        date = LocalDate.of(2026, 4, 23),
                        summary = CalendarDaySummary(
                            date = "2026-04-23",
                            hasPlan = true,
                            taskCount = 3,
                            completedSetCount = 0,
                            completionStatus = "PLANNED",
                            primaryLabel = "倒立撑 · 靠墙",
                        ),
                    ),
                ),
                recentDays = listOf(
                    CalendarDaySummary(
                        date = "2026-04-23",
                        hasPlan = true,
                        taskCount = 3,
                        completedSetCount = 0,
                        completionStatus = "PLANNED",
                        primaryLabel = "倒立撑 · 靠墙",
                    ),
                    CalendarDaySummary(
                        date = "2026-04-22",
                        hasPlan = true,
                        taskCount = 2,
                        completedSetCount = 4,
                        completionStatus = "PARTIAL",
                        primaryLabel = "双杠臂屈伸 · 标准",
                    ),
                    CalendarDaySummary(
                        date = "2026-04-21",
                        hasPlan = true,
                        taskCount = 2,
                        completedSetCount = 6,
                        completionStatus = "DONE",
                        primaryLabel = "引体向上 · 正手宽握",
                    ),
                ),
                exerciseVolumes = listOf(
                    ExerciseVolumeSummary(
                        templateId = 11,
                        exerciseName = "引体向上",
                        categoryName = "拉力训练",
                        categoryType = CategoryType.PULL,
                        trainedDays = 2,
                        completedSets = 8,
                        totalReps = 42,
                        totalExternalLoadKg = 20.0,
                    ),
                    ExerciseVolumeSummary(
                        templateId = 12,
                        exerciseName = "双杠臂屈伸",
                        categoryName = "推力训练",
                        categoryType = CategoryType.PUSH,
                        trainedDays = 1,
                        completedSets = 4,
                        totalReps = 18,
                    ),
                    ExerciseVolumeSummary(
                        templateId = 13,
                        exerciseName = "冲肩",
                        categoryName = "推力训练",
                        categoryType = CategoryType.PUSH,
                        trainedDays = 1,
                        completedSets = 4,
                        totalHoldSec = 38,
                    ),
                ),
                trendPoints = listOf(
                    OverviewTrendPoint(
                        range = OverviewTrendRange(
                            start = LocalDate.of(2026, 4, 6),
                            end = LocalDate.of(2026, 4, 12),
                        ),
                        summary = PeriodOverviewSummary(
                            plannedDays = 3,
                            trainedDays = 2,
                            plannedSets = 18,
                            completedSets = 10,
                            totalReps = 36,
                        ),
                    ),
                    OverviewTrendPoint(
                        range = OverviewTrendRange(
                            start = LocalDate.of(2026, 4, 13),
                            end = LocalDate.of(2026, 4, 19),
                        ),
                        summary = PeriodOverviewSummary(
                            plannedDays = 3,
                            trainedDays = 3,
                            plannedSets = 20,
                            completedSets = 14,
                            totalReps = 44,
                        ),
                    ),
                    OverviewTrendPoint(
                        range = OverviewTrendRange(
                            start = LocalDate.of(2026, 4, 20),
                            end = LocalDate.of(2026, 4, 26),
                        ),
                        summary = PeriodOverviewSummary(
                            plannedDays = 4,
                            trainedDays = 3,
                            plannedSets = 24,
                            completedSets = 16,
                            totalReps = 52,
                            totalHoldSec = 38,
                        ),
                    ),
                    OverviewTrendPoint(
                        range = OverviewTrendRange(
                            start = LocalDate.of(2026, 4, 27),
                            end = LocalDate.of(2026, 5, 3),
                        ),
                        summary = PeriodOverviewSummary(
                            plannedDays = 4,
                            trainedDays = 1,
                            plannedSets = 24,
                            completedSets = 6,
                            totalReps = 20,
                        ),
                    ),
                ),
            ),
            onSelectWeek = {},
            onSelectMonth = {},
            onPreviousPeriod = {},
            onNextPeriod = {},
            onOpenPlanner = {},
            onExportBackup = {},
            isExporting = false,
            backupStatusText = "最近备份：04/23 13:20",
        )
    }
}
