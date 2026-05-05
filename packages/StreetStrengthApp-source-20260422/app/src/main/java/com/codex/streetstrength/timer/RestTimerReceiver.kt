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
            RestTimerAlert.ACTION_STOP_ALERT -> handleStop(context)
        }
    }

    private fun handleFinish(context: Context, intent: Intent) {
        val timerId = intent.getLongExtra(RestTimerService.EXTRA_TIMER_ID, -1L)
        if (timerId <= 0L) return

        Log.i(TAG, "Received rest finish alarm for timer $timerId")
        RestTimerAlarmScheduler.cancel(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val shouldAlert = shouldAlertAndMarkTimerFired(context, timerId)
            if (shouldAlert) {
                val serviceStarted = RestTimerService.startFinishFromAlarm(context, timerId)
                if (!serviceStarted) {
                    Log.w(TAG, "Finish service start failed; receiver is taking over alert vibration")
                    RestTimerAlert.showFinishedNotification(context)
                    RestTimerAlert.startContinuousVibration(context)
                }
            }
            pendingResult.finish()
        }
    }

    private fun handleStop(context: Context) {
        runCatching { RestTimerController.stopRestAlert(context) }
            .onFailure { Log.w(TAG, "Failed to stop rest alert from notification action", it) }
    }

    private suspend fun shouldAlertAndMarkTimerFired(context: Context, timerId: Long): Boolean {
        return runCatching {
            val app = context.applicationContext as StreetStrengthApp
            when (app.trainingRepository.getRestTimer(timerId)?.state) {
                TimerState.RUNNING -> {
                    app.trainingRepository.markRestTimerFired(timerId)
                    true
                }
                TimerState.FIRED -> true
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
