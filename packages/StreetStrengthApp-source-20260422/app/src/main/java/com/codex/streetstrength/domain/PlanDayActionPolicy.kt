package com.codex.streetstrength.domain

import com.codex.streetstrength.data.model.CalendarCompletionStatus
import java.time.LocalDate

enum class PlanDateTiming {
    PAST,
    TODAY,
    FUTURE,
}

data class PlanDayActionPolicy(
    val timing: PlanDateTiming,
    val hasTasks: Boolean,
    val completionStatus: String,
    val hasRecordedTraining: Boolean,
    val isCompletedTodayTestingAllowed: Boolean,
    val canStartTraining: Boolean,
    val canEditPlan: Boolean,
)

fun resolvePlanDayActionPolicy(
    selectedDate: LocalDate,
    hasTasks: Boolean,
    completionStatus: String?,
    today: LocalDate = LocalDate.now(),
    allowCompletedTodayTesting: Boolean = false,
): PlanDayActionPolicy {
    val resolvedStatus = completionStatus ?: CalendarCompletionStatus.EMPTY.name
    val timing = when {
        selectedDate.isBefore(today) -> PlanDateTiming.PAST
        selectedDate.isAfter(today) -> PlanDateTiming.FUTURE
        else -> PlanDateTiming.TODAY
    }
    val hasRecordedTraining = resolvedStatus == CalendarCompletionStatus.PARTIAL.name ||
        resolvedStatus == CalendarCompletionStatus.DONE.name
    val isCompletedTodayTestingAllowed = allowCompletedTodayTesting &&
        timing == PlanDateTiming.TODAY &&
        resolvedStatus == CalendarCompletionStatus.DONE.name

    return PlanDayActionPolicy(
        timing = timing,
        hasTasks = hasTasks,
        completionStatus = resolvedStatus,
        hasRecordedTraining = hasRecordedTraining,
        isCompletedTodayTestingAllowed = isCompletedTodayTestingAllowed,
        canStartTraining = hasTasks &&
            timing == PlanDateTiming.TODAY &&
            (resolvedStatus != CalendarCompletionStatus.DONE.name || isCompletedTodayTestingAllowed),
        canEditPlan = timing != PlanDateTiming.PAST &&
            (!hasRecordedTraining || isCompletedTodayTestingAllowed),
    )
}
