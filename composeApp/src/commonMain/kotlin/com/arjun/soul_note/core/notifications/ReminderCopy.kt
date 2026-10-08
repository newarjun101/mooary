package com.arjun.soul_note.core.notifications

import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.reminder_diary_body
import com.arjun.soul_note.resources.reminder_diary_title
import com.arjun.soul_note.resources.reminder_mood_body
import com.arjun.soul_note.resources.reminder_mood_title
import com.arjun.soul_note.resources.reminder_reflection_body
import com.arjun.soul_note.resources.reminder_reflection_title
import org.jetbrains.compose.resources.StringResource

/**
 * The user-visible text a reminder carries.
 *
 * Deliberately resolved from resources rather than hardcoded, and shared by both native
 * schedulers: the alarm that fires and the notification the user reads are two ends of the
 * same message, and a reminder that arrives with an empty title is worse than one that
 * arrives a minute late.
 */
data class ReminderCopy(
    val title: String,
    val body: String,
)

/** The localised title and body for a reminder kind. */
data class ReminderCopyResources(
    val title: StringResource,
    val body: StringResource,
)

/**
 * Resources for a reminder kind, or null for a value this build does not know.
 *
 * The schedulers read these with `getString`, which works off the composition, so a
 * reminder scheduled from a background coroutine still gets the user's language.
 */
fun ReminderKind.copyResources(): ReminderCopyResources? = when (this) {
    ReminderKind.DAILY_DIARY -> ReminderCopyResources(
        title = Res.string.reminder_diary_title,
        body = Res.string.reminder_diary_body,
    )
    ReminderKind.DAILY_MOOD_CHECK_IN -> ReminderCopyResources(
        title = Res.string.reminder_mood_title,
        body = Res.string.reminder_mood_body,
    )
    ReminderKind.WEEKLY_REFLECTION -> ReminderCopyResources(
        title = Res.string.reminder_reflection_title,
        body = Res.string.reminder_reflection_body,
    )
}

/**
 * The English fallback, used where resources cannot be read — previews and unit tests.
 *
 * The schedulers themselves always go through [copyResources], so this copy is never what
 * a user sees; it exists so a missing resource degrades to English rather than to nothing.
 */
fun ReminderKind.copyOrNull(): ReminderCopy? = when (this) {
    ReminderKind.DAILY_DIARY -> ReminderCopy(
        title = "How was your day?",
        body = "Take a moment to write it down.",
    )
    ReminderKind.DAILY_MOOD_CHECK_IN -> ReminderCopy(
        title = "How are you feeling?",
        body = "A ten-second check-in is enough.",
    )
    ReminderKind.WEEKLY_REFLECTION -> ReminderCopy(
        title = "A week of writing",
        body = "Look back over the last seven days.",
    )
}
