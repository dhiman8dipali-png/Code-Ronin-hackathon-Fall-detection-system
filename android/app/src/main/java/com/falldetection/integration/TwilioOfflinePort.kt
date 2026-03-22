package com.falldetection.integration

import android.content.Context
import android.util.Log
import com.falldetection.model.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SmsManager

/**
 * Twilio Offline Port: A unified interface that mimics the Twilio API 
 * but functions natively when no internet is available.
 */
class TwilioOfflinePort(private val context: Context) {

    companion object {
        private const val TWILIO_ACCOUNT_SID = "YOUR_TWILIO_ACCOUNT_SID"
        private const val TWILIO_AUTH_TOKEN = "YOUR_TWILIO_AUTH_TOKEN"
        private const val TWILIO_PHONE_NUMBER = "+1234567890"  // Your Twilio number
        private const val TWILIO_API_URL = "https://api.twilio.com/2010-04-01"
        private const val TAG = "TwilioOfflinePort"
    }

    /**
     * Send SMS Alert via Twilio
     */
    suspend fun sendSMSAlert(
        toPhoneNumber: String,
        location: String,
        mapsLink: String
    ): Boolean = withContext(Dispatchers.IO) {
        val message = buildSmsMessage(location, mapsLink)
        
        if (!isNetworkAvailable()) {
            return@withContext sendNativeSMS(toPhoneNumber, message)
        }

        return@withContext try {
            val authStr = "$TWILIO_ACCOUNT_SID:$TWILIO_AUTH_TOKEN"
            val auth = java.util.Base64.getEncoder().encodeToString(authStr.toByteArray())

            val params = mapOf(
                "From" to TWILIO_PHONE_NUMBER,
                "To" to toPhoneNumber,
                "Body" to message
            )

            val postData = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(v, StandardCharsets.UTF_8.toString())}"
            }

            val url = URL("$TWILIO_API_URL/Accounts/$TWILIO_ACCOUNT_SID/Messages.json")
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Basic $auth")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Content-Length", postData.toByteArray().size.toString())
                doOutput = true
            }

            connection.outputStream.bufferedWriter().use { it.write(postData) }

            val responseCode = connection.responseCode
            val success = responseCode in 200..299

            if (success) {
                Log.d(TAG, "Twilio SMS sent successfully to $toPhoneNumber")
            } else {
                Log.e(TAG, "Twilio SMS failed, falling back to Native SMS")
                sendNativeSMS(toPhoneNumber, message)
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Twilio SMS: ${e.message}, falling back to Native SMS")
            sendNativeSMS(toPhoneNumber, message)
        }
    }

    /**
     * Trigger Voice Call via Twilio
     */
    suspend fun triggerVoiceCall(
        toPhoneNumber: String,
        mapsLink: String
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val twiml = buildTwiMLResponse(mapsLink)
            val authStr = "$TWILIO_ACCOUNT_SID:$TWILIO_AUTH_TOKEN"
            val auth = java.util.Base64.getEncoder().encodeToString(authStr.toByteArray())

            val params = mapOf(
                "From" to TWILIO_PHONE_NUMBER,
                "To" to toPhoneNumber,
                "Twiml" to twiml
            )

            val postData = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(v, StandardCharsets.UTF_8.toString())}"
            }

            val url = URL("$TWILIO_API_URL/Accounts/$TWILIO_ACCOUNT_SID/Calls.json")
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Basic $auth")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Content-Length", postData.toByteArray().size.toString())
                doOutput = true
            }

            connection.outputStream.bufferedWriter().use { it.write(postData) }

            val responseCode = connection.responseCode
            val success = responseCode in 200..299

            if (success) {
                Log.d(TAG, "Voice call initiated to $toPhoneNumber")
            } else {
                Log.e(TAG, "Voice call failed with code: $responseCode")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering voice call: ${e.message}")
            false
        }
    }

    /**
     * Send alerts to multiple emergency contacts
     */
    suspend fun sendAlertsToContacts(
        contacts: List<EmergencyContact>,
        location: String,
        mapsLink: String
    ): Pair<Int, Int> {
        Log.d(TAG, "Sending alerts to ${contacts.size} contacts")
        var successCount = 0
        var failureCount = 0

        for (contact in contacts) {
            Log.d(TAG, "Processing contact: ${contact.name}, Active: ${contact.isActive}")
            if (contact.isActive) {
                val smsSuccess = sendSMSAlert(contact.phoneNumber, location, mapsLink)
                if (smsSuccess) {
                    successCount++
                } else {
                    failureCount++
                }

                // Trigger voice call to first contact
                if (contact == contacts.first()) {
                    triggerVoiceCall(contact.phoneNumber, mapsLink)
                }
            }
        }

        return Pair(successCount, failureCount)
    }

    private fun buildSmsMessage(location: String, mapsLink: String): String {
        return "🚨 EMERGENCY: Fall detected! User may need help.\n\n" +
            "Location: $location\n" +
            "Maps Link: $mapsLink\n\n" +
            "Please check on them immediately if possible."
    }

    private fun buildTwiMLResponse(mapsLink: String): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <Response>
                <Say voice="alice">A fall has been detected. The user is at this location: $mapsLink</Say>
                <Say voice="alice">If you are the emergency contact, please go check on them immediately.</Say>
                <Say voice="alice">Call is being transferred to emergency services.</Say>
            </Response>
        """.trimIndent()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun sendNativeSMS(phoneNumber: String, message: String): Boolean {
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "Native SMS sent to $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send native SMS: ${e.message}")
            false
        }
    }
}
