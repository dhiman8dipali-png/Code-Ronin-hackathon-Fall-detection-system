package com.falldetection.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.falldetection.integration.AlertRequest
import com.falldetection.integration.ApiService
import com.falldetection.model.AlertState
import com.falldetection.model.FallDetectionEvent
import com.falldetection.model.SystemStatus
import com.falldetection.repository.FallDetectionRepository
import com.falldetection.repository.FallDetectionDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FallDetectionDatabase.getDatabase(application)
    private val repository = FallDetectionRepository(
        database.fallDetectionEventDao(),
        database.emergencyContactDao()
    )

    private val _systemStatus = MutableStateFlow<SystemStatus>(SystemStatus.SAFE)
    val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _fallCount = MutableStateFlow(0)
    val fallCount: StateFlow<Int> = _fallCount.asStateFlow()

    private val _lastFallEvent = MutableStateFlow<FallDetectionEvent?>(null)
    val lastFallEvent: StateFlow<FallDetectionEvent?> = _lastFallEvent.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            repository.getRecentEvents(hoursAgo = 24).collect { events ->
                _fallCount.value = events.size
                if (events.isNotEmpty()) {
                    _lastFallEvent.value = events.first()
                }
            }
        }
    }

    fun setMonitoring(isMonitoring: Boolean) {
        _isMonitoring.value = isMonitoring
        _systemStatus.value = if (isMonitoring) SystemStatus.MONITORING else SystemStatus.SAFE
    }

    fun setFallDetected() {
        _systemStatus.value = SystemStatus.FALL_DETECTED
    }

    fun setSafe() {
        _systemStatus.value = SystemStatus.SAFE
    }
}

class AlertViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FallDetectionDatabase.getDatabase(application)
    private val repository = FallDetectionRepository(
        database.fallDetectionEventDao(),
        database.emergencyContactDao()
    )

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:5000") // Android emulator localhost
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService: ApiService = retrofit.create(ApiService::class.java)
    private val twilioOfflinePort = com.falldetection.integration.TwilioOfflinePort(application)

    private val _alertState = MutableStateFlow<AlertState>(AlertState())
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(5)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()

    private val _isSoSTriggered = MutableStateFlow(false)
    val isSoSTriggered: StateFlow<Boolean> = _isSoSTriggered.asStateFlow()

    fun setFallAlert(event: FallDetectionEvent, confidence: Float, quantumConfidence: Float = 0f) {
        _alertState.value = AlertState(
            isFallDetected = true,
            confidence = confidence,
            quantumConfidence = quantumConfidence,
            event = event
        )
    }

    fun updateCountdown(seconds: Int) {
        _countdownSeconds.value = seconds
    }

    fun dismissAlert() {
        _alertState.value = AlertState()
    }

    fun triggerSoS() {
        _isSoSTriggered.value = true
        val event = _alertState.value.event
        if (event != null) {
            viewModelScope.launch {
                // 1. Try Backend API (if online)
                try {
                    val request = AlertRequest(
                        timestamp = event.timestamp,
                        latitude = event.latitude,
                        longitude = event.longitude,
                        confidence = _alertState.value.confidence,
                        accelerationMagnitude = event.accelerationMagnitude,
                        gyroscopeMagnitude = event.gyroscopeMagnitude,
                        tiltAngle = event.tiltAngle,
                        mapsLink = event.mapsLink
                    )
                    val response = apiService.sendAlert(request)
                    if (response.isSuccessful) {
                        updateEventWithSOS(event.id)
                    }
                } catch (e: Exception) {
                    Log.e("AlertViewModel", "API Alert failed: ${e.message}")
                }

                // 2. Trigger Twilio / Native SMS Fallback
                val contacts = repository.getAllContacts().first()
                val activeContacts = contacts.filter { it.isActive }
                
                if (activeContacts.isNotEmpty()) {
                    twilioOfflinePort.sendAlertsToContacts(
                        activeContacts,
                        "Lat: ${event.latitude}, Lon: ${event.longitude}",
                        event.mapsLink
                    )
                }
            }
        }
    }

    private suspend fun updateEventWithSOS(eventId: Long) {
        val events = repository.getAllEvents().first()
        val eventToUpdate = events.find { it.id == eventId }
        if (eventToUpdate != null) {
            repository.updateEvent(eventToUpdate.copy(sosTriggered = true))
        }
    }
}

class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FallDetectionDatabase.getDatabase(application)
    private val repository = FallDetectionRepository(
        database.fallDetectionEventDao(),
        database.emergencyContactDao()
    )

    val fallEvents = repository.getAllEvents()

    fun deleteEvent(event: FallDetectionEvent) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun clearOldEvents(daysOld: Int) {
        viewModelScope.launch {
            repository.deleteOldEvents(daysOld)
        }
    }
}

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FallDetectionDatabase.getDatabase(application)
    private val repository = FallDetectionRepository(
        database.fallDetectionEventDao(),
        database.emergencyContactDao()
    )

    val emergencyContacts = repository.getAllContacts()

    fun addContact(name: String, phoneNumber: String, email: String = "") {
        viewModelScope.launch {
            val contact = com.falldetection.model.EmergencyContact(
                name = name,
                phoneNumber = phoneNumber,
                email = email
            )
            repository.insertContact(contact)
        }
    }

    fun updateContact(contact: com.falldetection.model.EmergencyContact) {
        viewModelScope.launch {
            repository.updateContact(contact)
        }
    }

    fun deleteContact(contact: com.falldetection.model.EmergencyContact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun activateContact(contact: com.falldetection.model.EmergencyContact) {
        viewModelScope.launch {
            repository.updateContact(contact.copy(isActive = true))
        }
    }

    fun deactivateContact(contact: com.falldetection.model.EmergencyContact) {
        viewModelScope.launch {
            repository.updateContact(contact.copy(isActive = false))
        }
    }
}
