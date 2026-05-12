package com.codex.streetstrength.timer

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.local.ActiveRestTimerEntity
import com.codex.streetstrength.data.local.DayTaskEntity
import com.codex.streetstrength.data.local.ExerciseCategoryEntity
import com.codex.streetstrength.data.local.ExerciseTemplateEntity
import com.codex.streetstrength.data.local.ExerciseVariantEntity
import com.codex.streetstrength.data.local.GoalEntity
import com.codex.streetstrength.data.local.PlanCycleEntity
import com.codex.streetstrength.data.local.PlanDayEntity
import com.codex.streetstrength.data.local.PlanWeekEntity
import com.codex.streetstrength.data.local.TaskSetPlanEntity
import com.codex.streetstrength.data.local.TrainingDao
import com.codex.streetstrength.data.local.WorkoutSessionEntity
import com.codex.streetstrength.data.model.CategoryType
import com.codex.streetstrength.data.model.GoalStatus
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.model.SessionStatus
import com.codex.streetstrength.data.model.SourceType
import com.codex.streetstrength.data.model.TimerState
import com.codex.streetstrength.ui.formatRestClock
import com.codex.streetstrength.ui.theme.StreetStrengthTheme
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RestReminderSelfTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            StreetStrengthTheme {
                var state by remember { mutableStateOf(SelfTestUiState()) }
                var nowWallClockMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

                LaunchedEffect(state.endAtWallClockMs) {
                    while (state.endAtWallClockMs != null) {
                        nowWallClockMs = System.currentTimeMillis()
                        delay(250L)
                    }
                }

                RestReminderSelfTestScreen(
                    state = state,
                    remainingMs = state.endAtWallClockMs
                        ?.let { RestTimerClock.remainingFromWallClock(it, nowWallClockMs) }
                        ?: 0L,
                    onStart = {
                        lifecycleScope.launch {
                            runCatching {
                                val timer = startSelfTestTimer()
                                state = SelfTestUiState(
                                    timerId = timer.id,
                                    endAtWallClockMs = RestTimerClock.endAtWallClockMs(timer.createdAt, timer.durationSec),
                                    status = "$SELF_TEST_DURATION_SEC 秒休息倒计时已启动",
                                )
                            }.onFailure { error ->
                                state = SelfTestUiState(status = "启动失败：${error.message ?: error::class.java.simpleName}")
                            }
                        }
                    },
                    onStopAlert = {
                        lifecycleScope.launch {
                            runCatching {
                                state.timerId?.let { cancelDebugTimer(it) }
                                cancelSelfTestReceiverAlarm()
                                RestTimerController.stopRestAlert(this@RestReminderSelfTestActivity, state.timerId)
                                state = SelfTestUiState(status = "提醒和震动已关闭")
                            }.onFailure { error ->
                                state = state.copy(status = "关闭失败：${error.message ?: error::class.java.simpleName}")
                            }
                        }
                    },
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATIONS)
        }
    }

    private suspend fun startSelfTestTimer(): ActiveRestTimerEntity {
        cancelSelfTestReceiverAlarm()
        RestTimerController.stopRestAlert(this)

        val timer = createDebugTimer(durationSec = SELF_TEST_DURATION_SEC)
        RestTimerService.start(
            context = this,
            timerId = timer.id,
            endElapsedMs = timer.endElapsedRealtimeMs,
            endAtWallClockMs = RestTimerClock.endAtWallClockMs(timer.createdAt, timer.durationSec),
        )
        return timer
    }

    private suspend fun createDebugTimer(durationSec: Int): ActiveRestTimerEntity {
        val app = application as StreetStrengthApp
        val database = app.database
        val dao = database.trainingDao()
        val now = System.currentTimeMillis()
        val endElapsedRealtimeMs = SystemClock.elapsedRealtime() + durationSec * 1_000L

        return database.withTransaction {
            val categoryId = dao.getCategoryByType(CategoryType.CORE)?.id
                ?: dao.insertCategory(
                    ExerciseCategoryEntity(
                        type = CategoryType.CORE,
                        name = "核心",
                        sortOrder = 99,
                    ),
                )
            val templateId = dao.getTemplateByCategoryAndName(categoryId, DEBUG_TEMPLATE_NAME)?.id
                ?: dao.insertTemplate(
                    ExerciseTemplateEntity(
                        categoryId = categoryId,
                        name = DEBUG_TEMPLATE_NAME,
                        sourceType = SourceType.CUSTOM,
                        supportsExternalLoad = false,
                        defaultMetricType = MetricType.HOLD_SECONDS,
                        cue = "Debug only",
                        sortOrder = 9_999,
                    ),
                )
            val variantId = dao.getVariantByTemplateAndName(templateId, DEBUG_VARIANT_NAME)?.id
                ?: dao.insertVariant(
                    ExerciseVariantEntity(
                        templateId = templateId,
                        name = DEBUG_VARIANT_NAME,
                        metricType = MetricType.HOLD_SECONDS,
                        sortOrder = 9_999,
                    ),
                )

            val date = findDebugPlanDate(dao)
            val goalId = dao.insertGoal(
                GoalEntity(
                    title = "Debug Rest Reminder",
                    note = DEBUG_NOTE,
                    startDate = LocalDate.now().toString(),
                    status = GoalStatus.ACTIVE,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val cycleId = dao.insertCycle(
                PlanCycleEntity(
                    goalId = goalId,
                    name = "Debug Rest Reminder",
                    startDate = date.toString(),
                    sortOrder = 9_999,
                    createdAt = now,
                ),
            )
            val weekId = dao.insertWeek(
                PlanWeekEntity(
                    cycleId = cycleId,
                    weekIndex = 1,
                    focus = "Debug",
                    startDate = date.toString(),
                    endDate = date.toString(),
                ),
            )
            val dayId = dao.insertDay(
                PlanDayEntity(
                    weekId = weekId,
                    planDate = date.toString(),
                    title = "Debug",
                    note = DEBUG_NOTE,
                ),
            )
            val taskId = dao.insertDayTask(
                DayTaskEntity(
                    dayId = dayId,
                    templateId = templateId,
                    variantId = variantId,
                    orderInDay = 1,
                    restSec = durationSec,
                    note = DEBUG_NOTE,
                ),
            )
            dao.insertTaskSetPlans(
                listOf(
                    TaskSetPlanEntity(
                        taskId = taskId,
                        setIndex = 1,
                        targetHoldSec = durationSec,
                    ),
                ),
            )
            val sessionId = dao.insertWorkoutSession(
                WorkoutSessionEntity(
                    dayId = dayId,
                    startedAt = now,
                    status = SessionStatus.IN_PROGRESS,
                ),
            )
            val timer = ActiveRestTimerEntity(
                sessionId = sessionId,
                taskId = taskId,
                setIndex = 1,
                durationSec = durationSec,
                endElapsedRealtimeMs = endElapsedRealtimeMs,
                state = TimerState.RUNNING,
                createdAt = now,
            )
            val timerId = dao.insertRestTimer(timer)
            timer.copy(id = timerId)
        }
    }

    private suspend fun findDebugPlanDate(dao: TrainingDao): LocalDate {
        var candidate = LocalDate.now().plusYears(200)
        repeat(366) {
            val existing = dao.getPlanDayByDate(candidate.toString())
            if (existing == null || existing.note == DEBUG_NOTE) {
                return candidate
            }
            candidate = candidate.plusDays(1)
        }
        return LocalDate.now().plusYears(201)
    }

    private suspend fun cancelDebugTimer(timerId: Long) {
        val database = (application as StreetStrengthApp).database
        val dao = database.trainingDao()
        database.withTransaction {
            dao.getRestTimer(timerId)?.let { timer ->
                dao.updateRestTimer(timer.copy(state = TimerState.CANCELLED))
            }
        }
    }

    private fun cancelSelfTestReceiverAlarm() {
        selfTestFinishIntent(timerId = 0L, flags = PendingIntent.FLAG_NO_CREATE)?.let { pendingIntent ->
            getSystemService(AlarmManager::class.java).cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun selfTestFinishIntent(timerId: Long, flags: Int): PendingIntent? {
        val intent = Intent(this, RestTimerReceiver::class.java).apply {
            action = RestTimerService.ACTION_FINISH
            if (timerId > 0L) putExtra(RestTimerService.EXTRA_TIMER_ID, timerId)
        }
        return PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_SELF_TEST_FINISH,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SELF_TEST_DURATION_SEC = 10
        const val DEBUG_NOTE = "debug_rest_reminder_self_test"
        const val DEBUG_TEMPLATE_NAME = "Debug Rest Reminder"
        const val DEBUG_VARIANT_NAME = "Self Test"
        const val REQUEST_CODE_NOTIFICATIONS = 9_410
        const val REQUEST_CODE_SELF_TEST_FINISH = 9_411
    }
}

@Composable
private fun RestReminderSelfTestScreen(
    state: SelfTestUiState,
    remainingMs: Long,
    onStart: () -> Unit,
    onStopAlert: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "真机后台休息提醒自测",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "10 秒倒计时会启动正式休息计时服务。启动后切到后台或锁屏，到点应由正式接收器发出提醒和震动。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (state.endAtWallClockMs == null) "--:--" else formatRestClock(remainingMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.status,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.timerId?.let { timerId ->
                        Text(
                            text = "Timer #$timerId",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("启动 10 秒自测")
                }
                OutlinedButton(
                    onClick = onStopAlert,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("关闭震动")
                }
            }
        }
    }
}

private data class SelfTestUiState(
    val timerId: Long? = null,
    val endAtWallClockMs: Long? = null,
    val status: String = "未启动",
)
