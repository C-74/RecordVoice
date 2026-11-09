package com.example.recordvoice.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recordvoice.data.local.RecordingEntity
import com.example.recordvoice.ui.theme.AppBackground
import com.example.recordvoice.ui.theme.BorderSubtle
import com.example.recordvoice.ui.theme.CardSurface
import com.example.recordvoice.ui.theme.CoralPrimary
import com.example.recordvoice.ui.theme.CoralSoftBg
import com.example.recordvoice.ui.theme.CoralSoftBorder
import com.example.recordvoice.ui.theme.CoralTimerText
import com.example.recordvoice.ui.theme.DarkNavy
import com.example.recordvoice.ui.theme.OnlineGreen
import com.example.recordvoice.ui.theme.OnlineGreenSoft
import com.example.recordvoice.ui.theme.OutboxAmber
import com.example.recordvoice.ui.theme.OutboxAmberSoft
import com.example.recordvoice.ui.theme.SlateSecondary
import java.util.Locale

@Composable
fun RecordVoiceScreen(
    viewModel: RecorderViewModel,
    onRequestRecordWithPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onShareRecording: (RecordingEntity) -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val allRecordings by viewModel.allRecordings.collectAsStateWithLifecycle()
    val filteredRecordings by viewModel.filteredRecordings.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingSeconds by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val visualizerBars by viewModel.visualizerBars.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val micPermissionBanner by viewModel.micPermissionBanner.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    val playingNoteId by viewModel.playingNoteId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgressMs by viewModel.playbackProgressMs.collectAsStateWithLifecycle()
    val playbackDurationMs by viewModel.playbackDurationMs.collectAsStateWithLifecycle()
    val highlightedNoteId by viewModel.highlightedNoteId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // États des boîtes de dialogue (Renommer & Supprimer) conservés en cas de rotation (rememberSaveable)
    var renamingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTextInput by rememberSaveable { mutableStateOf("") }
    var deletingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
        (configuration.screenWidthDp >= 700)

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = DarkNavy,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isWideScreen) {
                // Barre d'onglets en haut sur Smartphone (Portrait)
                MobileTopTabBar(
                    selectedTab = selectedTab,
                    notesCount = allRecordings.size,
                    onSelectTab = { viewModel.selectTab(it) }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    if (selectedTab == 0) {
                        RecorderStudioPane(
                            isOnline = isOnline,
                            notificationsEnabled = notificationsEnabled,
                            isRecording = isRecording,
                            recordingSeconds = recordingSeconds,
                            visualizerBars = visualizerBars,
                            statusMessage = statusMessage,
                            micPermissionBanner = micPermissionBanner,
                            onToggleNotifications = onRequestNotificationPermission,
                            onRecordClick = onRequestRecordWithPermission,
                            onStopClick = { viewModel.stopRecording() },
                            onOpenAppSettings = onOpenAppSettings,
                            onDismissPermissionBanner = { viewModel.dismissPermissionBanner() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        NotesListPane(
                            allCount = allRecordings.size,
                            recordings = filteredRecordings,
                            searchQuery = searchQuery,
                            playingNoteId = playingNoteId,
                            isPlaying = isPlaying,
                            playbackProgressMs = playbackProgressMs,
                            playbackDurationMs = playbackDurationMs,
                            highlightedNoteId = highlightedNoteId,
                            onSearchChange = { viewModel.updateSearchQuery(it) },
                            onAddSampleDemo = { viewModel.addSampleDemoRecording() },
                            onTogglePlay = { viewModel.togglePlayRecording(it) },
                            onSeek = { id, frac -> viewModel.seekPlaybackTo(id, frac) },
                            onRenameClick = { rec ->
                                renamingNoteId = rec.id
                                renameTextInput = rec.title
                            },
                            onPinShortcutClick = { viewModel.pinShortcutToHomeScreen(it) },
                            onShareClick = onShareRecording,
                            onSaveExportClick = { viewModel.exportRecordingToDownloads(it) },
                            onDeleteClick = { deletingNoteId = it.id },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // Mode Tablette / Paysage (Landscape) : 2 colonnes côte à côte comme la PWA Desktop
                Row(modifier = Modifier.fillMaxSize()) {
                    NotesListPane(
                        allCount = allRecordings.size,
                        recordings = filteredRecordings,
                        searchQuery = searchQuery,
                        playingNoteId = playingNoteId,
                        isPlaying = isPlaying,
                        playbackProgressMs = playbackProgressMs,
                        playbackDurationMs = playbackDurationMs,
                        highlightedNoteId = highlightedNoteId,
                        onSearchChange = { viewModel.updateSearchQuery(it) },
                        onAddSampleDemo = { viewModel.addSampleDemoRecording() },
                        onTogglePlay = { viewModel.togglePlayRecording(it) },
                        onSeek = { id, frac -> viewModel.seekPlaybackTo(id, frac) },
                        onRenameClick = { rec ->
                            renamingNoteId = rec.id
                            renameTextInput = rec.title
                        },
                        onPinShortcutClick = { viewModel.pinShortcutToHomeScreen(it) },
                        onShareClick = onShareRecording,
                        onSaveExportClick = { viewModel.exportRecordingToDownloads(it) },
                        onDeleteClick = { deletingNoteId = it.id },
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(BorderSubtle)
                    )

                    RecorderStudioPane(
                        isOnline = isOnline,
                        notificationsEnabled = notificationsEnabled,
                        isRecording = isRecording,
                        recordingSeconds = recordingSeconds,
                        visualizerBars = visualizerBars,
                        statusMessage = statusMessage,
                        micPermissionBanner = micPermissionBanner,
                        onToggleNotifications = onRequestNotificationPermission,
                        onRecordClick = onRequestRecordWithPermission,
                        onStopClick = { viewModel.stopRecording() },
                        onOpenAppSettings = onOpenAppSettings,
                        onDismissPermissionBanner = { viewModel.dismissPermissionBanner() },
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }

    // Boîte de dialogue de renommage d'une note
    val noteToRename = allRecordings.find { it.id == renamingNoteId }
    if (noteToRename != null) {
        AlertDialog(
            onDismissRequest = { renamingNoteId = null },
            title = {
                Text(
                    text = "Renommer cette note",
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            },
            text = {
                OutlinedTextField(
                    value = renameTextInput,
                    onValueChange = { renameTextInput = it },
                    singleLine = true,
                    label = { Text("Titre de la note") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoralPrimary,
                        focusedLabelColor = CoralPrimary,
                        cursorColor = CoralPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameRecording(noteToRename, renameTextInput)
                        renamingNoteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingNoteId = null }) {
                    Text("Annuler", color = SlateSecondary)
                }
            }
        )
    }

    // Boîte de dialogue de confirmation de suppression
    val noteToDelete = allRecordings.find { it.id == deletingNoteId }
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { deletingNoteId = null },
            title = {
                Text(
                    text = "Supprimer la note ?",
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            },
            text = {
                Text(
                    text = "Supprimer « ${noteToDelete.title} » ? Cette action est irréversible.",
                    color = SlateSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRecording(noteToDelete)
                        deletingNoteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingNoteId = null }) {
                    Text("Annuler", color = SlateSecondary)
                }
            }
        )
    }
}

@Composable
private fun MobileTopTabBar(
    selectedTab: Int,
    notesCount: Int,
    onSelectTab: (Int) -> Unit
) {
    Surface(
        color = CardSurface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabPillButton(
                selected = selectedTab == 0,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Enregistreur",
                onClick = { onSelectTab(0) },
                modifier = Modifier.weight(1f)
            )

            TabPillButton(
                selected = selectedTab == 1,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = "Notes ($notesCount)",
                onClick = { onSelectTab(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabPillButton(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) CoralSoftBg else Color(0xFFF8FAFC),
        label = "tabBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) CoralPrimary else SlateSecondary,
        label = "tabContent"
    )
    val borderColor = if (selected) CoralSoftBorder else BorderSubtle

    Surface(
        onClick = onClick,
        color = bgColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecorderStudioPane(
    isOnline: Boolean,
    notificationsEnabled: Boolean,
    isRecording: Boolean,
    recordingSeconds: Int,
    visualizerBars: List<Float>,
    statusMessage: String,
    micPermissionBanner: PermissionBannerState,
    onToggleNotifications: () -> Unit,
    onRecordClick: () -> Unit,
    onStopClick: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissPermissionBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // En-tête (Header)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Badge Réseau (En ligne / Hors ligne)
                    Surface(
                        color = if (isOnline) OnlineGreenSoft else CoralSoftBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (isOnline) OnlineGreen.copy(alpha = 0.35f) else CoralSoftBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) OnlineGreen else CoralPrimary)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isOnline) "En ligne" else "Hors ligne",
                                color = if (isOnline) OnlineGreen else CoralPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bouton Notifications
                    Surface(
                        onClick = onToggleNotifications,
                        color = if (notificationsEnabled) CoralSoftBg else Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (notificationsEnabled) CoralSoftBorder else BorderSubtle)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = if (notificationsEnabled) CoralPrimary else SlateSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (notificationsEnabled) "Notifs actives" else "Activer notifs",
                                color = if (notificationsEnabled) CoralPrimary else SlateSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "RecordVoice",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkNavy
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Capturez vos idées vocales instantanément, stockées en local et disponibles hors ligne.",
                    fontSize = 14.sp,
                    color = SlateSecondary,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bannière pédagogique en cas de refus ou refus définitif de la permission Microphone (Chapitre 6)
        if (micPermissionBanner != PermissionBannerState.NONE) {
            PermissionFeedbackCard(
                bannerState = micPermissionBanner,
                onRetryRequest = onRecordClick,
                onOpenSettings = onOpenAppSettings,
                onDismiss = onDismissPermissionBanner
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bloc Studio : Visualiseur + Chronomètre + Statut + Boutons
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visualiseur sombre (#0F172A) + Chronomètre (#FF94A5)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkNavy)
                        .padding(16.dp)
                ) {
                    AudioWaveformCanvas(
                        bars = visualizerBars,
                        isRecording = isRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(65.dp)
                            .align(Alignment.BottomCenter)
                    )

                    Text(
                        text = formatDurationMmSs(recordingSeconds),
                        color = CoralTimerText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pilule de statut (Point lumineux + Texte)
                StatusIndicatorPill(
                    isRecording = isRecording,
                    statusMessage = statusMessage
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Boutons Enregistrer et Arrêter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onRecordClick,
                        enabled = !isRecording,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = CoralPrimary.copy(alpha = 0.45f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enregistrer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = onStopClick,
                        enabled = isRecording,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkNavy,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFE2E8F0),
                            disabledContentColor = SlateSecondary
                        ),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Arrêter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionFeedbackCard(
    bannerState: PermissionBannerState,
    onRetryRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CoralSoftBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CoralSoftBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (bannerState == PermissionBannerState.RATIONALE) {
                            Icons.Default.Mic
                        } else {
                            Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bannerState == PermissionBannerState.RATIONALE) {
                            "Pourquoi l'accès au microphone ?"
                        } else {
                            "Accès au microphone bloqué"
                        },
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy,
                        fontSize = 15.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = SlateSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (bannerState == PermissionBannerState.RATIONALE) {
                    "RecordVoice a besoin du microphone uniquement lorsque vous appuyez sur « Enregistrer » pour capturer votre mémo vocal en local."
                } else {
                    "La permission microphone a été refusée définitivement. Ouvrez les paramètres Android de l'application pour l'autoriser manuellement."
                },
                fontSize = 13.sp,
                color = DarkNavy.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (bannerState == PermissionBannerState.RATIONALE) {
                Button(
                    onClick = onRetryRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Autoriser le microphone", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ouvrir les paramètres", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AudioWaveformCanvas(
    bars: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val activeBrush = Brush.verticalGradient(
        colors = listOf(CoralSoftBorder, CoralPrimary)
    )
    val idleBrush = Brush.verticalGradient(
        colors = listOf(
            CoralSoftBorder.copy(alpha = 0.25f),
            CoralSoftBorder.copy(alpha = 0.25f)
        )
    )

    Canvas(modifier = modifier) {
        val barCount = 32
        val spacing = 3.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = ((size.width - totalSpacing) / barCount).coerceAtLeast(2f)

        for (i in 0 until barCount) {
            val fraction = if (isRecording) {
                bars.getOrElse(i) { 0.06f }.coerceIn(0.06f, 1f)
            } else {
                0.08f
            }
            val barHeight = (size.height * fraction).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + spacing)
            val y = size.height - barHeight

            drawRoundRect(
                brush = if (isRecording) activeBrush else idleBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

@Composable
private fun StatusIndicatorPill(
    isRecording: Boolean,
    statusMessage: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val isDenied = statusMessage.contains("refusé", ignoreCase = true) ||
        statusMessage.contains("Erreur", ignoreCase = true)

    val dotColor = when {
        isRecording -> CoralPrimary
        isDenied -> OutboxAmber
        else -> OnlineGreen
    }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(if (isRecording) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusMessage,
                color = DarkNavy,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NotesListPane(
    allCount: Int,
    recordings: List<RecordingEntity>,
    searchQuery: String,
    playingNoteId: Long?,
    isPlaying: Boolean,
    playbackProgressMs: Int,
    playbackDurationMs: Int,
    highlightedNoteId: Long?,
    onSearchChange: (String) -> Unit,
    onAddSampleDemo: () -> Unit,
    onTogglePlay: (RecordingEntity) -> Unit,
    onSeek: (Long, Float) -> Unit,
    onRenameClick: (RecordingEntity) -> Unit,
    onPinShortcutClick: (RecordingEntity) -> Unit,
    onShareClick: (RecordingEntity) -> Unit,
    onSaveExportClick: (RecordingEntity) -> Unit,
    onDeleteClick: (RecordingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Scroller automatiquement jusqu'à la note ouverte via un raccourci ou une notification
    LaunchedEffect(highlightedNoteId, recordings.size) {
        if (highlightedNoteId != null) {
            val targetIndex = recordings.indexOfFirst { it.id == highlightedNoteId }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    Column(
        modifier = modifier
            .background(AppBackground)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // En-tête de la liste : Titre + Badge compteur
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Notes enregistrées",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkNavy
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = CoralPrimary,
                    shape = CircleShape
                ) {
                    Text(
                        text = allCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Barre de recherche en direct
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(
                    text = "Rechercher une note vocale...",
                    color = SlateSecondary,
                    fontSize = 14.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Rechercher",
                    tint = SlateSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Effacer",
                            tint = SlateSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedBorderColor = CoralPrimary,
                unfocusedBorderColor = BorderSubtle,
                cursorColor = CoralPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (recordings.isEmpty()) {
            EmptyNotesState(
                hasSearchQuery = searchQuery.isNotBlank(),
                onAddSampleDemo = onAddSampleDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = recordings,
                    key = { it.id }
                ) { item ->
                    val isThisPlaying = playingNoteId == item.id
                    val isHighlighted = highlightedNoteId == item.id

                    RecordingCardItem(
                        recording = item,
                        isCurrentTrack = isThisPlaying,
                        isPlaying = isThisPlaying && isPlaying,
                        progressMs = if (isThisPlaying) playbackProgressMs else 0,
                        durationMs = if (isThisPlaying) playbackDurationMs else (item.duration * 1000).coerceAtLeast(1000),
                        isHighlighted = isHighlighted,
                        onTogglePlay = { onTogglePlay(item) },
                        onSeek = { frac -> onSeek(item.id, frac) },
                        onRenameClick = { onRenameClick(item) },
                        onPinShortcutClick = { onPinShortcutClick(item) },
                        onShareClick = { onShareClick(item) },
                        onSaveExportClick = { onSaveExportClick(item) },
                        onDeleteClick = { onDeleteClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesState(
    hasSearchQuery: Boolean,
    onAddSampleDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CoralSoftBg)
                    .border(1.dp, CoralSoftBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (hasSearchQuery) "Aucun résultat trouvé" else "Aucune note pour le moment",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = DarkNavy
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasSearchQuery) {
                    "Aucune note vocale ne correspond à votre recherche."
                } else {
                    "Utilisez l'onglet Enregistreur pour démarrer votre premier mémo vocal."
                },
                fontSize = 13.sp,
                color = SlateSecondary,
                textAlign = TextAlign.Center
            )

            if (!hasSearchQuery) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAddSampleDemo,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CoralSoftBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CoralSoftBg,
                        contentColor = CoralPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ajouter un exemple de test",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordingCardItem(
    recording: RecordingEntity,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    progressMs: Int,
    durationMs: Int,
    isHighlighted: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onRenameClick: () -> Unit,
    onPinShortcutClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val borderColor = if (isHighlighted || isCurrentTrack) CoralPrimary else BorderSubtle

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isHighlighted || isCurrentTrack) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Haut de carte : Titre (cliquable pour renommer) + Date + Badges Sync & Durée
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onRenameClick)
                        .padding(end = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recording.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DarkNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Renommer",
                            tint = SlateSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = recording.dateStr,
                        fontSize = 12.sp,
                        color = SlateSecondary
                    )
                }

                // Badges avec icônes Material : Sync / Outbox + Durée
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (recording.synced) OnlineGreenSoft else OutboxAmberSoft,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (recording.synced) OnlineGreen.copy(alpha = 0.3f) else OutboxAmber.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (recording.synced) Icons.Default.CloudDone else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (recording.synced) OnlineGreen else OutboxAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (recording.synced) "Sync" else "Outbox",
                                color = if (recording.synced) OnlineGreen else OutboxAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = CoralSoftBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, CoralSoftBorder)
                    ) {
                        Text(
                            text = formatDurationMmSs(recording.duration),
                            color = CoralPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lecteur Audio intégré (Play/Pause + Slider + Temps)
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val fraction = if (durationMs > 0) {
                        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    Slider(
                        value = fraction,
                        onValueChange = { onSeek(it) },
                        colors = SliderDefaults.colors(
                            thumbColor = CoralPrimary,
                            activeTrackColor = CoralPrimary,
                            inactiveTrackColor = CoralSoftBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val displayedSec = if (isCurrentTrack) (progressMs / 1000) else recording.duration
                    Text(
                        text = formatDurationMmSs(displayedSec),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = SlateSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Barre d'actions en bas de carte avec icônes Material : Épingler, Partager, Sauvegarder, Supprimer
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CardActionChip(
                    icon = Icons.Default.PushPin,
                    label = "Épingler",
                    tint = CoralPrimary,
                    bgColor = CoralSoftBg,
                    borderColor = CoralSoftBorder,
                    onClick = onPinShortcutClick
                )

                CardActionChip(
                    icon = Icons.Default.Share,
                    label = "Partager",
                    tint = DarkNavy,
                    bgColor = Color(0xFFF8FAFC),
                    borderColor = BorderSubtle,
                    onClick = onShareClick
                )

                CardActionChip(
                    icon = Icons.Default.Download,
                    label = "Sauvegarder",
                    tint = DarkNavy,
                    bgColor = Color(0xFFF8FAFC),
                    borderColor = BorderSubtle,
                    onClick = onSaveExportClick
                )

                CardActionChip(
                    icon = Icons.Default.Delete,
                    label = "Supprimer",
                    tint = CoralPrimary,
                    bgColor = Color(0xFFFFF1F3),
                    borderColor = CoralSoftBorder,
                    onClick = onDeleteClick
                )
            }
        }
    }
}

@Composable
private fun CardActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    bgColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

fun formatDurationMmSs(totalSeconds: Int): String {
    val safeSec = totalSeconds.coerceAtLeast(0)
    val minutes = safeSec / 60
    val seconds = safeSec % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
