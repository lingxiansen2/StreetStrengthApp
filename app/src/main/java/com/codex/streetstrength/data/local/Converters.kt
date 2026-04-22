package com.codex.streetstrength.data.local

import androidx.room.TypeConverter
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.GoalStatus
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SessionStatus
import com.codex.streetstrength.data.model.SourceType
import com.codex.streetstrength.data.model.TimerState

class Converters {
    @TypeConverter
    fun fromCategoryType(value: CategoryType?): String? = value?.name

    @TypeConverter
    fun toCategoryType(value: String?): CategoryType? = value?.let(CategoryType::valueOf)

    @TypeConverter
    fun fromMetricType(value: MetricType?): String? = value?.name

    @TypeConverter
    fun toMetricType(value: String?): MetricType? = value?.let(MetricType::valueOf)

    @TypeConverter
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? = value?.let(SourceType::valueOf)

    @TypeConverter
    fun fromGoalStatus(value: GoalStatus?): String? = value?.name

    @TypeConverter
    fun toGoalStatus(value: String?): GoalStatus? = value?.let(GoalStatus::valueOf)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus?): String? = value?.name

    @TypeConverter
    fun toSessionStatus(value: String?): SessionStatus? = value?.let(SessionStatus::valueOf)

    @TypeConverter
    fun fromTimerState(value: TimerState?): String? = value?.name

    @TypeConverter
    fun toTimerState(value: String?): TimerState? = value?.let(TimerState::valueOf)
}

