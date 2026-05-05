package com.codex.streetstrength.timer

import android.content.Context

object RestTimerController {
    fun stopRestAlert(context: Context) {
        val appContext = context.applicationContext
        RestTimerAlarmScheduler.cancel(appContext)
        RestTimerAlert.stop(appContext)
        RestTimerService.stop(appContext)
    }
}
