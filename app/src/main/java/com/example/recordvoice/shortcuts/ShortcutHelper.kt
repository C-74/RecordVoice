package com.example.recordvoice.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.recordvoice.MainActivity
import com.example.recordvoice.R
import com.example.recordvoice.data.local.RecordingEntity

/**
 * Fonctionnalité native impossible en PWA (Slide 107) :
 * Gestion complète des raccourcis Android (App Shortcuts) :
 * 1. Raccourcis épinglés (Pinned Shortcuts) sur l'écran d'accueil via le bouton « Épingler » sur chaque note
 * 2. Raccourcis dynamiques (Dynamic Shortcuts) des 3 dernières notes au clic long sur l'icône de l'app
 *    qui redirigent directement vers la note en question dans l'application
 * 3. Raccourci statique « Enregistrer » dans shortcuts.xml
 */
object ShortcutHelper {

    private fun shortcutIdForNote(noteId: Long): String = "note_shortcut_$noteId"

    private fun buildNoteShortcutInfo(
        context: Context,
        recording: RecordingEntity,
        rank: Int = 0
    ): ShortcutInfoCompat {
        // On passe l'ID à la fois dans data (Uri) et dans putExtra pour garantir à 100% sa conservation
        // par le Launcher Android (Pixel Search, Appui long, Raccourci épinglé)
        val playIntent = Intent(
            MainActivity.ACTION_PLAY_NOTE,
            Uri.parse("recordvoice://note/${recording.id}"),
            context,
            MainActivity::class.java
        ).apply {
            putExtra(MainActivity.EXTRA_NOTE_ID, recording.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val shortLabel = recording.title.take(20).ifBlank { "Mémo #${recording.id}" }
        val longLabel = recording.title.take(45).ifBlank { "Mémo vocal #${recording.id}" }

        return ShortcutInfoCompat.Builder(context, shortcutIdForNote(recording.id))
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_play))
            .setIntent(playIntent)
            .setRank(rank)
            .build()
    }

    /**
     * Épingle une note vocale directement sur l'écran d'accueil du téléphone (Pinned Shortcut).
     */
    fun requestPinRecordingShortcut(context: Context, recording: RecordingEntity): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false
        }
        val shortcutInfo = buildNoteShortcutInfo(context, recording)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
    }

    /**
     * Met à jour les raccourcis dynamiques (appui long sur l'icône de l'app) avec les 3 dernières notes.
     */
    fun updateDynamicShortcuts(context: Context, recordings: List<RecordingEntity>) {
        try {
            val topNotes = recordings.take(3)
            val shortcuts = topNotes.mapIndexed { index, rec ->
                buildNoteShortcutInfo(context, rec, rank = index)
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (_: Exception) {
        }
    }

    /**
     * Met à jour le libellé d'un raccourci existant (épinglé ou dynamique) quand la note est renommée.
     */
    fun onRecordingRenamed(context: Context, recording: RecordingEntity, allRecordings: List<RecordingEntity>) {
        try {
            val updatedShortcut = buildNoteShortcutInfo(context, recording)
            ShortcutManagerCompat.updateShortcuts(context, listOf(updatedShortcut))
            updateDynamicShortcuts(context, allRecordings)
        } catch (_: Exception) {
        }
    }

    /**
     * Désactive proprement le raccourci (épinglé ou dynamique) lorsqu'une note est supprimée.
     */
    fun onRecordingDeleted(context: Context, noteId: Long, remainingRecordings: List<RecordingEntity>) {
        try {
            val id = shortcutIdForNote(noteId)
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(id),
                "Cette note vocale a été supprimée."
            )
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(id))
            updateDynamicShortcuts(context, remainingRecordings)
        } catch (_: Exception) {
        }
    }
}
