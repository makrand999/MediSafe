package com.example.medac.data

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

object NetworkModule {
    const val BASE_URL = "http://169.58.196.107/medac/api/v1/"

    fun provideApi(context: Context): MedacApi {
        val gson = GsonBuilder().setLenient().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(context))
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(MedacApi::class.java)
    }

    private class AuthInterceptor(private val ctx: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val appCtx = ctx.applicationContext
            val access = TokenStore.getAccess(appCtx)
            var req = chain.request().newBuilder()
                .header("X-Request-ID", UUID.randomUUID().toString())
                .apply { if (!access.isNullOrBlank()) header("Authorization", "Bearer $access") }
                .build()
            var resp = chain.proceed(req)
            if (resp.code == 401 && !TokenStore.getRefresh(appCtx).isNullOrBlank()) {
                // try refresh synchronously (§3.6)
                val refreshed = tryRefresh(appCtx)
                if (refreshed) {
                    val newAccess = TokenStore.getAccess(appCtx)
                    resp.close()
                    req = chain.request().newBuilder()
                        .header("X-Request-ID", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer $newAccess")
                        .build()
                    resp = chain.proceed(req)
                } else {
                    // REFRESH_REUSE or expired -> clear session per spec
                    if (resp.header("x-request-id") != null) { /* log */ }
                }
            }
            return resp
        }

        private fun tryRefresh(context: Context): Boolean {
            return try {
                val refresh = TokenStore.getRefresh(context) ?: return false
                val url = BASE_URL + "auth/refresh"
                val body = """{"refresh_token":"$refresh"}"""
                val req = okhttp3.Request.Builder().url(url)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .build()
                val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
                val r = client.newCall(req).execute()
                val b = r.body?.string().orEmpty()
                val code = r.code
                r.close()
                if (code in 200..299) {
                    val json = org.json.JSONObject(b)
                    val access = json.optString("access_token")
                    val newRefresh = json.optString("refresh_token")
                    if (access.isNotBlank() && newRefresh.isNotBlank()) {
                        TokenStore.setAccess(context, access)
                        TokenStore.setRefresh(context, newRefresh)
                        return true
                    }
                } else if (b.contains("REFRESH_REUSE")) {
                    TokenStore.clear(context)
                }
                false
            } catch (_: Exception) { false }
        }
    }
}
