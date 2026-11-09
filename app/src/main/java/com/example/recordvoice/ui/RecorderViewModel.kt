package com.example.recordvoice.ui

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.recordvoice.analytics.UmamiAnalyticsHelper
import com.example.recordvoice.data.local.AppDatabase
import com.example.recordvoice.data.local.RecordingEntity
import com.example.recordvoice.data.preferences.UserPreferencesRepository
import com.example.recordvoice.data.repository.RecordingsRepository
import com.example.recordvoice.notifications.NotificationHelper
import com.example.recordvoice.shortcuts.ShortcutHelper
import com.example.recordvoice.util.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

enum class PermissionBannerState {
    NONE,
    RATIONALE,
    PERMANENTLY_DENIED
}

class RecorderViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    val repository = RecordingsRepository(appContext, database.recordingDao())
    private val prefs = UserPreferencesRepository(appContext)
    private val networkMonitor = NetworkMonitor(appContext)

    // Si une note a été demandée via un raccourci / notification au démarrage, on n'écrase pas l'onglet
    private var openedViaDeepLinkOrShortcut = false

    // --- Cycle de vie (Jour 4) : Recherche et onglet sauvegardés dans SavedStateHandle ---
    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow("search_query", "")
    val selectedTab: StateFlow<Int> = savedStateHandle.getStateFlow("selected_tab", 0)

    // --- État de l'enregistrement en cours (survit à la rotation de l'écran !) ---
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _visualizerBars = MutableStateFlow(List(32) { 0.05f })
    val visualizerBars: StateFlow<List<Float>> = _visualizerBars.asStateFlow()

    private val _statusMessage = MutableStateFlow("Prêt")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _micPermissionBanner = MutableStateFlow(PermissionBannerState.NONE)
    val micPermissionBanner: StateFlow<PermissionBannerState> = _micPermissionBanner.asStateFlow()

    // --- État du lecteur audio intégré aux cartes ---
    private val _playingNoteId = MutableStateFlow<Long?>(null)
    val playingNoteId: StateFlow<Long?> = _playingNoteId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgressMs = MutableStateFlow(0)
    val playbackProgressMs: StateFlow<Int> = _playbackProgressMs.asStateFlow()

    private val _playbackDurationMs = MutableStateFlow(1000)
    val playbackDurationMs: StateFlow<Int> = _playbackDurationMs.asStateFlow()

    private val _highlightedNoteId = MutableStateFlow<Long?>(null)
    val highlightedNoteId: StateFlow<Long?> = _highlightedNoteId.asStateFlow()

    // --- Événements Toast / Snackbar ---
    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastEvents = _toastEvents.asSharedFlow()

    // --- État Réseau & Notifications ---
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnlineFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = networkMonitor.isCurrentlyOnline()
    )

    val notificationsEnabled: StateFlow<Boolean> = prefs.notificationsEnabledFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationHelper.hasNotificationPermission(appContext)
    )

    // --- Liste complète & Liste filtrée depuis Room (Single Source of Truth) ---
    val allRecordings: StateFlow<List<RecordingEntity>> = repository.allRecordingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredRecordings: StateFlow<List<RecordingEntity>> = combine(
        allRecordings,
        searchQuery
    ) { list, query ->
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            list
        } else {
            list.filter { it.title.lowercase().contains(trimmed) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var timerJob: Job? = null
    private var visualizerJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    init {
        NotificationHelper.createNotificationChannel(appContext)

        // Restaurer le dernier onglet depuis DataStore uniquement si aucun raccourci n'a forcé d'onglet
        viewModelScope.launch {
            val savedTab = prefs.lastSelectedTabFlow.first()
            if (!openedViaDeepLinkOrShortcut && !savedStateHandle.contains("selected_tab")) {
                savedStateHandle["selected_tab"] = savedTab
            }
        }

        // Dès que le réseau revient, déclencher la synchronisation de l'Outbox (WorkManager)
        viewModelScope.launch {
            var previousOnline = networkMonitor.isCurrentlyOnline()
            networkMonitor.isOnlineFlow.collect { online ->
                if (online && !previousOnline) {
                    emitToast("Connexion rétablie : synchronisation de l'Outbox en cours...")
                    repository.triggerSyncNow()
                } else if (!online && previousOnline) {
                    emitToast("Mode hors ligne : enregistrements placés dans l'Outbox")
                }
                previousOnline = online
            }
        }

        // Mettre à jour les 3 dernières notes dans les raccourcis dynamiques au démarrage et à chaque changement
        viewModelScope.launch {
            allRecordings.collect { list ->
                ShortcutHelper.updateDynamicShortcuts(appContext, list)
            }
        }
    }

    fun selectTab(index: Int, fromIntent: Boolean = false) {
        if (fromIntent) {
            openedViaDeepLinkOrShortcut = true
        }
        savedStateHandle["selected_tab"] = index
        // Annexe 4 (Slide 6 & 8) : déclaration manuelle de l'écran Compose avec nom en dur
        val screenName = if (index == 1) "notes" else "recorder"
        UmamiAnalyticsHelper.trackScreenView(appContext, screenName)
        viewModelScope.launch {
            prefs.setLastSelectedTab(index)
        }
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle["search_query"] = query
    }

    fun emitToast(message: String) {
        _toastEvents.tryEmit(message)
    }

    // --- Gestion de la permission Microphone (Chapitre 6 : jusqu'au refus définitif) ---
    fun onMicPermissionRequested() {
        viewModelScope.launch {
            prefs.markMicPermissionRequested()
        }
    }

    fun onMicPermissionDenied(shouldShowRationale: Boolean) {
        _statusMessage.value = "Accès micro refusé. Veuillez l'autoriser."
        _micPermissionBanner.value = if (shouldShowRationale) {
            PermissionBannerState.RATIONALE
        } else {
            PermissionBannerState.PERMANENTLY_DENIED
        }
        emitToast("Permission microphone refusée")
    }

    fun dismissPermissionBanner() {
        _micPermissionBanner.value = PermissionBannerState.NONE
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            prefs.markNotifPermissionRequested()
            prefs.setNotificationsEnabled(granted)
            if (granted) {
                emitToast("Notifications activées avec succès")
            } else {
                emitToast("Notifications désactivées")
            }
        }
    }

    // --- Enregistrement Audio (MediaRecorder + Visualiseur 32 barres) ---
    fun startRecording() {
        if (_isRecording.value) return
        stopPlayback()
        _micPermissionBanner.value = PermissionBannerState.NONE

        try {
            val outputFile = repository.createNewAudioFilePath("m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _isRecording.value = true
            _recordingSeconds.value = 0
            _statusMessage.value = "Enregistrement en cours..."

            // Chronomètre chaque seconde
            timerJob?.cancel()
            timerJob = viewModelScope.launch {
                while (isActive && _isRecording.value) {
                    delay(1000L)
                    _recordingSeconds.value += 1
                }
            }

            // Visualiseur 32 barres en direct (~20 FPS)
            visualizerJob?.cancel()
            visualizerJob = viewModelScope.launch {
                var tick = 0
                while (isActive && _isRecording.value) {
                    val rawAmp = try {
                        mediaRecorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    // Normalisation souple entre 0.06f et 1.0f
                    val normalized = (rawAmp / 18000f).coerceIn(0.06f, 1.0f)
                    val bars = List(32) { index ->
                        val wave = (sin((index * 0.45) + (tick * 0.35)) + 1.0) * 0.5
                        val jitter = (0.35f + (wave.toFloat() * 0.65f))
                        (normalized * jitter).coerceIn(0.06f, 1.0f)
                    }
                    _visualizerBars.value = bars
                    tick++
                    delay(50L)
                }
            }
        } catch (e: Exception) {
            _isRecording.value = false
            _statusMessage.value = "Erreur micro : ${e.localizedMessage ?: "indisponible"}"
            emitToast("Impossible de démarrer le microphone")
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        val durationSec = _recordingSeconds.value.coerceAtLeast(1)
        val outputFile = currentOutputFile

        timerJob?.cancel()
        visualizerJob?.cancel()
        _isRecording.value = false
        _recordingSeconds.value = 0
        _statusMessage.value = "Prêt"
        _visualizerBars.value = List(32) { 0.05f }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }

        if (outputFile != null && outputFile.exists()) {
            viewModelScope.launch {
                val online = networkMonitor.isCurrentlyOnline()
                repository.saveNewRecording(
                    filePath = outputFile.absolutePath,
                    mimeType = "audio/mp4",
                    durationSeconds = durationSec
                )
                // Annexe 4 : Événement d'activation (note_created)
                UmamiAnalyticsHelper.trackNoteCreated(
                    context = appContext,
                    durationSeconds = durationSec,
                    isOnline = online
                )
                if (online) {
                    emitToast("Note vocale enregistrée et synchronisation lancée")
                } else {
                    emitToast("Enregistré hors ligne (placé dans l'Outbox)")
                }
            }
        }
    }

    // --- Ajout d'un exemple de test 440 Hz (État vide ou démo) ---
    fun addSampleDemoRecording() {
        viewModelScope.launch {
            val count = allRecordings.value.size
            val online = networkMonitor.isCurrentlyOnline()
            repository.createAndSaveSampleDemoRecording(count)
            UmamiAnalyticsHelper.trackNoteCreated(
                context = appContext,
                durationSeconds = 2,
                isOnline = online
            )
            if (online) {
                emitToast("Exemple de test ajouté au stockage")
            } else {
                emitToast("Exemple ajouté hors ligne (placé dans l'Outbox)")
            }
        }
    }

    // --- Actions sur les cartes de notes (Lecture, Renommage, Suppression, Export, Épinglage) ---
    fun togglePlayRecording(recording: RecordingEntity) {
        if (_playingNoteId.value == recording.id) {
            val player = mediaPlayer
            if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                    _isPlaying.value = false
                } else {
                    player.start()
                    _isPlaying.value = true
                    startPlaybackProgressLoop()
                }
                return
            }
        }
        startNewPlayback(recording)
    }

    fun startNewPlayback(recording: RecordingEntity) {
        stopPlayback()
        val file = File(recording.filePath)
        if (!file.exists()) {
            emitToast("Fichier audio introuvable")
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            mediaPlayer = player
            _playingNoteId.value = recording.id
            _isPlaying.value = true
            _playbackDurationMs.value = player.duration.coerceAtLeast(1000)
            _playbackProgressMs.value = 0

            player.setOnCompletionListener {
                _isPlaying.value = false
                _playbackProgressMs.value = _playbackDurationMs.value
                playbackJob?.cancel()
            }

            startPlaybackProgressLoop()
        } catch (_: Exception) {
            emitToast("Lecture audio impossible")
            stopPlayback()
        }
    }

    fun seekPlaybackTo(recordingId: Long, fraction: Float) {
        if (_playingNoteId.value != recordingId) return
        val player = mediaPlayer ?: return
        val targetMs = (fraction.coerceIn(0f, 1f) * _playbackDurationMs.value).toInt()
        try {
            player.seekTo(targetMs)
            _playbackProgressMs.value = targetMs
        } catch (_: Exception) {
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            _playingNoteId.value = null
            _isPlaying.value = false
            _playbackProgressMs.value = 0
        }
    }

    private fun startPlaybackProgressLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                val pos = try {
                    mediaPlayer?.currentPosition ?: 0
                } catch (_: Exception) {
                    0
                }
                _playbackProgressMs.value = pos
                delay(100L)
            }
        }
    }

    fun renameRecording(recording: RecordingEntity, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(recording, newTitle)
            emitToast("Note renommée")
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        if (_playingNoteId.value == recording.id) {
            stopPlayback()
        }
        viewModelScope.launch {
            repository.deleteRecording(recording)
            emitToast("Note supprimée")
        }
    }

    fun exportRecordingToDownloads(recording: RecordingEntity) {
        viewModelScope.launch {
            val fileName = repository.exportToDownloads(recording)
            if (fileName != null) {
                emitToast("Sauvegardé dans Téléchargements : $fileName")
            } else {
                emitToast("Échec de l'exportation du fichier")
            }
        }
    }

    fun pinShortcutToHomeScreen(recording: RecordingEntity) {
        val supported = ShortcutHelper.requestPinRecordingShortcut(appContext, recording)
        // Annexe 4 : Événement de la fonctionnalité native (shortcut_user)
        UmamiAnalyticsHelper.trackShortcutUsed(
            context = appContext,
            shortcutType = "pinned",
            action = "pin_to_home",
            result = if (supported) "ok" else "unsupported"
        )
        if (!supported) {
            emitToast("Ce lanceur Android ne supporte pas l'épinglage de raccourcis")
        }
    }

    /**
     * Appelé depuis un raccourci (appui long sur l'icône ou épinglé), une notification ou un App Link (?noteId=X).
     * Bascule immédiatement sur l'onglet Notes, scrolle et met la note en surbrillance, puis lance sa lecture.
     */
    fun openAndPlayNoteById(noteId: Long) {
        selectTab(1, fromIntent = true)
        updateSearchQuery("") // S'assurer que la note n'est pas masquée par un filtre
        _highlightedNoteId.value = noteId
        viewModelScope.launch {
            // Attendre que la liste Room soit chargée si l'app démarre à froid
            val list = repository.allRecordingsFlow.first()
            val target = list.find { it.id == noteId }
            if (target != null) {
                startNewPlayback(target)
                emitToast("Lecture : ${target.title}")
            }
        }
    }

    override fun onCleared() {
        stopPlayback()
        if (_isRecording.value) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
        }
    }
}
