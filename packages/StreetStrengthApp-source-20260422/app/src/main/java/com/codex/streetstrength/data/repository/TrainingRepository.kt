package com.codex.streetstrength.data.repository

import androidx.room.withTransaction
import com.codex.streetstrength.data.local.ActiveRestTimerEntity
import com.codex.streetstrength.data.local.AppDatabase
import com.codex.streetstrength.data.local.CalendarDaySummary
import com.codex.streetstrength.data.local.DayTaskEntity
import com.codex.streetstrength.data.local.ExerciseCategoryEntity
import com.codex.streetstrength.data.local.ExerciseCategoryWithTemplates
import com.codex.streetstrength.data.local.ExerciseTemplateEntity
import com.codex.streetstrength.data.local.ExerciseVolumeSummary
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
import com.codex.streetstrength.data.model.TrainingOrderMode
import com.codex.streetstrength.data.model.buildTrainingOrderModeNote
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.preferences.UserPreferences
import com.codex.streetstrength.debug.PlanTestingSwitch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

data class DayTaskDraft(
    val date: String,
    val cycleId: Long? = null,
    val templateId: Long,
    val variantId: Long,
    val sets: Int,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val targetDropPerSet: Int = 0,
    val plannedLoadKg: Double = 0.0,
    val restSec: Int,
    val note: String? = null,
)

data class DayTaskEditDraft(
    val taskId: Long,
    val templateId: Long,
    val variantId: Long,
    val sets: Int,
    val targetReps: Int? = null,
    val targetHoldSec: Int? = null,
    val targetDropPerSet: Int = 0,
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

data class BackupRestoreResult(
    val formatVersion: Int,
    val databaseVersion: Int,
    val restoredGoals: Int,
    val restoredPlanDays: Int,
    val restoredDayTasks: Int,
    val restoredTaskSetPlans: Int,
    val restoredSessions: Int,
    val restoredSetLogs: Int,
    val restoredRestTimers: Int,
)

private data class BackupSnapshot(
    val formatVersion: Int,
    val databaseVersion: Int,
    val preferences: UserPreferences,
    val goals: List<GoalEntity>,
    val cycles: List<PlanCycleEntity>,
    val weeks: List<PlanWeekEntity>,
    val planDays: List<PlanDayEntity>,
    val categories: List<ExerciseCategoryEntity>,
    val templates: List<ExerciseTemplateEntity>,
    val variants: List<ExerciseVariantEntity>,
    val dayTasks: List<DayTaskEntity>,
    val taskSetPlans: List<TaskSetPlanEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val setLogs: List<SetLogEntity>,
    val restTimers: List<ActiveRestTimerEntity>,
)

private fun UserPreferences.toJson(): JSONObject = JSONObject().apply {
    put("defaultRestSeconds", defaultRestSeconds)
    put("presetRestPrimary", presetRestPrimary)
    put("presetRestSecondary", presetRestSecondary)
    put("keepScreenOn", keepScreenOn)
    put("recentLoadKg", recentLoadKg)
    putNullable("activeGoalId", activeGoalId)
    putNullable("activeCycleId", activeCycleId)
    put("favoriteTemplateIds", favoriteTemplateIds.map { it.toString() }.toJsonArray())
}

private fun GoalEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    putNullable("note", note)
    put("startDate", startDate)
    putNullable("targetDate", targetDate)
    put("status", status.name)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

private fun PlanCycleEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("goalId", goalId)
    put("name", name)
    put("startDate", startDate)
    putNullable("endDate", endDate)
    put("sortOrder", sortOrder)
    put("createdAt", createdAt)
}

private fun PlanWeekEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("cycleId", cycleId)
    put("weekIndex", weekIndex)
    putNullable("focus", focus)
    put("startDate", startDate)
    put("endDate", endDate)
}

private fun PlanDayEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("weekId", weekId)
    put("planDate", planDate)
    put("title", title)
    putNullable("note", note)
}

private fun ExerciseCategoryEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("type", type.name)
    put("name", name)
    put("sortOrder", sortOrder)
}

private fun ExerciseTemplateEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("categoryId", categoryId)
    put("name", name)
    put("sourceType", sourceType.name)
    put("supportsExternalLoad", supportsExternalLoad)
    put("defaultMetricType", defaultMetricType.name)
    putNullable("cue", cue)
    put("isArchived", isArchived)
    put("sortOrder", sortOrder)
}

private fun ExerciseVariantEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("templateId", templateId)
    put("name", name)
    put("metricType", metricType.name)
    putNullable("cue", cue)
    put("sortOrder", sortOrder)
}

private fun DayTaskEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("dayId", dayId)
    put("templateId", templateId)
    put("variantId", variantId)
    put("orderInDay", orderInDay)
    put("plannedLoadKg", plannedLoadKg)
    put("restSec", restSec)
    putNullable("note", note)
}

private fun TaskSetPlanEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("taskId", taskId)
    put("setIndex", setIndex)
    putNullable("targetReps", targetReps)
    putNullable("targetHoldSec", targetHoldSec)
    put("targetLoadKg", targetLoadKg)
}

private fun WorkoutSessionEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("dayId", dayId)
    put("startedAt", startedAt)
    putNullable("finishedAt", finishedAt)
    put("status", status.name)
}

