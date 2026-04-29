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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codex.streetstrength.R

object RestTimerAlert {
    const val ACTION_STOP_ALERT = "com.codex.streetstrength.timer.STOP_ALERT"

    private val FINISH_VIBRATION_PATTERN = longArrayOf(0, 220, 140, 320, 180, 540)
    private const val CHANNEL_RUNNING = "rest_running"
    private const val CHANNEL_FINISHED = "rest_finished"
    private const val NOTIFICATION_ID_FOREGROUND = 401
    private const val REQUEST_CODE_CONTENT = 21
    private const val REQUEST_CODE_STOP = 22

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
    fun showFinishedAlert(context: Context) {
        ensureChannels(context)
        if (canPostNotifications(context)) {
            runCatching {
                NotificationManagerCompat.from(context).notify(
                    NOTIFICATION_ID_FOREGROUND,
                    buildFinishedNotification(context),
                )
            }
        }
        startContinuousVibration(context)
    }

    fun buildFinishedNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("休息结束")
            .setContentText("回到训练页后手动开始下一项")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent(context, stopAlert = true))
            .addAction(R.drawable.ic_app, "打开训练", contentIntent(context, stopAlert = true))
            .addAction(R.drawable.ic_app, "关闭提醒", stopIntent(context))
            .build()

    fun stop(context: Context) {
        cancelVibration(context)
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_FOREGROUND)
    }

    fun startContinuousVibration(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(
                VibrationEffect.createWaveform(FINISH_VIBRATION_PATTERN, 0),
                VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build(),
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (!vibrator.hasVibrator()) return
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                VibrationEffect.createWaveform(FINISH_VIBRATION_PATTERN, 0),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build(),
            )
        }
    }

    fun cancelVibration(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.cancel()
        }
    }

    private fun contentIntent(context: Context, stopAlert: Boolean): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (stopAlert) {
            launchIntent.putExtra(RestTimerService.EXTRA_STOP_ALERT, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopIntent(context: Context): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = ACTION_STOP_ALERT
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
