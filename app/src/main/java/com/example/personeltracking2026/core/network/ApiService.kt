package com.example.personeltracking2026.core.network

import com.example.personeltracking2026.data.model.AboutResponse
import com.example.personeltracking2026.data.model.LoginRequest
import com.example.personeltracking2026.data.model.LoginResponse
import com.example.personeltracking2026.data.model.PersonelDetailResponse
import com.example.personeltracking2026.data.model.UpdateApkResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("v1/mobile/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("v1/about-us")
    suspend fun getAboutUs(
        @Header("Authorization") token: String
    ): Response<AboutResponse>

    @GET("v1/check-token")
    suspend fun checkToken(
        @Header("Authorization") token: String
    ): Response<Void>

    @GET("v1/read-personel/{id}")
    suspend fun getPersonelDetail(
        @Path("id") id: Int,
        @Header("Authorization") token: String
    ): Response<PersonelDetailResponse>

    @GET("v1/latest-version")
    suspend fun getLatestVersion(): Response<UpdateApkResponse>
}