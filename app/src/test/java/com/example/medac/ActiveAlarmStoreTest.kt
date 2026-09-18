package com.example.medac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ActiveAlarmStoreTest {

    @Test
    fun testActiveAlarmModel() {
        val now = System.currentTimeMillis()
        val alarm = ActiveAlarm("Aspirin", "08:00", now)

        assertEquals("Aspirin", alarm.medicineName)
        assertEquals("08:00", alarm.time)
        assertEquals(now, alarm.triggeredMillis)
    }

    @Test
    fun testActionConstants() {
        assertEquals("com.example.medac.ACTION_MEDICINE_DUE", ActiveAlarmStore.ACTION_MEDICINE_DUE)
        assertEquals("com.example.medac.EXTRA_SHOW_DUE_ALARM", ActiveAlarmStore.EXTRA_SHOW_DUE_ALARM)
        assertEquals("com.example.medac.EXTRA_DUE_MEDICINE_NAME", ActiveAlarmStore.EXTRA_DUE_MEDICINE_NAME)
        assertEquals("com.example.medac.EXTRA_DUE_TIME", ActiveAlarmStore.EXTRA_DUE_TIME)
    }
}
