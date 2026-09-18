package com.example.medac

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @POST("send")
    suspend fun sendMessage(@Body request: SendRequest): SendResponse

    @Multipart
    @POST("upload")
    suspend fun uploadFile(@Part file: MultipartBody.Part): UploadResponse

    @POST("refresh")
    suspend fun refreshSession(): Map<String, String>

    @GET("health")
    suspend fun healthCheck(): Map<String, Any>
}
