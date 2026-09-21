package com.example.medac.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Boundary coverage for the circadian bucketing that drives the Today's
 * Schedule chapter grouping and card tints. The boundaries are the whole
 * point of the feature, so each edge hour is asserted explicitly.
 */
class DayPeriodTest {

    @Test
    fun dayPeriodForHour_bucketBoundaries() {
        // Morning: 05:00–11:59
        assertEquals(DayPeriod.MORNING, dayPeriodForHour(5))
        assertEquals(DayPeriod.MORNING, dayPeriodForHour(11))
        // Afternoon: 12:00–16:59
        assertEquals(DayPeriod.AFTERNOON, dayPeriodForHour(12))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodForHour(16))
        // Evening: 17:00–20:59
        assertEquals(DayPeriod.EVENING, dayPeriodForHour(17))
        assertEquals(DayPeriod.EVENING, dayPeriodForHour(20))
        // Night: 21:00–04:59
        assertEquals(DayPeriod.NIGHT, dayPeriodForHour(21))
        assertEquals(DayPeriod.NIGHT, dayPeriodForHour(23))
        assertEquals(DayPeriod.NIGHT, dayPeriodForHour(0))
        assertEquals(DayPeriod.NIGHT, dayPeriodForHour(4))
    }

    @Test
    fun dayPeriodForTime_parsesCanonical24HourValues() {
        assertEquals(DayPeriod.MORNING, dayPeriodForTime("08:00"))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodForTime("13:30"))
        assertEquals(DayPeriod.EVENING, dayPeriodForTime("19:45"))
        assertEquals(DayPeriod.NIGHT, dayPeriodForTime("22:00"))
        assertEquals(DayPeriod.NIGHT, dayPeriodForTime("00:30"))
    }

    @Test
    fun dayPeriodForTime_tolerates12HourClock() {
        assertEquals(DayPeriod.MORNING, dayPeriodForTime("8:00 AM"))
        assertEquals(DayPeriod.EVENING, dayPeriodForTime("8:00 PM"))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodForTime("1 PM"))
        // 12 AM is midnight, 12 PM is noon.
        assertEquals(DayPeriod.NIGHT, dayPeriodForTime("12:00 AM"))
        assertEquals(DayPeriod.AFTERNOON, dayPeriodForTime("12:00 PM"))
    }

    @Test
    fun dayPeriodForTime_unparseableFallsBackToMorning() {
        assertEquals(DayPeriod.MORNING, dayPeriodForTime(""))
        assertEquals(DayPeriod.MORNING, dayPeriodForTime("as needed"))
    }

    @Test
    fun companionOrdered_isChronological() {
        assertEquals(
            listOf(DayPeriod.MORNING, DayPeriod.AFTERNOON, DayPeriod.EVENING, DayPeriod.NIGHT),
            orderedDayPeriods
        )
    }
}
