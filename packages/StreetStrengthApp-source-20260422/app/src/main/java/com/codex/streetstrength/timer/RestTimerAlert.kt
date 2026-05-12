package com.codex.streetstrength.timer

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codex.streetstrength.MainActivity
import com.codex.streetstrength.R

object RestTimerAlert {
    private const val TAG = "RestTimerAlert"
    const val ACTION_STOP_ALERT = "com.codex.streetstrength.timer.STOP_ALERT"

    @Volatile
    private var vibrationActive = false

    @Volatile
    private var activeAlertTimerId: Long? = null

    private val FINISH_VIBRATION_PATTERN = longArrayOf(0, 220, 140, 320, 180, 540)
    private const val CHANNEL_RUNNING = "rest_running"
    private const val CHANNEL_FINISHED = "rest_finished_silent_v2"
    private const val NOTIFICATION_ID_FOREGROUND = 401
    private const val REQUEST_CODE_CONTENT = 21
    private const val REQUEST_CODE_STOP = 22
    private const val ALERT_PREFS = "rest_timer_alert"
    private const val KEY_STOPPED_TIMER_ID = "stopped_timer_id"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            "休息计时",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        val finished = NotificationChannel(
            CHANNEL_FINISHED,
            "计时结束",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(running)
        notificationManager.createNotificationChannel(finished)
    }

    @SuppressLint("MissingPermission")
    fun showFinishedNotification(
        context: Context,
        timerId: Long,
    ) {
        if (isStopRequested(context, timerId)) {
            Log.i(TAG, "Skipping finished notification for stopped timer $timerId")
            return
        }
        ensureChannels(context)
        if (canPostNotifications(context)) {
            runCatching {
                NotificationManagerCompat.from(context).notify(
                    NOTIFICATION_ID_FOREGROUND,
                    buildFinishedNotification(context, timerId),
                )
            }.onFailure {
                Log.w(TAG, "Failed to show finished notification for timer $timerId", it)
            }
        }
    }

    fun buildFinishedNotification(
        context: Context,
        timerId: Long,
    ) =
        NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("休息结束")
            .setContentText("回到训练页后手动开始下一组")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(0)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent(context, stopAlert = true, timerId = timerId))
            .addAction(R.drawable.ic_app, "打开训练", contentIntent(context, stopAlert = true, timerId = timerId))
            .addAction(R.drawable.ic_app, "关闭提醒", stopIntent(context, timerId))
            .build()

    fun stop(
        context: Context,
        timerId: Long? = null,
    ) {
        val stoppedTimerId = timerId ?: activeAlertTimerId
        markStopRequested(context, stoppedTimerId)
        if (stoppedTimerId != null) {
            Log.i(TAG, "Rest alert stop requested; suppressing future finish for timer $stoppedTimerId")
        }
        cancelVibration(context)
        activeAlertTimerId = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_FOREGROUND)
    }

    @SuppressLint("MissingPermission")
    fun startContinuousVibration(
        context: Context,
        timerId: Long,
    ): Boolean {
        if (isStopRequested(context, timerId)) {
            Log.i(TAG, "Skipping continuous vibration for stopped timer $timerId")
            return false
        }
        if (vibrationActive && activeAlertTimerId == timerId) {
            Log.i(TAG, "Continuous rest-finished vibration already active for timer $timerId")
            return true
        }
        if (vibrationActive) {
            Log.i(TAG, "Restarting continuous rest-finished vibration")
            cancelVibration(context)
        } else {
            Log.i(TAG, "Starting continuous rest-finished vibration for timer $timerId")
        }
        activeAlertTimerId = timerId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            val vibrator = vibratorManager.defaultVibrator
            if (!vibrator.hasVibrator()) return false
            vibrator.vibrate(
                VibrationEffect.createWaveform(FINISH_VIBRATION_PATTERN, 0),
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build(),
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (!vibrator.hasVibrator()) return false
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                VibrationEffect.createWaveform(FINISH_VIBRATION_PATTERN, 0),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build(),
            )
        }
        vibrationActive = true
        return true
    }

    fun cancelVibration(context: Context) {
        Log.i(TAG, "Canceling continuous rest-finished vibration")
        vibrationActive = false
        activeAlertTimerId = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            runCatching { vibratorManager.cancel() }
            runCatching { vibratorManager.defaultVibrator.cancel() }
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.cancel()
        }
    }

    fun isStopRequested(
        context: Context,
        timerId: Long,
    ): Boolean = prefs(context).getLong(KEY_STOPPED_TIMER_ID, -1L) == timerId

    fun clearStopRequest(
        context: Context,
        timerId: Long,
    ) {
        val prefs = prefs(context)
        if (prefs.getLong(KEY_STOPPED_TIMER_ID, -1L) == timerId) {
            prefs.edit().remove(KEY_STOPPED_TIMER_ID).apply()
        }
    }

    private fun markStopRequested(
        context: Context,
        timerId: Long?,
    ) {
        if (timerId == null || timerId <= 0L) return
        Log.i(TAG, "Marked rest alert stopped for timer $timerId")
        prefs(context).edit().putLong(KEY_STOPPED_TIMER_ID, timerId).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE)

    private fun contentIntent(
        context: Context,
        stopAlert: Boolean,
        timerId: Long,
    ): PendingIntent? {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (stopAlert) {
            launchIntent.putExtra(RestTimerService.EXTRA_STOP_ALERT, true)
            launchIntent.putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopIntent(
        context: Context,
        timerId: Long,
    ): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = ACTION_STOP_ALERT
            putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
