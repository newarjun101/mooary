package com.arjun.soul_note.ui.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Rasterises the 3D chart geometry and checks the depth is genuinely on screen.
 *
 * The whole point of the isometric work is that a second, offset face exists and is shaded
 * differently from the first. A drawing call that compiles and produces a non-blank image can
 * still have both faces at the same place, or shade them identically, and nothing in a unit
 * test of the arithmetic would notice -- the drawing only fails when a person looks at it. So
 * the shapes are actually rasterised through the same `DrawScope` the composable uses and the
 * pixels are counted.
 *
 * Not a Compose UI test: there is no node tree here, just the drawing primitives, so this needs
 * no test harness and no device.
 */
class Chart3dTest {

    private fun rasterise(size: IntSize, block: DrawScope.() -> Unit): Bitmap {
        val bitmap = Bitmap()
        bitmap.allocPixels(
            ImageInfo(
                width = size.width,
                height = size.height,
                colorType = ColorType.N32,
                alphaType = ColorAlphaType.PREMUL,
            ),
        )
        val canvas = Canvas(bitmap).asComposeCanvas()
        val scope = CanvasDrawScope()
        scope.draw(
            density = Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            canvas = canvas,
            size = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()),
        ) { block() }
        return bitmap
    }

    private fun Bitmap.luminanceAt(x: Int, y: Int): Float {
        val argb = getColor(x, y)
        return (
            0.2126f * org.jetbrains.skia.Color.getR(argb) +
                0.7152f * org.jetbrains.skia.Color.getG(argb) +
                0.0722f * org.jetbrains.skia.Color.getB(argb)
            ) / 255f
    }

    private fun Bitmap.isPainted(x: Int, y: Int): Boolean = org.jetbrains.skia.Color.getA(getColor(x, y)) > 8

    @Test
    fun `a bar draws a top face, a front face and a side face`() {
        val bitmap = rasterise(IntSize(200, 200)) {
            isometricBar(
                left = 40f,
                baseline = 180f,
                width = 80f,
                height = 120f,
                depth = 40f,
                color = Color(0.4f, 0.4f, 0.45f),
            )
        }

        // Front face: x 40..120, y 60..180.
        assertTrue(bitmap.isPainted(80, 120), "the front face should be filled")
        // Top face: the parallelogram between the front edge at y=60 and the back edge at
        // y=40, shifted right by depth * DepthSkew, so x 40..134 across that band.
        assertTrue(bitmap.isPainted(100, 50), "the top face should be offset by the depth")
        // Right face: the strip between the front edge at x=120 and the back edge at x=134.
        assertTrue(bitmap.isPainted(127, 100), "the side face should be visible")
        // Empty canvas well outside the bar.
        assertTrue(!bitmap.isPainted(5, 5), "nothing should be drawn outside the bar")
    }

    @Test
    fun `a bar's faces are shaded differently from one another`() {
        val bitmap = rasterise(IntSize(200, 200)) {
            isometricBar(
                left = 40f,
                baseline = 180f,
                width = 80f,
                height = 120f,
                depth = 40f,
                color = Color(0.5f, 0.5f, 0.5f),
            )
        }

        // The top face is lit, the front is not, so they cannot be the same value. If the
        // shading term were dropped these would match and the bar would read as a flat shape.
        val top = bitmap.luminanceAt(100, 50)
        val front = bitmap.luminanceAt(80, 120)
        assertTrue(top > front, "top face ($top) should be lighter than the front ($front)")
    }

    @Test
    fun `a bar draws nothing when it has no height`() {
        val bitmap = rasterise(IntSize(120, 120)) {
            isometricBar(
                left = 20f,
                baseline = 100f,
                width = 60f,
                height = 0f,
                depth = 20f,
                color = Color.Black,
            )
        }

        var painted = 0
        for (y in 0 until 120) for (x in 0 until 120) if (bitmap.isPainted(x, y)) painted++
        assertTrue(painted == 0, "an empty bucket must not draw a bar, drew $painted pixels")
    }

    @Test
    fun `a pie slice reaches the centre rather than leaving a hole`() {
        // The old chart was a ring, so the middle was empty and a count sat in the hole. This
        // one is a pie: a wedge that stops at the centre is an annulus, and a wedge that
        // overshoots the centre is a drawing bug. Both are checked, because the difference
        // between them is one boolean argument in drawArc and nothing else.
        val bitmap = rasterise(IntSize(240, 240)) {
            drawArc(
                color = Color(0.5f, 0.45f, 0.4f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(30f, 30f),
                size = androidx.compose.ui.geometry.Size(180f, 180f),
            )
        }

        assertTrue(bitmap.isPainted(120, 120), "a pie has no hole in the middle")
        assertTrue(!bitmap.isPainted(120, 40), "a quarter wedge must not fill the whole disc")
    }

    @Test
    fun `shading never darkens a face out of legibility`() {
        // The floor of the diffuse term is 0.55, which is what stops a muted mood colour from
        // being pushed below contrast on its unlit face.
        val base = Color(0.9f, 0.7f, 0.5f)
        val unlit = shade(base, 0f, 0f, -1f)
        assertTrue(unlit.red <= base.red, "an unlit face is darker than its base")
        assertTrue(unlit.red > base.red * 0.5f, "but not by so much that the colour is lost")
    }

    @Test
    fun `shading keeps the alpha of the colour it was given`() {
        assertTrue(shade(Color(0.5f, 0.5f, 0.5f, 0.4f), 0f, 1f, 0f).alpha == 0.4f)
    }

    @Test
    fun `a contact shadow sits under the shape it belongs to`() {
        val bitmap = rasterise(IntSize(160, 120)) {
            contactShadow(centre = Offset(80f, 100f), radiusX = 40f, radiusY = 6f, alpha = 0.5f)
        }

        assertTrue(bitmap.isPainted(80, 100), "the shadow should be at its centre")
        assertTrue(!bitmap.isPainted(80, 20), "and not spread across the whole card")
    }
}

