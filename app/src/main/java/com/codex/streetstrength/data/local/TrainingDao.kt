package com.codex.streetstrength.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {
    @Query("SELECT COUNT(*) FROM exercise_templates WHERE sourceType = 'BUILTIN'")
    suspend fun countBuiltInTemplates(): Int

    @Query("SELECT COUNT(*) FROM goals")
    suspend fun countGoals(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: PlanCycleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeek(week: PlanWeekEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(day: PlanDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ExerciseCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ExerciseTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ExerciseVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayTask(task: DayTaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskSetPlans(plans: List<TaskSetPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetLog(log: SetLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestTimer(timer: ActiveRestTimerEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Update
    suspend fun updateRestTimer(timer: ActiveRestTimerEntity)

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM plan_cycles WHERE goalId = :goalId ORDER BY createdAt DESC, sortOrder DESC")
    fun observeCyclesForGoal(goalId: Long): Flow<List<PlanCycleEntity>>

    @Query("SELECT * FROM calendar_day_summary WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeMonthSummary(from: String, to: String): Flow<List<CalendarDaySummary>>

    @Query(
        """
        WITH day_range AS (
            SELECT id, planDate
            FROM plan_days
            WHERE planDate BETWEEN :from AND :to
        ),
        plan_stats AS (
            SELECT
                COUNT(DISTINCT CASE WHEN dt.id IS NOT NULL THEN dr.id END) AS plannedDays,
                COUNT(DISTINCT dt.id) AS plannedTasks,
                COUNT(DISTINCT tsp.id) AS plannedSets
            FROM day_range dr
            LEFT JOIN day_tasks dt ON dt.dayId = dr.id
            LEFT JOIN task_set_plans tsp ON tsp.taskId = dt.id
        ),
        session_stats AS (
            SELECT
                COUNT(DISTINCT CASE WHEN sl.id IS NOT NULL THEN ws.dayId END) AS trainedDays,
                COUNT(DISTINCT CASE WHEN ws.status = 'COMPLETED' THEN ws.id END) AS completedSessions,
                COUNT(DISTINCT sl.id) AS completedSets,
                COALESCE(SUM(CASE WHEN sl.isSkipped = 0 THEN sl.actualReps ELSE 0 END), 0) AS totalReps,
                COALESCE(SUM(CASE WHEN sl.isSkipped = 0 THEN sl.actualHoldSec ELSE 0 END), 0) AS totalHoldSec,
                COALESCE(SUM(CASE WHEN sl.isSkipped = 0 THEN sl.actualLoadKg ELSE 0 END), 0.0) AS totalExternalLoadKg
            FROM day_range dr
            LEFT JOIN workout_sessions ws ON ws.dayId = dr.id AND ws.status != 'ABANDONED'
            LEFT JOIN set_logs sl ON sl.sessionId = ws.id
        ),
        completion_stats AS (
            SELECT COUNT(*) AS completedDays
            FROM calendar_day_summary
            WHERE date BETWEEN :from AND :to AND completionStatus = 'DONE'
        )
        SELECT
            COALESCE(plan_stats.plannedDays, 0) AS plannedDays,
            COALESCE(session_stats.trainedDays, 0) AS trainedDays,
            COALESCE(completion_stats.completedDays, 0) AS completedDays,
            COALESCE(plan_stats.plannedTasks, 0) AS plannedTasks,
            COALESCE(plan_stats.plannedSets, 0) AS plannedSets,
            COALESCE(session_stats.completedSets, 0) AS completedSets,
            COALESCE(session_stats.completedSessions, 0) AS completedSessions,
            COALESCE(session_stats.totalReps, 0) AS totalReps,
            COALESCE(session_stats.totalHoldSec, 0) AS totalHoldSec,
            COALESCE(session_stats.totalExternalLoadKg, 0.0) AS totalExternalLoadKg
        FROM plan_stats, session_stats, completion_stats
        """,
    )
    fun observePeriodOverview(from: String, to: String): Flow<PeriodOverviewSummary>

    @Transaction
    @Query("SELECT * FROM plan_days WHERE planDate = :date LIMIT 1")
    fun observePlanDay(date: String): Flow<PlanDayWithTasks?>

    @Transaction
    @Query("SELECT * FROM exercise_categories ORDER BY sortOrder ASC")
    fun observeExerciseCatalog(): Flow<List<ExerciseCategoryWithTemplates>>

    @Query("SELECT * FROM plan_days WHERE planDate = :date LIMIT 1")
    suspend fun getPlanDayByDate(date: String): PlanDayEntity?

    @Query("SELECT * FROM plan_weeks WHERE cycleId = :cycleId AND startDate = :startDate LIMIT 1")
    suspend fun getWeekByStart(cycleId: Long, startDate: String): PlanWeekEntity?

    @Query("SELECT * FROM day_tasks WHERE dayId = :dayId ORDER BY orderInDay ASC")
    suspend fun getDayTasks(dayId: Long): List<DayTaskEntity>

    @Query("SELECT * FROM task_set_plans WHERE taskId = :taskId ORDER BY setIndex ASC")
    suspend fun getSetPlansForTask(taskId: Long): List<TaskSetPlanEntity>

    @Query("SELECT COALESCE(MAX(orderInDay), 0) FROM day_tasks WHERE dayId = :dayId")
    suspend fun getMaxTaskOrder(dayId: Long): Int

    @Query("DELETE FROM task_set_plans WHERE taskId = :taskId")
    suspend fun deleteTaskSetPlans(taskId: Long)

    @Query("DELETE FROM day_tasks WHERE id = :taskId")
    suspend fun deleteDayTask(taskId: Long)

    @Query("DELETE FROM active_rest_timers WHERE sessionId = :sessionId")
    suspend fun deleteRestTimersForSession(sessionId: Long)

    @Query("SELECT * FROM workout_sessions WHERE dayId = :dayId ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestSessionForDay(dayId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE dayId = :dayId ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestSession(dayId: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    fun observeSetLogs(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM active_rest_timers WHERE sessionId = :sessionId AND state = 'RUNNING' ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveRestTimer(sessionId: Long): Flow<ActiveRestTimerEntity?>

    @Query("SELECT * FROM active_rest_timers WHERE id = :timerId LIMIT 1")
    suspend fun getRestTimer(timerId: Long): ActiveRestTimerEntity?

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId AND taskId = :taskId AND setIndex = :setIndex LIMIT 1")
    suspend fun getSetLog(sessionId: Long, taskId: Long, setIndex: Int): SetLogEntity?

    @Query("SELECT * FROM plan_cycles ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestCycle(): PlanCycleEntity?

    @Query("SELECT * FROM plan_cycles WHERE id = :cycleId LIMIT 1")
    suspend fun getCycleById(cycleId: Long): PlanCycleEntity?

    @Query("SELECT * FROM goals ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestGoal(): GoalEntity?

    @Query("SELECT * FROM exercise_categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryByType(type: com.codex.streetstrength.data.model.CategoryType): ExerciseCategoryEntity?

    @Query("SELECT * FROM plan_days WHERE weekId = :weekId ORDER BY planDate ASC")
    suspend fun getPlanDaysForWeek(weekId: Long): List<PlanDayEntity>
}
