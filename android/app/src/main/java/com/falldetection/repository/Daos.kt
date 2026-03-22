package com.falldetection.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.falldetection.model.FallDetectionEvent
import com.falldetection.model.EmergencyContact
import kotlinx.coroutines.flow.Flow

@Dao
interface FallDetectionEventDao {

    @Insert
    suspend fun insertEvent(event: FallDetectionEvent): Long

    @Query("SELECT * FROM fall_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<FallDetectionEvent>>

    @Query("SELECT * FROM fall_events WHERE timestamp >= :fromTimestamp ORDER BY timestamp DESC")
    fun getEventsFromTime(fromTimestamp: Long): Flow<List<FallDetectionEvent>>

    @Query("SELECT * FROM fall_events WHERE sosTriggered = 1 ORDER BY timestamp DESC")
    fun getSOSTriggeredEvents(): Flow<List<FallDetectionEvent>>

    @Query("SELECT * FROM fall_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): FallDetectionEvent?

    @Update
    suspend fun updateEvent(event: FallDetectionEvent)

    @Delete
    suspend fun deleteEvent(event: FallDetectionEvent)

    @Query("DELETE FROM fall_events WHERE timestamp < :timestamp")
    suspend fun deleteEventsOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM fall_events WHERE timestamp >= :fromTimestamp")
    suspend fun getEventCountSince(fromTimestamp: Long): Int
}

@Dao
interface EmergencyContactDao {

    @Insert
    suspend fun insertContact(contact: EmergencyContact): Long

    @Query("SELECT * FROM emergency_contacts WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveContacts(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts ORDER BY createdAt DESC")
    fun getAllContacts(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: Long): EmergencyContact?

    @Update
    suspend fun updateContact(contact: EmergencyContact)

    @Delete
    suspend fun deleteContact(contact: EmergencyContact)

    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE isActive = 1")
    suspend fun getActiveContactCount(): Int
}
