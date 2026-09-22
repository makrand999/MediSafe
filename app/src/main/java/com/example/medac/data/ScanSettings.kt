package com.example.medac.data

import android.content.Context

/**
 * How a label photo is turned into medicine fields.
 *
 * - [VISION]: the photo is sent to the server and read by the vision model
 *   (`POST /medicine/identify`). This is the default.
 * - [OCR]: the text is read on-device with Google Lens and only the text is
 *   sent (`POST /medicine/identify-text` / label-interpretations).
 *
 * Both paths stay available: OCR is kept as the fallback whenever the vision
 * call fails, times out, or comes back without a medicine name.
 */
enum class ScanStrategy { VISION, OCR }

/**
 * User preference for the default label-scan path, persisted in SharedPreferences.
 * Read it once in [com.example.medac.MedacViewModel.load] and observe the
 * ViewModel StateFlow from the UI.
 */
object ScanSettings {
    /** Vision is on by default; OCR remains the fallback. */
    const val DEFAULT_VISION = true

    private const val PREFS_NAME = "app_settings"
    private const val KEY_VISION_DEFAULT = "scan_vision_default"

    fun isVisionDefault(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VISION_DEFAULT, DEFAULT_VISION)

    fun setVisionDefault(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VISION_DEFAULT, enabled).apply()
    }

    fun strategyFor(visionDefault: Boolean): ScanStrategy =
        if (visionDefault) ScanStrategy.VISION else ScanStrategy.OCR

    /**
     * A vision answer is only accepted when it actually named a medicine;
     * anything else (blank name, empty result) falls back to the OCR path.
     */
    fun visionResultUsable(name: String?): Boolean = !name.isNullOrBlank()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
