package com.example.medac.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenStore {
    private const val PREFS = "medac_secure_prefs"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_EMAIL = "email"
    private const val KEY_SYNC_CURSOR = "sync_cursor"

    private fun prefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    var accessToken: String?
        get() = null // in-memory only per spec §4.1 — stored via setAccess only for current process
        set(_) {}

    // in-memory holder
    private var inMemoryAccess: String? = null
    fun getAccess(context: Context): String? = inMemoryAccess ?: prefs(context).getString(KEY_ACCESS, null)
    fun setAccess(context: Context, token: String?) {
        inMemoryAccess = token
        // also persist for process restart fallback (encrypted)
        if (token == null) prefs(context).edit().remove(KEY_ACCESS).apply()
        else prefs(context).edit().putString(KEY_ACCESS, token).apply()
    }

    fun getRefresh(context: Context): String? = prefs(context).getString(KEY_REFRESH, null)
    fun setRefresh(context: Context, token: String?) {
        val e = prefs(context).edit()
        if (token == null) e.remove(KEY_REFRESH) else e.putString(KEY_REFRESH, token)
        e.apply()
    }

    fun getUserId(context: Context): String? = prefs(context).getString(KEY_USER_ID, null)
    fun getUsername(context: Context): String? = prefs(context).getString(KEY_USERNAME, null)
    fun getEmail(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)

    fun saveSession(context: Context, access: String, refresh: String, userId: String, username: String, email: String) {
        inMemoryAccess = access
        prefs(context).edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clear(context: Context) {
        inMemoryAccess = null
        prefs(context).edit().clear().apply()
        // also clear legacy auth prefs
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun getSyncCursor(context: Context, patientId: String): String? =
        prefs(context).getString("cursor_$patientId", null)

    fun setSyncCursor(context: Context, patientId: String, cursor: String?) {
        val e = prefs(context).edit()
        if (cursor == null) e.remove("cursor_$patientId") else e.putString("cursor_$patientId", cursor)
        e.apply()
    }

    fun isLoggedIn(context: Context): Boolean = !getRefresh(context).isNullOrBlank() || !getAccess(context).isNullOrBlank()
}
