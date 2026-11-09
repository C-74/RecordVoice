package com.example.recordvoice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.recordvoice.analytics.UmamiAnalyticsHelper
import com.example.recordvoice.data.preferences.UserPreferencesRepository
import com.example.recordvoice.notifications.RecordVoiceMessagingService
import com.example.recordvoice.ui.RecordVoiceScreen
import com.example.recordvoice.ui.RecorderViewModel
import com.example.recordvoice.ui.theme.RecordVoiceTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_START_RECORDING = "com.example.recordvoice.ACTION_START_RECORDING"
        const val ACTION_PLAY_NOTE = "com.example.recordvoice.ACTION_PLAY_NOTE"
        const val ACTION_OPEN_NOTES_TAB = "com.example.recordvoice.ACTION_OPEN_NOTES_TAB"
        const val EXTRA_NOTE_ID = "extra_note_id"
    }

    private val viewModel: RecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force les icônes de la barre de statut (heure, batterie, réseau) en sombre sur le fond clair
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        // Récupérer et stocker le jeton FCM en arrière-plan
        fetchAndStoreFcmToken()

        if (savedInstanceState == null) {
            val isFromFcmBg = intent?.extras?.containsKey("google.message_id") == true ||
                intent?.extras?.containsKey("from") == true
            val launchSource = when {
                isFromFcmBg -> "fcm_push"
                intent?.action == ACTION_START_RECORDING -> "shortcut_static"
                intent?.action == ACTION_PLAY_NOTE -> "shortcut_note"
                intent?.action == ACTION_OPEN_NOTES_TAB -> "notification"
                intent?.action == Intent.ACTION_VIEW -> "app_link"
                else -> "launcher"
            }
            UmamiAnalyticsHelper.trackAppLaunchSession(
                context = this,
                launchSource = launchSource
            )
            handleIncomingIntent(intent)
        }

        setContent {
            RecordVoiceTheme {
                val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()

                // Maintien de l'écran allumé (Wake Lock / FLAG_KEEP_SCREEN_ON) pendant l'enregistrement
                DisposableEffect(isRecording) {
                    if (isRecording) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                // Launcher pour la permission RECORD_AUDIO (Chapitre 6 : demandée au bon moment, jusqu'au refus définitif)
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.startRecording()
                    } else {
                        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        )
                        viewModel.onMicPermissionDenied(shouldShowRationale)
                    }
                }

                // Launcher pour la permission POST_NOTIFICATIONS (Android 13+)
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    viewModel.onNotificationPermissionResult(isGranted)
                    if (isGranted) {
                        fetchAndStoreFcmToken()
                    }
                }

                RecordVoiceScreen(
                    viewModel = viewModel,
                    onRequestRecordWithPermission = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.startRecording()
                        } else {
                            viewModel.onMicPermissionRequested()
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasNotifPerm = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasNotifPerm) {
                                viewModel.onNotificationPermissionResult(granted = true)
                                fetchAndStoreFcmToken()
                            } else {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            viewModel.onNotificationPermissionResult(granted = true)
                            fetchAndStoreFcmToken()
                        }
                    },
                    onOpenAppSettings = {
                        val settingsIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(settingsIntent)
                    },
                    onShareRecording = { recording ->
                        val chooser = viewModel.repository.createShareIntent(recording)
                        if (chooser != null) {
                            startActivity(chooser)
                        } else {
                            viewModel.emitToast("Impossible de partager ce fichier audio")
                        }
                    },
                    onCopyFcmToken = {
                        fetchAndCopyFcmToken()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Récupère le jeton Firebase Cloud Messaging en tâche de fond et l'enregistre dans DataStore
     * (et l'affiche discrètement dans Logcat avec le tag RecordVoiceFCM).
     */
    private fun fetchAndStoreFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(RecordVoiceMessagingService.TAG, "Impossible de récupérer le jeton FCM", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result ?: return@addOnCompleteListener
            Log.d(RecordVoiceMessagingService.TAG, "Jeton FCM : $token")

            lifecycleScope.launch {
                UserPreferencesRepository(applicationContext).saveFcmToken(token)
            }
        }
    }

    /**
     * Récupère le jeton Firebase Cloud Messaging actuel, le copie directement dans le presse-papiers,
     * met à jour DataStore et affiche un Toast de confirmation.
     */
    private fun fetchAndCopyFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful || task.result == null) {
                Log.w(RecordVoiceMessagingService.TAG, "Impossible de récupérer le jeton FCM", task.exception)
                viewModel.emitToast("Impossible de récupérer le jeton FCM")
                return@addOnCompleteListener
            }
            val token = task.result
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FCM Token", token))
            viewModel.emitToast("Jeton FCM copié dans le presse-papiers !")
            Log.d(RecordVoiceMessagingService.TAG, "Jeton FCM copié : $token")

            lifecycleScope.launch {
                UserPreferencesRepository(applicationContext).saveFcmToken(token)
            }
        }
    }

    /**
     * Gère :
     * 1. Le raccourci statique (ACTION_START_RECORDING) -> ouvre l'onglet Enregistreur et lance l'enregistrement
     * 2. Les raccourcis dynamiques (3 dernières notes) / épinglés & notifications (ACTION_PLAY_NOTE) ->
     *    ouvre l'onglet Notes, scrolle jusqu'à la note et lance sa lecture
     * 3. Les notifications globales (ACTION_OPEN_NOTES_TAB) ou Push Firebase reçu app tuée -> ouvre l'onglet Notes
     * 4. L'App Link (https://warm-faun-5052e9.netlify.app) -> ouvre l'app ou la note ciblée (?noteId=X)
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // Cas d'une notification Push Firebase cliquée alors que l'app était tuée
        val extras = intent.extras
        if (extras != null && (extras.containsKey("google.message_id") || extras.containsKey("from"))) {
            val fcmNoteId = extras.getString("noteId")?.toLongOrNull()
            if (fcmNoteId != null) {
                viewModel.openAndPlayNoteById(fcmNoteId)
            } else {
                viewModel.selectTab(1, fromIntent = true)
                viewModel.emitToast("Ouvert depuis la notification Push")
            }
            return
        }

        when (intent.action) {
            ACTION_START_RECORDING -> {
                UmamiAnalyticsHelper.trackShortcutUsed(
                    context = this,
                    shortcutType = "static",
                    action = "start_recording"
                )
                viewModel.selectTab(0, fromIntent = true)
                val hasMic = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    viewModel.startRecording()
                    viewModel.emitToast("Enregistrement lancé depuis le raccourci")
                } else {
                    viewModel.emitToast("Appuyez sur Enregistrer pour autoriser le micro")
                }
            }

            ACTION_PLAY_NOTE -> {
                val extraId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
                val uriId = intent.data?.lastPathSegment?.toLongOrNull() ?: -1L
                val noteId = if (extraId != -1L) extraId else uriId

                UmamiAnalyticsHelper.trackShortcutUsed(
                    context = this,
                    shortcutType = "note_shortcut",
                    action = "open_and_play_note"
                )

                if (noteId != -1L) {
                    viewModel.openAndPlayNoteById(noteId)
                } else {
                    viewModel.selectTab(1, fromIntent = true)
                }
            }

            ACTION_OPEN_NOTES_TAB -> {
                viewModel.selectTab(1, fromIntent = true)
            }

            Intent.ACTION_VIEW -> {
                val data: Uri? = intent.data
                if (data != null) {
                    val noteIdParam = data.getQueryParameter("noteId")?.toLongOrNull()
                    if (noteIdParam != null) {
                        viewModel.openAndPlayNoteById(noteIdParam)
                    } else if (data.path?.contains("notes", ignoreCase = true) == true) {
                        viewModel.selectTab(1, fromIntent = true)
                        viewModel.emitToast("Ouvert via l'App Link RecordPWA")
                    } else {
                        viewModel.emitToast("Ouvert via l'App Link RecordPWA")
                    }
                }
            }
        }
    }
}