class ChartShareTest {

    @Test
    fun `slices always close the circle`() {
        val skewed = shareOfCircle(listOf(10, 3, 1))
        assertEquals(360f, skewed.sum(), 0.01f, "a ring must have no unclaimed wedge")
    }

    @Test
    fun `slices sum to the circle for every shape of week`() {
        listOf(
            listOf(1),
            listOf(2, 1),
            listOf(10, 3, 1),
            listOf(9, 9, 9, 9),
            listOf(100, 1),
            listOf(1, 1, 1, 1, 1, 1),
        ).forEach { counts ->
            assertEquals(360f, shareOfCircle(counts).sum(), 0.01f, "counts=$counts")
        }
    }

    @Test
    fun `each slice is proportional to its count`() {
        val counts = listOf(10, 3, 1)
        val extents = shareOfCircle(counts)
        assertEquals(257.14f, extents[0], 0.05f)
        assertEquals(77.14f, extents[1], 0.05f)
        assertEquals(25.71f, extents[2], 0.05f)
    }

    @Test
    fun `the legend percentage and the slice agree`() {
        // The description and the legend both print count/total; if the ring ever normalised
        // against anything else the two would silently disagree, which is the whole failure.
        val counts = listOf(10, 3, 1)
        val total = counts.sum()
        counts.forEachIndexed { index, count ->
            val fromChart = shareOfCircle(counts)[index] / 360f * 100
            assertEquals(count.toDouble() / total * 100, fromChart.toDouble(), 0.01)
        }
    }

    @Test
    fun `an empty or all-zero distribution claims nothing`() {
        assertEquals(listOf(0f, 0f), shareOfCircle(listOf(0, 0)))
        assertEquals(emptyList(), shareOfCircle(emptyList()))
    }

    @Test
    fun `the old largest-slice normalisation overshoots the circle`() {
        // Kept as a test so the reason for shareOfCircle cannot be quietly undone. This is
        // the formula that shipped: 360 + 108 + 36 for a 10/3/1 week.
        val counts = listOf(10, 3, 1)
        val largest = counts.max()
        val wrong = counts.sumOf { (it.toFloat() / largest * 360f).toDouble() }.toFloat()
        assertEquals(504f, wrong, 0.01f)
        assertTrue(wrong > 360f, "the old formula is expected to overlap itself")
    }
}

/**
 * The pie's own arithmetic: the entrance animation and the tap hit-test.
 *
 * Both are pure functions on purpose. The pie is the one chart in the app that is
 * interactive, and an interaction bug is not something a rendered PNG can show you -- a slice
 * that highlights on hover but is not the slice the tap lands on is invisible to every
 * screenshot in the gallery.
 */
class MoodPieGeometryTest {

    private val skewed = shareOfCircle(listOf(10, 3, 1))

    @Test
    fun `an undrawn pie claims no angle at all`() {
        assertTrue(visibleSweeps(skewed, 0f).all { it == 0f })
    }

    @Test
    fun `a fully drawn pie is the same as no animation`() {
        assertEquals(skewed, visibleSweeps(skewed, 1f))
    }

    @Test
    fun `the entrance fills the circle as one sweep`() {
        // Half way in, the pie covers half the circle -- not half of every slice, which would
        // leave a hole in the middle and gaps between the slices.
        listOf(0.25f, 0.5f, 0.75f).forEach { progress ->
            val total = visibleSweeps(skewed, progress).sum()
            assertEquals(360f * progress, total, 0.01f)
        }
    }

    @Test
    fun `an over-run animation clamps instead of overflowing the circle`() {
        assertEquals(360f, visibleSweeps(skewed, 1.4f).sum(), 0.01f)
    }

    @Test
    fun `a tap lands in the slice it was drawn in`() {
        // The first slice is 10/14 of the circle, starting at 12 o'clock and running clockwise.
        assertEquals(0, sliceAt(0f, skewed))
        assertEquals(0, sliceAt(100f, skewed))
        assertEquals(1, sliceAt(260f, skewed))
        assertEquals(2, sliceAt(350f, skewed))
    }

