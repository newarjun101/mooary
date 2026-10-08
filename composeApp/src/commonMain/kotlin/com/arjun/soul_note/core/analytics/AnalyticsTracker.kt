package com.arjun.soul_note.core.analytics

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The closed set of analytics events this product is allowed to emit.
 *
 * There is no `log(text: String)` escape hatch anywhere in the codebase: adding a new
 * event requires adding it here, which makes the privacy surface reviewable in one place.
 */
enum class AnalyticsEvent(val key: String) {
    APP_OPEN("app_open"),
    SCREEN_VIEW("screen_view"),
    LOGIN("login"),
    SIGNUP("signup"),
    LOGOUT("logout"),
    PASSWORD_RESET_REQUESTED("password_reset_requested"),
    EMAIL_VERIFICATION_REQUESTED("email_verification_requested"),
    DIARY_CREATED("diary_created"),
    DIARY_UPDATED("diary_updated"),
    DIARY_DELETED("diary_deleted"),
    DIARY_FAVORITED("diary_favorited"),
    MOOD_RECORDED("mood_recorded"),
    MOOD_UPDATED("mood_updated"),
    MOOD_DELETED("mood_deleted"),
    EXPORT_STARTED("export_started"),
    EXPORT_COMPLETED("export_completed"),
    ACCOUNT_DELETED("account_deleted"),
    SYNC_COMPLETED("sync_completed"),
    SYNC_FAILED("sync_failed");
}

/** The only parameter names an event may carry. Everything else is dropped. */
object AnalyticsParams {
    const val SCREEN_NAME = "screen_name"
    const val ENTRY_COUNT = "entry_count"
    const val HAS_QUERY = "has_query"
    const val FILTER_TYPE = "filter_type"
    const val RESULT = "result"
    const val FORMAT = "format"
    const val PENDING_COUNT = "pending_count"
    const val ERROR_TYPE = "error_type"
}

/** Structural guarantee: an event can never carry diary text, a title, or a mood note. */
data class AnalyticsEventCall(
    val event: AnalyticsEvent,
    val parameters: Map<String, String> = emptyMap(),
)

private val ALLOWED_PARAMETERS = setOf(
    AnalyticsParams.SCREEN_NAME,
    AnalyticsParams.ENTRY_COUNT,
    AnalyticsParams.HAS_QUERY,
    AnalyticsParams.FILTER_TYPE,
    AnalyticsParams.RESULT,
    AnalyticsParams.FORMAT,
    AnalyticsParams.PENDING_COUNT,
    AnalyticsParams.ERROR_TYPE,
)

/** Destination for telemetry. Kept abstract so no SDK type leaks into the app. */
interface AnalyticsSink {
    fun send(call: AnalyticsEventCall)
}

/** Analytics facade. Strips any parameter that is not explicitly allow-listed. */
class AnalyticsTracker(
    private val sink: AnalyticsSink,
) {
    private val _calls = MutableSharedFlow<AnalyticsEventCall>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Observable stream of sanitised events, for tests and the in-app privacy screen. */
    val calls: SharedFlow<AnalyticsEventCall> = _calls.asSharedFlow()

    fun track(event: AnalyticsEvent, parameters: Map<String, String> = emptyMap()) {
        val sanitised = parameters
            .filterKeys { it in ALLOWED_PARAMETERS }
            .filterValues { it.isNotBlank() }
            .mapValues { (_, value) -> value.take(40) }
        val call = AnalyticsEventCall(event, sanitised)
        _calls.tryEmit(call)
        sink.send(call)
    }
}

/** Local-only sink: keeps aggregate counters so telemetry is inspectable on-device. */
class InMemoryAnalyticsSink : AnalyticsSink {
    private val counts = LinkedHashMap<AnalyticsEvent, Int>()

    override fun send(call: AnalyticsEventCall) {
        counts[call.event] = (counts[call.event] ?: 0) + 1
    }

    fun snapshot(): Map<AnalyticsEvent, Int> = counts.toMap()

    fun total(): Int = counts.values.sum()
}
