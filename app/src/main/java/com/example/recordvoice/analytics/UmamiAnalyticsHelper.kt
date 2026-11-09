package com.example.recordvoice.analytics

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.recordvoice.data.local.AnalyticsEventEntity
import com.example.recordvoice.data.local.AppDatabase
import com.example.recordvoice.data.preferences.UserPreferencesRepository
import com.example.recordvoice.worker.SyncOutboxWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Annexe 4 - Analytics (Slides 4, 5, 7, 8, 12, 19) :
 * Client Umami Analytics sans cookie pour Android avec file d'attente hors-ligne (Outbox Room + WorkManager).
 *
 * Mesures envoyées vers Umami Cloud (`POST https://cloud.umami.is/api/send`) :
 * 1. `first_open`    : Première ouverture de l'application (Acquisition / Entonnoir)
 * 2. `note_created`  : Création d'une note vocale avec `is_first` (0/1), `offline` (0/1), `duration_sec` (Activation)
 * 3. `session_start` : Retour de l'utilisateur (plusieurs fois par jour `sessions_today` + continuité `J+0`, `J+1`, `J+2`, `J+5`...) (Rétention)
 * 4. `shortcut_user` : Utilisation / épinglage d'un raccourci Android sur l'écran d'accueil (Fonctionnalité native)
 * + Suivi manuel des écrans Compose (`/recorder` et `/notes`, écrits en dur pour résister à R8).
 */
object UmamiAnalyticsHelper {

    private const val TAG = "RecordVoiceAnalytics"
    const val UMAMI_ENDPOINT = "https://cloud.umami.is/api/send"
    const val UMAMI_WEBSITE_ID = "bd60c11f-35a5-4e1b-832c-d833f305f760"
    const val UMAMI_HOSTNAME = "warm-faun-5052e9.netlify.app"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Enregistre le démarrage de session (`session_start`) et, si c'est le tout premier lancement,
     * l'événement `first_open`.
     */
    fun trackAppLaunchSession(context: Context, launchSource: String) {
        val appContext = context.applicationContext
        scope.launch {
            val prefs = UserPreferencesRepository(appContext)
            val info = prefs.recordSessionAndGetInfo()

            if (info.isFirstOpen) {
                enqueueEvent(
                    context = appContext,
                    eventName = "first_open",
                    screenUrl = "/recorder",
                    screenTitle = "RecordVoice - Enregistreur",
                    properties = mapOf(
                        "source" to launchSource,
                        "platform" to "android",
                        "sdk_int" to Build.VERSION.SDK_INT
                    )
                )
            }

            enqueueEvent(
                context = appContext,
                eventName = "session_start",
                screenUrl = "/recorder",
                screenTitle = "RecordVoice - Session",
                properties = mapOf(
                    "retention_day" to info.retentionDayLabel,
                    "days_since_first_open" to info.daysSinceFirstOpen,
                    "sessions_today" to info.sessionsToday,
                    "total_sessions" to info.totalSessions,
                    "source" to launchSource
                )
            )
        }
    }

    /**
     * Activation (Slide 4 & 12) : `note_created`
     * Mesure si les nouveaux utilisateurs arrivent au premier geste utile (`is_first = 1`)
     * et s'ils enregistrent hors-ligne (`offline = 1` ou `0`).
     */
    fun trackNoteCreated(context: Context, durationSeconds: Int, isOnline: Boolean) {
        val appContext = context.applicationContext
        scope.launch {
            val prefs = UserPreferencesRepository(appContext)
            val isFirst = prefs.checkAndMarkFirstNoteCreated()
            enqueueEvent(
                context = appContext,
                eventName = "note_created",
                screenUrl = "/recorder",
                screenTitle = "RecordVoice - Enregistreur",
                properties = mapOf(
                    "is_first" to if (isFirst) 1 else 0,
                    "offline" to if (isOnline) 0 else 1,
                    "duration_sec" to durationSeconds
                )
            )
        }
    }

