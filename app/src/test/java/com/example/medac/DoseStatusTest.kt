package com.example.medac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Canonical dose-vocabulary helpers shared by every adherence ring, history
 * filter, and status badge: empty input must yield "no data" (null), never a
 * fabricated 0% or 100%.
 */
class DoseStatusTest {

    @Test
    fun testAdherencePercentOrNull_emptyIsNull() {
        assertNull(adherencePercentOrNull(0, 0))
        assertNull(adherencePercentOrNull(0, -1))
    }

    @Test
    fun testAdherencePercentOrNull_computesTakenOverTotal() {
        assertEquals(100, adherencePercentOrNull(3, 3))
        assertEquals(50, adherencePercentOrNull(1, 2))
        assertEquals(0, adherencePercentOrNull(0, 4))
        assertEquals(66, adherencePercentOrNull(2, 3))
    }

    @Test
    fun testParseStrengthDose_extractsValueAndUnit() {
        assertEquals("100" to "mg", parseStrengthDose("100mg"))
        assertEquals("2.5" to "ml", parseStrengthDose("2.5 mL"))
        assertEquals("1" to null, parseStrengthDose("1 tablet"))
        assertEquals(null to null, parseStrengthDose("as directed"))
    }

    @Test
    fun testServerStrengthUnit_matchesServerEnumCasing() {
        assertEquals("mL", serverStrengthUnit("ml"))
        assertEquals("mg", serverStrengthUnit("mg"))
        assertEquals("mcg", serverStrengthUnit("mcg"))
        assertNull(serverStrengthUnit(null))
    }

    @Test
    fun testNormalizeFormRouteDoseUnit_serverMappings() {
        assertEquals("tablet", normalizeFormForServer("Tablet"))
        assertEquals("capsule", normalizeFormForServer("CAPSULE"))
        assertEquals("cream", normalizeFormForServer("Topical"))
        assertEquals("other", normalizeFormForServer("Syrup"))
        assertNull(normalizeFormForServer(""))
        assertEquals("injection", normalizeRouteForForm("Injection"))
        assertEquals("inhalation", normalizeRouteForForm("inhaler"))
        assertEquals("ophthalmic", normalizeRouteForForm("Drops"))
        assertEquals("oral", normalizeRouteForForm("Tablet"))
        assertEquals("mL", normalizeDoseUnitForForm("Liquid"))
        assertEquals("puff", normalizeDoseUnitForForm("Inhaler"))
        assertEquals("drop", normalizeDoseUnitForForm("drops"))
        assertEquals("tablet", normalizeDoseUnitForForm("Tablet"))
    }

    @Test
    fun testFindMedicine_prefersIdFallsBackToName() {
        val meds = listOf(
            ManagedMedicine(id = 1L, name = "Aspirin", genericNameAndDose = "100mg"),
            ManagedMedicine(id = 2L, name = "Ibuprofen", genericNameAndDose = "200mg")
        )
        assertEquals(2L, findMedicine(meds, 2L, "Unknown")?.id)
        assertEquals(1L, findMedicine(meds, 99L, "aspirin")?.id)
        assertEquals(null, findMedicine(meds, 99L, "Unknown"))
    }

    @Test
    fun testDoseStatusForLog_distinguishesSkippedFromMissed() {
        val base = DoseLogEntry(
            id = "k", medicineId = 1L, medicineName = "M",
            time = "08:00", date = "2026-01-01", status = "TAKEN"
        )
        assertEquals(DoseStatus.TAKEN, doseStatusForLog("08:00", base.copy(status = "TAKEN")))
        assertEquals(DoseStatus.SKIPPED, doseStatusForLog("08:00", base.copy(status = "SKIPPED")))
        assertEquals(DoseStatus.MISSED, doseStatusForLog("08:00", base.copy(status = "MISSED")))
    }
}