private fun SetLogEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("sessionId", sessionId)
    put("taskId", taskId)
    put("setIndex", setIndex)
    putNullable("actualReps", actualReps)
    putNullable("actualHoldSec", actualHoldSec)
    put("actualLoadKg", actualLoadKg)
    put("completedAt", completedAt)
    put("isSkipped", isSkipped)
}

private fun ActiveRestTimerEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("sessionId", sessionId)
    put("taskId", taskId)
    put("setIndex", setIndex)
    put("durationSec", durationSec)
    put("endElapsedRealtimeMs", endElapsedRealtimeMs)
    put("state", state.name)
    put("createdAt", createdAt)
}

private inline fun <T> Iterable<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray {
    val array = JSONArray()
    for (item in this) {
        array.put(transform(item))
    }
    return array
}

private fun Iterable<String>.toJsonArray(): JSONArray {
    val array = JSONArray()
    for (item in this) {
        array.put(item)
    }
    return array
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}

private fun String.toBackupSnapshot(
    supportedFormatVersion: Int,
    currentDatabaseVersion: Int,
): BackupSnapshot {
    val root = JSONObject(this)
    val formatVersion = root.getInt("formatVersion")
    require(formatVersion == supportedFormatVersion) {
        "Unsupported backup format version: $formatVersion."
    }
    val databaseVersion = root.optInt("databaseVersion", currentDatabaseVersion)
    require(databaseVersion <= currentDatabaseVersion) {
        "Backup database version $databaseVersion is newer than app database version $currentDatabaseVersion."
    }

    return BackupSnapshot(
        formatVersion = formatVersion,
        databaseVersion = databaseVersion,
        preferences = root.getJSONObject("preferences").toUserPreferences(),
        goals = root.getJSONArray("goals").mapObjects { it.toGoalEntity() },
        cycles = root.getJSONArray("cycles").mapObjects { it.toPlanCycleEntity() },
        weeks = root.getJSONArray("weeks").mapObjects { it.toPlanWeekEntity() },
        planDays = root.getJSONArray("planDays").mapObjects { it.toPlanDayEntity() },
        categories = root.getJSONArray("categories").mapObjects { it.toExerciseCategoryEntity() },
        templates = root.getJSONArray("templates").mapObjects { it.toExerciseTemplateEntity() },
        variants = root.getJSONArray("variants").mapObjects { it.toExerciseVariantEntity() },
        dayTasks = root.getJSONArray("dayTasks").mapObjects { it.toDayTaskEntity() },
        taskSetPlans = root.getJSONArray("taskSetPlans").mapObjects { it.toTaskSetPlanEntity() },
        sessions = root.getJSONArray("sessions").mapObjects { it.toWorkoutSessionEntity() },
        setLogs = root.getJSONArray("setLogs").mapObjects { it.toSetLogEntity() },
        restTimers = root.getJSONArray("restTimers").mapObjects { it.toActiveRestTimerEntity() },
    )
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    val items = ArrayList<T>(length())
    for (index in 0 until length()) {
        items += transform(getJSONObject(index))
    }
    return items
}

private fun JSONArray.toLongSet(): Set<Long> {
    val items = linkedSetOf<Long>()
    for (index in 0 until length()) {
        when (val value = get(index)) {
            is Number -> items += value.toLong()
            is String -> value.toLongOrNull()?.let { items += it }
        }
    }
    return items
}

private fun JSONObject.toUserPreferences(): UserPreferences = UserPreferences(
    defaultRestSeconds = optInt("defaultRestSeconds", 90),
    presetRestPrimary = optInt("presetRestPrimary", 60),
    presetRestSecondary = optInt("presetRestSecondary", 90),
    keepScreenOn = optBoolean("keepScreenOn", true),
    recentLoadKg = optDouble("recentLoadKg", 0.0),
    activeGoalId = optNullableLong("activeGoalId"),
    activeCycleId = optNullableLong("activeCycleId"),
    favoriteTemplateIds = optJSONArray("favoriteTemplateIds")?.toLongSet().orEmpty(),
)

private fun JSONObject.toGoalEntity(): GoalEntity = GoalEntity(
    id = getLong("id"),
    title = getString("title"),
    note = optNullableString("note"),
    startDate = getString("startDate"),
    targetDate = optNullableString("targetDate"),
    status = GoalStatus.valueOf(getString("status")),
    createdAt = getLong("createdAt"),
    updatedAt = getLong("updatedAt"),
)

private fun JSONObject.toPlanCycleEntity(): PlanCycleEntity = PlanCycleEntity(
    id = getLong("id"),
    goalId = getLong("goalId"),
    name = getString("name"),
    startDate = getString("startDate"),
    endDate = optNullableString("endDate"),
    sortOrder = getInt("sortOrder"),
    createdAt = getLong("createdAt"),
)

private fun JSONObject.toPlanWeekEntity(): PlanWeekEntity = PlanWeekEntity(
    id = getLong("id"),
    cycleId = getLong("cycleId"),
    weekIndex = getInt("weekIndex"),
    focus = optNullableString("focus"),
    startDate = getString("startDate"),
    endDate = getString("endDate"),
)

