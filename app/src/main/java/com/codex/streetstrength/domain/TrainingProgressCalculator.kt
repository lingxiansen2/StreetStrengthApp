package com.codex.streetstrength.domain

import com.codex.streetstrength.data.local.DayTaskWithDetails
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.local.SetLogEntity
import com.codex.streetstrength.data.local.TaskSetPlanEntity

data class TrainingCursor(
    val task: DayTaskWithDetails,
    val setPlan: TaskSetPlanEntity,
    val isLastOverallSet: Boolean,
)

data class TrainingProgressSnapshot(
    val cursor: TrainingCursor?,
    val isFinished: Boolean,
)

fun calculateTrainingProgress(
    dayPlan: PlanDayWithTasks?,
    logs: List<SetLogEntity>,
): TrainingProgressSnapshot {
    if (dayPlan == null) return TrainingProgressSnapshot(cursor = null, isFinished = false)
    val tasks = dayPlan.tasks
        .sortedBy { it.task.orderInDay }
        .map { task -> task.copy(setPlans = task.setPlans.sortedBy { it.setIndex }) }
    if (tasks.isEmpty()) return TrainingProgressSnapshot(cursor = null, isFinished = false)

    val completedKeys = logs.map { "${it.taskId}:${it.setIndex}" }.toSet()
    tasks.forEachIndexed { taskIndex, task ->
        val nextSet = task.setPlans.firstOrNull { "${task.task.id}:${it.setIndex}" !in completedKeys }
        if (nextSet != null) {
            val hasRemainingAfter = tasks.drop(taskIndex).flatMapIndexed { offset, t ->
                val source = if (offset == 0) t.setPlans.filter { it.setIndex > nextSet.setIndex } else t.setPlans
                source.map { "${t.task.id}:${it.setIndex}" }
            }.any { it !in completedKeys }

            return TrainingProgressSnapshot(
                cursor = TrainingCursor(
                    task = task,
                    setPlan = nextSet,
                    isLastOverallSet = !hasRemainingAfter,
                ),
                isFinished = false,
            )
        }
    }
    return TrainingProgressSnapshot(cursor = null, isFinished = true)
}

private inline fun <T, R> List<T>.flatMapIndexed(transform: (Int, T) -> Iterable<R>): List<R> {
    val result = mutableListOf<R>()
    forEachIndexed { index, item -> result += transform(index, item) }
    return result
}

