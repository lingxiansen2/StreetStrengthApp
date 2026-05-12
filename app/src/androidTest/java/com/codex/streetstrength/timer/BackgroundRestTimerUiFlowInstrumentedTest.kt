package com.codex.streetstrength.timer

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.codex.streetstrength.MainActivity
import com.codex.streetstrength.StreetStrengthApp
import com.codex.streetstrength.data.model.MetricType
import com.codex.streetstrength.data.repository.DayTaskDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class BackgroundRestTimerUiFlowInstrumentedTest {

    private val app: StreetStrengthApp = ApplicationProvider.getApplicationContext()

    private val notificationPermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val seedRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                seedSingleRestingSetPlan()
                base.evaluate()
            }
        }
    }

    private val composeRule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(notificationPermission)
        .around(seedRule)
        .around(composeRule)

    @After
    fun tearDown() {
        RestTimerAlert.stop(app)
        app.stopService(Intent(app, RestTimerService::class.java))
        scenario?.let { runCatching { it.close() } }
        scenario = null
    }

    @Test
    fun backgroundRestFinishThenContinueAndEndDoesNotCrash() {
        launchActivity()
        waitForText("开始训练")
        clickFirstText("开始训练")

        waitForText("完成本组")
        clickFirstText("完成本组")

        waitForText("休息中")
        moveTaskToBack()
        Thread.sleep(90_000L)

        bringAppToForeground()
        waitForText("开始下一项")
        clickFirstText("开始下一项")

        waitForText("完成本组")
        clickFirstText("结束训练")

        waitForText("当前训练会退出")
        composeRule.onAllNodesWithText("结束训练").onLast().performClick()

        waitForText("今日计划")
        composeRule.onNodeWithText("今日计划", substring = true).assertIsDisplayed()
    }

    private fun seedSingleRestingSetPlan() = runBlocking {
        app.database.clearAllTables()
        app.trainingRepository.seedBuiltIns()
        val catalog = app.trainingRepository.observeExerciseCatalog().first { categories ->
            categories.any { category -> category.templates.isNotEmpty() }
        }
        val template = catalog
            .flatMap { it.templates }
            .first { template -> template.variants.any { it.metricType == MetricType.REPS } }
        val variant = template.variants.first { it.metricType == MetricType.REPS }
        val taskId = app.trainingRepository.createOrUpdateDayTask(
            DayTaskDraft(
                date = LocalDate.now().toString(),
                templateId = template.template.id,
                variantId = variant.id,
                sets = 2,
                targetReps = 1,
                restSec = 30,
            ),
        )
        assertTrue("Failed to create test day task.", taskId > 0L)
    }

    private fun moveTaskToBack() {
        val activeScenario = checkNotNull(scenario) { "Activity scenario is not launched." }
        activeScenario.onActivity { activity ->
            assertTrue("Activity could not move to background.", activity.moveTaskToBack(true))
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun bringAppToForeground() {
        val activeScenario = checkNotNull(scenario) { "Activity scenario is not launched." }
        runCatching {
            activeScenario.moveToState(Lifecycle.State.RESUMED)
        }.onFailure {
            val launchIntent = Intent(app, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            app.startActivity(launchIntent)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun launchActivity() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun waitForText(
        text: String,
        timeoutMillis: Long = 45_000L,
    ) {
        val matcher = hasText(text, substring = true)
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val visibleOrComposed = composeRule.onAllNodes(matcher)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
            if (visibleOrComposed) {
                true
            } else {
                runCatching {
                    composeRule.onAllNodes(hasScrollAction())
                        .onFirst()
                        .performScrollToNode(matcher)
                }
                composeRule.onAllNodes(matcher)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
        }
    }

    private fun clickFirstText(text: String) {
        composeRule.onAllNodesWithText(text, substring = true).onFirst().performClick()
    }
}
