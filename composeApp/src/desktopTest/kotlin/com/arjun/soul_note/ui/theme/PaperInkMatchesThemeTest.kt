package com.arjun.soul_note.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps the iOS window background in step with the Kotlin theme.
 *
 * `Paper` and `Espresso` are duplicated as RGB literals in `iosApp/iosApp/iosApp.swift`, because
 * the safe-area bands around the page are painted by SwiftUI's scene root before any Compose code
 * runs and nothing in Kotlin can reach them. A duplicated token is only safe if something fails
 * when the two halves drift, so this fails when they do.
 *
 * The assertion is on the whole *set* of colours in the definition rather than on the light one
 * and then the dark one. The Swift side is a ternary, so "the dark branch" and "the light branch"
 * are positions rather than names, and a positional parse silently accepts a file that lost a
 * branch. Set equality makes a missing, added or altered colour all the same failure.
 */
class PaperInkMatchesThemeTest {

    private val swiftSource = readRepoFile("../iosApp/iosApp/iosApp.swift")

    @Test
    fun `the iOS window ink is exactly the Kotlin page colours`() {
        val definition = swiftSource.substringAfter("soulNotePage")
            .substringBefore("static let")
            .ifEmpty { error("no soulNotePage definition in iosApp.swift") }

        val triple = Regex(
            """red:\s*(\d+)\.0\s*/\s*255\.0,\s*green:\s*(\d+)\.0\s*/\s*255\.0,\s*blue:\s*(\d+)\.0\s*/\s*255\.0""",
        ).findAll(definition).map { match ->
            val (r, g, b) = match.destructured
            argb(r.toInt(), g.toInt(), b.toInt())
        }.toSet()

        assertEquals(setOf(argb(Paper), argb(Espresso)), triple)
    }

    private fun argb(color: Color): Int {
        fun channel(value: Float) = (value * 255f).toInt()
        return argb(channel(color.red), channel(color.green), channel(color.blue))
    }

    private fun argb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b

    private fun readRepoFile(relativePath: String): String {
        val file = File(relativePath)
        assertTrue(file.exists(), "cannot read $relativePath from ${File(".").absolutePath}")
        return file.readText()
    }
}