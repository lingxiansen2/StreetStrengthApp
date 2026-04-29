package com.codex.streetstrength.domain

import com.codex.streetstrength.data.local.DayTaskEntity
import com.codex.streetstrength.data.local.DayTaskWithDetails
import com.codex.streetstrength.data.local.ExerciseTemplateEntity
import com.codex.streetstrength.data.local.ExerciseVariantEntity
import com.codex.streetstrength.data.local.PlanDayEntity
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.local.SetLogEntity
import com.codex.streetstrength.data.local.TaskSetPlanEntity
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SourceType
import com.codex.streetstrength.data.model.TrainingOrderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingProgressCalculatorTest {

    @Test
    fun `returns next unfinished set in round-robin order`() {
        val snapshot = calculateTrainingProgress(
            dayPlan = samplePlan(),
            logs = listOf(
                SetLogEntity(
                    id = 1,
                    sessionId = 11,
                    taskId = 101,
                    setIndex = 1,
                    completedAt = 1_000L,
                ),
            ),
        )

        assertFalse(snapshot.isFinished)
        assertEquals(102L, snapshot.cursor?.task?.task?.id)
        assertEquals(1, snapshot.cursor?.setPlan?.setIndex)
        assertEquals(1, snapshot.cursor?.roundIndex)
        assertEquals(2, snapshot.cursor?.totalRounds)
        assertEquals(2, snapshot.cursor?.positionInRound)
        assertEquals(2, snapshot.cursor?.tasksInRound)
    }

    @Test
    fun `returns next unfinished set in sequential order`() {
        val snapshot = calculateTrainingProgress(
            dayPlan = samplePlan(),
            logs = listOf(
                SetLogEntity(
                    id = 1,
                    sessionId = 11,
                    taskId = 101,
                    setIndex = 1,
                    completedAt = 1_000L,
                ),
            ),
            orderMode = TrainingOrderMode.SEQUENTIAL,
        )

        assertFalse(snapshot.isFinished)
        assertEquals(101L, snapshot.cursor?.task?.task?.id)
        assertEquals(2, snapshot.cursor?.setPlan?.setIndex)
        assertEquals(2, snapshot.cursor?.roundIndex)
        assertEquals(2, snapshot.cursor?.totalRounds)
        assertEquals(1, snapshot.cursor?.positionInRound)
        assertEquals(2, snapshot.cursor?.tasksInRound)
    }

    @Test
    fun `marks finished when all sets are logged`() {
        val snapshot = calculateTrainingProgress(
            dayPlan = samplePlan(),
            logs = listOf(
                SetLogEntity(id = 1, sessionId = 11, taskId = 101, setIndex = 1, completedAt = 1_000L),
                SetLogEntity(id = 2, sessionId = 11, taskId = 101, setIndex = 2, completedAt = 2_000L),
                SetLogEntity(id = 3, sessionId = 11, taskId = 102, setIndex = 1, completedAt = 3_000L),
            ),
        )

        assertTrue(snapshot.isFinished)
        assertEquals(null, snapshot.cursor)
    }

    private fun samplePlan(): PlanDayWithTasks {
        val template = ExerciseTemplateEntity(
            id = 11,
            categoryId = 1,
            name = "引体向上",
            sourceType = SourceType.BUILTIN,
            supportsExternalLoad = true,
            defaultMetricType = MetricType.REPS,
        )
        val variant = ExerciseVariantEntity(
            id = 21,
            templateId = 11,
            name = "标准正握",
            metricType = MetricType.REPS,
        )
        return PlanDayWithTasks(
            day = PlanDayEntity(
                id = 1,
                weekId = 1,
                planDate = "2026-04-22",
                title = "周三",
            ),
            tasks = listOf(
                DayTaskWithDetails(
                    task = DayTaskEntity(
                        id = 101,
                        dayId = 1,
                        templateId = 11,
                        variantId = 21,
                        orderInDay = 1,
                        restSec = 90,
                    ),
                    template = template,
                    variant = variant,
                    setPlans = listOf(
                        TaskSetPlanEntity(id = 1001, taskId = 101, setIndex = 1, targetReps = 5),
                        TaskSetPlanEntity(id = 1002, taskId = 101, setIndex = 2, targetReps = 5),
                    ),
                ),
                DayTaskWithDetails(
                    task = DayTaskEntity(
                        id = 102,
                        dayId = 1,
                        templateId = 11,
                        variantId = 21,
                        orderInDay = 2,
                        restSec = 90,
                    ),
                    template = template,
                    variant = variant,
                    setPlans = listOf(
                        TaskSetPlanEntity(id = 1003, taskId = 102, setIndex = 1, targetReps = 3),
                    ),
                ),
            ),
        )
    }
}
