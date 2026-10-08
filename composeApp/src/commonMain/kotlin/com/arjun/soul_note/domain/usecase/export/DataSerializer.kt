package com.arjun.soul_note.domain.usecase.export

import com.arjun.soul_note.core.datetime.Iso8601
import com.arjun.soul_note.domain.model.ExportBundle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON and CSV rendering for a full account export.
 *
 * This is a *portable, self-describing* format rather than an internal-storage dump: it
 * uses the same field names the API documents, so an export stays meaningful after the
 * app is gone. CSV is RFC-4180 compliant, which is what makes a journal openable in any
 * spreadsheet on any platform.
 */
object DataSerializer {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun toJson(bundle: ExportBundle): String = buildJsonObject {
        put("exportedAt", Iso8601.formatTimestamp(bundle.exportedAt))
        put("schemaVersion", JsonPrimitive(SCHEMA_VERSION))
        put(
            "profile",
            buildJsonObject {
                put("id", bundle.profile.id)
                put("email", bundle.profile.email)
                put("displayName", bundle.profile.displayName )
                put("photoUrl", bundle.profile.photoUrl )
                put("createdAt", Iso8601.formatTimestamp(bundle.profile.createdAt))
                put("updatedAt", Iso8601.formatTimestamp(bundle.profile.updatedAt))
            },
        )
        put(
            "diaryEntries",
            buildJsonArray {
                bundle.diaryEntries.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("id", entry.id)
                            put("title", entry.title)
                            put("content", entry.content)
                            put("entryDate", Iso8601.formatDate(entry.entryDate))
                            put("mood", entry.mood.id)
                            put("emotions", JsonArray(entry.emotions.map { JsonPrimitive(it.id) }))
                            put("emotionNames", JsonArray(entry.emotions.map { JsonPrimitive(it.name) }))
                            put("tags", JsonArray(entry.tags.map { JsonPrimitive(it) }))
                            put("isFavorite", entry.isFavorite)
                            put("wordCount", entry.wordCount)
                            put("location", entry.location )
                            put("weather", entry.weather )
                            put("createdAt", Iso8601.formatTimestamp(entry.createdAt))
                            put("updatedAt", Iso8601.formatTimestamp(entry.updatedAt))
                        },
                    )
                }
            },
        )
        put(
            "moods",
            buildJsonArray {
                bundle.moodEntries.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("id", entry.id)
                            put("mood", entry.mood.id)
                            put("note", entry.note )
                            put("emotions", JsonArray(entry.emotions.map { JsonPrimitive(it.id) }))
                            put("emotionNames", JsonArray(entry.emotions.map { JsonPrimitive(it.name) }))
                            put("date", Iso8601.formatDate(entry.date))
                            put("createdAt", Iso8601.formatTimestamp(entry.createdAt))
                            put("updatedAt", Iso8601.formatTimestamp(entry.updatedAt))
                        },
                    )
                }
            },
        )
    }.let { json.encodeToString(JsonObject.serializer(), it) }

    /**
     * Three logical tables in one file, separated by a `# table: <name>` marker line so
     * both spreadsheets and `awk` can split it without a custom parser.
     */
    fun toCsv(bundle: ExportBundle): String = buildString {
        appendLine("# table: profile")
        appendLine("id,email,displayName,photoUrl,createdAt,updatedAt")
        appendCsvRow(
            listOf(
                bundle.profile.id,
                bundle.profile.email,
                bundle.profile.displayName.orEmpty(),
                bundle.profile.photoUrl.orEmpty(),
                Iso8601.formatTimestamp(bundle.profile.createdAt),
                Iso8601.formatTimestamp(bundle.profile.updatedAt),
            ),
        )

        appendLine()
        appendLine("# table: diaryEntries")
        appendLine(
            "id,title,content,entryDate,mood,emotions,tags,isFavorite,wordCount,location,weather,createdAt,updatedAt",
        )
        bundle.diaryEntries.forEach { entry ->
            appendCsvRow(
                listOf(
                    entry.id,
                    entry.title,
                    entry.content,
                    Iso8601.formatDate(entry.entryDate),
                    entry.mood.id,
                    entry.emotions.joinToString("|") { it.id },
                    entry.tags.joinToString("|"),
                    entry.isFavorite.toString(),
                    entry.wordCount.toString(),
                    entry.location.orEmpty(),
                    entry.weather.orEmpty(),
                    Iso8601.formatTimestamp(entry.createdAt),
                    Iso8601.formatTimestamp(entry.updatedAt),
                ),
            )
        }

        appendLine()
        appendLine("# table: moods")
        appendLine("id,mood,note,emotions,date,createdAt,updatedAt")
        bundle.moodEntries.forEach { entry ->
            appendCsvRow(
                listOf(
                    entry.id,
                    entry.mood.id,
                    entry.note.orEmpty(),
                    entry.emotions.joinToString("|") { it.id },
                    Iso8601.formatDate(entry.date),
                    Iso8601.formatTimestamp(entry.createdAt),
                    Iso8601.formatTimestamp(entry.updatedAt),
                ),
            )
        }
    }

    /** RFC-4180: quote when the value contains a comma, quote or newline; double the quotes. */
    internal fun String.toCsvField(): String =
        if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + replace("\"", "\"\"") + "\""
        } else {
            this
        }

    private fun StringBuilder.appendCsvRow(values: List<String>) {
        appendLine(values.joinToString(",") { it.toCsvField() })
    }

    private const val SCHEMA_VERSION = 1
}
