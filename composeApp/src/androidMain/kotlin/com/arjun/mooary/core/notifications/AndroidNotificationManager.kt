package com.arjun.mooary.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arjun.mooary.core.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * Android reminders, scheduled through `AlarmManager`.
 *
 * The alarm itself is armed by [scheduleReminderAlarm], which the receiver reuses when it
 * re-arms the following occurrence after a reminder fires.
 */
class AndroidNotificationManager(
    private val context: Context,
    private val logger: AppLogger,
) : NotificationManager {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun scheduleReminder(request: ReminderRequest): Result<ScheduledReminder> =
        withContext(Dispatchers.Default) {
            if (alarmManager == null) {
                return@withContext Result.failure(IllegalStateException("No AlarmManager"))
            }

            val copy = request.kind.localisedCopy()
            runCatching {
                scheduleReminderAlarm(context, request, copy.title, copy.body)
                    ?: error("Reminder ${request.id} has no future occurrence")
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { error ->
                    logger.error(TAG) { "could not schedule ${request.id}" }
                    Result.failure(error)
                },
            )
        }

    override suspend fun cancelReminder(id: String) {
        withContext(Dispatchers.Default) {
            val manager = alarmManager ?: return@withContext
            manager.cancel(
                PendingIntent.getBroadcast(
                    context,
                    id.hashCode(),
                    Intent().setComponent(receiverComponent(context)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    /**
     * Android's alarm table is not enumerable, so every stored reminder id is cancelled by
     * asking the platform to drop this package's alarms — `AlarmManager` clears only
     * alarms it created, so nothing outside the app is touched.
     */
    override suspend fun cancelAll() {
        withContext(Dispatchers.Default) {
            runCatching { alarmManager?.cancelAll() }
                .onFailure { logger.warn(TAG) { "could not cancel all reminders" } }
        }
    }

    /** Always empty: the alarm table is not queryable, and guessing would be a lie. */
    override suspend fun scheduled(): List<ScheduledReminder> = emptyList()

    /**
     * Whether the app may post a reminder right now.
     *
     * On API 33+ this asks the PackageManager whether `POST_NOTIFICATIONS` is granted, not
     * `NotificationManagerCompat.areNotificationsEnabled()`. Those are not interchangeable
     * and the difference is observable: `areNotificationsEnabled()` is an IPC query against
     * NotificationManagerService, so immediately after the user taps Allow it still answers
     * with the pre-grant value. The result is that granting the permission makes the app
     * report a refusal, leave the switch on with nothing scheduled behind it, and tell the
     * user they said no. The grant itself is answered from this process's own uid state and
     * is already correct by the time the Activity result is delivered.
     *
     * Below API 33 the runtime permission does not exist, so the only meaningful question is
     * whether notifications are switched on for the app at all.
     */
    override suspend fun requestPermission(): Boolean = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * The user's language where it can be read, English otherwise.
     *
     * Resource loading is wrapped because it is the one thing here that can fail for a
     * reason unrelated to the reminder: a missed reminder is recoverable, a crash inside
     * the scheduler is not.
     */
    private suspend fun ReminderKind.localisedCopy(): ReminderCopy {
        val resources = copyResources() ?: return copyOrNull() ?: ReminderCopy("", "")
        return runCatching { ReminderCopy(getString(resources.title), getString(resources.body)) }
            .getOrElse {
                logger.warn(TAG) { "falling back to English reminder copy" }
                copyOrNull() ?: ReminderCopy("", "")
            }
    }

    private companion object {
        const val TAG = "AndroidNotificationManager"
    }
}
