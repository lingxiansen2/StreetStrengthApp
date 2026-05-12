package com.codex.streetstrength.timer

import android.content.Context

object RestTimerController {
    fun stopRestAlert(
        context: Context,
        timerId: Long? = null,
    ) {
        val appContext = context.applicationContext
        RestTimerAlarmScheduler.cancel(appContext)
        RestTimerAlert.stop(appContext, timerId)
        RestTimerService.stop(appContext, timerId)
    }
}