private fun JSONObject.toPlanDayEntity(): PlanDayEntity = PlanDayEntity(
    id = getLong("id"),
    weekId = getLong("weekId"),
    planDate = getString("planDate"),
    title = getString("title"),
    note = optNullableString("note"),
)

private fun JSONObject.toExerciseCategoryEntity(): ExerciseCategoryEntity = ExerciseCategoryEntity(
    id = getLong("id"),
    type = CategoryType.valueOf(getString("type")),
    name = getString("name"),
    sortOrder = getInt("sortOrder"),
)

private fun JSONObject.toExerciseTemplateEntity(): ExerciseTemplateEntity = ExerciseTemplateEntity(
    id = getLong("id"),
    categoryId = getLong("categoryId"),
    name = getString("name"),
    sourceType = SourceType.valueOf(getString("sourceType")),
    supportsExternalLoad = getBoolean("supportsExternalLoad"),
    defaultMetricType = MetricType.valueOf(getString("defaultMetricType")),
    cue = optNullableString("cue"),
    isArchived = getBoolean("isArchived"),
    sortOrder = getInt("sortOrder"),
)

private fun JSONObject.toExerciseVariantEntity(): ExerciseVariantEntity = ExerciseVariantEntity(
    id = getLong("id"),
    templateId = getLong("templateId"),
    name = getString("name"),
    metricType = MetricType.valueOf(getString("metricType")),
    cue = optNullableString("cue"),
    sortOrder = getInt("sortOrder"),
)

private fun JSONObject.toDayTaskEntity(): DayTaskEntity = DayTaskEntity(
    id = getLong("id"),
    dayId = getLong("dayId"),
    templateId = getLong("templateId"),
    variantId = getLong("variantId"),
    orderInDay = getInt("orderInDay"),
    plannedLoadKg = getDouble("plannedLoadKg"),
    restSec = getInt("restSec"),
    note = optNullableString("note"),
)

private fun JSONObject.toTaskSetPlanEntity(): TaskSetPlanEntity = TaskSetPlanEntity(
    id = getLong("id"),
    taskId = getLong("taskId"),
    setIndex = getInt("setIndex"),
    targetReps = optNullableInt("targetReps"),
    targetHoldSec = optNullableInt("targetHoldSec"),
    targetLoadKg = getDouble("targetLoadKg"),
)

private fun JSONObject.toWorkoutSessionEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    id = getLong("id"),
    dayId = getLong("dayId"),
    startedAt = getLong("startedAt"),
    finishedAt = optNullableLong("finishedAt"),
    status = SessionStatus.valueOf(getString("status")),
)

private fun JSONObject.toSetLogEntity(): SetLogEntity = SetLogEntity(
    id = getLong("id"),
    sessionId = getLong("sessionId"),
    taskId = getLong("taskId"),
    setIndex = getInt("setIndex"),
    actualReps = optNullableInt("actualReps"),
    actualHoldSec = optNullableInt("actualHoldSec"),
    actualLoadKg = getDouble("actualLoadKg"),
    completedAt = getLong("completedAt"),
    isSkipped = getBoolean("isSkipped"),
)

private fun JSONObject.toActiveRestTimerEntity(): ActiveRestTimerEntity = ActiveRestTimerEntity(
    id = getLong("id"),
    sessionId = getLong("sessionId"),
    taskId = getLong("taskId"),
    setIndex = getInt("setIndex"),
    durationSec = getInt("durationSec"),
    endElapsedRealtimeMs = getLong("endElapsedRealtimeMs"),
    state = TimerState.valueOf(getString("state")),
    createdAt = getLong("createdAt"),
)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

private fun JSONObject.optNullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

private fun ActiveRestTimerEntity.cancelForRestore(): ActiveRestTimerEntity =
    if (state == TimerState.CANCELLED) this else copy(state = TimerState.CANCELLED)

