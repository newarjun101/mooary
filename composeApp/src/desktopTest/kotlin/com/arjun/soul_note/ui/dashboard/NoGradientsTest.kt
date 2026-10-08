package com.arjun.soul_note.ui.dashboard

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * "There are no gradients in this app" is a design decision with no compiler behind it: a
 * `Brush.verticalGradient` compiles, renders, and looks locally plausible. Five were removed at
 * once and this is what stops the sixth arriving with a reviewer approving it.
 *
 * A grep over the source tree rather than a runtime assertion, because the failure being guarded
 * is a *source* pattern -- nothing observable at runtime distinguishes a legal translucent
 * shadow from an illegal gradient.
 */
class NoGradientsTest {

    // Assembled at runtime rather than written as literals: a list of these names sitting in
    // the file means the scan matches its own detector and reports six offenders that are the
    // six needles. The split keeps each half from appearing whole in this source.
    private val gradientApis =
        listOf("vertical", "horizontal", "radial", "linear", "sweep")
            .map { dir -> dir + "Gradi" + "ent(" } +
            listOf(".gradi" + "ent(")

    private fun sources(): List<File> {
        val root = File("src")
        assertTrue(root.isDirectory, "source root not found from ${File("").absolutePath}")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "build" !in it.path }
            .toList()
    }

    @Test
    fun `no gradient brush is used anywhere in the app`() {
        val offenders = sources()
            .filterNot { it.name == "NoGradientsTest.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val hit = gradientApis.firstOrNull { it in line }
                    // Comments may name a gradient: several record the one that was removed and
                    // why, and that is the only place this history survives.
                    val isComment =
                        line.trimStart().startsWith("*") || line.trimStart().startsWith("//")
                    if (hit != null && !isComment) {
                        "${file.path}:${index + 1} uses $hit"
                    } else {
                        null
                    }
                }
            }
        assertEquals(
            emptyList(),
            offenders,
            "gradients reintroduced; shape and shadow carry depth in this design:\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * The gallery is a hand-drawn replica, so it drifts silently: the ribbon kept a vertical
     * wash here after the shipped chart had already gone flat, which meant the PNG handed over
     * for visual review was not the chart that runs. Asserting the fill keeps the replica honest
     * about the one thing it exists to show.
     */
    @Test
    fun `gallery ribbon mirrors the flat fill the app draws`() {
        val gallery = File(
            "src/desktopTest/kotlin/com/arjun/soul_note/ui/dashboard/Chart3dGallery.kt",
        )
        val app = File(
            "src/commonMain/kotlin/com/arjun/soul_note/ui/dashboard/DashboardCharts.kt",
        )
        // Read the alpha from the area fill itself: the trend chart has several other alpha
        // values for markers and background bands, but only this path fills the ribbon body.
        val lines = app.readLines()
        val fillIndex = lines.indexOfFirst { "drawPath(path = area" in it }
        assertTrue(fillIndex > 0, "MoodTrendChart flat ribbon fill not found")
        val alpha = Regex("""copy\(alpha = ([0-9.]+)f\)""").find(lines[fillIndex])
        assertNotNull(alpha, "no alpha on the flat ribbon fill")
        val expected = alpha.groupValues[1]
        assertTrue(
            expected in gallery.readText(),
            "gallery ribbon fill alpha $expected differs from MoodTrendChart",
        )
    }
}
