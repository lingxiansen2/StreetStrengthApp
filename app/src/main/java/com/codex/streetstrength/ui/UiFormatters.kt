package com.codex.streetstrength.ui

import com.codex.streetstrength.data.model.CalendarCompletionStatus
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SourceType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("yyyy / MM")
private val shortDateFormatter = DateTimeFormatter.ofPattern("MM/dd")

fun formatMonth(month: YearMonth): String = month.atDay(1).format(monthFormatter)

fun formatShortDate(date: LocalDate): String = date.format(shortDateFormatter)

fun formatWeekdayCn(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "\u5468\u4e00"
    2 -> "\u5468\u4e8c"
    3 -> "\u5468\u4e09"
    4 -> "\u5468\u56db"
    5 -> "\u5468\u4e94"
    6 -> "\u5468\u516d"
    else -> "\u5468\u65e5"
}

fun formatRestClock(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) + 999L) / 1_000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun formatLoadKg(value: Double): String {
    return if (value <= 0.0) {
        "0kg"
    } else if (value % 1.0 == 0.0) {
        "${value.toInt()}kg"
    } else {
        "${"%.1f".format(Locale.US, value)}kg"
    }
}

fun formatMetric(targetReps: Int?, targetHoldSec: Int?, fallback: String = "\u529b\u7aed"): String {
    return when {
        targetReps != null -> "${targetReps}\u6b21"
        targetHoldSec != null -> "${targetHoldSec}\u79d2"
        else -> fallback
    }
}

fun formatCategoryType(type: CategoryType): String = when (type) {
    CategoryType.PULL -> "\u62c9\u529b"
    CategoryType.PUSH -> "\u63a8\u529b"
    CategoryType.CORE -> "\u6838\u5fc3"
    CategoryType.ARMS -> "\u624b\u81c2"
    CategoryType.LEGS -> "\u817f\u90e8"
    CategoryType.SKILL -> "\u6280\u80fd\u57fa\u7840"
}

fun formatMetricType(metricType: MetricType): String = when (metricType) {
    MetricType.REPS -> "\u6b21\u6570"
    MetricType.HOLD_SECONDS -> "\u65f6\u957f"
    MetricType.HOLD_TO_FAILURE -> "\u529b\u7aed"
    MetricType.HOLD_SECONDS_PLUS_ECCENTRIC -> "\u9501\u5b9a+\u79bb\u5fc3"
}

fun formatSourceType(sourceType: SourceType): String = when (sourceType) {
    SourceType.BUILTIN -> "\u5185\u7f6e"
    SourceType.CUSTOM -> "\u81ea\u5b9a\u4e49"
}

fun formatCalendarStatus(status: String): String = when (status) {
    CalendarCompletionStatus.EMPTY.name -> "\u672a\u5b89\u6392"
    CalendarCompletionStatus.PLANNED.name -> "\u5df2\u8ba1\u5212"
    CalendarCompletionStatus.PARTIAL.name -> "\u8fdb\u884c\u4e2d"
    CalendarCompletionStatus.DONE.name -> "\u5df2\u5b8c\u6210"
    else -> status
}
