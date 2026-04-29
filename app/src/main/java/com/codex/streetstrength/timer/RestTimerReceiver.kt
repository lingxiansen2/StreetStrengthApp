package com.codex.streetstrength.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.codex.streetstrength.StreetStrengthApp
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
        RestTimerAlert.showFinishedAlert(context)
        markTimerFiredAsync(context, timerId)

        runCatching { RestTimerService.startFinishFromAlarm(context, timerId) }
            .onFailure { Log.w(TAG, "Foreground service start was delayed or blocked by system", it) }
    }

    private fun handleStop(context: Context) {
        RestTimerAlert.stop(context)
        runCatching { RestTimerService.stop(context) }
            .onFailure { Log.w(TAG, "Failed to stop timer service from notification action", it) }
    }

    private fun markTimerFiredAsync(context: Context, timerId: Long) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as StreetStrengthApp
                app.trainingRepository.markRestTimerFired(timerId)
            }.onFailure {
                Log.w(TAG, "Failed to mark rest timer as fired from receiver: $timerId", it)
            }
            pendingResult.finish()
        }
    }

    private companion object {
        const val TAG = "RestTimerReceiver"
    }
}
