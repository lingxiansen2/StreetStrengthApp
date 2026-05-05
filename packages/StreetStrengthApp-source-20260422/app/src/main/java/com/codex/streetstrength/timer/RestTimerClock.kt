package com.codex.streetstrength.timer

import android.os.SystemClock

object RestTimerClock {
    fun endAtWallClockMs(
        createdAtMs: Long,
        durationSec: Int,
    ): Long = createdAtMs + durationSec.coerceAtLeast(0).toLong() * 1_000L

    fun endAtWallClockMsFromElapsed(
        endElapsedRealtimeMs: Long,
        nowWallClockMs: Long = System.currentTimeMillis(),
        nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
    ): Long = nowWallClockMs + (endElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)

    fun remainingFromWallClock(
        endAtWallClockMs: Long,
        nowWallClockMs: Long = System.currentTimeMillis(),
    ): Long = (endAtWallClockMs - nowWallClockMs).coerceAtLeast(0L)
}
