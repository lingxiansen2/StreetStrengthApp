package com.codex.streetstrength.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.model.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RestTimerService.ACTION_FINISH -> handleFinish(context, intent)
            RestTimerAlert.ACTION_STOP_ALERT -> handleStop(context, intent)
        }
    }

    private fun handleFinish(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(RestTimerService.EXTRA_TIMER_ID, -1L)
        if (timerId <= 0L) return

        if (RestTimerAlert.isStopRequested(context, timerId)) {
            Log.i(TAG, "Ignoring rest finish alarm for stopped timer $timerId")
            return
        }
        Log.i(TAG, "Received rest finish alarm for timer $timerId")
        RestTimerAlarmScheduler.cancel(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val shouldAlert = shouldAlertAndMarkTimerFired(context, timerId)
            if (shouldAlert) {
                if (RestTimerAlert.isStopRequested(context, timerId)) {
                    Log.i(TAG, "Skipping receiver alert for stopped timer $timerId after claim")
                    pendingResult.finish()
                    return@launch
                }
                // Alert immediately from the alarm receiver so OEM-delayed foreground
                // service starts cannot swallow the rest-finished vibration.
                RestTimerAlert.showFinishedNotification(context, timerId)
                RestTimerAlert.startContinuousVibration(context, timerId)
                val serviceStarted = RestTimerService.startFinishFromAlarm(context, timerId)
                if (!serviceStarted) {
                    Log.w(TAG, "Finish service start failed; receiver alert remains active")
                }
            }
            pendingResult.finish()
        }
    }

    private fun handleStop(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(RestTimerService.EXTRA_TIMER_ID, -1L).takeIf { it > 0L }
        Log.i(TAG, "Received stop rest alert action timerId=${timerId ?: "unknown"}")
        runCatching { RestTimerController.stopRestAlert(context, timerId) }
            .onFailure { Log.w(TAG, "Failed to stop rest alert from notification action", it) }
    }

    private suspend fun shouldAlertAndMarkTimerFired(context: Context, timerId: Long): Boolean {
        return runCatching {
            val app = context.applicationContext as StreetStrengthApp
            val claimed = app.trainingRepository.markRestTimerFiredIfRunning(timerId)
            if (claimed) {
                Log.i(TAG, "Marked rest timer fired from receiver timerId=$timerId source=receiver")
                return@runCatching true
            }
            val timerState = app.trainingRepository.getRestTimer(timerId)?.state
            Log.i(TAG, "Receiver finish inspected timerId=$timerId state=$timerState source=receiver")
            when (timerState) {
                TimerState.RUNNING -> {
                    Log.i(TAG, "Timer $timerId was claimed by another finish path; skip duplicate receiver alert")
                    false
                }
                TimerState.FIRED -> {
                    Log.i(TAG, "Timer $timerId already fired; skip duplicate receiver alert")
                    false
                }
                TimerState.CANCELLED, null -> false
            }
        }.onFailure {
            Log.w(TAG, "Failed to verify rest timer before receiver alert: $timerId", it)
        }.getOrDefault(true)
    }

    private companion object {
        const val TAG = "RestTimerReceiver"
    }
}
