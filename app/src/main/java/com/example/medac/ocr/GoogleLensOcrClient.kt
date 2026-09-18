package com.example.medac.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max

object GoogleLensOcrClient {

    private const val TAG = "GoogleLensOcr"
    private const val ENDPOINT = "https://lensfrontend-pa.googleapis.com/v1/crupload"
    private const val DEFAULT_API_KEY = "AIzaSyDr2UxVnv_U85AbhhY8XSHSIavUW0DC-sY"
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val DEFAULT_IMAGE_MAX_DIMENSION = 1500

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class PreparedImage(
        val bytes: ByteArray,
        val width: Int,
        val height: Int
    )

    /**
     * Reads, auto-orients, scales and compresses an image from a Content URI.
     */
    suspend fun prepareImage(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val cr = context.contentResolver

        // 1. Decode bounds & orientation
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            cr.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation: ${e.message}")
        }

        // 2. Decode bitmap
        val originalBitmap: Bitmap = cr.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Could not open or decode image from URI: $uri")

        // 3. Rotate bitmap if needed
        val orientedBitmap = applyExifRotation(originalBitmap, orientation)

        // 4. Downscale if max dimension exceeds DEFAULT_IMAGE_MAX_DIMENSION
        val origW = orientedBitmap.width
        val origH = orientedBitmap.height
        val maxDim = max(origW, origH)

        val scaledBitmap = if (maxDim > DEFAULT_IMAGE_MAX_DIMENSION) {
            val scale = DEFAULT_IMAGE_MAX_DIMENSION.toFloat() / maxDim.toFloat()
            val newW = (origW * scale).toInt().coerceAtLeast(1)
            val newH = (origH * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(orientedBitmap, newW, newH, true)
        } else {
            orientedBitmap
        }

        // 5. Compress to JPEG bytes (90% quality)
        val byteStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
        val imageBytes = byteStream.toByteArray()

        val finalW = scaledBitmap.width
        val finalH = scaledBitmap.height

        if (scaledBitmap != originalBitmap && scaledBitmap != orientedBitmap) {
            scaledBitmap.recycle()
        }
        if (orientedBitmap != originalBitmap) {
            orientedBitmap.recycle()
        }
        originalBitmap.recycle()

        PreparedImage(bytes = imageBytes, width = finalW, height = finalH)
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rotate bitmap: ${e.message}")
            bitmap
        }
    }

    /**
     * Perform Google Lens OCR on an image Uri.
     */
    suspend fun recognizeText(
        context: Context,
        uri: Uri,
        language: String = "en"
    ): LensOcrResult = withContext(Dispatchers.IO) {
        val prepared = prepareImage(context, uri)
        recognizeText(
            imageBytes = prepared.bytes,
            width = prepared.width,
            height = prepared.height,
            language = language
        )
    }

    /**
     * Perform Google Lens OCR on raw prepared image bytes.
     */
    suspend fun recognizeText(
        imageBytes: ByteArray,
        width: Int,
        height: Int,
        language: String = "en"
    ): LensOcrResult = withContext(Dispatchers.IO) {
        val protoRequest = LensProtobufCodec.encodeLensOverlayRequest(
            imageBytes = imageBytes,
            width = width,
            height = height,
            language = language
        )

        val mediaType = "application/x-protobuf".toMediaTypeOrNull()
        val requestBody = protoRequest.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-protobuf")
            .addHeader("X-Goog-Api-Key", DEFAULT_API_KEY)
            .addHeader("User-Agent", DEFAULT_USER_AGENT)
            .addHeader("Accept", "*/*")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw IllegalStateException("Google Lens OCR HTTP error ${response.code}: $errBody")
        }

        val responseBytes = response.body?.bytes()
            ?: throw IllegalStateException("Empty response body received from Google Lens endpoint")

        LensProtobufCodec.parseLensResponse(responseBytes)
    }
}
