package com.codex.streetstrength.domain

import com.codex.streetstrength.data.local.DayTaskWithDetails
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.local.SetLogEntity
import com.codex.streetstrength.data.local.TaskSetPlanEntity
import com.codex.streetstrength.data.model.TrainingOrderMode

data class TrainingCursor(
    val task: DayTaskWithDetails,
    val setPlan: TaskSetPlanEntity,
    val isLastOverallSet: Boolean,
    val roundIndex: Int,
    val totalRounds: Int,
    val positionInRound: Int,
    val tasksInRound: Int,
)

data class TrainingProgressSnapshot(
    val cursor: TrainingCursor?,
    val isFinished: Boolean,
)

fun calculateTrainingProgress(
    dayPlan: PlanDayWithTasks?,
    logs: List<SetLogEntity>,
    orderMode: TrainingOrderMode = TrainingOrderMode.CIRCUIT,
): TrainingProgressSnapshot {
    if (dayPlan == null) return TrainingProgressSnapshot(cursor = null, isFinished = false)
    val tasks = dayPlan.tasks
        .sortedBy { it.task.orderInDay }
        .map { task -> task.copy(setPlans = task.setPlans.sortedBy { it.setIndex }) }
    if (tasks.isEmpty()) return TrainingProgressSnapshot(cursor = null, isFinished = false)

    val completedKeys = logs.map { "${it.taskId}:${it.setIndex}" }.toSet()
    val planSequence = when (orderMode) {
        TrainingOrderMode.CIRCUIT -> buildCircuitSequence(tasks)
        TrainingOrderMode.SEQUENTIAL -> buildSequentialSequence(tasks)
    }

    planSequence.forEachIndexed { sequenceIndex, slot ->
        val key = "${slot.task.task.id}:${slot.setPlan.setIndex}"
        if (key !in completedKeys) {
            val hasRemainingAfter = planSequence.drop(sequenceIndex + 1)
                .any { next -> "${next.task.task.id}:${next.setPlan.setIndex}" !in completedKeys }
            return TrainingProgressSnapshot(
                cursor = TrainingCursor(
                    task = slot.task,
                    setPlan = slot.setPlan,
                    isLastOverallSet = !hasRemainingAfter,
                    roundIndex = slot.roundIndex,
                    totalRounds = slot.totalRounds,
                    positionInRound = slot.positionInRound,
                    tasksInRound = slot.tasksInRound,
                ),
                isFinished = false,
            )
        }
    }
    return TrainingProgressSnapshot(cursor = null, isFinished = true)
}

private fun buildCircuitSequence(tasks: List<DayTaskWithDetails>): List<TrainingSlot> {
    val totalRounds = tasks.maxOf { task -> task.setPlans.maxOfOrNull { it.setIndex } ?: 0 }
    val sequence = mutableListOf<TrainingSlot>()
    for (round in 1..totalRounds) {
        val roundSlots = tasks.mapNotNull { task ->
            task.setPlans.firstOrNull { it.setIndex == round }?.let { setPlan ->
                TrainingSlot(
                    task = task,
                    setPlan = setPlan,
                    roundIndex = round,
                    totalRounds = totalRounds,
                )
            }
        }
        roundSlots.forEachIndexed { index, slot ->
            sequence += slot.copy(
                positionInRound = index + 1,
                tasksInRound = roundSlots.size,
            )
        }
    }
    return sequence
}

private fun buildSequentialSequence(tasks: List<DayTaskWithDetails>): List<TrainingSlot> {
    return tasks.flatMapIndexed { taskIndex, task ->
        task.setPlans.map { setPlan ->
            TrainingSlot(
                task = task,
                setPlan = setPlan,
                roundIndex = setPlan.setIndex,
                totalRounds = task.setPlans.size,
                positionInRound = taskIndex + 1,
                tasksInRound = tasks.size,
            )
        }
    }
}

private data class TrainingSlot(
    val task: DayTaskWithDetails,
    val setPlan: TaskSetPlanEntity,
    val roundIndex: Int,
    val totalRounds: Int,
    val positionInRound: Int = 1,
    val tasksInRound: Int = 1,
)
