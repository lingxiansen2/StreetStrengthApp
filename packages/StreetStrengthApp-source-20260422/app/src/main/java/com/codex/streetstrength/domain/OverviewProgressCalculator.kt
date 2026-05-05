package com.codex.streetstrength.domain

import com.codex.streetstrength.data.local.PeriodOverviewSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

data class OverviewTrendRange(
    val start: LocalDate,
    val end: LocalDate,
)

data class OverviewTrendPoint(
    val range: OverviewTrendRange,
    val summary: PeriodOverviewSummary,
)

fun buildTrailingWeekRanges(
    anchorDate: LocalDate,
    weekCount: Int = 4,
): List<OverviewTrendRange> {
    require(weekCount > 0) { "weekCount must be positive." }
    val currentWeekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (weekCount - 1 downTo 0).map { weeksAgo ->
        val start = currentWeekStart.minusWeeks(weeksAgo.toLong())
        OverviewTrendRange(start = start, end = start.plusDays(6))
    }
}

fun calculateTrendDeltaPercent(
    current: Int,
    previous: Int,
): Int? {
    if (previous <= 0) return null
    return (((current - previous).toFloat() / previous.toFloat()) * 100f).roundToInt()
}
