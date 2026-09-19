package com.example.medac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Regression coverage for the alarm notification identity scheme.
 *
 * Every notification ID / PendingIntent request code on the alarm path must be
 * unique per dose (medicine + time) and purpose. A shared constant (e.g. the
 * pre-alarm open intent previously using requestCode 0 for every medicine)
 * lets simultaneously-firing doses overwrite each other's notifications and
 * tap targets under FLAG_UPDATE_CURRENT.
 */
class AlarmIdentityTest {

    @Test
    fun testPreAlarmOpenRequestCode_uniquePerDose() {
        val aspirin = preAlarmOpenRequestCode("Aspirin", "08:00")
        val ibuprofen = preAlarmOpenRequestCode("Ibuprofen", "08:00")
        val aspirinEvening = preAlarmOpenRequestCode("Aspirin", "20:00")

        assertNotEquals(aspirin, ibuprofen)
        assertNotEquals(aspirin, aspirinEvening)
        assertNotEquals(ibuprofen, aspirinEvening)
    }

    @Test
    fun testPreAlarmOpenRequestCode_stable() {
        assertEquals(
            preAlarmOpenRequestCode("Aspirin", "08:00"),
            preAlarmOpenRequestCode("Aspirin", "08:00")
        )
    }

    @Test
    fun testAlarmAndPreNotificationIds_distinctPerDoseAndPurpose() {
        val alarmA = alarmNotificationId("Aspirin", "08:00")
        val alarmB = alarmNotificationId("Ibuprofen", "08:00")
        val preA = preNotificationId("Aspirin", "08:00")
        val preB = preNotificationId("Ibuprofen", "08:00")

        assertNotEquals(alarmA, alarmB)
        assertNotEquals(preA, preB)
        assertNotEquals(alarmA, preA)
        assertNotEquals(alarmB, preB)
    }

    @Test
    fun testNotificationIds_matchLegacyFormat() {
        // Guards the extraction: helpers must produce the exact IDs the rest of
        // the alarm path (scheduler, receiver, activities) has always used.
        assertEquals("Aspirin|08:00|alarm_notif".hashCode(), alarmNotificationId("Aspirin", "08:00"))
        assertEquals("Aspirin|08:00|pre_notif".hashCode(), preNotificationId("Aspirin", "08:00"))
    }
}
