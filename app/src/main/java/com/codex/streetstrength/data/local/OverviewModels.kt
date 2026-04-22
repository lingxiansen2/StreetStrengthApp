package com.codex.streetstrength.data.local

data class PeriodOverviewSummary(
    val plannedDays: Int = 0,
    val trainedDays: Int = 0,
    val completedDays: Int = 0,
    val plannedTasks: Int = 0,
    val plannedSets: Int = 0,
    val completedSets: Int = 0,
    val completedSessions: Int = 0,
    val totalReps: Int = 0,
    val totalHoldSec: Int = 0,
    val totalExternalLoadKg: Double = 0.0,
)
