package com.codex.streetstrength

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import com.codex.streetstrength.data.local.AppDatabase
import com.codex.streetstrength.data.local.AppDatabaseMigrations
import com.codex.streetstrength.data.model.TimerState
import com.codex.streetstrength.data.preferences.PreferencesRepository
import com.codex.streetstrength.data.repository.TrainingRepository
import com.codex.streetstrength.timer.RestTimerAlarmScheduler
import com.codex.streetstrength.timer.RestTimerClock
import com.codex.streetstrength.timer.RestTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class StreetStrengthApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "street-strength.db",
        ).addMigrations(*AppDatabaseMigrations.ALL).build()
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(applicationContext)
    }

    val trainingRepository: TrainingRepository by lazy {
        TrainingRepository(
            database = database,
            preferencesRepository = preferencesRepository,
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            trainingRepository.seedBuiltIns()
            restoreRestTimerInfrastructure()
            watchRestTimerInfrastructure()
        }
    }

    private suspend fun restoreRestTimerInfrastructure() {
        val timer = runCatching { trainingRepository.getLatestActiveRestTimer() }
            .onFailure { Log.w(TAG, "Failed to load rest timer on app start", it) }
            .getOrNull() ?: return
        when {
            timer.state == TimerState.RUNNING && timer.endElapsedRealtimeMs > SystemClock.elapsedRealtime() -> {
                RestTimerService.start(
                    context = applicationContext,
                    timerId = timer.id,
                    endElapsedMs = timer.endElapsedRealtimeMs,
                    endAtWallClockMs = RestTimerClock.endAtWallClockMs(timer.createdAt, timer.durationSec),
                )
            }

            timer.state == TimerState.RUNNING -> {
                val dispatched = RestTimerAlarmScheduler.dispatchFinish(applicationContext, timer.id)
                if (!dispatched) {
                    trainingRepository.markRestTimerFired(timer.id)
                }
            }
        }
    }

    private suspend fun watchRestTimerInfrastructure() {
        var scheduledTimer: Pair<Long, Long>? = null
        val expiredTimersDispatched = mutableSetOf<Long>()
        trainingRepository.observeLatestActiveRestTimer().collect { timer ->
            when {
                timer == null -> scheduledTimer = null
                timer.state == TimerState.RUNNING && timer.endElapsedRealtimeMs > SystemClock.elapsedRealtime() -> {
                    val key = timer.id to timer.endElapsedRealtimeMs
                    if (scheduledTimer != key) {
                        Log.i(TAG, "Scheduling persisted rest timer ${timer.id} from app watchdog")
                        RestTimerAlarmScheduler.schedule(
                            context = applicationContext,
                            timerId = timer.id,
                            endElapsedMs = timer.endElapsedRealtimeMs,
                            endAtWallClockMs = RestTimerClock.endAtWallClockMs(timer.createdAt, timer.durationSec),
                        )
                        scheduledTimer = key
                    }
                }
                timer.state == TimerState.RUNNING && expiredTimersDispatched.add(timer.id) -> {
                    Log.i(TAG, "Dispatching expired persisted rest timer ${timer.id} from app watchdog")
                    RestTimerAlarmScheduler.dispatchFinish(applicationContext, timer.id)
                    scheduledTimer = null
                }
                timer.state == TimerState.FIRED -> {
                    scheduledTimer = null
                }
            }
        }
    }

    private companion object {
        const val TAG = "StreetStrengthApp"
    }
}
