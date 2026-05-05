package com.codex.streetstrength.data.model

private const val TRAINING_ORDER_MODE_PREFIX = "trainingOrderMode="

fun parseTrainingOrderModeNote(note: String?): TrainingOrderMode {
    val rawMode = note
        ?.lineSequence()
        ?.firstOrNull { it.startsWith(TRAINING_ORDER_MODE_PREFIX) }
        ?.removePrefix(TRAINING_ORDER_MODE_PREFIX)
    return rawMode
        ?.let { runCatching { TrainingOrderMode.valueOf(it) }.getOrNull() }
        ?: TrainingOrderMode.CIRCUIT
}

fun buildTrainingOrderModeNote(mode: TrainingOrderMode): String {
    return "$TRAINING_ORDER_MODE_PREFIX${mode.name}"
}