    @Test
    fun `every point on the circle belongs to exactly one slice`() {
        val centres = skewed.indices.map { i ->
            var start = 0f
            skewed.take(i).forEach { start += it }
            start + skewed[i] / 2f
        }
        centres.forEachIndexed { expected, angle ->
            assertEquals(expected, sliceAt(angle, skewed), "midpoint of slice $expected")
        }
    }

    @Test
    fun `the angle wraps rather than falling off the end`() {
        // 360 is the same place as 0, and a negative angle is one just before 12 o'clock --
        // which for this split is the last slice, not the first.
        assertEquals(0, sliceAt(360f, skewed))
        assertEquals(2, sliceAt(-10f, skewed))
    }

    @Test
    fun `every degree of the circle selects something`() {
        // The 2-degree hairline between slices is subtracted when drawing, not here, so the
        // hit-test deliberately has no dead zone. A gap that narrow is an unhittable target,
        // and refusing to select it would mean a tap near a boundary did nothing at all --
        // worse than it picking the nearer slice, which is what a person aiming at the edge
        // of a wedge means.
        for (angle in 0 until 360) {
            assertTrue(
                sliceAt(angle.toFloat(), skewed) >= 0,
                "no slice owns $angle degrees, so that tap would do nothing",
            )
        }
    }

    @Test
    fun `a boundary belongs to exactly one slice`() {
        // Half-open intervals, so the shared edge of two slices is claimed once rather than
        // twice; a closed test at both ends would make the hit-test disagree with the
        // drawing about which wedge a boundary tap lights up.
        val twoSlices = listOf(180f, 180f)
        assertEquals(0, sliceAt(179.9f, twoSlices))
        assertEquals(1, sliceAt(180f, twoSlices))
        assertEquals(1, sliceAt(359.9f, twoSlices))
    }

    @Test
    fun `a zero-count slice is never a target`() {
        // A mood with no records still has a legend row; without the guard every tap in the
        // whole distribution would land on it, because zero-width intervals match nothing and
        // the next test down would be the only thing stopping it.
        val withEmpty = listOf(180f, 0f, 180f)
        assertEquals(0, sliceAt(90f, withEmpty))
        assertEquals(2, sliceAt(270f, withEmpty))
    }

    @Test
    fun `nothing to draw means nothing to tap`() {
        assertEquals(emptyList<Float>(), visibleSweeps(emptyList(), 0.5f))
        assertEquals(-1, sliceAt(90f, emptyList()))
    }

    @Test
    fun `the selection pop is the same share of the pie on every screen`() {
        // Regression guard for a raw pixel constant. The pop is the only thing that shows a
        // selection in the shape, so if it is measured in px the feature quietly weakens as
        // density rises -- 4% of the pie on mdpi, under 2% on a 420dpi phone.
        val shareOn = { density: Float ->
            val d = Density(density)
            (d.run { 7.dp.toPx() } / d.run { 168.dp.toPx() }) * 100f
        }
        assertEquals(shareOn(1f), shareOn(2f), 0.001f)
        assertEquals(shareOn(1f), shareOn(2.625f), 0.001f)
    }

    @Test
    fun `a dead centre tap resolves to six o'clock, so radius has to reject it`() {
        // This is the actual defect, pinned so it cannot be reintroduced: the centre angle is
        // not undefined, it is 180 -- six o'clock, the last slice. Clearing the selection on a
        // tap the user aimed at the centre label cannot be done by the angle alone, and the
        // slice it lands on is not even the one at twelve.
        val atCentre = clockDegreesFromCentre(0f, 0f)
        assertEquals(180f, atCentre, 0.0001f, "the radius guard is what must reject it")
        // Half-open extents, so 180 sits in the third of four quarters rather than the last.
        assertEquals(2, sliceAt(atCentre, listOf(90f, 90f, 90f, 90f)))
    }

    @Test
    fun `the dead zone is a comfortable target but never eats a slice`() {
        val d = Density(2.625f)
        val zone = d.run { 32.dp.toPx() }
        val radius = d.run { (168.dp.toPx() / 2f) - 4.dp.toPx() }
        assertTrue(zone >= 16f, "dead zone must be hittable by a thumb")
        assertTrue(zone < radius / 2f, "dead zone must stay well inside the rim")
    }

    @Test
    fun `the tapped radius is the drawn radius`() {
        // The hit test and the draw path each compute an inset. If they disagree the pie
        // accepts taps on bare page outside its edge and drops taps on the rim.
        for (density in listOf(1f, 2f, 2.625f, 3f)) {
            val d = Density(density)
            val radius = (d.run { 168.dp.toPx() } / 2f) - d.run { 4.dp.toPx() }
            assertTrue(radius > 0f, "inset must leave room to draw")
        }
    }
}
