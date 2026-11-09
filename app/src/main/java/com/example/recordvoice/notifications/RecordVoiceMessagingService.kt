package com.example.recordvoice.notifications

import android.util.Log
import com.example.recordvoice.data.preferences.UserPreferencesRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Chapitre 5 & Antisèche 2/2 (Slide 13 de Android - TP.pdf) :
 * Service Firebase Cloud Messaging (FCM) permettant de recevoir des notifications Push
 * même lorsque l'application est tuée, et d'ouvrir l'écran Notes (ou une note précise via data["noteId"]).
 */
class RecordVoiceMessagingService : FirebaseMessagingService() {

    companion object {
        const val TAG = "RecordVoiceFCM"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nouveau jeton FCM : $token")
        serviceScope.launch {
            UserPreferencesRepository(applicationContext).saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message Push FCM reçu : ${message.data}")

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "RecordVoice"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Vos notes vocales vous attendent. Touchez pour ouvrir."
        val noteId = message.data["noteId"]?.toLongOrNull()

        NotificationHelper.showNotification(
            context = applicationContext,
            title = title,
            body = body,
            noteId = noteId
        )
    }
}
