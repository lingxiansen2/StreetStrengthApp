package com.codex.streetstrength.data.repository

import androidx.room.withTransaction
import com.codex.streetstrength.data.local.ActiveRestTimerEntity
import com.codex.streetstrength.data.local.AppDatabase
import com.codex.streetstrength.data.local.CalendarDaySummary
import com.codex.streetstrength.data.local.DayTaskEntity
import com.codex.streetstrength.data.local.ExerciseCategoryEntity
import com.codex.streetstrength.data.local.ExerciseCategoryWithTemplates
import com.codex.streetstrength.data.local.ExerciseTemplateEntity
import com.codex.streetstrength.data.local.ExerciseVariantEntity
import com.codex.streetstrength.data.local.GoalEntity
import com.codex.streetstrength.data.local.PeriodOverviewSummary
import com.codex.streetstrength.data.local.PlanCycleEntity
import com.codex.streetstrength.data.local.PlanDayEntity
import com.codex.streetstrength.data.local.PlanDayWithTasks
import com.codex.streetstrength.data.local.PlanWeekEntity
import com.codex.streetstrength.data.local.SetLogEntity
import com.codex.streetstrength.data.local.TaskSetPlanEntity
import com.codex.streetstrength.data.local.WorkoutSessionEntity
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.GoalStatus
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SessionStatus
import com.codex.streetstrength.data.model.SourceType
import com.codex.streetstrength.data.model.TimerState
import com.codex.streetstrength.data.preferences.PreferencesRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow

data class DayTaskDraft(
    val date: String,
    val cycleId: Long? = null,
    val templateId: Long,
    val variantId: Long,
    val sets: Int,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val plannedLoadKg: Double = 0.0,
    val restSec: Int,
    val note: String? = null,
)

data class CustomExerciseDraft(
    val categoryType: CategoryType,
    val name: String,
    val variantName: String = "Standard",
    val supportsExternalLoad: Boolean,
    val defaultMetricType: MetricType,
    val cue: String? = null,
)

data class SetCompletionInput(
    val sessionId: Long,
    val taskId: Long,
    val setIndex: Int,
    val actualReps: Int? = null,
    val actualHoldSec: Int? = null,
    val actualLoadKg: Double = 0.0,
    val restSec: Int,
    val shouldStartRest: Boolean,
)

