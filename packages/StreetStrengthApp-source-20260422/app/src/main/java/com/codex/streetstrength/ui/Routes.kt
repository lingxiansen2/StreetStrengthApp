package com.codex.streetstrength.ui

object Routes {
    const val Calendar = "calendar"
    const val Overview = "overview"
    const val Library = "library"
    const val PlannerPattern = "planner/{date}"
    const val TrainingPattern = "training/{date}"

    fun planner(date: String): String = "planner/$date"

    fun training(date: String): String = "training/$date"
}
