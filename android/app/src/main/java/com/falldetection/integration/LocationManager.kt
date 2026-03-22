package com.falldetection.integration

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Location Services Manager
 * Handles GPS location retrieval and Google Maps link generation
 */
class LocationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationManager"
        private const val GOOGLE_MAPS_URL_TEMPLATE = 
            "https://www.google.com/maps/search/?api=1&query="
    }

    /**
     * Get current location
     */
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val locationTask = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )

            locationTask.addOnSuccessListener { location ->
                continuation.resume(location)
            }

            locationTask.addOnFailureListener {
                Log.e(TAG, "Error getting location: ${it.message}")
                continuation.resume(null)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
            continuation.resume(null)
        }
    }

    /**
     * Generate Google Maps link from location
     */
    fun generateMapsLink(latitude: Double, longitude: Double): String {
        return "$GOOGLE_MAPS_URL_TEMPLATE$latitude,$longitude"
    }

    /**
     * Format location as readable address
     */
    fun getReadableLocation(latitude: Double, longitude: Double): String {
        return "Lat: $latitude, Long: $longitude"
    }
}
