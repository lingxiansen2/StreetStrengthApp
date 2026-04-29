package com.codex.streetstrength.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverviewProgressCalculatorTest {

    @Test
    fun `builds four trailing monday based week ranges`() {
        val ranges = buildTrailingWeekRanges(LocalDate.of(2026, 4, 29))

        assertEquals(4, ranges.size)
        assertEquals(LocalDate.of(2026, 4, 6), ranges.first().start)
        assertEquals(LocalDate.of(2026, 4, 12), ranges.first().end)
        assertEquals(LocalDate.of(2026, 4, 27), ranges.last().start)
        assertEquals(LocalDate.of(2026, 5, 3), ranges.last().end)
    }

    @Test
    fun `calculates rounded delta from previous value`() {
        assertEquals(25, calculateTrendDeltaPercent(current = 10, previous = 8))
        assertEquals(-50, calculateTrendDeltaPercent(current = 4, previous = 8))
    }

    @Test
    fun `returns no delta when previous value is empty`() {
        assertNull(calculateTrendDeltaPercent(current = 6, previous = 0))
    }
}
