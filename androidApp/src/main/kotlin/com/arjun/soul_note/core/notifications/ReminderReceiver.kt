package com.arjun.soul_note.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arjun.soul_note.app.R
import kotlinx.datetime.LocalTime

/**
 * Posts a reminder notification when its alarm fires, then arms the next occurrence.
 *
 * Re-arming is the important half. `AlarmManager` has no repeating weekday trigger, so the
 * alarm is one-shot: without this the reminder would fire exactly once after the user turned
 * it on and then never again.
 *
 * Registered in the manifest and always exported=false, so nothing outside the app can
 * trigger it. Reminders are best-effort: if the platform has revoked notification permission,
 * the notification is dropped rather than throwing, because a crash from a background
 * broadcast is far worse than a missed nudge. The re-arm is independent of that, so a
 * reminder keeps its cadence even while notifications are suppressed.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderIntents.EXTRA_ID)
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra(ReminderIntents.EXTRA_KIND) ?: "") }
            .getOrDefault(ReminderKind.DAILY_DIARY)
        val fallback = ReminderKind.DAILY_DIARY.copyOrNull() ?: return
        val copy = kind.copyOrNull() ?: fallback
        val title = intent.getStringExtra(ReminderIntents.EXTRA_TITLE) ?: copy.title
        val body = intent.getStringExtra(ReminderIntents.EXTRA_BODY) ?: copy.body

        createChannel(context)
        post(context, id, kind, title, body)
        rearm(context, intent, id, kind, title, body)
    }

    private fun post(context: Context, id: String?, kind: ReminderKind, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            // Keyed by the reminder id when there is one, so a rescheduled reminder
            // replaces its own notification instead of stacking a second copy.
            val notificationId = id?.hashCode() ?: kind.name.hashCode()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /**
     * Arms the following occurrence from the schedule carried in [intent].
     *
     * The reminder is rebuilt from those extras rather than looked up, so a delivered
     * reminder keeps repeating even if the user has since turned reminders off — turning
     * them off cancels the alarm, and a race between that cancel and this re-arm would
     * otherwise resurrect it. A missing or malformed schedule simply means there is nothing
     * left to arm.
     */
    private fun rearm(
        context: Context,
        intent: Intent,
        id: String?,
        kind: ReminderKind,
        title: String,
        body: String,
    ) {
        val reminderId = id ?: return
        val hour = intent.getIntExtra(ReminderIntents.EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(ReminderIntents.EXTRA_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) return

        val request = ReminderRequest(
            id = reminderId,
            kind = kind,
            time = LocalTime(hour, minute),
            daysOfWeek = intent.getIntArrayExtra(ReminderIntents.EXTRA_DAYS)?.toSet().orEmpty(),
        )
        runCatching { scheduleReminderAlarm(context, request, title, body) }
    }

    private fun createChannel(context: Context) {
        ensureChannel(context)
    }

    companion object {
        const val CHANNEL_ID = "diary_reminders"
        const val CHANNEL_NAME = "Journal reminders"

        /**
         * Creates the reminders channel if it is not already there.
         *
         * Called at startup, not only when a reminder fires. Posting to a channel that does not
         * exist yet is not an error on API 26+ — the notification is silently dropped — so a
         * push that arrived before the user's first local reminder would simply never appear,
         * with nothing in the log to say why.
         */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(AndroidNotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    // DEFAULT importance: a reminder that makes sound every evening becomes
                    // something people turn off, including the reminder itself.
                    AndroidNotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
