package com.example.medac

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.medac.data.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    const val APK_URL = "http://169.58.196.107/apks/medisafe.apk"
    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_LAST_MODIFIED = "last_apk_modified_timestamp"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    data class UpdateCheckResult(
        val updateAvailable: Boolean,
        val remoteLastModified: Long,
        val formattedDate: String = ""
    )

    /**
     * Checks if a newer APK is available based on HTTP Last-Modified header.
     */
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(APK_URL)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult(false, 0L)
                }

                val lastModifiedHeader = response.header("Last-Modified")
                    ?: return@withContext UpdateCheckResult(false, 0L)

                val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                val remoteTime = try {
                    sdf.parse(lastModifiedHeader)?.time ?: 0L
                } catch (_: Exception) {
                    0L
                }

                if (remoteTime == 0L) {
                    return@withContext UpdateCheckResult(false, 0L)
                }

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var currentInstalledTime = prefs.getLong(KEY_LAST_MODIFIED, 0L)

                if (currentInstalledTime == 0L) {
                    // Fall back to package install/update time
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        currentInstalledTime = pInfo.lastUpdateTime
                    } catch (_: Exception) {
                        currentInstalledTime = 0L
                    }
                }

                // If remote is at least 3 seconds newer than current installed time
                val isNewer = (remoteTime - currentInstalledTime) > 3000L
                UpdateCheckResult(isNewer, remoteTime, lastModifiedHeader)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Check for update failed", e)
            UpdateCheckResult(false, 0L)
        }
    }

    /**
     * Downloads the APK file into cacheDir and reports progress (0..100).
     */
    suspend fun downloadApk(
        context: Context,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(APK_URL).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()

            val apkFile = File(context.cacheDir, "medisafe-update.apk")
            if (apkFile.exists()) apkFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalRead * 100) / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }

            apkFile
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Download APK failed", e)
            null
        }
    }

    /**
     * Clears local application data (Room DB, SharedPreferences, cache, internal files)
     * so that the newly updated app will start completely fresh.
     */
    fun clearAppData(context: Context, newRemoteTimestamp: Long) {
        try {
            // Delete Room database
            context.deleteDatabase("medac_database")
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Failed to delete medac_database", e)
        }

        try {
            // Clear authentication & token store
            TokenStore.clear(context)
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Failed to clear TokenStore", e)
        }

        try {
            // Clear active alarms
            ActiveAlarmStore.clearActiveAlarm(context)
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Failed to clear ActiveAlarmStore", e)
        }

        try {
            // Clear internal files except update apk
            val filesDir = context.filesDir
            filesDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Failed to clear filesDir", e)
        }

        try {
            // Store the timestamp for the update so we know it's installed
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_LAST_MODIFIED, newRemoteTimestamp).apply()
        } catch (e: Exception) {
            android.util.Log.e("AppUpdateManager", "Failed to record updated timestamp", e)
        }
    }

    /**
     * Triggers installation of the downloaded APK.
     */
    fun installApk(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ verify permission to install unknown apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.provider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        activity.startActivity(installIntent)
    }
}
