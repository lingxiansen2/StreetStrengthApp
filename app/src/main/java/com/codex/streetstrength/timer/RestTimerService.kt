package com.codex.streetstrength.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.codex.streetstrength.R
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.model.TimerState
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

    private var inProcessAlarmListener: AlarmManager.OnAlarmListener? = null
    private var countdownJob: Job? = null
    private var stopRequested = false

    @Volatile
    private var isFinishingTimer = false

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
                startTimer(timerId, endElapsedMs)
            }

            ACTION_FINISH -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                if (timerId <= 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                serviceScope.launch { finishTimer(timerId) }
            }

            ACTION_STOP -> stopTimerService()

            else -> restoreLatestActiveTimer()
        }
        return START_STICKY
    }

    private fun startTimer(
        timerId: Long,
        endElapsedMs: Long,
    ) {
        resetForNewTimer(cancelPersistentAlarm = true)
        stopRequested = false
        val remaining = (endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_FOREGROUND,
            buildRunningNotification(formatRestClock(remaining)),
            foregroundServiceTypeCompat(),
        )
        if (remaining <= 0L) {
            dispatchFinishThroughReceiver(timerId)
        } else {
            scheduleTimerAlarms(timerId, endElapsedMs)
            startCountdown(timerId, endElapsedMs)
        }
    }

    private fun restoreLatestActiveTimer() {
        serviceScope.launch {
            val timer = runCatching { repository.getLatestActiveRestTimer() }.getOrNull()
            when {
                timer == null -> stopSelf()
                timer.state == TimerState.FIRED -> startFinishedAlert()
                timer.endElapsedRealtimeMs <= SystemClock.elapsedRealtime() -> dispatchFinishThroughReceiver(timer.id)
                else -> startTimer(timer.id, timer.endElapsedRealtimeMs)
            }
        }
    }

    private fun foregroundServiceTypeCompat(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
    }

    private fun scheduleTimerAlarms(
        timerId: Long,
        endElapsedMs: Long,
    ) {
        cancelAlarm()
        schedulePersistentAlarm(timerId, endElapsedMs)
        scheduleInProcessExactAlarm(timerId, endElapsedMs)
    }

    private fun scheduleInProcessExactAlarm(
        timerId: Long,
        endElapsedMs: Long,
    ) {
        val listener = AlarmManager.OnAlarmListener {
            dispatchFinishThroughReceiver(timerId)
        }
        inProcessAlarmListener = listener
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            endElapsedMs,
            "street_strength_rest_timer",
            listener,
            Handler(mainLooper),
        )
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedulePersistentAlarm(
        timerId: Long,
        endElapsedMs: Long,
    ) {
        val pendingIntent = finishAlarmIntent(timerId)
        val remainingMs = (endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val triggerAtWallClockMs = System.currentTimeMillis() + remainingMs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    endElapsedMs,
                    pendingIntent,
                )
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        triggerAtWallClockMs,
                        alarmClockShowIntent(),
                    ),
                    pendingIntent,
                )
            }
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                endElapsedMs,
                pendingIntent,
            )
        }
        Log.i(TAG, "Scheduled rest finish receiver alarm for timer $timerId at $triggerAtWallClockMs")
    }

    private fun alarmClockShowIntent(): PendingIntent? {
        val launchIntent = (packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.codex.streetstrength.MainActivity::class.java)).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_ALARM_CLOCK_SHOW,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startCountdown(
        timerId: Long,
        endElapsedMs: Long,
    ) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            while (isActive) {
                val remaining = endElapsedMs - SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    dispatchFinishThroughReceiver(timerId)
                    break
                }
                notificationManager.notify(
                    NOTIFICATION_ID_FOREGROUND,
                    buildRunningNotification(formatRestClock(remaining.coerceAtLeast(0L))),
                )
                delay(1_000L)
            }
        }
    }

    private fun dispatchFinishThroughReceiver(timerId: Long) {
        runCatching {
            finishAlarmIntent(timerId).send()
            Log.i(TAG, "Dispatched rest finish through receiver for timer $timerId")
        }.onFailure {
            Log.w(TAG, "Failed to dispatch rest finish through receiver for timer: $timerId", it)
            serviceScope.launch { finishTimer(timerId) }
        }
    }

    private suspend fun finishTimer(timerId: Long) {
        if (!markTimerAsFinishing()) return
        cancelAlarm()
        cancelPersistentAlarm()
        countdownJob?.cancel()
        val alertStarted = runCatching { startFinishedAlert() }
            .onFailure {
                Log.w(TAG, "Failed to start finished alert for timer: $timerId", it)
                stopTimerService()
            }
            .isSuccess
        if (!alertStarted) return
        runCatching { repository.markRestTimerFired(timerId) }
            .onFailure { Log.w(TAG, "Failed to mark rest timer as fired: $timerId", it) }
    }

    private fun markTimerAsFinishing(): Boolean {
        if (isFinishingTimer) return false
        synchronized(this) {
            if (isFinishingTimer) return false
            isFinishingTimer = true
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startFinishedAlert() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_FOREGROUND,
            RestTimerAlert.buildFinishedNotification(this),
            foregroundServiceTypeCompat(),
        )
        RestTimerAlert.startContinuousVibration(this)
    }

    @SuppressLint("MissingPermission")
    private fun buildRunningNotification(remaining: String) =
        NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("休息中")
            .setContentText(remaining)
            .setShowWhen(false)
            .setUsesChronometer(false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(stopAlert = false))
            .build()

    private fun contentIntent(stopAlert: Boolean): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (stopAlert) {
            launchIntent.putExtra(EXTRA_STOP_ALERT, true)
        }
        return PendingIntent.getActivity(
            this,
            if (stopAlert) 11 else 10,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun finishAlarmIntent(timerId: Long): PendingIntent {
        val intent = Intent(this, RestTimerReceiver::class.java).apply {
            action = ACTION_FINISH
            putExtra(EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_FINISH_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingFinishAlarmIntent(): PendingIntent? {
        val intent = Intent(this, RestTimerReceiver::class.java).apply {
            action = ACTION_FINISH
        }
        return PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_FINISH_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
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
                enableVibration(false)
            }
            systemNotificationManager.createNotificationChannel(running)
            systemNotificationManager.createNotificationChannel(finished)
        }
    }

    private fun cancelVibration() {
        RestTimerAlert.cancelVibration(this)
    }

    private fun resetForNewTimer(cancelPersistentAlarm: Boolean) {
        cancelAlarm()
        if (cancelPersistentAlarm) {
            cancelPersistentAlarm()
        }
        countdownJob?.cancel()
        cancelVibration()
        notificationManager.cancel(NOTIFICATION_ID_FOREGROUND)
        isFinishingTimer = false
    }

    private fun stopTimerService() {
        stopRequested = true
        resetForNewTimer(cancelPersistentAlarm = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cancelAlarm() {
        inProcessAlarmListener?.let { alarmManager.cancel(it) }
        inProcessAlarmListener = null
    }

    private fun cancelPersistentAlarm() {
        existingFinishAlarmIntent()?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    override fun onDestroy() {
        cancelAlarm()
        countdownJob?.cancel()
        if (stopRequested) {
            cancelPersistentAlarm()
            cancelVibration()
            notificationManager.cancel(NOTIFICATION_ID_FOREGROUND)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RestTimerService"
        private const val ACTION_START = "com.codex.streetstrength.timer.START"
        private const val ACTION_STOP = "com.codex.streetstrength.timer.STOP"
        const val ACTION_FINISH = "com.codex.streetstrength.timer.FINISH"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        private const val EXTRA_END_ELAPSED_MS = "extra_end_elapsed_ms"
        const val EXTRA_STOP_ALERT = "extra_stop_alert"
        private const val CHANNEL_RUNNING = "rest_running"
        private const val CHANNEL_FINISHED = "rest_finished"
        private const val NOTIFICATION_ID_FOREGROUND = 401
        private const val REQUEST_CODE_FINISH_ALARM = 13
        private const val REQUEST_CODE_ALARM_CLOCK_SHOW = 14

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
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_STOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startFinishFromAlarm(
            context: Context,
            timerId: Long,
        ) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_FINISH
                putExtra(EXTRA_TIMER_ID, timerId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun shouldStopAlertFromIntent(intent: Intent?): Boolean =
            intent?.getBooleanExtra(EXTRA_STOP_ALERT, false) == true
    }
}
