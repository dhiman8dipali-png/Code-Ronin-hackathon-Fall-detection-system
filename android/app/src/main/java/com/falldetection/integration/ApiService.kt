package com.falldetection.integration

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/api/alert")
    suspend fun sendAlert(@Body request: AlertRequest): Response<Unit>
}

data class AlertRequest(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val confidence: Float,
    val accelerationMagnitude: Float,
    val gyroscopeMagnitude: Float,
    val tiltAngle: Float,
    val mapsLink: String
)
