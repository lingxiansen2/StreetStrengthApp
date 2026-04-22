package com.codex.streetstrength.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GoalEntity::class,
        PlanCycleEntity::class,
        PlanWeekEntity::class,
        PlanDayEntity::class,
        ExerciseCategoryEntity::class,
        ExerciseTemplateEntity::class,
        ExerciseVariantEntity::class,
        DayTaskEntity::class,
        TaskSetPlanEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        ActiveRestTimerEntity::class,
    ],
    views = [CalendarDaySummary::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao
}

