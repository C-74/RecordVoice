package com.example.recordvoice.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.recordvoice.analytics.UmamiAnalyticsHelper
import com.example.recordvoice.data.local.AppDatabase
import com.example.recordvoice.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Chapitre 4 (L'arrière-plan) & Annexe 4 (Analytics - Slide 5 & 7) :
 * Dépile automatiquement :
 * 1. L'Outbox des notes vocales (notes avec synced = false) vers https://jsonplaceholder.typicode.com/posts
 * 2. L'Outbox des événements Analytics Umami (analytics_events avec synced = false) vers https://cloud.umami.is/api/send
 * dès que la connexion réseau est disponible, même si l'application a été fermée par l'utilisateur.
 */
class SyncOutboxWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val recordingDao = db.recordingDao()
        val analyticsDao = db.analyticsDao()

        // 1. Synchronisation des notes vocales en attente (Outbox Métier)
        val unsyncedRecordings = recordingDao.getUnsyncedRecordings()
        var syncedNotesCount = 0
        var lastSyncedId: Long? = null

        for (item in unsyncedRecordings) {
            val success = postRecordingMetadata(
                recordingId = item.id,
                title = item.title,
                duration = item.duration,
                timestamp = item.timestamp
            )
            if (success) {
                recordingDao.markAsSynced(item.id)
                syncedNotesCount++
                lastSyncedId = item.id
            } else {
                return@withContext if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        }

        if (syncedNotesCount > 0) {
            NotificationHelper.showNotification(
                context = applicationContext,
                title = "Synchronisation terminée",
                body = "$syncedNotesCount note(s) vocale(s) sauvegardée(s) dans le cloud. Touchez pour ouvrir la note.",
                noteId = lastSyncedId
            )
        }

        // 2. Synchronisation de la file hors-ligne Umami Analytics (Annexe 4 - Slide 5 & 7)
        val unsyncedEvents = analyticsDao.getUnsyncedEvents()
        for (event in unsyncedEvents) {
            val sent = UmamiAnalyticsHelper.sendEventToUmamiHttp(applicationContext, event)
            if (sent) {
                analyticsDao.markEventSynced(event.id)
            } else {
                return@withContext if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        }
        analyticsDao.deleteSyncedEvents()

        Result.success()
    }

    private fun postRecordingMetadata(
        recordingId: Long,
        title: String,
        duration: Int,
        timestamp: Long
    ): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://jsonplaceholder.typicode.com/posts")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put("title", title)
                put("recordingId", recordingId)
                put("duration", duration)
                put("timestamp", timestamp)
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sync_outbox_work"

        fun enqueueSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SyncOutboxWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
