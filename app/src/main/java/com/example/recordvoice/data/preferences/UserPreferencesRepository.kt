package com.example.recordvoice.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "record_voice_prefs")

data class SessionAnalyticsInfo(
    val isFirstOpen: Boolean,
    val daysSinceFirstOpen: Int,
    val retentionDayLabel: String,
    val sessionsToday: Int,
    val totalSessions: Int
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_LAST_SELECTED_TAB = intPreferencesKey("last_selected_tab")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_MIC_PERMISSION_REQUESTED_ONCE = booleanPreferencesKey("mic_permission_requested_once")
        private val KEY_NOTIF_PERMISSION_REQUESTED_ONCE = booleanPreferencesKey("notif_permission_requested_once")

        // Clés DataStore pour l'Analytics (first_open, session_start J+1/J+2/J+5, note_created)
        private val KEY_FIRST_OPEN_TIMESTAMP = longPreferencesKey("first_open_timestamp")
        private val KEY_LAST_SESSION_DATE = stringPreferencesKey("last_session_date")
        private val KEY_SESSIONS_TODAY_COUNT = intPreferencesKey("sessions_today_count")
        private val KEY_TOTAL_SESSIONS_COUNT = intPreferencesKey("total_sessions_count")
        private val KEY_FIRST_NOTE_CREATED = booleanPreferencesKey("first_note_created")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    val fcmTokenFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_FCM_TOKEN] ?: ""
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FCM_TOKEN] = token
        }
    }

    val lastSelectedTabFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SELECTED_TAB] ?: 0
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: false
    }

    val micPermissionRequestedOnceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MIC_PERMISSION_REQUESTED_ONCE] ?: false
    }

    val notifPermissionRequestedOnceFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIF_PERMISSION_REQUESTED_ONCE] ?: false
    }

    suspend fun setLastSelectedTab(tabIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SELECTED_TAB] = tabIndex
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun markMicPermissionRequested() {
        context.dataStore.edit { prefs ->
            prefs[KEY_MIC_PERMISSION_REQUESTED_ONCE] = true
        }
    }

    suspend fun markNotifPermissionRequested() {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIF_PERMISSION_REQUESTED_ONCE] = true
        }
    }

    /**
     * Met à jour les compteurs de session dans DataStore et calcule :
     * - Si c'est le tout premier lancement (`first_open`)
     * - La continuité en jours depuis l'installation (`J+0`, `J+1`, `J+2`, `J+5`...)
     * - Le nombre de sessions dans la même journée (`sessions_today`)
     */
    suspend fun recordSessionAndGetInfo(): SessionAnalyticsInfo {
        val now = System.currentTimeMillis()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

        var isFirstOpen = false
        var daysSinceFirstOpen = 0
        var sessionsToday = 1
        var totalSessions = 1

        context.dataStore.edit { prefs ->
            val existingFirstOpen = prefs[KEY_FIRST_OPEN_TIMESTAMP]
            if (existingFirstOpen == null) {
                isFirstOpen = true
                prefs[KEY_FIRST_OPEN_TIMESTAMP] = now
                daysSinceFirstOpen = 0
            } else {
                val diffMs = (now - existingFirstOpen).coerceAtLeast(0L)
                daysSinceFirstOpen = TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
            }

            val lastDate = prefs[KEY_LAST_SESSION_DATE]
            sessionsToday = if (lastDate == todayStr) {
                (prefs[KEY_SESSIONS_TODAY_COUNT] ?: 0) + 1
            } else {
                1
            }
            totalSessions = (prefs[KEY_TOTAL_SESSIONS_COUNT] ?: 0) + 1

            prefs[KEY_LAST_SESSION_DATE] = todayStr
            prefs[KEY_SESSIONS_TODAY_COUNT] = sessionsToday
            prefs[KEY_TOTAL_SESSIONS_COUNT] = totalSessions
        }

        return SessionAnalyticsInfo(
            isFirstOpen = isFirstOpen,
            daysSinceFirstOpen = daysSinceFirstOpen,
            retentionDayLabel = "J+$daysSinceFirstOpen",
            sessionsToday = sessionsToday,
            totalSessions = totalSessions
        )
    }

    /**
     * Retourne `true` s'il s'agit de la toute première note vocale créée par l'utilisateur
     * (et mémorise l'état dans DataStore pour les suivantes).
     */
    suspend fun checkAndMarkFirstNoteCreated(): Boolean {
        var isFirstNote = false
        context.dataStore.edit { prefs ->
            val alreadyCreated = prefs[KEY_FIRST_NOTE_CREATED] ?: false
            if (!alreadyCreated) {
                isFirstNote = true
                prefs[KEY_FIRST_NOTE_CREATED] = true
            }
        }
        return isFirstNote
    }
}