class TrainingRepository(
    private val database: AppDatabase,
    private val preferencesRepository: PreferencesRepository,
) {
    private val dao = database.trainingDao()

    fun observeGoals(): Flow<List<GoalEntity>> = dao.observeGoals()

    fun observeCycles(goalId: Long): Flow<List<PlanCycleEntity>> = dao.observeCyclesForGoal(goalId)

    fun observeMonthSummary(from: String, to: String): Flow<List<CalendarDaySummary>> =
        dao.observeMonthSummary(from, to)

    fun observePeriodOverview(from: String, to: String): Flow<PeriodOverviewSummary> =
        dao.observePeriodOverview(from, to)

    fun observeDayPlan(date: String): Flow<PlanDayWithTasks?> = dao.observePlanDay(date)

    fun observeExerciseCatalog(): Flow<List<ExerciseCategoryWithTemplates>> = dao.observeExerciseCatalog()

    fun observeLatestSession(dayId: Long): Flow<WorkoutSessionEntity?> = dao.observeLatestSession(dayId)

    fun observeSetLogs(sessionId: Long): Flow<List<SetLogEntity>> = dao.observeSetLogs(sessionId)

    fun observeActiveRestTimer(sessionId: Long): Flow<ActiveRestTimerEntity?> = dao.observeActiveRestTimer(sessionId)

    suspend fun seedBuiltIns() {
        database.withTransaction {
            if (dao.countBuiltInTemplates() == 0) {
                val pullId = dao.insertCategory(
                    ExerciseCategoryEntity(
                        type = CategoryType.PULL,
                        name = "拉力训练",
                        sortOrder = 0,
                    ),
                )
                val pushId = dao.insertCategory(
                    ExerciseCategoryEntity(
                        type = CategoryType.PUSH,
                        name = "推力训练",
                        sortOrder = 1,
                    ),
                )

                val pullUpId = dao.insertTemplate(
                    ExerciseTemplateEntity(
                        categoryId = pullId,
                        name = "引体向上",
                        sourceType = SourceType.BUILTIN,
                        supportsExternalLoad = true,
                        defaultMetricType = MetricType.REPS,
                        cue = "胸骨找杠，核心收紧，底部不过度松肩",
                        sortOrder = 0,
                    ),
                )

                listOf(
                    "标准正握" to MetricType.REPS,
                    "标准反握" to MetricType.REPS,
                    "正手窄握" to MetricType.REPS,
                    "正手宽握" to MetricType.REPS,
                    "反手窄握" to MetricType.REPS,
                    "反手宽握" to MetricType.REPS,
                    "正手前半程引体" to MetricType.REPS,
                    "正手后半程引体" to MetricType.REPS,
                    "引体顶端锁定 x 秒 + 离心下方" to MetricType.HOLD_SECONDS_PLUS_ECCENTRIC,
                    "引体顶端锁定到力竭" to MetricType.HOLD_TO_FAILURE,
                ).forEachIndexed { index, (name, metricType) ->
                    dao.insertVariant(
                        ExerciseVariantEntity(
                            templateId = pullUpId,
                            name = name,
                            metricType = metricType,
                            cue = when (metricType) {
                                MetricType.HOLD_SECONDS_PLUS_ECCENTRIC -> "顶端锁定后慢速离心"
                                MetricType.HOLD_TO_FAILURE -> "保持顶端锁定直到失速"
                                else -> null
                            },
                            sortOrder = index,
                        ),
                    )
                }

                val weightedPushUpId = dao.insertTemplate(
                    ExerciseTemplateEntity(
                        categoryId = pushId,
                        name = "腰间俯卧撑",
                        sourceType = SourceType.BUILTIN,
                        supportsExternalLoad = true,
                        defaultMetricType = MetricType.REPS,
                        cue = "躯干成整体，肘部自然向后",
                        sortOrder = 0,
                    ),
                )
                dao.insertVariant(
                    ExerciseVariantEntity(
                        templateId = weightedPushUpId,
                        name = "标准",
                        metricType = MetricType.REPS,
                        sortOrder = 0,
                    ),
                )

                val handstandPushUpId = dao.insertTemplate(
                    ExerciseTemplateEntity(
                        categoryId = pushId,
                        name = "倒立撑",
                        sourceType = SourceType.BUILTIN,
                        supportsExternalLoad = false,
                        defaultMetricType = MetricType.REPS,
                        cue = "保持中立，避免塌腰",
                        sortOrder = 1,
                    ),
                )
                dao.insertVariant(
                    ExerciseVariantEntity(
                        templateId = handstandPushUpId,
                        name = "标准",
                        metricType = MetricType.REPS,
                        sortOrder = 0,
                    ),
                )

                val shoulderRushId = dao.insertTemplate(
                    ExerciseTemplateEntity(
                        categoryId = pushId,
                        name = "冲肩",
                        sourceType = SourceType.BUILTIN,
                        supportsExternalLoad = false,
                        defaultMetricType = MetricType.HOLD_SECONDS,
                        cue = "肩胛主动前顶，保持稳定输出",
                        sortOrder = 2,
                    ),
                )
                dao.insertVariant(
                    ExerciseVariantEntity(
                        templateId = shoulderRushId,
                        name = "标准",
                        metricType = MetricType.HOLD_SECONDS,
                        sortOrder = 0,
                    ),
                )
            }

            if (dao.countGoals() == 0) {
                val today = LocalDate.now()
                val goalId = dao.insertGoal(
                    GoalEntity(
                        title = "街头健身基础力量",
                        note = "以拉力/推力的基础力量提升为主",
                        startDate = today.toString(),
                        targetDate = today.plusWeeks(12).toString(),
                        status = GoalStatus.ACTIVE,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                val cycleId = dao.insertCycle(
                    PlanCycleEntity(
                        goalId = goalId,
                        name = "当前周期",
                        startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
                        endDate = today.plusWeeks(12).toString(),
                        sortOrder = 0,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                preferencesRepository.setActiveGoalId(goalId)
                preferencesRepository.setActiveCycleId(cycleId)
            }
        }
    }

    suspend fun createGoal(
        title: String,
        targetDate: String?,
        note: String? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertGoal(
            GoalEntity(
                title = title,
                note = note,
                startDate = LocalDate.now().toString(),
                targetDate = targetDate,
                status = GoalStatus.ACTIVE,
                createdAt = now,
                updatedAt = now,
            ),
        )
        preferencesRepository.setActiveGoalId(id)
        return id
    }

    suspend fun createCycle(
        goalId: Long,
        name: String,
        startDate: String,
        endDate: String?,
    ): Long {
        val cycleId = dao.insertCycle(
            PlanCycleEntity(
                goalId = goalId,
                name = name,
                startDate = startDate,
                endDate = endDate,
                sortOrder = 0,
                createdAt = System.currentTimeMillis(),
            ),
        )
        preferencesRepository.setActiveCycleId(cycleId)
        return cycleId
    }

    suspend fun setActiveGoal(goalId: Long) {
        preferencesRepository.setActiveGoalId(goalId)
    }

    suspend fun setActiveCycle(cycleId: Long) {
        preferencesRepository.setActiveCycleId(cycleId)
    }

    suspend fun createCustomExercise(input: CustomExerciseDraft) {
        database.withTransaction {
            val categoryId = dao.getCategoryByType(input.categoryType)?.id
                ?: error("Category ${input.categoryType} is missing. Seed should have created it.")
            val templateId = dao.insertTemplate(
                ExerciseTemplateEntity(
                    categoryId = categoryId,
                    name = input.name,
                    sourceType = SourceType.CUSTOM,
                    supportsExternalLoad = input.supportsExternalLoad,
                    defaultMetricType = input.defaultMetricType,
                    cue = input.cue,
                    sortOrder = 999,
                ),
            )
            dao.insertVariant(
                ExerciseVariantEntity(
                    templateId = templateId,
                    name = "默认",
                    metricType = input.defaultMetricType,
                    cue = input.cue,
                    sortOrder = 0,
                ),
            )
        }
    }

    suspend fun createOrUpdateDayTask(input: DayTaskDraft): Long {
        return database.withTransaction {
            val cycleId = input.cycleId ?: dao.getLatestCycle()?.id ?: seedCycleAndReturn()
            val planDay = ensurePlanDay(input.date, cycleId)
            val nextOrder = dao.getMaxTaskOrder(planDay.id) + 1
            val taskId = dao.insertDayTask(
                DayTaskEntity(
                    dayId = planDay.id,
                    templateId = input.templateId,
                    variantId = input.variantId,
                    orderInDay = nextOrder,
                    plannedLoadKg = input.plannedLoadKg,
                    restSec = input.restSec,
                    note = input.note,
                ),
            )
            dao.insertTaskSetPlans(
                (1..input.sets).map { setNumber ->
                    TaskSetPlanEntity(
                        taskId = taskId,
                        setIndex = setNumber,
                        targetReps = input.targetReps,
                        targetHoldSec = input.targetHoldSec,
                        targetLoadKg = input.plannedLoadKg,
                    )
                },
            )
            preferencesRepository.setRecentLoadKg(input.plannedLoadKg)
            taskId
        }
    }

    suspend fun deleteDayTask(taskId: Long) {
        database.withTransaction {
            dao.deleteTaskSetPlans(taskId)
            dao.deleteDayTask(taskId)
        }
    }

    suspend fun duplicatePlanDay(sourceDate: String, targetDate: String) {
        database.withTransaction {
            val sourceDay = dao.getPlanDayByDate(sourceDate) ?: return@withTransaction
            val sourceTasks = dao.getDayTasks(sourceDay.id)
            if (sourceTasks.isEmpty()) return@withTransaction

            val latestCycleId = dao.getLatestCycle()?.id ?: return@withTransaction
            val targetDay = ensurePlanDay(targetDate, latestCycleId)
            dao.getDayTasks(targetDay.id).forEach { existing ->
                dao.deleteTaskSetPlans(existing.id)
                dao.deleteDayTask(existing.id)
            }

            sourceTasks.forEach { task ->
                val newTaskId = dao.insertDayTask(
                    task.copy(
                        id = 0,
                        dayId = targetDay.id,
                    ),
                )
                val plans = dao.getSetPlansForTask(task.id)
                dao.insertTaskSetPlans(
                    plans.map { it.copy(id = 0, taskId = newTaskId) },
                )
            }
        }
    }

    suspend fun duplicatePreviousWeekToCurrent(referenceDate: String) {
        val current = LocalDate.parse(referenceDate)
        val currentWeekStart = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val previousWeekStart = currentWeekStart.minusWeeks(1)
        repeat(7) { index ->
            duplicatePlanDay(
                sourceDate = previousWeekStart.plusDays(index.toLong()).toString(),
                targetDate = currentWeekStart.plusDays(index.toLong()).toString(),
            )
        }
    }

    suspend fun ensureSessionForDate(date: String): Pair<PlanDayEntity, WorkoutSessionEntity?> {
        return database.withTransaction {
            val day = dao.getPlanDayByDate(date) ?: return@withTransaction ensureEmptyDay(date)
            val hasTasks = dao.getDayTasks(day.id).isNotEmpty()
            if (!hasTasks) {
                day to null
            } else {
                val latest = dao.getLatestSessionForDay(day.id)
                if (latest?.status == SessionStatus.IN_PROGRESS) {
                    day to latest
                } else {
                    val now = System.currentTimeMillis()
                    val sessionId = dao.insertWorkoutSession(
                        WorkoutSessionEntity(
                            dayId = day.id,
                            startedAt = now,
                            status = SessionStatus.IN_PROGRESS,
                        ),
                    )
                    day to WorkoutSessionEntity(
                        id = sessionId,
                        dayId = day.id,
                        startedAt = now,
                        status = SessionStatus.IN_PROGRESS,
                    )
                }
            }
        }
    }

    suspend fun completeSet(input: SetCompletionInput): ActiveRestTimerEntity? {
        return database.withTransaction {
            val existing = dao.getSetLog(input.sessionId, input.taskId, input.setIndex)
            if (existing == null) {
                dao.insertSetLog(
                    SetLogEntity(
                        sessionId = input.sessionId,
                        taskId = input.taskId,
                        setIndex = input.setIndex,
                        actualReps = input.actualReps,
                        actualHoldSec = input.actualHoldSec,
                        actualLoadKg = input.actualLoadKg,
                        completedAt = System.currentTimeMillis(),
                        isSkipped = false,
                    ),
                )
            }

            dao.deleteRestTimersForSession(input.sessionId)

            if (!input.shouldStartRest || input.restSec <= 0) {
                null
            } else {
                val timer = ActiveRestTimerEntity(
                    sessionId = input.sessionId,
                    taskId = input.taskId,
                    setIndex = input.setIndex,
                    durationSec = input.restSec,
                    endElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime() + input.restSec * 1_000L,
                    state = TimerState.RUNNING,
                    createdAt = System.currentTimeMillis(),
                )
                val timerId = dao.insertRestTimer(timer)
                timer.copy(id = timerId)
            }
        }
    }

    suspend fun skipSet(
        sessionId: Long,
        taskId: Long,
        setIndex: Int,
    ) {
        database.withTransaction {
            val existing = dao.getSetLog(sessionId, taskId, setIndex)
            if (existing == null) {
                dao.insertSetLog(
                    SetLogEntity(
                        sessionId = sessionId,
                        taskId = taskId,
                        setIndex = setIndex,
                        completedAt = System.currentTimeMillis(),
                        isSkipped = true,
                    ),
                )
            }
        }
    }

    suspend fun clearRestTimer(timerId: Long?) {
        database.withTransaction {
            timerId?.let { id ->
                dao.getRestTimer(id)?.let { timer ->
                    dao.updateRestTimer(timer.copy(state = TimerState.CANCELLED))
                }
            }
        }
    }

    suspend fun markRestTimerFired(timerId: Long) {
        database.withTransaction {
            dao.getRestTimer(timerId)?.let { timer ->
                dao.updateRestTimer(timer.copy(state = TimerState.FIRED))
            }
        }
    }

    suspend fun completeSession(dayId: Long) {
        database.withTransaction {
            val session = dao.getLatestSessionForDay(dayId) ?: return@withTransaction
            dao.updateSession(
                session.copy(
                    status = SessionStatus.COMPLETED,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
            dao.deleteRestTimersForSession(session.id)
        }
    }

    suspend fun abandonSession(dayId: Long) {
        database.withTransaction {
            val session = dao.getLatestSessionForDay(dayId) ?: return@withTransaction
            dao.updateSession(
                session.copy(
                    status = SessionStatus.ABANDONED,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
            dao.deleteRestTimersForSession(session.id)
        }
    }

    private suspend fun ensurePlanDay(date: String, cycleId: Long): PlanDayEntity {
        dao.getPlanDayByDate(date)?.let { return it }

        val targetDate = LocalDate.parse(date)
        val weekStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val cycleWeek = dao.getWeekByStart(cycleId, weekStart.toString()) ?: run {
            val cycleStart = LocalDate.parse(
                dao.getCycleById(cycleId)?.startDate ?: weekStart.toString(),
            )
            val weekIndex = (java.time.temporal.ChronoUnit.WEEKS.between(cycleStart, weekStart) + 1).toInt()
                .coerceAtLeast(1)
            val weekId = dao.insertWeek(
                PlanWeekEntity(
                    cycleId = cycleId,
                    weekIndex = weekIndex,
                    startDate = weekStart.toString(),
                    endDate = weekEnd.toString(),
                ),
            )
            PlanWeekEntity(
                id = weekId,
                cycleId = cycleId,
                weekIndex = weekIndex,
                startDate = weekStart.toString(),
                endDate = weekEnd.toString(),
            )
        }

        val dayId = dao.insertDay(
            PlanDayEntity(
                weekId = cycleWeek.id,
                planDate = date,
                title = targetDate.dayOfWeek.name,
            ),
        )
        return PlanDayEntity(
            id = dayId,
            weekId = cycleWeek.id,
            planDate = date,
            title = targetDate.dayOfWeek.name,
        )
    }

    private suspend fun ensureEmptyDay(date: String): Pair<PlanDayEntity, WorkoutSessionEntity?> {
        val cycleId = dao.getLatestCycle()?.id ?: seedCycleAndReturn()
        return ensurePlanDay(date, cycleId) to null
    }

    private suspend fun seedCycleAndReturn(): Long {
        val goalId = dao.getLatestGoal()?.id ?: createGoal(title = "街头健身基础力量", targetDate = null)
        return createCycle(
            goalId = goalId,
            name = "当前周期",
            startDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
            endDate = LocalDate.now().plusWeeks(12).toString(),
        )
    }
}
