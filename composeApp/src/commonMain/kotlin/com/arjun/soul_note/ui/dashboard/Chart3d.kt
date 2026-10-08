package com.arjun.soul_note.ui.dashboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A very small isometric renderer for the dashboard charts.
 *
 * There is no 3D engine here and no charting library, on purpose: the project draws its charts
 * on a `Canvas` and every shape is a `Path`, and adding either a charting library or a 3D
 * library to soften a pie chart would be a large dependency for one screen. What the dashboard
 * actually needs is *depth* — the sense that a bar is a solid and a ring has thickness — and
 * that is a projection and a shading term, both of which are a few dozen lines.
 *
 * The projection is a fixed isometric rather than a real camera because a real one cannot show
 * a readable axis label without a text layout pass, and a chart whose numbers cannot be read is
 * decoration. Depth is therefore sold three ways, in ascending order of subtlety:
 *
 *  - an actual offset second face, so a bar has a visible side and a ring has a visible rim;
 *  - a shading term from a light direction, so the two faces are distinguishable as *faces*;
 *  - a contact shadow, which is what stops the shapes floating.
 *
 * Faces are painted in an explicit order per shape -- far wall, near wall, then the face that
 * caps them -- rather than sorted by depth. Sorting would be the general answer, but every
 * shape here is convex and its correct order is written out in three lines, so a sort would be
 * a second way to be wrong. If a chart is ever added here that is *not* convex, this is the
 * assumption to revisit first.
 */

/** Light direction in scene space. Up and slightly toward the viewer, so top faces are lit. */
private val LightX = -0.42f
private val LightY = 0.78f
private val LightZ = -0.46f

/** How far a unit of scene depth is offset on screen. Purely cosmetic but load-bearing. */
internal const val DepthSkew = 0.34f

/**
 * Shades [base] for a face whose normal in scene space is [nx], [ny], [nz].
 *
 * A single diffuse term against a fixed light, deliberately not a Phong highlight: a specular
 * lobe on a bar chart reads as glass, and glass is not what a paper journal is made of. The
 * floor of `0.55` means an unlit face is still legible, which matters because mood colours are
 * already muted a step off full chroma and a second darkening would push the small ones past
 * the contrast they need against the card.
 */
internal fun shade(base: Color, nx: Float, ny: Float, nz: Float): Color {
    val length = sqrt(nx * nx + ny * ny + nz * nz)
    if (length == 0f) return base
    val dot = (nx * LightX + ny * LightY + nz * LightZ) / length
    val term = 0.55f + 0.45f * dot.coerceIn(-1f, 1f)
    return Color(
        red = (base.red * term).coerceIn(0f, 1f),
        green = (base.green * term).coerceIn(0f, 1f),
        blue = (base.blue * term).coerceIn(0f, 1f),
        alpha = base.alpha,
    )
}

/** Draws a filled quad, skipping it entirely when it collapses to a line. */
private fun DrawScope.quad(
    a: Offset,
    b: Offset,
    c: Offset,
    d: Offset,
    color: Color,
) {
    val area = abs((b.x - a.x) * (d.y - a.y) - (b.y - a.y) * (d.x - a.x))
    if (area < 0.5f) return
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawPath(path, color)
}

/**
 * A vertical bar standing on [baseline], extruded back and to the right by [extrude].
 *
 * Three faces rather than six: the camera never sees the back, the left or the underside of a
 * bar that grows upward from a visible baseline, and drawing them would only cost overdraw.
 */
internal fun DrawScope.isometricBar(
    left: Float,
    baseline: Float,
    width: Float,
    height: Float,
    depth: Float,
    color: Color,
) {
    if (height <= 0.5f) return
    val top = baseline - height
    val right = left + width
    val backX = right + depth * DepthSkew
    val backY = top - depth * 0.5f

    // Right face first, then front, then top: the two side faces are only distinguishable by
    // their shading, so the order has to match the depth order or the shading fights it.
    quad(
        Offset(right, top),
        Offset(backX, backY),
        Offset(backX, backY + height),
        Offset(right, baseline),
        shade(color, 0.6f, 0f, 0.8f),
    )
    quad(
        Offset(left, top),
        Offset(right, top),
        Offset(right, baseline),
        Offset(left, baseline),
        shade(color, 0f, 0f, 1f),
    )
    quad(
        Offset(left, top),
        Offset(right, top),
        Offset(backX, backY),
        Offset(left + depth * DepthSkew, backY),
        shade(color, 0f, 1f, 0.2f),
    )
}

/** A soft contact shadow beneath an extruded shape, so it sits on the card. */
internal fun DrawScope.contactShadow(
    centre: Offset,
    radiusX: Float,
    radiusY: Float,
    alpha: Float,
) {
    drawOval(
        color = Color(red = 0f, green = 0f, blue = 0f, alpha = alpha),
        topLeft = Offset(centre.x - radiusX, centre.y - radiusY),
        size = androidx.compose.ui.geometry.Size(radiusX * 2, radiusY * 2),
    )
}

/**
 * Angular extent of each slice of a ring or pie, in degrees, summing to exactly 360.
 *
 * Divided by the *total*, never by the largest slice. Dividing by the largest sounds like a
 * reasonable way to make the biggest slice span the full circle, and for two categories it
 * is -- but a third slice then starts where the first one ended, wraps past 12 o'clock and
 * paints back over it. With a realistic skew like 10/3/1 the extents come to 360 + 108 + 36
 * and the ring quietly overlaps itself, while the legend beside it prints the true 71/21/7.
 * Nothing crashes and the image still looks like a chart, which is why it survived review.
 *
 * Takes counts rather than domain types so it stays pure geometry, and so the composable and
 * the offline gallery in `Chart3dGallery` are forced through the same arithmetic. They
 * previously each had their own copy, and the gallery's happened to be the correct one --
 * so the rendered PNGs looked right while the composable was wrong, and no image or test
 * could have caught it.
 *
 * Callers subtract their own gap from each returned extent for drawing, but advance by the
 * extent itself, so the slices still meet at 360 rather than leaving a wedge unclaimed.
 */
internal fun shareOfCircle(counts: List<Int>): List<Float> {
    val total = counts.sum()
    if (total <= 0) return counts.map { 0f }
    return counts.map { (it.toFloat() / total) * 360f }
}
