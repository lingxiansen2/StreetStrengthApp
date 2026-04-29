package com.codex.streetstrength.data.model

enum class CategoryType {
    PULL,
    PUSH,
    CORE,
    ARMS,
    LEGS,
    SKILL,
}

enum class MetricType {
    REPS,
    HOLD_SECONDS,
    HOLD_TO_FAILURE,
    HOLD_SECONDS_PLUS_ECCENTRIC,
}

enum class SourceType {
    BUILTIN,
    CUSTOM,
}

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
}

enum class TimerState {
    RUNNING,
    FIRED,
    CANCELLED,
}

enum class TrainingOrderMode {
    CIRCUIT,
    SEQUENTIAL,
}

enum class CalendarCompletionStatus {
    EMPTY,
    PLANNED,
    PARTIAL,
    DONE,
}
