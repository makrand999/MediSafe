package com.example.medac.ui

import app.cash.paparazzi.Paparazzi
import com.example.medac.ui.theme.MedacTheme
import org.junit.Rule
import org.junit.Test

class ComponentsPaparazziTest {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun stepIndicator() {
        paparazzi.snapshot {
            MedacTheme {
                StepIndicator(currentStep = 1, steps = listOf("Details", "Schedule", "Review"))
            }
        }
    }

    @Test
    fun schedulePreviewRow() {
        paparazzi.snapshot {
            MedacTheme {
                SchedulePreviewRow(text = "Every day at 8:00 AM")
            }
        }
    }

    @Test
    fun uncertainFieldBox() {
        paparazzi.snapshot {
            MedacTheme {
                UncertainFieldBox(label = "Dosage", value = "500 mg")
            }
        }
    }
}
