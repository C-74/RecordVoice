package com.example.recordvoice.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.recordvoice.MainActivity
import com.example.recordvoice.R

object NotificationHelper {
    const val CHANNEL_ID = "record_voice_channel"
    private const val CHANNEL_NAME = "Synchronisation Outbox"
    private const val CHANNEL_DESC = "Notifications de synchronisation des notes vocales dans le cloud"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Affiche une notification avec icône microphone dédiée (ic_stat_mic) et un clic utile (Chapitre 5) :
     * toucher la notification ouvre MainActivity sur l'onglet Notes (et sélectionne/joue la note si noteId est fourni).
     */
    fun showNotification(
        context: Context,
        title: String,
        body: String,
        noteId: Long? = null,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = if (noteId != null) {
                MainActivity.ACTION_PLAY_NOTE
            } else {
                MainActivity.ACTION_OPEN_NOTES_TAB
            }
            if (noteId != null) {
                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_mic)
            .setColor(0xFFFF2B4B.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Ignoré si la permission est révoquée entre-temps
        }
    }
}
