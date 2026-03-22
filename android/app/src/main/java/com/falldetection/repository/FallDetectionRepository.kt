package com.falldetection.repository

import com.falldetection.model.FallDetectionEvent
import com.falldetection.model.EmergencyContact
import kotlinx.coroutines.flow.Flow

/**
 * Repository for fall detection data
 */
class FallDetectionRepository(
    private val eventDao: FallDetectionEventDao,
    private val contactDao: EmergencyContactDao
) {

    // Fall Event operations
    suspend fun insertEvent(event: FallDetectionEvent): Long {
        return eventDao.insertEvent(event)
    }

    fun getAllEvents(): Flow<List<FallDetectionEvent>> {
        return eventDao.getAllEvents()
    }

    fun getRecentEvents(hoursAgo: Int): Flow<List<FallDetectionEvent>> {
        val timestamp = System.currentTimeMillis() - (hoursAgo * 60 * 60 * 1000)
        return eventDao.getEventsFromTime(timestamp)
    }

    fun getSOSTriggeredEvents(): Flow<List<FallDetectionEvent>> {
        return eventDao.getSOSTriggeredEvents()
    }

    suspend fun updateEvent(event: FallDetectionEvent) {
        eventDao.updateEvent(event)
    }

    suspend fun deleteEvent(event: FallDetectionEvent) {
        eventDao.deleteEvent(event)
    }

    suspend fun deleteOldEvents(daysOld: Int) {
        val timestamp = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000)
        eventDao.deleteEventsOlderThan(timestamp)
    }

    suspend fun getEventCountToday(): Int {
        val startOfDay = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        return eventDao.getEventCountSince(startOfDay)
    }

    // Emergency Contact operations
    suspend fun insertContact(contact: EmergencyContact): Long {
        return contactDao.insertContact(contact)
    }

    fun getActiveContacts(): Flow<List<EmergencyContact>> {
        return contactDao.getActiveContacts()
    }

    fun getAllContacts(): Flow<List<EmergencyContact>> {
        return contactDao.getAllContacts()
    }

    suspend fun updateContact(contact: EmergencyContact) {
        contactDao.updateContact(contact)
    }

    suspend fun deleteContact(contact: EmergencyContact) {
        contactDao.deleteContact(contact)
    }

    suspend fun getActiveContactCount(): Int {
        return contactDao.getActiveContactCount()
    }
}
