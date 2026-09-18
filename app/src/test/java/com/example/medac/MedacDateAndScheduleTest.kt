package com.example.medac

import com.example.medac.data.CreateScheduleRequest
import com.example.medac.data.FixedTime
import com.example.medac.data.MedacDateUtils
import com.example.medac.data.UpdateMedicationRequest
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedacDateAndScheduleTest {
    private val gson = Gson()

    @Test
    fun `test normalizeToIsoDate handles both dd-MM-yyyy and yyyy-MM-dd`() {
        assertEquals("2026-08-27", MedacDateUtils.normalizeToIsoDate("27/08/2026"))
        assertEquals("2026-08-27", MedacDateUtils.normalizeToIsoDate("2026-08-27"))
        assertEquals("2026-01-05", MedacDateUtils.normalizeToIsoDate("05/01/2026"))
        assertNull(MedacDateUtils.normalizeToIsoDate(null))
        assertNull(MedacDateUtils.normalizeToIsoDate(""))
        assertNull(MedacDateUtils.normalizeToIsoDate("invalid-date"))
    }

    @Test
    fun `test computeEndDate computes correct end dates for durations`() {
        assertEquals("2026-09-03", MedacDateUtils.computeEndDate("2026-08-27", "7"))
        assertEquals("2026-09-03", MedacDateUtils.computeEndDate("2026-08-27", "7 days"))
        assertEquals("2026-09-10", MedacDateUtils.computeEndDate("2026-08-27", "14"))
        assertEquals("2026-09-26", MedacDateUtils.computeEndDate("2026-08-27", "30"))
        assertEquals("2026-11-25", MedacDateUtils.computeEndDate("2026-08-27", "90"))
        assertNull(MedacDateUtils.computeEndDate("2026-08-27", "∞ Ongoing"))
        assertNull(MedacDateUtils.computeEndDate("2026-08-27", "Ongoing"))
        assertNull(MedacDateUtils.computeEndDate(null, "7"))
    }

    @Test
    fun `test UpdateMedicationRequest serializes to snake_case fields`() {
        val req = UpdateMedicationRequest(
            enteredName = "Amoxicillin",
            enteredStrengthValue = "500",
            enteredStrengthUnit = "mg",
            form = "Capsule",
            route = "oral",
            doseQuantityValue = "1",
            doseQuantityUnit = "capsule",
            labelInstructionsText = "Take with water",
            status = "active",
            highAttention = false,
            startDate = "2026-08-27",
            endDate = "2026-09-03"
        )
        val json = gson.toJson(req)
        assertTrue(json.contains("\"entered_name\":\"Amoxicillin\""))
        assertTrue(json.contains("\"entered_strength_value\":\"500\""))
        assertTrue(json.contains("\"entered_strength_unit\":\"mg\""))
        assertTrue(json.contains("\"label_instructions_text\":\"Take with water\""))
        assertTrue(json.contains("\"start_date\":\"2026-08-27\""))
        assertTrue(json.contains("\"end_date\":\"2026-09-03\""))
    }

    @Test
    fun `test CreateScheduleRequest serializes correctly with fixed times`() {
        val req = CreateScheduleRequest(
            scheduleType = "fixed_times",
            timingMode = "local_clock",
            timezone = "America/New_York",
            missWindowMinutes = 60,
            effectiveFrom = "2026-08-27T08:00:00Z",
            fixedTimes = listOf(FixedTime("08:00", "1", "tablet"), FixedTime("20:00", "1", "tablet"))
        )
        val json = gson.toJson(req)
        assertTrue(json.contains("\"schedule_type\":\"fixed_times\""))
        assertTrue(json.contains("\"timing_mode\":\"local_clock\""))
        assertTrue(json.contains("\"timezone\":\"America/New_York\""))
        assertTrue(json.contains("\"effective_from\":\"2026-08-27T08:00:00Z\""))
        assertTrue(json.contains("\"local_time\":\"08:00\""))
        assertTrue(json.contains("\"local_time\":\"20:00\""))
    }
}
