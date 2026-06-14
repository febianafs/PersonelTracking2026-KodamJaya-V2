package com.example.personeltracking2026kodamjayav2.data.repository

import com.example.personeltracking2026kodamjayav2.core.network.RetrofitClient
import com.example.personeltracking2026kodamjayav2.data.model.AboutResponse

class AboutRepository {

    private val api = RetrofitClient.instance

    suspend fun getAboutUs(token: String): Result<AboutResponse> {
        return try {

            val response = api.getAboutUs(
                "Bearer $token"
            )

            if (response.isSuccessful) {

                val body = response.body()

                if (body != null) {
                    Result.Success(body)
                } else {
                    Result.Error("Empty response body")
                }

            } else {

                Result.Error(
                    "Failed: ${response.code()} ${response.message()}"
                )

            }

        } catch (e: Exception) {

            Result.Error(
                e.localizedMessage ?: "Something went wrong"
            )

        }
    }
}