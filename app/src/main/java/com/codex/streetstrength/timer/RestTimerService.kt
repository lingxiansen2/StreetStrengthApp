package com.codex.streetstrength.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codex.streetstrength.R
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.ui.formatRestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RestTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { (application as StreetStrengthApp).trainingRepository }
    private val alarmManager by lazy { getSystemService(AlarmManager::class.java) }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val systemNotificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var alarmListener: AlarmManager.OnAlarmListener? = null
    private var countdownJob: Job? = null
    private var activeTimerId: Long? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                val endElapsedMs = intent.getLongExtra(EXTRA_END_ELAPSED_MS, -1L)
                if (timerId <= 0L || endElapsedMs <= 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                activeTimerId = timerId
                startForeground(
                    NOTIFICATION_ID_RUNNING,
                    buildRunningNotification(formatRestClock(endElapsedMs - SystemClock.elapsedRealtime())),
                )
                scheduleExactAlarm(timerId, endElapsedMs)
                startCountdown(endElapsedMs)
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleExactAlarm(timerId: Long, endElapsedMs: Long) {
        cancelAlarm()
        val listener = AlarmManager.OnAlarmListener {
            serviceScope.launch {
                repository.markRestTimerFired(timerId)
                vibrate()
                showFinishedNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        alarmListener = listener
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            endElapsedMs,
            "street_strength_rest_timer",
            listener,
            Handler(mainLooper),
        )
    }

    private fun startCountdown(endElapsedMs: Long) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (isActive) {
                val remaining = endElapsedMs - SystemClock.elapsedRealtime()
                if (remaining <= 0L) break
                notificationManager.notify(
                    NOTIFICATION_ID_RUNNING,
                    buildRunningNotification(formatRestClock(remaining)),
                )
                delay(1_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showFinishedNotification() {
        notificationManager.notify(
            NOTIFICATION_ID_FINISHED,
            NotificationCompat.Builder(this, CHANNEL_FINISHED)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle("休息结束")
                .setContentText("进入下一组")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 200, 120, 240))
                .setContentIntent(contentIntent())
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun buildRunningNotification(remaining: String) = NotificationCompat.Builder(this, CHANNEL_RUNNING)
        .setSmallIcon(R.drawable.ic_app)
        .setContentTitle("休息中")
        .setContentText(remaining)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(contentIntent())
        .build()

    private fun contentIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            10,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 120, 240)
            }
            systemNotificationManager.createNotificationChannel(running)
            systemNotificationManager.createNotificationChannel(finished)
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VibratorManager::class.java)
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 220), -1),
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun cancelAlarm() {
        alarmListener?.let { alarmManager.cancel(it) }
        alarmListener = null
    }

    override fun onDestroy() {
        cancelAlarm()
        countdownJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.codex.streetstrength.timer.START"
        private const val ACTION_STOP = "com.codex.streetstrength.timer.STOP"
        private const val EXTRA_TIMER_ID = "extra_timer_id"
        private const val EXTRA_END_ELAPSED_MS = "extra_end_elapsed_ms"
        private const val CHANNEL_RUNNING = "rest_running"
        private const val CHANNEL_FINISHED = "rest_finished"
        private const val NOTIFICATION_ID_RUNNING = 401
        private const val NOTIFICATION_ID_FINISHED = 402

        fun start(
            context: Context,
            timerId: Long,
            endElapsedMs: Long,
        ) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_END_ELAPSED_MS, endElapsedMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RestTimerService::class.java))
        }
    }
}
