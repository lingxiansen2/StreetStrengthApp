package com.codex.streetstrength.data.local

import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.codex.streetstrength.data.model.CalendarCompletionStatus
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.GoalStatus
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SessionStatus
import com.codex.streetstrength.data.model.SourceType
import com.codex.streetstrength.data.model.TimerState

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val startDate: String,
    val targetDate: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "plan_cycles",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("goalId")],
)
data class PlanCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val name: String,
    val startDate: String,
    val endDate: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "plan_weeks",
    foreignKeys = [
        ForeignKey(
            entity = PlanCycleEntity::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cycleId"), Index(value = ["cycleId", "weekIndex"], unique = true)],
)
data class PlanWeekEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long,
    val weekIndex: Int,
    val focus: String? = null,
    val startDate: String,
    val endDate: String,
)

@Entity(
    tableName = "plan_days",
    foreignKeys = [
        ForeignKey(
            entity = PlanWeekEntity::class,
            parentColumns = ["id"],
            childColumns = ["weekId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("weekId"), Index(value = ["planDate"], unique = true)],
)
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekId: Long,
    val planDate: String,
    val title: String,
    val note: String? = null,
)

@Entity(
    tableName = "exercise_categories",
    indices = [Index(value = ["type"], unique = true)],
)
data class ExerciseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: CategoryType,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "exercise_templates",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ExerciseTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sourceType: SourceType,
    val supportsExternalLoad: Boolean,
    val defaultMetricType: MetricType,
    val cue: String? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "exercise_variants",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId"), Index(value = ["templateId", "name"], unique = true)],
)
data class ExerciseVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val name: String,
    val metricType: MetricType,
    val cue: String? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "day_tasks",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExerciseVariantEntity::class,
            parentColumns = ["id"],
            childColumns = ["variantId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("dayId"),
        Index("templateId"),
        Index("variantId"),
        Index(value = ["dayId", "orderInDay"], unique = true),
    ],
)
data class DayTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val templateId: Long,
    val variantId: Long,
    val orderInDay: Int,
    val plannedLoadKg: Double = 0.0,
    val restSec: Int,
    val note: String? = null,
)

@Entity(
    tableName = "task_set_plans",
    foreignKeys = [
        ForeignKey(
            entity = DayTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId"), Index(value = ["taskId", "setIndex"], unique = true)],
)
data class TaskSetPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val setIndex: Int,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val targetLoadKg: Double = 0.0,
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dayId")],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DayTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("taskId"), Index(value = ["sessionId", "taskId", "setIndex"], unique = true)],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskId: Long,
    val setIndex: Int,
    val actualReps: Int? = null,
    val actualHoldSec: Int? = null,
    val actualLoadKg: Double = 0.0,
    val completedAt: Long,
    val isSkipped: Boolean = false,
)

@Entity(
    tableName = "active_rest_timers",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DayTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("taskId"), Index("state")],
)
data class ActiveRestTimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskId: Long,
    val setIndex: Int,
    val durationSec: Int,
    val endElapsedRealtimeMs: Long,
    val state: TimerState = TimerState.RUNNING,
    val createdAt: Long,
)

@DatabaseView(
    viewName = "calendar_day_summary",
    value = """
        SELECT
            pd.planDate AS date,
            CASE WHEN COUNT(DISTINCT dt.id) > 0 THEN 1 ELSE 0 END AS hasPlan,
            COUNT(DISTINCT dt.id) AS taskCount,
            COUNT(DISTINCT sl.id) AS completedSetCount,
            CASE
                WHEN COUNT(DISTINCT dt.id) = 0 THEN 'EMPTY'
                WHEN COUNT(DISTINCT tsp.id) = 0 THEN 'PLANNED'
                WHEN COUNT(DISTINCT sl.id) = 0 THEN 'PLANNED'
                WHEN COUNT(DISTINCT sl.id) < COUNT(DISTINCT tsp.id) THEN 'PARTIAL'
                ELSE 'DONE'
            END AS completionStatus,
            MIN(et.name || ' · ' || ev.name) AS primaryLabel
        FROM plan_days pd
        LEFT JOIN day_tasks dt ON dt.dayId = pd.id
        LEFT JOIN task_set_plans tsp ON tsp.taskId = dt.id
        LEFT JOIN workout_sessions ws ON ws.dayId = pd.id AND ws.status != 'ABANDONED'
        LEFT JOIN set_logs sl ON sl.sessionId = ws.id AND sl.taskId = dt.id
        LEFT JOIN exercise_templates et ON et.id = dt.templateId
        LEFT JOIN exercise_variants ev ON ev.id = dt.variantId
        GROUP BY pd.id
    """,
)
data class CalendarDaySummary(
    val date: String,
    val hasPlan: Boolean,
    val taskCount: Int,
    val completedSetCount: Int,
    val completionStatus: String = CalendarCompletionStatus.EMPTY.name,
    val primaryLabel: String? = null,
)

