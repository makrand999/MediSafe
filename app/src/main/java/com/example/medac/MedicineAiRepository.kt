package com.example.medac

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AiMedicine(
    val name: String,
    val genericName: String,
    val purpose: String,
    val instructions: String,
    val suggestedTimes: List<String>,
    val form: String = "",
    val imageConfidence: Double = 0.7,
    val ocrConfidence: Double = 0.7
)

object MedicineAiRepository {
    private const val BASE_URL = "http://169.58.196.107/medac/api/v1"

    suspend fun identify(context: Context, uri: Uri): AuthApiResult<AiMedicine> = withContext(Dispatchers.IO) {
        val token = AuthRepository.getToken(context) ?: return@withContext AuthApiResult.Error("Not logged in")
        try {
            val base64 = compressToBase64(context, uri) ?: return@withContext AuthApiResult.Error("Could not read image")
            // Updated 2026-08-19: POST /medac/api/v1/medicine/identify (routes.ts:572 bodyLimit ~5.9MB base64, 429 10/min 100/hour)
            // Server accepts "imageBase64" alias "image" and strips "data:image/...;base64," prefix (routes.ts:585)
            val url = URL("$BASE_URL/medicine/identify")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 30000
                readTimeout = 40000
            }
            val payload = JSONObject().apply {
                put("imageBase64", base64)
                put("mimeType", "image/jpeg")
            }.toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            if (code in 200..299) {
                val json = JSONObject(body)
                // New server shape (routes.ts:139) returns both {success, medicine, interpretation (alias), ai_run_id...}
                // Prefer medicine (legacy) but fallback to interpretation if medicine empty
                var med = json.optJSONObject("medicine")
                if(med==null || med.optString("name").isBlank()) {
                    val interp = json.optJSONObject("interpretation")
                    if(interp!=null && interp.optString("name").isNotBlank()) med=interp
                    else if(interp!=null && interp.optString("candidate_name").isNotBlank()){
                        // patient-scoped shape occasionally returned via legacy alias: map candidate fields
                        val name = interp.optString("candidate_name", interp.optString("name",""))
                        val genericName = interp.optString("candidate_generic_name","")
                        val form = interp.optString("form","")
                        val directions = interp.optString("label_directions_text","")
                        val purpose = interp.optString("purpose", interp.optString("indication_text",""))
                        val conf = interp.optDouble("confidence",0.7)
                        return@withContext AuthApiResult.Success(AiMedicine(name, genericName, purpose, directions, listOf("08:00"), form, conf, conf))
                    }
                }
                if(med==null) med=json
                val name = med.optString("name", med.optString("candidate_name",""))
                val genericName = med.optString("genericName", med.optString("generic_name", med.optString("candidate_generic_name","")))
                val purpose = med.optString("purpose", med.optString("indication_text",""))
                val instructions = med.optString("instructions", med.optString("label_directions_text",""))
                val form = med.optString("form", med.optString("form_factor", ""))
                val imageConfidence = med.optDouble("imageConfidence", med.optDouble("image_confidence", med.optDouble("confidence",0.7)))
                val ocrConfidence = med.optDouble("ocrConfidence", med.optDouble("ocr_confidence", med.optDouble("confidence",0.7)))
                val times = mutableListOf<String>()
                val arr: JSONArray? = med.optJSONArray("suggestedTimes") ?: med.optJSONArray("suggested_times")
                if (arr != null) for (i in 0 until arr.length()) times.add(arr.optString(i))
                if (times.isEmpty()) times.add("08:00")
                android.util.Log.d("MedicineAiRepository","identify success: name=$name conf=$imageConfidence/$ocrConfidence body=${body.take(300)}")
                AuthApiResult.Success(AiMedicine(name, genericName, purpose, instructions, times, form, imageConfidence, ocrConfidence))
            } else {
                // Legacy error shape {success:false,error:"..."} or {error:{code,message}}
                val msg = try {
                    val j=JSONObject(body)
                    j.optString("error", j.optJSONObject("error")?.optString("message") ?: body)
                } catch (_: Exception) { body }
                android.util.Log.w("MedicineAiRepository","identify error $code: $msg")
                AuthApiResult.Error(msg.ifBlank { "Identify failed ($code)" })
            }
        } catch (e: Exception) {
            android.util.Log.w("MedicineAiRepository","identify exception: ${e.message}")
            AuthApiResult.Error(e.message ?: "Network error")
        }
    }


    suspend fun identifyFromText(context: Context, ocrText: String): AuthApiResult<AiMedicine> = withContext(Dispatchers.IO) {
        val token = AuthRepository.getToken(context) ?: return@withContext AuthApiResult.Error("Not logged in")
        if (ocrText.isBlank()) return@withContext AuthApiResult.Error("No OCR text")
        // ocrText 3..5000 chars trimmed (routes.ts:626), same 200 {success:true,medicine:{...}} shape as /medicine/identify
        try {
            val url = URL("$BASE_URL/medicine/identify-text")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 20000
                readTimeout = 30000
            }
            val payload = JSONObject().apply { put("ocrText", ocrText) }.toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            if (code in 200..299) {
                val json = JSONObject(body)
                var med = json.optJSONObject("medicine")
                if(med==null || med.optString("name").isBlank()){
                    val interp=json.optJSONObject("interpretation")
                    if(interp!=null && (interp.optString("name").isNotBlank() || interp.optString("candidate_name").isNotBlank())){
                        val name=interp.optString("name", interp.optString("candidate_name",""))
                        val genericName=interp.optString("genericName", interp.optString("candidate_generic_name",""))
                        val form=interp.optString("form","")
                        val directions=interp.optString("label_directions_text","")
                        val purpose=interp.optString("purpose", interp.optString("indication_text",""))
                        val conf=interp.optDouble("confidence",0.7)
                        android.util.Log.d("MedicineAiRepository","identify-text via interpretation alias: name=$name")
                        return@withContext AuthApiResult.Success(AiMedicine(name, genericName, purpose, directions, listOf("08:00"), form, conf, conf))
                    }
                }
                if(med==null) med=json
                val name = med.optString("name", med.optString("candidate_name",""))
                val genericName = med.optString("genericName", med.optString("generic_name", med.optString("candidate_generic_name","")))
                val purpose = med.optString("purpose", med.optString("indication_text",""))
                val instructions = med.optString("instructions", med.optString("label_directions_text",""))
                val form = med.optString("form", "")
                val imageConfidence = med.optDouble("imageConfidence", med.optDouble("image_confidence", med.optDouble("confidence",0.7)))
                val ocrConfidence = med.optDouble("ocrConfidence", med.optDouble("ocr_confidence", med.optDouble("confidence",0.7)))
                val times = mutableListOf<String>()
                val arr: JSONArray? = med.optJSONArray("suggestedTimes") ?: med.optJSONArray("suggested_times")
                if (arr != null) for (i in 0 until arr.length()) times.add(arr.optString(i))
                if (times.isEmpty()) times.add("08:00")
                android.util.Log.d("MedicineAiRepository","identify-text success: name=$name conf=$ocrConfidence body=${body.take(300)}")
                AuthApiResult.Success(AiMedicine(name, genericName, purpose, instructions, times, form, imageConfidence, ocrConfidence))
            } else {
                val msg = try { val j=JSONObject(body); j.optString("error", j.optJSONObject("error")?.optString("message") ?: body) } catch (_: Exception) { body }
                android.util.Log.w("MedicineAiRepository","identify-text error $code: $msg")
                AuthApiResult.Error(msg.ifBlank { "Identify failed ($code)" })
            }
        } catch (e: Exception) {
            AuthApiResult.Error(e.message ?: "Network error")
        }
    }

    private fun compressToBase64(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = input.readBytes()
            input.close()
            var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val maxSide = 1024
            val w = bitmap.width
            val h = bitmap.height
            val scale = maxOf(w, h).toFloat() / maxSide
            if (scale > 1f) {
                val nw = (w / scale).toInt().coerceAtLeast(1)
                val nh = (h / scale).toInt().coerceAtLeast(1)
                bitmap = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val compressed = out.toByteArray()
            if (compressed.size > 4 * 1024 * 1024) return null
            Base64.encodeToString(compressed, Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }
}
