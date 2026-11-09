package com.example.recordvoice.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Chapitre 2 & Antisèche 1/2 (Room) :
 * - Version incrémentée avec Migration explicite (MIGRATION_1_2) pour ne jamais perdre les notes existantes
 * - exportSchema = true (schémas versionnés dans app/schemas/)
 * - Aucun fallbackToDestructiveMigration
 */
@Database(
    entities = [RecordingEntity::class, AnalyticsEventEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration de la version 1 (table recordings seule) vers la version 2
         * (ajout de la table analytics_events pour la file hors-ligne Umami).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analytics_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventName` TEXT NOT NULL,
                        `screenUrl` TEXT NOT NULL,
                        `screenTitle` TEXT NOT NULL,
                        `propertiesJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `synced` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "audio_recordings_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
