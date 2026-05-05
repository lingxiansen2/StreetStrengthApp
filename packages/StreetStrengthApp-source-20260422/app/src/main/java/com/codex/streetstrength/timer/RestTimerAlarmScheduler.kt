package com.codex.streetstrength.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.codex.streetstrength.MainActivity

object RestTimerAlarmScheduler {
    private const val TAG = "RestTimerAlarmScheduler"
    private const val REQUEST_CODE_FINISH_ALARM = 13
    private const val REQUEST_CODE_FINISH_ALARM_CLOCK = 15
    private const val REQUEST_CODE_ALARM_CLOCK_SHOW = 14

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(
        context: Context,
        timerId: Long,
        endElapsedMs: Long,
        endAtWallClockMs: Long = RestTimerClock.endAtWallClockMsFromElapsed(endElapsedMs),
    ) {
        if (timerId <= 0L || endElapsedMs <= 0L) return
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        cancel(appContext)

        val exactPendingIntent = finishAlarmIntent(appContext, timerId, REQUEST_CODE_FINISH_ALARM)
        val remainingMs = (endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        if (remainingMs <= 0L) {
            dispatchFinish(appContext, timerId)
            return
        }
        val triggerAtWallClockMs = endAtWallClockMs.takeIf { it > 0L }
            ?: System.currentTimeMillis() + remainingMs
        Log.i(
            TAG,
            "Scheduling rest finish receiver alarm for timer $timerId " +
                "elapsed=$endElapsedMs wall=$triggerAtWallClockMs",
        )

        val exactScheduled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endElapsedMs,
                    exactPendingIntent,
                )
                Log.i(TAG, "Scheduled exact elapsed rest finish alarm for timer $timerId")
            }.onFailure { error ->
                Log.w(TAG, "setExactAndAllowWhileIdle failed for timer $timerId", error)
            }.isSuccess
        } else {
            runCatching {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endElapsedMs,
                    exactPendingIntent,
                )
                Log.i(TAG, "Scheduled exact elapsed rest finish alarm for timer $timerId")
            }.onFailure { error ->
                Log.w(TAG, "setExact failed for timer $timerId", error)
            }.isSuccess
        }

        val alarmClockScheduled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        triggerAtWallClockMs,
                        alarmClockShowIntent(appContext),
                    ),
                    finishAlarmIntent(appContext, timerId, REQUEST_CODE_FINISH_ALARM_CLOCK),
                )
                Log.i(TAG, "Scheduled alarm clock backup rest finish alarm for timer $timerId")
            }.onFailure { error ->
                Log.w(TAG, "setAlarmClock backup failed for timer $timerId", error)
            }.isSuccess
        } else {
            false
        }

        if (!exactScheduled && !alarmClockScheduled) {
            Log.w(TAG, "Failed to schedule rest finish receiver alarm for timer $timerId")
            return
        }
        Log.i(TAG, "Scheduled rest finish receiver alarm for timer $timerId")
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        listOf(REQUEST_CODE_FINISH_ALARM, REQUEST_CODE_FINISH_ALARM_CLOCK).forEach { requestCode ->
            existingFinishAlarmIntent(appContext, requestCode)?.let { pendingIntent ->
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.i(TAG, "Cancelled rest finish receiver alarm requestCode=$requestCode")
            }
        }
    }

    fun dispatchFinish(
        context: Context,
        timerId: Long,
    ): Boolean {
        if (timerId <= 0L) return false
        val appContext = context.applicationContext
        return runCatching {
            finishAlarmIntent(appContext, timerId, REQUEST_CODE_FINISH_ALARM).send()
            Log.i(TAG, "Dispatched rest finish through receiver for timer $timerId")
        }.onFailure {
            Log.w(TAG, "Failed to dispatch rest finish through receiver for timer: $timerId", it)
        }.isSuccess
    }

    private fun alarmClockShowIntent(context: Context): PendingIntent? {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_ALARM_CLOCK_SHOW,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun finishAlarmIntent(
        context: Context,
        timerId: Long,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = RestTimerService.ACTION_FINISH
            putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingFinishAlarmIntent(
        context: Context,
        requestCode: Int,
    ): PendingIntent? {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = RestTimerService.ACTION_FINISH
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
