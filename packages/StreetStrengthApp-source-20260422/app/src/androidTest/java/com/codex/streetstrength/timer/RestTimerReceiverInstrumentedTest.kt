package com.codex.streetstrength.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.codex.streetstrength.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestTimerReceiverInstrumentedTest {

    @get:Rule
    val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() {
        alarmManager.cancel(finishPendingIntent())
        RestTimerAlert.stop(context)
    }

    @Test
    fun alarmClockDeliveryPostsRestFinishedNotification() {
        RestTimerAlert.stop(context)

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                System.currentTimeMillis() + 2_000L,
                showPendingIntent(),
            ),
            finishPendingIntent(),
        )

        val delivered = waitUntil(timeoutMs = 15_000L) {
            notificationManager.activeNotifications.any { notification ->
                notification.packageName == context.packageName &&
                    notification.id == REST_NOTIFICATION_ID
            }
        }

        assertTrue("Rest finished notification was not posted after alarm delivery.", delivered)
    }

    private fun finishPendingIntent(): PendingIntent {
        val intent = Intent(context, RestTimerReceiver::class.java).apply {
            action = RestTimerService.ACTION_FINISH
            putExtra(RestTimerService.EXTRA_TIMER_ID, TEST_TIMER_ID)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_FINISH_TEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW_TEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(250L)
        }
        return condition()
    }

    private companion object {
        const val REST_NOTIFICATION_ID = 401
        const val REQUEST_CODE_FINISH_TEST = 9_401
        const val REQUEST_CODE_SHOW_TEST = 9_402
        const val TEST_TIMER_ID = 9_999_401L
    }
}
