package com.arjun.soul_note.core.notifications

import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arjun.soul_note.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives pushed messages and posts them as ordinary notifications.
 *
 * Lives in `:androidApp` rather than `:composeApp` for the same reason [ReminderReceiver] does:
 * it needs this module's `R` for the status-bar icon and the channel name, and a KMP library
 * target has no app resources of its own.
 *
 * Handles the `data` half of a message and leaves the `notification` half alone. The system
 * draws a `notification` message itself whenever the app is in the background, so handling both
 * would show the same push twice with different chrome depending on what the app happened to be
 * doing. A data message is delivered here and only here, which means the appearance of the
 * notification is this app's decision rather than the SDK's.
 *
 * The service is declared but disabled unless the build passed
 * `-Pdiary.firebaseMessaging=true`; see the manifest. None of this runs otherwise.
 *
 * Nothing read out of the message is logged. The body is untrusted input arriving while nobody
 * is looking at it, and the rules are the same as for a synced entry: posted verbatim, never
 * parsed, never written down.
 */
class FcmMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data[TITLE] ?: return
        val body = message.data[BODY] ?: return
        post(title, body)
    }

    private fun post(title: String, body: String) {
        val result = runCatching {
            val notification = NotificationCompat.Builder(this, ReminderReceiver.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            // Same reason the receiver checks this: on API 33+ a permission the user has
            // withdrawn makes this throw SecurityException rather than return false, and a
            // crash inside a push callback is a crash the user cannot explain.
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Log.i(TAG, "notifications are not permitted; dropping a pushed message")
                return
            }
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
        result.onFailure { Log.w(TAG, "could not post a pushed message") }
    }

    override fun onNewToken(token: String) {
        // The token just rotated. Re-registering needs a signed-in user, and a service has no
        // business reaching into the session, so this is left to the next sign-in rather than
        // fired from here. The stale document is keyed by the old token's hash, so it lingers
        // until a sender prunes on `updatedAt`.
        //
        // The token is not logged and not passed on. It is a credential.
        Log.i(TAG, "push token rotated")
    }

    private companion object {
        const val TAG = "FcmMessagingService"
        const val NOTIFICATION_ID = 4201
        const val TITLE = "title"
        const val BODY = "body"
    }
}
