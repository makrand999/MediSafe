package com.example.medac.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The label-scan default and its fallback rule. Vision is the default, but a
 * vision answer is only accepted when it actually named a medicine — otherwise
 * the flow keeps the Google Lens OCR result.
 */
class ScanSettingsTest {

    @Test
    fun visionIsTheDefault() {
        assertTrue(ScanSettings.DEFAULT_VISION)
        assertEquals(ScanStrategy.VISION, ScanSettings.strategyFor(ScanSettings.DEFAULT_VISION))
    }

    @Test
    fun strategyFollowsTheToggle() {
        assertEquals(ScanStrategy.VISION, ScanSettings.strategyFor(visionDefault = true))
        assertEquals(ScanStrategy.OCR, ScanSettings.strategyFor(visionDefault = false))
    }

    @Test
    fun visionResultIsOnlyUsableWhenItNamedAMedicine() {
        assertFalse(ScanSettings.visionResultUsable(null))
        assertFalse(ScanSettings.visionResultUsable(""))
        assertFalse(ScanSettings.visionResultUsable("   "))
        assertTrue(ScanSettings.visionResultUsable("Metformin Hydrochloride"))
    }
}