    /**
     * Fonctionnalité native (Slide 4, 12 & 19) : `shortcut_user`
     * Mesure l'épinglage d'une note sur l'écran d'accueil ou l'ouverture via un raccourci Android.
     */
    fun trackShortcutUsed(context: Context, shortcutType: String, action: String, result: String = "ok") {
        val appContext = context.applicationContext
        scope.launch {
            enqueueEvent(
                context = appContext,
                eventName = "shortcut_user",
                screenUrl = "/notes",
                screenTitle = "RecordVoice - Notes",
                properties = mapOf(
                    "type" to shortcutType,
                    "action" to action,
                    "result" to result
                )
            )
        }
    }

    /**
     * Suivi manuel des écrans Compose (Slide 6 & 8 : "Le piège Compose : noms d'écran écrits en dur").
     */
    fun trackScreenView(context: Context, screenName: String) {
        val appContext = context.applicationContext
        val (url, title) = when (screenName) {
            "notes" -> "/notes" to "RecordVoice - Notes"
            else -> "/recorder" to "RecordVoice - Enregistreur"
        }
        scope.launch {
            enqueueEvent(
                context = appContext,
                eventName = "", // Pageview Umami
                screenUrl = url,
                screenTitle = title,
                properties = emptyMap()
            )
        }
    }

    /**
     * Écrit immédiatement l'événement dans la table `analytics_events` de Room (Outbox sur disque)
     * puis demande à WorkManager d'envoyer le lot dès que le réseau est connecté.
     */
    private suspend fun enqueueEvent(
        context: Context,
        eventName: String,
        screenUrl: String,
        screenTitle: String,
        properties: Map<String, Any>
    ) {
        val jsonProps = JSONObject()
        properties.forEach { (k, v) -> jsonProps.put(k, v) }

        val dao = AppDatabase.getInstance(context).analyticsDao()
        val entity = AnalyticsEventEntity(
            eventName = eventName,
            screenUrl = screenUrl,
            screenTitle = screenTitle,
            propertiesJson = jsonProps.toString(),
            createdAt = System.currentTimeMillis(),
            synced = false
        )
        dao.insertEvent(entity)

        val label = if (eventName.isEmpty()) "screen_view($screenUrl)" else "$eventName $jsonProps"
        Log.d(TAG, "Événement enregistré dans l'Outbox Room : $label")

        // Planifie l'envoi via WorkManager (immédiat si en ligne, différé si en Mode Avion)
        SyncOutboxWorker.enqueueSync(context)
    }

    /**
     * Appelé par SyncOutboxWorker en arrière-plan pour envoyer un événement vers `POST https://cloud.umami.is/api/send`.
     */
    fun sendEventToUmamiHttp(context: Context, event: AnalyticsEventEntity): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val metrics = context.resources.displayMetrics
            val screenRes = "${metrics.widthPixels}x${metrics.heightPixels}"
            val language = Locale.getDefault().toLanguageTag()

            val payloadObj = JSONObject().apply {
                put("website", UMAMI_WEBSITE_ID)
                put("hostname", UMAMI_HOSTNAME)
                put("screen", screenRes)
                put("language", language)
                put("title", event.screenTitle)
                put("url", event.screenUrl)
                if (event.eventName.isNotEmpty()) {
                    put("name", event.eventName)
                }
                if (event.propertiesJson.isNotEmpty() && event.propertiesJson != "{}") {
                    put("data", JSONObject(event.propertiesJson))
                }
            }

            val rootBody = JSONObject().apply {
                put("type", "event")
                put("payload", payloadObj)
            }

            val url = URL(UMAMI_ENDPOINT)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                // User-Agent standard requis par Umami pour ne pas être filtré comme bot serveur
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                )
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(rootBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val ok = responseCode in 200..299
            Log.d(TAG, "Envoi Umami [${event.eventName.ifEmpty { "pageview" }}] -> HTTP $responseCode")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Envoi Umami différé (hors-ligne) : ${e.localizedMessage}")
            false
        } finally {
            connection?.disconnect()
        }
    }
}
