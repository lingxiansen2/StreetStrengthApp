package com.codex.streetstrength.domain

import com.codex.streetstrength.data.model.CalendarCompletionStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDayActionPolicyTest {

    private val today: LocalDate = LocalDate.of(2026, 4, 29)

    @Test
    fun `today planned task can start and edit`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today,
            hasTasks = true,
            completionStatus = CalendarCompletionStatus.PLANNED.name,
            today = today,
        )

        assertEquals(PlanDateTiming.TODAY, policy.timing)
        assertTrue(policy.hasTasks)
        assertFalse(policy.hasRecordedTraining)
        assertTrue(policy.canStartTraining)
        assertTrue(policy.canEditPlan)
    }

    @Test
    fun `past planned task cannot start or edit`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today.minusDays(1),
            hasTasks = true,
            completionStatus = CalendarCompletionStatus.PLANNED.name,
            today = today,
        )

        assertEquals(PlanDateTiming.PAST, policy.timing)
        assertFalse(policy.hasRecordedTraining)
        assertFalse(policy.canStartTraining)
        assertFalse(policy.canEditPlan)
    }

    @Test
    fun `future planned task cannot start but can edit`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today.plusDays(1),
            hasTasks = true,
            completionStatus = CalendarCompletionStatus.PLANNED.name,
            today = today,
        )

        assertEquals(PlanDateTiming.FUTURE, policy.timing)
        assertFalse(policy.hasRecordedTraining)
        assertFalse(policy.canStartTraining)
        assertTrue(policy.canEditPlan)
    }

    @Test
    fun `today partial task can resume but cannot edit`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today,
            hasTasks = true,
            completionStatus = CalendarCompletionStatus.PARTIAL.name,
            today = today,
        )

        assertEquals(PlanDateTiming.TODAY, policy.timing)
        assertTrue(policy.hasRecordedTraining)
        assertTrue(policy.canStartTraining)
        assertFalse(policy.canEditPlan)
    }

    @Test
    fun `today done task cannot start or edit`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today,
            hasTasks = true,
            completionStatus = CalendarCompletionStatus.DONE.name,
            today = today,
        )

        assertEquals(PlanDateTiming.TODAY, policy.timing)
        assertTrue(policy.hasRecordedTraining)
        assertFalse(policy.canStartTraining)
        assertFalse(policy.canEditPlan)
    }

    @Test
    fun `debug testing can reopen completed today task`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today,
            hasTasks = true,
            completionStatus = CalendarCompletionStatus.DONE.name,
            today = today,
            allowCompletedTodayTesting = true,
        )

        assertEquals(PlanDateTiming.TODAY, policy.timing)
        assertTrue(policy.hasRecordedTraining)
        assertTrue(policy.isCompletedTodayTestingAllowed)
        assertTrue(policy.canStartTraining)
        assertTrue(policy.canEditPlan)
    }

    @Test
    fun `empty today defaults to editable without start action`() {
        val policy = resolvePlanDayActionPolicy(
            selectedDate = today,
            hasTasks = false,
            completionStatus = null,
            today = today,
        )

        assertEquals(CalendarCompletionStatus.EMPTY.name, policy.completionStatus)
        assertEquals(PlanDateTiming.TODAY, policy.timing)
        assertFalse(policy.hasRecordedTraining)
        assertFalse(policy.canStartTraining)
        assertTrue(policy.canEditPlan)
    }
}