class TrainingRepository(
    private val database: AppDatabase,
    private val preferencesRepository: PreferencesRepository,
) {
    private companion object {
        const val BACKUP_FORMAT_VERSION = 1
        const val DATABASE_VERSION = 1
    }

    private val dao = database.trainingDao()

    fun observeGoals(): Flow<List<GoalEntity>> = dao.observeGoals()

    fun observeCycles(goalId: Long): Flow<List<PlanCycleEntity>> = dao.observeCyclesForGoal(goalId)

    fun observeMonthSummary(from: String, to: String): Flow<List<CalendarDaySummary>> =
        dao.observeMonthSummary(from, to)

    fun observePeriodOverview(from: String, to: String): Flow<PeriodOverviewSummary> =
        dao.observePeriodOverview(from, to)

    fun observeExerciseVolume(from: String, to: String): Flow<List<ExerciseVolumeSummary>> =
        dao.observeExerciseVolume(from, to)

    fun observeDayPlan(date: String): Flow<PlanDayWithTasks?> = dao.observePlanDay(date)

    fun observeExerciseCatalog(): Flow<List<ExerciseCategoryWithTemplates>> = dao.observeExerciseCatalog()

    fun observeLatestSession(dayId: Long): Flow<WorkoutSessionEntity?> = dao.observeLatestSession(dayId)

    fun observeSetLogs(sessionId: Long): Flow<List<SetLogEntity>> = dao.observeSetLogs(sessionId)

    fun observeActiveRestTimer(sessionId: Long): Flow<ActiveRestTimerEntity?> = dao.observeActiveRestTimer(sessionId)

    fun observeLatestActiveRestTimer(): Flow<ActiveRestTimerEntity?> = dao.observeLatestActiveRestTimer()

    suspend fun exportBackupJson(appVersion: String): String {
        val preferences = preferencesRepository.preferencesFlow.first()
        return database.withTransaction {
            JSONObject().apply {
                put("formatVersion", BACKUP_FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("appVersion", appVersion)
                put("databaseVersion", DATABASE_VERSION)
                put("preferences", preferences.toJson())
                put("goals", dao.getAllGoals().toJsonArray { it.toJson() })
                put("cycles", dao.getAllCycles().toJsonArray { it.toJson() })
                put("weeks", dao.getAllWeeks().toJsonArray { it.toJson() })
                put("planDays", dao.getAllPlanDays().toJsonArray { it.toJson() })
                put("categories", dao.getAllCategories().toJsonArray { it.toJson() })
                put("templates", dao.getAllTemplates().toJsonArray { it.toJson() })
                put("variants", dao.getAllVariants().toJsonArray { it.toJson() })
                put("dayTasks", dao.getAllDayTasks().toJsonArray { it.toJson() })
                put("taskSetPlans", dao.getAllTaskSetPlans().toJsonArray { it.toJson() })
                put("sessions", dao.getAllSessions().toJsonArray { it.toJson() })
                put("setLogs", dao.getAllSetLogs().toJsonArray { it.toJson() })
                put("restTimers", dao.getAllRestTimers().toJsonArray { it.toJson() })
            }.toString(2)
        }
    }

    suspend fun restoreBackupJson(json: String): BackupRestoreResult {
        val snapshot = json.toBackupSnapshot(
            supportedFormatVersion = BACKUP_FORMAT_VERSION,
            currentDatabaseVersion = DATABASE_VERSION,
        )
        database.withTransaction {
            clearDatabaseForRestore()
            restoreDatabaseSnapshot(snapshot)
        }
        preferencesRepository.replacePreferences(snapshot.preferences)
        return snapshot.toRestoreResult()
    }

    private suspend fun clearDatabaseForRestore() {
        dao.deleteAllRestTimers()
        dao.deleteAllSetLogs()
        dao.deleteAllSessions()
        dao.deleteAllTaskSetPlans()
        dao.deleteAllDayTasks()
        dao.deleteAllPlanDays()
        dao.deleteAllWeeks()
        dao.deleteAllCycles()
        dao.deleteAllGoals()
        dao.deleteAllVariants()
        dao.deleteAllTemplates()
        dao.deleteAllCategories()
    }

    private suspend fun restoreDatabaseSnapshot(snapshot: BackupSnapshot) {
        snapshot.categories.forEach { dao.insertCategory(it) }
        snapshot.templates.forEach { dao.insertTemplate(it) }
        snapshot.variants.forEach { dao.insertVariant(it) }
        snapshot.goals.forEach { dao.insertGoal(it) }
        snapshot.cycles.forEach { dao.insertCycle(it) }
        snapshot.weeks.forEach { dao.insertWeek(it) }
        snapshot.planDays.forEach { dao.insertDay(it) }
        snapshot.dayTasks.forEach { dao.insertDayTask(it) }
        if (snapshot.taskSetPlans.isNotEmpty()) {
            dao.insertTaskSetPlans(snapshot.taskSetPlans)
        }
        snapshot.sessions.forEach { dao.insertWorkoutSession(it) }
        snapshot.setLogs.forEach { dao.insertSetLog(it) }
        snapshot.restTimers.forEach { dao.insertRestTimer(it.cancelForRestore()) }
    }

    private fun BackupSnapshot.toRestoreResult(): BackupRestoreResult = BackupRestoreResult(
        formatVersion = formatVersion,
        databaseVersion = databaseVersion,
        restoredGoals = goals.size,
        restoredPlanDays = planDays.size,
        restoredDayTasks = dayTasks.size,
        restoredTaskSetPlans = taskSetPlans.size,
        restoredSessions = sessions.size,
        restoredSetLogs = setLogs.size,
        restoredRestTimers = restTimers.size,
    )

    suspend fun seedBuiltIns() {
        database.withTransaction {
            val pullId = ensureCategory(CategoryType.PULL, "拉力训练", 0)
            val pushId = ensureCategory(CategoryType.PUSH, "推力训练", 1)
            val coreId = ensureCategory(CategoryType.CORE, "核心训练", 2)
            val armsId = ensureCategory(CategoryType.ARMS, "手臂训练", 3)
            val legsId = ensureCategory(CategoryType.LEGS, "腿部训练", 4)
            val skillId = ensureCategory(CategoryType.SKILL, "技能基础", 5)

            seedPullTemplates(pullId)
            seedPushTemplates(pushId)
            seedCoreTemplates(coreId)
            seedArmTemplates(armsId)
            seedLegTemplates(legsId)
            seedSkillTemplates(skillId)

            if (dao.countGoals() == 0) {
                val today = LocalDate.now()
                val goalId = dao.insertGoal(
                    GoalEntity(
                        title = "街头健身基础力量",
                        note = "以拉力、推力和核心稳定提升为主",
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

    private suspend fun ensureCategory(
        type: CategoryType,
        name: String,
        sortOrder: Int,
    ): Long {
        return dao.getCategoryByType(type)?.id ?: dao.insertCategory(
            ExerciseCategoryEntity(
                type = type,
                name = name,
                sortOrder = sortOrder,
            ),
        )
    }

    private suspend fun seedPullTemplates(categoryId: Long) {
        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "引体向上",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "全程收紧核心，避免摆动，先做稳定再追求次数。",
                sortOrder = 0,
                variants = listOf(
                    BuiltInVariantDefinition("标准正握", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("标准反握", MetricType.REPS, sortOrder = 1),
                    BuiltInVariantDefinition("正手窄握", MetricType.REPS, sortOrder = 2),
                    BuiltInVariantDefinition("正手宽握", MetricType.REPS, sortOrder = 3),
                    BuiltInVariantDefinition("反手窄握", MetricType.REPS, sortOrder = 4),
                    BuiltInVariantDefinition("反手宽握", MetricType.REPS, sortOrder = 5),
                    BuiltInVariantDefinition("正手前半程引体", MetricType.REPS, sortOrder = 6),
                    BuiltInVariantDefinition("正手后半程引体", MetricType.REPS, sortOrder = 7),
                    BuiltInVariantDefinition(
                        name = "顶部锁定+离心",
                        metricType = MetricType.HOLD_SECONDS_PLUS_ECCENTRIC,
                        cue = "顶部停稳后控制下降速度。",
                        sortOrder = 8,
                    ),
                    BuiltInVariantDefinition(
                        name = "顶部锁定到力竭",
                        metricType = MetricType.HOLD_TO_FAILURE,
                        cue = "保持肩胛稳定，出现明显代偿后停止。",
                        sortOrder = 9,
                    ),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "水平划船",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "身体保持一条直线，胸口主动找杠。",
                sortOrder = 1,
                variants = listOf(
                    BuiltInVariantDefinition("标准正握", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("标准反握", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "肩胛引体",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "手臂基本伸直，通过肩胛下沉和上提完成动作。",
                sortOrder = 2,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )
    }

    private suspend fun seedPushTemplates(categoryId: Long) {
        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "腰间俯卧撑",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "躯干全程稳定，肘部自然向后。",
                sortOrder = 0,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "倒立撑",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "先保证中立位和肩胛控制，再增加深度和次数。",
                sortOrder = 1,
                variants = listOf(
                    BuiltInVariantDefinition("靠墙", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "冲肩",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "主动把肩膀顶出去，保持节奏稳定。",
                sortOrder = 2,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.HOLD_SECONDS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "双杠臂屈伸",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "肩胛下沉，动作底部避免耸肩和塌腰。",
                sortOrder = 3,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "V字俯卧撑",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "重心前移，强调肩部推举发力。",
                sortOrder = 4,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )
    }

    private suspend fun seedCoreTemplates(categoryId: Long) {
        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "平板支撑",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "收紧腹臀，避免腰部塌陷。",
                sortOrder = 0,
                variants = listOf(
                    BuiltInVariantDefinition("前平板", MetricType.HOLD_SECONDS, sortOrder = 0),
                    BuiltInVariantDefinition("侧平板", MetricType.HOLD_SECONDS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "Hollow Body Hold",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "腰背贴地，手脚尽量拉长，保持持续张力。",
                sortOrder = 1,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.HOLD_SECONDS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "仰卧举腿",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "下降阶段慢控，避免借力甩腿。",
                sortOrder = 2,
                variants = listOf(
                    BuiltInVariantDefinition("屈膝", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("直腿", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "悬垂举腿",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "先稳住肩胛，再抬腿，减少摆动。",
                sortOrder = 3,
                variants = listOf(
                    BuiltInVariantDefinition("屈膝", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("直腿", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "L-Sit支撑",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "肩膀下压，双腿主动伸直抬高。",
                sortOrder = 4,
                variants = listOf(
                    BuiltInVariantDefinition("屈膝", MetricType.HOLD_SECONDS, sortOrder = 0),
                    BuiltInVariantDefinition("标准", MetricType.HOLD_SECONDS, sortOrder = 1),
                ),
            ),
        )
    }

    private suspend fun seedArmTemplates(categoryId: Long) {
        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "自重弯举",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "身体保持一条直线，肘部固定，用肱二头肌拉起身体。",
                sortOrder = 0,
                variants = listOf(
                    BuiltInVariantDefinition("低杠", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("毛巾/吊环", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "窄距反握引体",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "反握窄距，先保持肩胛稳定，再强调肘屈发力。",
                sortOrder = 1,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("顶部停顿", MetricType.REPS, cue = "顶部停稳1秒再下降。", sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "反握划船",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "胸口找杠，手肘贴近身体，控制离心下降。",
                sortOrder = 2,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "自重肱三头肌伸展",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "肘部朝前，身体整体前后移动，避免肩部塌陷。",
                sortOrder = 3,
                variants = listOf(
                    BuiltInVariantDefinition("低杠", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("墙面", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "钻石俯卧撑",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "手距收窄，肘部自然后收，保持核心和肩胛稳定。",
                sortOrder = 4,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("跪姿", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "椅上臂屈伸",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "肩胛下沉，动作底部不过度前顶肩。",
                sortOrder = 5,
                variants = listOf(
                    BuiltInVariantDefinition("屈膝", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("直腿", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )
    }

    private suspend fun seedLegTemplates(categoryId: Long) {
        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "自重深蹲",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "膝盖跟随脚尖方向，底部保持躯干稳定。",
                sortOrder = 0,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("暂停", MetricType.REPS, cue = "底部停顿1-2秒。", sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "弓步蹲",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "前脚稳定发力，后膝接近地面但不砸地。",
                sortOrder = 1,
                variants = listOf(
                    BuiltInVariantDefinition("原地", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("行走", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "保加利亚分腿蹲",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "前脚踩稳，躯干略前倾，控制下放速度。",
                sortOrder = 2,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "单腿深蹲进阶",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "先保证膝踝轨迹稳定，再逐步增加深度。",
                sortOrder = 3,
                variants = listOf(
                    BuiltInVariantDefinition("箱式", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("辅助", MetricType.REPS, sortOrder = 1),
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 2),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "靠墙静蹲",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "大腿接近平行地面，腰背贴墙，保持均匀呼吸。",
                sortOrder = 4,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.HOLD_SECONDS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "提踵",
                supportsExternalLoad = true,
                defaultMetricType = MetricType.REPS,
                cue = "顶端充分收缩，下降阶段慢控。",
                sortOrder = 5,
                variants = listOf(
                    BuiltInVariantDefinition("双脚", MetricType.REPS, sortOrder = 0),
                    BuiltInVariantDefinition("单脚", MetricType.REPS, sortOrder = 1),
                ),
            ),
        )
    }

    private suspend fun seedSkillTemplates(categoryId: Long) {
        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "靠墙倒立",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "手指主动抓地，肋骨内收，保持肩部上顶。",
                sortOrder = 0,
                variants = listOf(
                    BuiltInVariantDefinition("背墙", MetricType.HOLD_SECONDS, sortOrder = 0),
                    BuiltInVariantDefinition("面墙", MetricType.HOLD_SECONDS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "肩胛俯卧撑",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.REPS,
                cue = "手臂伸直，通过肩胛前伸和回收完成动作。",
                sortOrder = 1,
                variants = listOf(
                    BuiltInVariantDefinition("标准", MetricType.REPS, sortOrder = 0),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "蛙立",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "手掌压地，重心前移，先追求稳定停留。",
                sortOrder = 2,
                variants = listOf(
                    BuiltInVariantDefinition("辅助", MetricType.HOLD_SECONDS, sortOrder = 0),
                    BuiltInVariantDefinition("标准", MetricType.HOLD_SECONDS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "前水平团身",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "手臂伸直下压，骨盆后倾，背部主动发力。",
                sortOrder = 3,
                variants = listOf(
                    BuiltInVariantDefinition("单腿", MetricType.HOLD_SECONDS, sortOrder = 0),
                    BuiltInVariantDefinition("团身", MetricType.HOLD_SECONDS, sortOrder = 1),
                ),
            ),
        )

        ensureTemplateWithVariants(
            categoryId = categoryId,
            definition = BuiltInTemplateDefinition(
                name = "支撑顶肩",
                supportsExternalLoad = false,
                defaultMetricType = MetricType.HOLD_SECONDS,
                cue = "双臂伸直，肩胛下压，身体离地后保持稳定。",
                sortOrder = 4,
                variants = listOf(
                    BuiltInVariantDefinition("双杠", MetricType.HOLD_SECONDS, sortOrder = 0),
                    BuiltInVariantDefinition("地面", MetricType.HOLD_SECONDS, sortOrder = 1),
                ),
            ),
        )
    }

    private suspend fun ensureTemplateWithVariants(
        categoryId: Long,
        definition: BuiltInTemplateDefinition,
    ) {
        val templateId = dao.getTemplateByCategoryAndName(categoryId, definition.name)?.id
            ?: dao.insertTemplate(
                ExerciseTemplateEntity(
                    categoryId = categoryId,
                    name = definition.name,
                    sourceType = SourceType.BUILTIN,
                    supportsExternalLoad = definition.supportsExternalLoad,
                    defaultMetricType = definition.defaultMetricType,
                    cue = definition.cue,
                    sortOrder = definition.sortOrder,
                ),
            )

        definition.variants.forEach { variant ->
            val existing = dao.getVariantByTemplateAndName(templateId, variant.name)
            if (existing == null) {
                dao.insertVariant(
                    ExerciseVariantEntity(
                        templateId = templateId,
                        name = variant.name,
                        metricType = variant.metricType,
                        cue = variant.cue,
                        sortOrder = variant.sortOrder,
                    ),
                )
            }
        }
    }

    private data class BuiltInTemplateDefinition(
        val name: String,
        val supportsExternalLoad: Boolean,
        val defaultMetricType: MetricType,
        val cue: String? = null,
        val sortOrder: Int,
        val variants: List<BuiltInVariantDefinition>,
    )

    private data class BuiltInVariantDefinition(
        val name: String,
        val metricType: MetricType,
        val cue: String? = null,
        val sortOrder: Int,
    )

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

    suspend fun setTrainingOrderMode(
        date: String,
        cycleId: Long?,
        mode: TrainingOrderMode,
    ) {
        database.withTransaction {
            if (!canMutatePlanDate(date)) return@withTransaction
            val resolvedCycleId = cycleId ?: dao.getLatestCycle()?.id ?: seedCycleAndReturn()
            val day = ensurePlanDay(date, resolvedCycleId)
            dao.updatePlanDayNote(day.id, buildTrainingOrderModeNote(mode))
        }
    }

    suspend fun createCustomExercise(input: CustomExerciseDraft) {
        database.withTransaction {
            val templateName = input.name.trim()
            val variantName = input.variantName.trim().ifBlank { "默认" }
            val cue = input.cue?.trim()?.ifBlank { null }
            if (templateName.isBlank()) return@withTransaction

            val categoryId = dao.getCategoryByType(input.categoryType)?.id
                ?: error("Category ${input.categoryType} is missing. Seed should have created it.")
            val templateId = dao.insertTemplate(
                ExerciseTemplateEntity(
                    categoryId = categoryId,
                    name = templateName,
                    sourceType = SourceType.CUSTOM,
                    supportsExternalLoad = input.supportsExternalLoad,
                    defaultMetricType = input.defaultMetricType,
                    cue = cue,
                    sortOrder = 999,
                ),
            )
            dao.insertVariant(
                ExerciseVariantEntity(
                    templateId = templateId,
                    name = variantName,
                    metricType = input.defaultMetricType,
                    cue = cue,
                    sortOrder = 0,
                ),
            )
        }
    }

    suspend fun createOrUpdateDayTask(input: DayTaskDraft): Long {
        return database.withTransaction {
            if (!canMutatePlanDate(input.date)) return@withTransaction 0L
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
                buildTaskSetPlans(
                    taskId = taskId,
                    sets = input.sets,
                    targetReps = input.targetReps,
                    targetHoldSec = input.targetHoldSec,
                    targetDropPerSet = input.targetDropPerSet,
                    plannedLoadKg = input.plannedLoadKg,
                ),
            )
            preferencesRepository.setRecentLoadKg(input.plannedLoadKg)
            taskId
        }
    }

    suspend fun createDayTasks(
        inputs: List<DayTaskDraft>,
        mode: TrainingOrderMode? = null,
    ): List<Long> {
        if (inputs.isEmpty()) return emptyList()
        return database.withTransaction {
            val firstInput = inputs.first()
            if (!canMutatePlanDate(firstInput.date)) return@withTransaction emptyList()
            val cycleId = firstInput.cycleId ?: dao.getLatestCycle()?.id ?: seedCycleAndReturn()
            val planDay = ensurePlanDay(firstInput.date, cycleId)
            if (mode != null) {
                dao.updatePlanDayNote(planDay.id, buildTrainingOrderModeNote(mode))
            }

            var nextOrder = dao.getMaxTaskOrder(planDay.id)
            val taskIds = mutableListOf<Long>()
            inputs.forEach { input ->
                nextOrder += 1
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
                    buildTaskSetPlans(
                        taskId = taskId,
                        sets = input.sets,
                        targetReps = input.targetReps,
                        targetHoldSec = input.targetHoldSec,
                        targetDropPerSet = input.targetDropPerSet,
                        plannedLoadKg = input.plannedLoadKg,
                    ),
                )
                taskIds += taskId
            }
            inputs.lastOrNull()?.plannedLoadKg?.let { preferencesRepository.setRecentLoadKg(it) }
            taskIds
        }
    }

    suspend fun updateDayTask(input: DayTaskEditDraft) {
        database.withTransaction {
            val task = dao.getDayTaskById(input.taskId) ?: return@withTransaction
            if (!canMutatePlanDay(task.dayId)) return@withTransaction
            dao.updateDayTask(
                task.copy(
                    templateId = input.templateId,
                    variantId = input.variantId,
                    plannedLoadKg = input.plannedLoadKg,
                    restSec = input.restSec,
                    note = input.note,
                ),
            )
            dao.deleteTaskSetPlans(input.taskId)
            dao.insertTaskSetPlans(
                buildTaskSetPlans(
                    taskId = input.taskId,
                    sets = input.sets,
                    targetReps = input.targetReps,
                    targetHoldSec = input.targetHoldSec,
                    targetDropPerSet = input.targetDropPerSet,
                    plannedLoadKg = input.plannedLoadKg,
                ),
            )
            preferencesRepository.setRecentLoadKg(input.plannedLoadKg)
        }
    }

    suspend fun duplicateDayTask(taskId: Long): Long? {
        return database.withTransaction {
            val task = dao.getDayTaskById(taskId) ?: return@withTransaction null
            if (!canMutatePlanDay(task.dayId)) return@withTransaction null
            val nextOrder = dao.getMaxTaskOrder(task.dayId) + 1
            val newTaskId = dao.insertDayTask(task.copy(id = 0, orderInDay = nextOrder))
            dao.insertTaskSetPlans(
                dao.getSetPlansForTask(taskId).map { it.copy(id = 0, taskId = newTaskId) },
            )
            newTaskId
        }
    }

    suspend fun moveDayTask(taskId: Long, direction: Int) {
        if (direction == 0) return
        database.withTransaction {
            val task = dao.getDayTaskById(taskId) ?: return@withTransaction
            if (!canMutatePlanDay(task.dayId)) return@withTransaction
            val tasks = dao.getDayTasks(task.dayId)
            val currentIndex = tasks.indexOfFirst { it.id == taskId }
            val targetIndex = currentIndex + direction
            if (currentIndex == -1 || targetIndex !in tasks.indices) return@withTransaction

            val target = tasks[targetIndex]
            dao.updateDayTaskOrder(task.id, -1)
            dao.updateDayTaskOrder(target.id, task.orderInDay)
            dao.updateDayTaskOrder(task.id, target.orderInDay)
            normalizeDayTaskOrders(task.dayId)
        }
    }

    suspend fun deleteDayTask(taskId: Long) {
        database.withTransaction {
            val task = dao.getDayTaskById(taskId) ?: return@withTransaction
            val dayId = task.dayId
            if (!canMutatePlanDay(dayId)) return@withTransaction
            dao.deleteTaskSetPlans(taskId)
            dao.deleteDayTask(taskId)
            normalizeDayTaskOrders(dayId)
        }
    }

    suspend fun duplicatePlanDay(sourceDate: String, targetDate: String) {
        database.withTransaction {
            if (!canMutatePlanDate(targetDate)) return@withTransaction
            val sourceDay = dao.getPlanDayByDate(sourceDate) ?: return@withTransaction
            val sourceTasks = dao.getDayTasks(sourceDay.id)
            if (sourceTasks.isEmpty()) return@withTransaction

            val latestCycleId = dao.getLatestCycle()?.id ?: return@withTransaction
            val targetDay = ensurePlanDay(targetDate, latestCycleId)
            dao.updatePlanDayNote(targetDay.id, sourceDay.note)
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
            val requestedDate = runCatching { LocalDate.parse(date) }.getOrNull()
            if (requestedDate != LocalDate.now()) {
                val day = dao.getPlanDayByDate(date) ?: return@withTransaction ensureEmptyDay(date)
                return@withTransaction day to null
            }

            val day = dao.getPlanDayByDate(date) ?: return@withTransaction ensureEmptyDay(date)
            val hasTasks = dao.getDayTasks(day.id).isNotEmpty()
            if (!hasTasks) {
                day to null
            } else {
                val latest = dao.getLatestSessionForDay(day.id)
                if (latest?.status == SessionStatus.IN_PROGRESS ||
                    (latest?.status == SessionStatus.COMPLETED && !isCompletedTodayTestingAllowed())
                ) {
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

    suspend fun completeSet(
        input: SetCompletionInput,
        onRestTimerCreated: (ActiveRestTimerEntity) -> Unit = {},
    ): ActiveRestTimerEntity? {
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
                timer.copy(id = timerId).also(onRestTimerCreated)
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

    suspend fun getRestTimer(timerId: Long): ActiveRestTimerEntity? {
        return database.withTransaction {
            dao.getRestTimer(timerId)
        }
    }

    suspend fun getLatestActiveRestTimer(): ActiveRestTimerEntity? {
        return database.withTransaction {
            dao.getLatestActiveRestTimer()
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

    private suspend fun canMutatePlanDate(date: String): Boolean {
        val planDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        if (planDate.isBefore(LocalDate.now())) return false
        val existingDay = dao.getPlanDayByDate(date) ?: return true
        return canMutatePlanDay(existingDay.id, planDate)
    }

    private suspend fun canMutatePlanDay(
        dayId: Long,
        knownDate: LocalDate? = null,
    ): Boolean {
        val planDate = knownDate ?: dao.getAllPlanDays()
            .firstOrNull { it.id == dayId }
            ?.planDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return false
        if (planDate.isBefore(LocalDate.now())) return false

        val latestSession = dao.getLatestSessionForDay(dayId)
        return when (latestSession?.status) {
            SessionStatus.IN_PROGRESS -> false
            SessionStatus.COMPLETED -> isCompletedTodayTestingAllowed(planDate)
            else -> true
        }
    }

    private fun isCompletedTodayTestingAllowed(planDate: LocalDate = LocalDate.now()): Boolean {
        return PlanTestingSwitch.enabled && planDate == LocalDate.now()
    }

    private fun buildTaskSetPlans(
        taskId: Long,
        sets: Int,
        targetReps: Int?,
        targetHoldSec: Int?,
        targetDropPerSet: Int,
        plannedLoadKg: Double,
    ): List<TaskSetPlanEntity> {
        return (1..sets).map { setNumber ->
            val drop = targetDropPerSet.coerceAtLeast(0) * (setNumber - 1)
            TaskSetPlanEntity(
                taskId = taskId,
                setIndex = setNumber,
                targetReps = targetReps?.let { (it - drop).coerceAtLeast(1) },
                targetHoldSec = targetHoldSec?.let { (it - drop).coerceAtLeast(1) },
                targetLoadKg = plannedLoadKg,
            )
        }
    }

    private suspend fun normalizeDayTaskOrders(dayId: Long) {
        dao.getDayTasks(dayId).forEachIndexed { index, task ->
            val normalizedOrder = index + 1
            if (task.orderInDay != normalizedOrder) {
                dao.updateDayTaskOrder(task.id, normalizedOrder)
            }
        }
    }
}

