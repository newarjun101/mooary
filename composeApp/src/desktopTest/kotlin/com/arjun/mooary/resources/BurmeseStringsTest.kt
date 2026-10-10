package com.arjun.mooary.resources

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps `values-my/strings.xml` in step with the default `values/strings.xml`.
 *
 * Compose resources fall back to the default file for any key a translation is missing, so a
 * forgotten key does not fail a build — it quietly ships English inside a Burmese screen. The
 * only reliable guard is to compare the two files, which is what this does. It also compares
 * the format specifiers, because a translation that drops `%1$d` renders the literal text
 * rather than the number.
 *
 * This lives in `desktopTest` rather than `commonTest` because it reads the XML straight out of
 * the source tree. Reading a localised value through `StringResource` instead would need the
 * resource system running for a given locale, which a unit test cannot arrange.
 */
class BurmeseStringsTest {

    @Test
    fun sourceFilesAreFound() {
        // Guards the rest of the file: if the path ever goes stale, every comparison below
        // would compare two empty maps and pass.
        assertTrue(defaultsFile().isFile, "cannot find the default strings.xml")
        assertTrue(burmeseFile().isFile, "cannot find values-my/strings.xml")
        assertTrue(parse(defaultsFile()).size > 100, "default strings.xml parsed as empty")
    }

    @Test
    fun burmeseCoversEveryDefaultKey() {
        val defaults = parse(defaultsFile())
        val burmese = parse(burmeseFile())

        assertEquals(
            emptyList<String>(),
            (burmese.keys - defaults.keys).toList(),
            "values-my has keys that values does not; they can never be read",
        )
        assertEquals(
            emptyList<String>(),
            (defaults.keys - burmese.keys).toList(),
            "values-my is missing keys; they would silently render in English",
        )
    }

    @Test
    fun burmeseKeepsTheSameFormatSpecifiers() {
        val defaults = parse(defaultsFile())
        val burmese = parse(burmeseFile())

        val mismatched = burmese.keys.filter { key ->
            specifiers(defaults.getValue(key)) != specifiers(burmese.getValue(key))
        }
        assertEquals(emptyList<String>(), mismatched, "format specifiers differ from the default")
    }

    @Test
    fun burmeseIsActuallyTranslated() {
        // Guards against a copy of the English file passing the two tests above.
        val defaults = parse(defaultsFile())
        val burmese = parse(burmeseFile())

        val stillEnglish = (burmese.keys - IDENTICAL_BY_DESIGN)
            .filter { defaults.getValue(it) == burmese.getValue(it) }
        assertEquals(
            emptyList<String>(),
            stillEnglish,
            "these are still the English text; only the emoji scales and pure " +
                "pass-throughs may match the default",
        )
    }

    @Test
    fun burmeseStringsAreNotEmpty() {
        val burmese = parse(burmeseFile())
        assertEquals(emptyList(), burmese.filterValues { it.isBlank() }.keys.toList(), "blank translation")
    }

    private fun defaultsFile() = locate("values/strings.xml")

    private fun burmeseFile() = locate("values-my/strings.xml")

    /**
     * Walks up from the working directory looking for the module, so the test does not depend
     * on Gradle's working directory or on a fixed depth.
     */
    private fun locate(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "composeApp/$RELATIVE_PREFIX/$relative")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return File("composeApp/$RELATIVE_PREFIX/$relative")
    }

    private fun parse(file: File): Map<String, String> =
        Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to unescape(it.groupValues[2].trim()) }

    /** The set of `%n$d`-style arguments, so argument order matters but surrounding prose does not. */
    private fun specifiers(value: String): List<String> =
        Regex("""%\d+\$[a-z]""").findAll(value).map { it.value }.sorted().toList()

    private fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
        .replace("\\'", "'")
}

/** The resource root, relative to the `composeApp` module directory. */
private const val RELATIVE_PREFIX = "src/commonMain/composeResources"

/** Strings that are correct to leave byte-identical to the English original. */
private val IDENTICAL_BY_DESIGN = setOf(
    "app_name",
    "mood_entry_line",
    "mood_note_prefix",
    "calendar_mood_line",
    "calendar_mood_note",
    "calendar_month_label",
    "chart_mood_share",
    "chart_emotion_share",
    "settings_language_en_short",
    "settings_language_mm_short",
    "developer_github",
    "developer_github_handle",
    "developer_github_url",
    "developer_link_label",
    "developer_linkedin",
    "developer_linkedin_handle",
    "developer_linkedin_url",
    "developer_phone_number",
    "developer_phone_url",
    "developer_email_address",
    "developer_email_url",
    "developer_version_value",
    "date_selector_label",
    // Reminder notifications are intentionally English in every locale by product request.
    "reminder_diary_title",
    "reminder_diary_body",
    "reminder_mood_title",
    "reminder_mood_body",
    "reminder_reflection_title",
    "reminder_reflection_body",
)
