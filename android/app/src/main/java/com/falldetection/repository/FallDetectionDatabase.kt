package com.falldetection.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.falldetection.model.FallDetectionEvent
import com.falldetection.model.EmergencyContact

@Database(
    entities = [FallDetectionEvent::class, EmergencyContact::class],
    version = 1,
    exportSchema = false
)
abstract class FallDetectionDatabase : RoomDatabase() {

    abstract fun fallDetectionEventDao(): FallDetectionEventDao
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        @Volatile
        private var INSTANCE: FallDetectionDatabase? = null

        fun getDatabase(context: Context): FallDetectionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FallDetectionDatabase::class.java,
                    "fall_detection_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
