package com.example.medac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderSchedulerTest {

    @Test
    fun testNextPreReminderTimeMillis_invalidFormats() {
        assertNull(nextPreReminderTimeMillis("invalid"))
        assertNull(nextPreReminderTimeMillis("12"))
        assertNull(nextPreReminderTimeMillis("12:ab"))
    }

    @Test
    fun testNextPreReminderTimeMillis_returnsFutureTime() {
        val result = nextPreReminderTimeMillis("14:30", minutesBefore = 2)
        assertNotNull(result)
        assertTrue(result!! > System.currentTimeMillis() - 1000)
    }

    @Test
    fun testNextPreReminderTimeMillis_calculatesTwoMinutesBefore() {
        // Form a time 10 minutes in the future from now
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 10)
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val timeStr = String.format("%02d:%02d", hour, minute)

        val triggerMillis = nextPreReminderTimeMillis(timeStr, minutesBefore = 2)
        assertNotNull(triggerMillis)

        val triggerCal = Calendar.getInstance().apply {
            timeInMillis = triggerMillis!!
        }

        // The expected trigger minute should be target minute - 2
        val expectedCal = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            add(Calendar.MINUTE, -2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(expectedCal.get(Calendar.HOUR_OF_DAY), triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(expectedCal.get(Calendar.MINUTE), triggerCal.get(Calendar.MINUTE))
        assertEquals(0, triggerCal.get(Calendar.SECOND))
    }

    @Test
    fun testNextDoseTimeMillis_exactCalculation() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 15)
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val timeStr = String.format("%02d:%02d", hour, minute)

        val triggerMillis = nextDoseTimeMillis(timeStr)
        assertNotNull(triggerMillis)

        val triggerCal = Calendar.getInstance().apply {
            timeInMillis = triggerMillis!!
        }

        assertEquals(hour, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(minute, triggerCal.get(Calendar.MINUTE))
        assertEquals(0, triggerCal.get(Calendar.SECOND))
    }

    @Test
    fun testDefaultSnoozeDelayIsThreeMinutes() {
        assertEquals(3, DEFAULT_SNOOZE_DELAY_MINUTES)
    }

    @Test
    fun testNextPreReminderTimeMillis_midnightWrapAround() {
        val triggerMillis = nextPreReminderTimeMillis("00:01", minutesBefore = 2)
        assertNotNull(triggerMillis)
        val cal = Calendar.getInstance().apply {
            timeInMillis = triggerMillis!!
        }
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testCurrentDateFormatted_returnsValidDateFormat() {
        val dateStr = currentDateFormatted()
        assertTrue("Date must match YYYY-MM-DD", dateStr.matches(Regex("""\d{4}-\d{2}-\d{2}""")))
    }

    @Test
    fun testDoseLogKey_matchesAppStandard() {
        val key = doseLogKey(123456L, "2026-08-27", "08:00")
        assertEquals("123456_2026-08-27_08:00", key)
    }
}
