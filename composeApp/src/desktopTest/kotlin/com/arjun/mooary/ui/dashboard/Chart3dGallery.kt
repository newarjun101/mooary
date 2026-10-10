package com.arjun.mooary.ui.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType

/**
 * Renders the 3D charts to PNGs under `build/chart3d/`.
 *
 * A 3D chart is the one thing in this app that cannot be judged from a test result: the
 * geometry can be correct and the chart can still read as flat, or as a diagram of a 3D object
 * rather than as the mood of a person. That judgement needs an image, and this environment
 * cannot view one, so the shapes are rendered here with the real theme colours and written out
 * to be looked at instead.
 *
 * Not a UI test and not a snapshot test: nothing is compared against a stored image, because
 * a golden that nobody regenerates is worse than no golden. These files are an output, not an
 * assertion. The assertions live in [Chart3dTest].
 */
class Chart3dGallery {

    private val outDir = File("build/chart3d").apply { mkdirs() }

    @Test
    fun `renders the 3d charts for review`() {
        renderPie()
        renderBars()
        renderRibbon()
    }

    private fun save(name: String, size: IntSize, block: DrawScope.() -> Unit) {
        val bitmap = Bitmap()
        bitmap.allocPixels(
            ImageInfo(
                width = size.width,
                height = size.height,
                colorType = ColorType.N32,
                alphaType = ColorAlphaType.PREMUL,
            ),
        )
        val composeCanvas = Canvas(bitmap).asComposeCanvas()
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, composeCanvas, Size(size.width.toFloat(), size.height.toFloat())) {
            drawRect(Color(0xFFFAF6F0))
            block()
        }
        val file = File(outDir, "$name.png")
        val data = org.jetbrains.skia.Image.makeFromBitmap(bitmap)
            .encodeToData(EncodedImageFormat.PNG)

        file.writeBytes(data!!.bytes)
        assertTrue(file.length() > 0, "$name.png was empty")
        println("wrote ${file.absolutePath} (${file.length()} bytes)")
    }

    private val palette = listOf(
        Color(0xFF8A4B32),
        Color(0xFF4E6552),
        Color(0xFF78612A),
        Color(0xFF973528),
        Color(0xFF6B4A6E),
    )

    private fun renderPie() = save("pie", IntSize(360, 300)) {
        // The real geometry, through the same functions the composable calls. This gallery
        // used to carry its own `weight * 360f`, which happened to be the correct formula
        // while the composable divided by the largest slice instead -- so the PNG showed a
        // proper pie for a chart that actually overlapped itself, and no amount of looking at
        // the images could have found it. One function, both callers.
        val counts = listOf(10, 3, 1)
        val extents = shareOfCircle(counts)
        val centre = Offset(size.width / 2f, 140f)
        val outer = 112f
        val gap = 2f

        var angle = 0f
        extents.forEachIndexed { index, extent ->
            drawArc(
                color = palette[index],
                startAngle = angle + gap / 2f,
                sweepAngle = extent - gap,
                useCenter = true,
                topLeft = Offset(centre.x - outer, centre.y - outer),
                size = Size(outer * 2f, outer * 2f),
            )
            angle += extent
        }

        // And the selection, drawn the way a tap draws it: the chosen wedge moved out along
        // its own mid-angle with the rest of the pie stepped back. A chart you can tap is
        // only reviewable if the gallery shows the tapped state too.
        val chosen = 1
        var mid = 0f
        extents.forEachIndexed { index, extent ->
            if (index == chosen) {
                val radians = (mid + extent / 2f) * PI / 180.0
                val push = 7f
                drawArc(
                    color = palette[index],
                    startAngle = mid + gap / 2f,
                    sweepAngle = extent - gap,
                    useCenter = true,
                    topLeft = Offset(
                        centre.x - outer + (cos(radians) * push).toFloat(),
                        centre.y - outer + (sin(radians) * push).toFloat(),
                    ),
                    size = Size(outer * 2f, outer * 2f),
                )
            }
            mid += extent
        }
    }

    private fun renderBars() = save("bars", IntSize(360, 220)) {
        val floor = 186f
        val values = listOf(0.35f, 0.62f, 0.48f, 0.9f, 0.55f, 0.72f, 0.3f, 0.44f)
        val slot = size.width / values.size
        val barWidth = slot * 0.44f
        val depth = barWidth * 0.55f
        drawOval(
            color = Color(0xFF8A4B32).copy(alpha = 0.07f),
            topLeft = Offset(-10f, floor - 2f),
            size = Size(size.width + 20f, 7f),
        )
        values.forEachIndexed { index, value ->
            val left = slot * index + (slot - barWidth) / 2f
            contactShadow(
                centre = Offset(left + barWidth / 2f + depth * DepthSkew, floor + 1f),
                radiusX = barWidth / 2f + depth * DepthSkew,
                radiusY = 3f,
                alpha = 0.55f,
            )
            isometricBar(
                left = left,
                baseline = floor,
                width = barWidth,
                height = (size.height * value * 0.82f).coerceAtLeast(barWidth * 0.6f),
                depth = depth,
                color = if (index == values.lastIndex) Color(0xFF78612A) else Color(0xFF8A4B32),
            )
        }
    }

    private fun renderRibbon() = save("ribbon", IntSize(360, 220)) {
        val floor = size.height - 6f
        val line = Color(0xFF8A4B32)
        listOf(1.0, 3.0, 5.0).forEach { score ->
            val y = floor * (1f - (score / 5.0).toFloat())
            drawLine(Color(0xFFD7CAB4), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val values = listOf(0.45, 0.55, 0.4, 0.62, 0.5, 0.72, 0.66, 0.8, 0.7)
        val path = androidx.compose.ui.graphics.Path().apply {
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1)
                val y = floor * (1f - value.toFloat())
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        // Same four passes as MoodTrendChart. The middle one was a vertical wash here and flat
        // there, so the PNG being reviewed was not the chart that ships -- a replica that
        // drifted is the one thing this gallery cannot be for.
        drawPath(path, Color(0f, 0f, 0f, 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(path, shade(line, 0f, 0f, 1f).copy(alpha = 0.10f), style = androidx.compose.ui.graphics.drawscope.Fill)
        drawPath(path, shade(line, 0.2f, 0.4f, 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawPath(path, line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            drawCircle(line, radius = 2.5f, center = Offset(x, floor * (1f - value.toFloat())))
        }
    }
}
