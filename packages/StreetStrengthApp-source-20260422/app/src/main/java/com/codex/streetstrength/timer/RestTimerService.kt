package com.codex.streetstrength.timer

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.codex.streetstrength.MainActivity
import com.codex.streetstrength.R
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.model.TimerState
import java.text.DateFormat
import java.util.Date
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
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { (application as StreetStrengthApp).trainingRepository }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val systemNotificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private var countdownJob: Job? = null
    private var prepareTimeoutJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopRequested = false
    private var preparedWithoutTimer = false

    @Volatile
    private var isFinishingTimer = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action ?: "RESTORE"} startId=$startId")
        when (intent?.action) {
            ACTION_PREPARE -> {
                val endAtWallClockMs = intent.getLongExtra(EXTRA_END_AT_WALL_CLOCK_MS, -1L)
                prepareTimer(
                    endAtWallClockMs = endAtWallClockMs.takeIf { it > 0L }
                        ?: System.currentTimeMillis() + PREPARE_TIMEOUT_MS,
                )
            }

            ACTION_START -> {
                val timerId = intent.getLongExtra(EXTRA_TIMER_ID, -1L)
                val endElapsedMs = intent.getLongExtra(EXTRA_END_ELAPSED_MS, -1L)
                val endAtWallClockMs = intent.getLongExtra(EXTRA_END_AT_WALL_CLOCK_MS, -1L)
                if (timerId <= 0L || endElapsedMs <= 0L) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTimer(
                    timerId = timerId,
                    endElapsedMs = endElapsedMs,
                    endAtWallClockMs = endAtWallClockMs.takeIf { it > 0L }
                        ?: RestTimerClock.endAtWallClockMsFromElapsed(endElapsedMs),
                )
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

    private fun prepareTimer(endAtWallClockMs: Long) {
        resetForNewTimer(cancelPersistentAlarm = false)
        stopRequested = false
        preparedWithoutTimer = true
        acquireTimerWakeLock(PREPARE_TIMEOUT_MS + WAKE_LOCK_GRACE_MS)
        Log.i(TAG, "Preparing rest timer service")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_FOREGROUND,
            buildRunningNotification(endAtWallClockMs),
            foregroundServiceTypeCompat(),
        )
        prepareTimeoutJob?.cancel()
        prepareTimeoutJob = timerScope.launch {
            delay(PREPARE_TIMEOUT_MS)
            if (preparedWithoutTimer && !stopRequested) {
                Log.w(TAG, "Prepared rest timer service timed out before timer start")
                stopTimerService()
            }
        }
    }

    private fun startTimer(
        timerId: Long,
        endElapsedMs: Long,
        endAtWallClockMs: Long,
    ) {
        resetForNewTimer(cancelPersistentAlarm = false)
        stopRequested = false
        preparedWithoutTimer = false
        val remaining = (endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        acquireTimerWakeLock(remaining + WAKE_LOCK_GRACE_MS)
        Log.i(TAG, "Starting rest timer $timerId remaining=${remaining}ms")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_FOREGROUND,
            buildRunningNotification(endAtWallClockMs),
            foregroundServiceTypeCompat(),
        )
        if (remaining <= 0L) {
            dispatchFinishThroughReceiver(timerId)
        } else {
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
                else -> startTimer(
                    timerId = timer.id,
                    endElapsedMs = timer.endElapsedRealtimeMs,
                    endAtWallClockMs = RestTimerClock.endAtWallClockMs(timer.createdAt, timer.durationSec),
                )
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

    private fun startCountdown(
        timerId: Long,
        endElapsedMs: Long,
    ) {
        countdownJob?.cancel()
        countdownJob = timerScope.launch {
            val remaining = (endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining + FINISH_DISPATCH_GRACE_MS)
            if (!isActive || stopRequested || isFinishingTimer) return@launch
            if (SystemClock.elapsedRealtime() >= endElapsedMs) {
                Log.i(TAG, "Countdown fallback dispatching rest finish for timer $timerId")
                dispatchFinishThroughReceiver(timerId)
            }
        }
    }

    private fun dispatchFinishThroughReceiver(timerId: Long) {
        if (!RestTimerAlarmScheduler.dispatchFinish(this, timerId)) {
            serviceScope.launch { finishTimer(timerId) }
        }
    }

    private suspend fun finishTimer(timerId: Long) {
        if (!markTimerAsFinishing()) return
        cancelPersistentAlarm()
        countdownJob?.cancel()
        releaseTimerWakeLock()
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
    private fun buildRunningNotification(endAtWallClockMs: Long) =
        NotificationCompat.Builder(this, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle("休息中")
          .setContentText("预计 ${formatWallClockTime(endAtWallClockMs)} 结束")
          .setWhen(endAtWallClockMs)
          .setShowWhen(true)
          .setUsesChronometer(false)
          .setOnlyAlertOnce(true)
          .setOngoing(true)
          .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent(stopAlert = false))
            .build()

    private fun formatWallClockTime(timeMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))

    private fun contentIntent(stopAlert: Boolean): PendingIntent? {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
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
        if (cancelPersistentAlarm) {
            cancelPersistentAlarm()
        }
        countdownJob?.cancel()
        prepareTimeoutJob?.cancel()
        preparedWithoutTimer = false
        releaseTimerWakeLock()
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

    private fun cancelPersistentAlarm() {
        RestTimerAlarmScheduler.cancel(this)
    }

    private fun acquireTimerWakeLock(timeoutMs: Long) {
        val boundedTimeoutMs = timeoutMs
            .coerceAtLeast(MIN_WAKE_LOCK_TIMEOUT_MS)
            .coerceAtMost(MAX_WAKE_LOCK_TIMEOUT_MS)
        releaseTimerWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:rest_timer")
            .apply {
                setReferenceCounted(false)
                acquire(boundedTimeoutMs)
            }
        Log.i(TAG, "Acquired rest timer wake lock for ${boundedTimeoutMs}ms")
    }

    private fun releaseTimerWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                runCatching { lock.release() }
                    .onFailure { Log.w(TAG, "Failed to release rest timer wake lock", it) }
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        prepareTimeoutJob?.cancel()
        if (stopRequested) {
            cancelPersistentAlarm()
        }
        releaseTimerWakeLock()
        cancelVibration()
        notificationManager.cancel(NOTIFICATION_ID_FOREGROUND)
        stopForeground(STOP_FOREGROUND_REMOVE)
        timerScope.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RestTimerService"
        private const val FINISH_DISPATCH_GRACE_MS = 500L
        private const val PREPARE_TIMEOUT_MS = 30_000L
        private const val WAKE_LOCK_GRACE_MS = 60_000L
        private const val MIN_WAKE_LOCK_TIMEOUT_MS = 10_000L
        private const val MAX_WAKE_LOCK_TIMEOUT_MS = 30 * 60 * 1_000L
        private const val ACTION_PREPARE = "com.codex.streetstrength.timer.PREPARE"
        private const val ACTION_START = "com.codex.streetstrength.timer.START"
        private const val ACTION_STOP = "com.codex.streetstrength.timer.STOP"
        const val ACTION_FINISH = "com.codex.streetstrength.timer.FINISH"
        const val EXTRA_TIMER_ID = "extra_timer_id"
        private const val EXTRA_END_ELAPSED_MS = "extra_end_elapsed_ms"
        private const val EXTRA_END_AT_WALL_CLOCK_MS = "extra_end_at_wall_clock_ms"
        const val EXTRA_STOP_ALERT = "extra_stop_alert"
        private const val CHANNEL_RUNNING = "rest_running"
        private const val CHANNEL_FINISHED = "rest_finished_silent_v2"
        private const val NOTIFICATION_ID_FOREGROUND = 401

        fun prepare(
            context: Context,
            endAtWallClockMs: Long,
        ) {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_PREPARE
                putExtra(EXTRA_END_AT_WALL_CLOCK_MS, endAtWallClockMs)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "Failed to prepare rest timer service", it) }
        }

        fun start(
            context: Context,
            timerId: Long,
            endElapsedMs: Long,
            endAtWallClockMs: Long = RestTimerClock.endAtWallClockMsFromElapsed(endElapsedMs),
        ) {
            RestTimerAlarmScheduler.schedule(
                context = context,
                timerId = timerId,
                endElapsedMs = endElapsedMs,
                endAtWallClockMs = endAtWallClockMs,
            )
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TIMER_ID, timerId)
                putExtra(EXTRA_END_ELAPSED_MS, endElapsedMs)
                putExtra(EXTRA_END_AT_WALL_CLOCK_MS, endAtWallClockMs)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "Failed to start rest timer service", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RestTimerService::class.java)
            runCatching {
                context.stopService(intent)
            }.onFailure { Log.w(TAG, "Failed to stop rest timer service", it) }
        }

        fun startFinishFromAlarm(
            context: Context,
            timerId: Long,
        ): Boolean {
            val intent = Intent(context, RestTimerService::class.java).apply {
                action = ACTION_FINISH
                putExtra(EXTRA_TIMER_ID, timerId)
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "Started rest finish service for timer $timerId from alarm")
            }.onFailure { Log.w(TAG, "Failed to start rest finish service", it) }
                .isSuccess
        }

        fun shouldStopAlertFromIntent(intent: Intent?): Boolean =
            intent?.getBooleanExtra(EXTRA_STOP_ALERT, false) == true
    }
}
