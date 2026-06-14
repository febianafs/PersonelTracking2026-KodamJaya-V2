package com.example.personeltracking2026kodamjayav2.data.repository

import com.example.personeltracking2026kodamjayav2.core.network.RetrofitClient
import com.example.personeltracking2026kodamjayav2.data.model.PersonelData

class PersonelRepository {
    private val api = RetrofitClient.instance

    suspend fun getPersonelDetail(userId: Int, token: String): Result<PersonelData> {
        return try {
            val response = api.getPersonelDetail(userId, "Bearer $token")
            if (response.isSuccessful && response.body()?.data != null) {
                Result.Success(response.body()!!.data!!)
            } else {
                Result.Error(response.body()?.message ?: "Failed to load data")
            }
        } catch (e: Exception) {
            Result.Error("Cannot connect to server")
        }
    }
}