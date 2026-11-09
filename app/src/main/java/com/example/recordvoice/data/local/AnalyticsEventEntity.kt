package com.example.recordvoice.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Annexe 4 (Analytics - Slide 5 & 7) :
 * File d'attente hors-ligne (Outbox) sur disque pour les événements Umami Analytics.
 * Quand le téléphone est en mode avion, les événements attendent dans Room et partent
 * par lots au retour du réseau via WorkManager.
 */
@Entity(tableName = "analytics_events")
data class AnalyticsEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventName: String,
    val screenUrl: String,
    val screenTitle: String,
    val propertiesJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
