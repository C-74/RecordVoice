package com.example.recordvoice.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.recordvoice.data.local.RecordingDao
import com.example.recordvoice.data.local.RecordingEntity
import com.example.recordvoice.shortcuts.ShortcutHelper
import com.example.recordvoice.worker.SyncOutboxWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/**
 * Chapitre 1 (Les couches) :
 * Un Repository unique servant de Source de Vérité (Single Source of Truth) entre Room,
 * le stockage disque interne des fichiers audio, WorkManager (Outbox) et les App Shortcuts.
 */
class RecordingsRepository(
    private val appContext: Context,
    private val recordingDao: RecordingDao
) {
    val allRecordingsFlow: Flow<List<RecordingEntity>> = recordingDao.getAllRecordingsFlow()

    fun getRecordingsDirectory(): File {
        val dir = File(appContext.filesDir, "recordings")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createNewAudioFilePath(extension: String = "m4a"): File {
        val fileName = "rec_${System.currentTimeMillis()}.$extension"
        return File(getRecordingsDirectory(), fileName)
    }

    /**
     * Enregistre une nouvelle note vocale dans Room, met à jour les raccourcis dynamiques
     * et planifie la synchronisation Outbox via WorkManager.
     * La notification système n'est envoyée que lorsque la synchronisation cloud (Outbox) est terminée.
     */
    suspend fun saveNewRecording(
        filePath: String,
        mimeType: String,
        durationSeconds: Int,
        customTitle: String? = null
    ): RecordingEntity = withContext(Dispatchers.IO) {
        val now = Date()
        val shortDate = SimpleDateFormat("d MMM", Locale.FRENCH).format(now)
        val shortTime = SimpleDateFormat("HH:mm", Locale.FRENCH).format(now)
        val fullDateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.FRENCH).format(now)

        val defaultTitle = customTitle ?: "Note du $shortDate à $shortTime"
        val entity = RecordingEntity(
            title = defaultTitle,
            filePath = filePath,
            mimeType = mimeType,
            duration = durationSeconds.coerceAtLeast(1),
            timestamp = System.currentTimeMillis(),
            dateStr = fullDateStr,
            synced = false // Placé dans l'Outbox, synchronisé par WorkManager dès que le réseau est actif
        )

        val insertedId = recordingDao.insertRecording(entity)
        val savedEntity = entity.copy(id = insertedId)

        // Mettre à jour les 3 dernières notes dans les raccourcis dynamiques
        val allList = recordingDao.getAllRecordingsList()
        ShortcutHelper.updateDynamicShortcuts(appContext, allList)

        // Lancement de la synchronisation arrière-plan (Chapitre 4 & 5)
        SyncOutboxWorker.enqueueSync(appContext)

        savedEntity
    }

    /**
     * Génère un fichier audio WAV 440 Hz de 2 secondes (exactement comme createSampleAudioBlob() dans app.js).
     */
    suspend fun createAndSaveSampleDemoRecording(currentCount: Int): RecordingEntity = withContext(Dispatchers.IO) {
        val sampleRate = 8000
        val durationSeconds = 2
        val numSamples = sampleRate * durationSeconds
        val wavFile = createNewAudioFilePath(extension = "wav")

        val buffer = ByteBuffer.allocate(44 + numSamples).order(ByteOrder.LITTLE_ENDIAN)
        // En-tête RIFF WAVE 8-bit PCM mono 8000 Hz
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + numSamples)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // Mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate)
        buffer.putShort(1)
        buffer.putShort(8)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(numSamples)

        for (i in 0 until numSamples) {
            val sample = sin(2.0 * Math.PI * 440.0 * (i.toDouble() / sampleRate))
            val byteVal = ((sample + 1.0) * 127.5).toInt().coerceIn(0, 255).toByte()
            buffer.put(byteVal)
        }

        FileOutputStream(wavFile).use { fos ->
            fos.write(buffer.array())
        }

        saveNewRecording(
            filePath = wavFile.absolutePath,
            mimeType = "audio/wav",
            durationSeconds = durationSeconds,
            customTitle = "Mémo vocal de démonstration #${currentCount + 1}"
        )
    }

    suspend fun renameRecording(recording: RecordingEntity, newTitle: String) = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@withContext
        recordingDao.updateTitle(recording.id, trimmed)
        val updated = recording.copy(title = trimmed)
        val allList = recordingDao.getAllRecordingsList()
        ShortcutHelper.onRecordingRenamed(appContext, updated, allList)
    }

    suspend fun deleteRecording(recording: RecordingEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
        recordingDao.deleteById(recording.id)
        val remaining = recordingDao.getAllRecordingsList()
        ShortcutHelper.onRecordingDeleted(appContext, recording.id, remaining)
    }

    fun triggerSyncNow() {
        SyncOutboxWorker.enqueueSync(appContext)
    }

    /**
     * Exporte le fichier audio dans le dossier Téléchargements (Downloads) de l'appareil.
     */
    suspend fun exportToDownloads(recording: RecordingEntity): String? = withContext(Dispatchers.IO) {
        val sourceFile = File(recording.filePath)
        if (!sourceFile.exists()) return@withContext null

        val ext = if (recording.mimeType.contains("wav")) "wav" else "m4a"
        val cleanFileName = "${slugify(recording.title)}.$ext"

        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, cleanFileName)
                    put(MediaStore.Downloads.MIME_TYPE, recording.mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = appContext.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                resolver.openOutputStream(uri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                cleanFileName
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, cleanFileName)
                sourceFile.copyTo(targetFile, overwrite = true)
                cleanFileName
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Crée un Intent de partage natif (Intent.ACTION_SEND) via FileProvider.
     */
    fun createShareIntent(recording: RecordingEntity): Intent? {
        val file = File(recording.filePath)
        if (!file.exists()) return null

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = recording.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, recording.title)
            putExtra(Intent.EXTRA_TEXT, "Écouter ma note vocale : ${recording.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, "Partager « ${recording.title} »")
    }

    private fun slugify(text: String): String {
        val normalized = text.lowercase(Locale.FRENCH)
            .replace(Regex("[àáâãäå]"), "a")
            .replace(Regex("[èéêë]"), "e")
            .replace(Regex("[ìíîï]"), "i")
            .replace(Regex("[òóôõö]"), "o")
            .replace(Regex("[ùúûü]"), "u")
            .replace("ç", "c")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^\\w\\-]+"), "")
            .replace(Regex("--+"), "-")
            .trim('-')
        return normalized.ifEmpty { "audio-note" }
    }
}
