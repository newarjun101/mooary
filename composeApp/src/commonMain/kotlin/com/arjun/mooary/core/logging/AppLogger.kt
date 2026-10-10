package com.arjun.mooary.core.logging

/**
 * Privacy-first logger.
 *
 * The application holds the most sensitive data imaginable (free-form personal journal
 * text and mood notes). The logger therefore has **no API capable of accepting user
 * content**: every entry point takes structured, developer-authored values only, and
 * [REDACTED] is what any record-derived string becomes.
 */
interface AppLogger {

    fun debug(tag: String, message: () -> String)
    fun info(tag: String, message: () -> String)
    fun warn(tag: String, throwable: Throwable? = null, message: () -> String)
    fun error(tag: String, throwable: Throwable? = null, message: () -> String)
}

/** The single value that may ever be logged in place of user-authored text. */
const val REDACTED: String = "<redacted>"

/** Severity threshold for [ConsoleLogger]. */
enum class LogSeverity { DEBUG, INFO, WARN, ERROR }

/** Replaces any user-authored string with [REDACTED]. */
fun Any?.redact(): String = REDACTED

/** Console logger used on JVM/Android, and on Native in debug builds. */
class ConsoleLogger(
    private val enabled: Boolean,
    private val minSeverity: LogSeverity = LogSeverity.DEBUG,
) : AppLogger {

    override fun debug(tag: String, message: () -> String) {
        if (enabled && minSeverity <= LogSeverity.DEBUG) println("D/$tag: ${message()}")
    }

    override fun info(tag: String, message: () -> String) {
        if (enabled && minSeverity <= LogSeverity.INFO) println("I/$tag: ${message()}")
    }

    override fun warn(tag: String, throwable: Throwable?, message: () -> String) {
        if (enabled && minSeverity <= LogSeverity.WARN) {
            println("W/$tag: ${message()}${describe(throwable)}")
        }
    }

    override fun error(tag: String, throwable: Throwable?, message: () -> String) {
        if (enabled && minSeverity <= LogSeverity.ERROR) {
            println("E/$tag: ${message()}${describe(throwable)}")
        }
    }

    /**
     * Only the exception *type* is printed, never its message: SDK exception messages
     * routinely embed the payload that failed, which for this app means diary text.
     */
    private fun describe(throwable: Throwable?): String =
        throwable?.let { " (${it::class.simpleName})" }.orEmpty()
}

/** Logger that drops everything. Used by release builds. */
object NoOpLogger : AppLogger {
    override fun debug(tag: String, message: () -> String) = Unit
    override fun info(tag: String, message: () -> String) = Unit
    override fun warn(tag: String, throwable: Throwable?, message: () -> String) = Unit
    override fun error(tag: String, throwable: Throwable?, message: () -> String) = Unit
}
