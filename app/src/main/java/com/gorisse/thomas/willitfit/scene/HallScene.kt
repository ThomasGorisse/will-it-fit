package com.gorisse.thomas.willitfit.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Hall
import com.gorisse.thomas.willitfit.fit.HallLimit
import com.gorisse.thomas.willitfit.fit.HallVerdict
import com.gorisse.thomas.willitfit.fit.side
import com.gorisse.thomas.willitfit.fit.turn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The turn seen from above, in centimetres, y down: the first hallway comes up from the bottom
 * between x = 0 and x = width, the second one leaves to the right between y = 0 and
 * y = turn width. Their outer walls are x = 0 and y = 0, the inner corner is ([innerCorner]).
 * [footprint] is the object, four corners, at the tightest point of the turn.
 */
data class HallPlan(val hall: Hall, val footprint: List<Offset>) {
    val innerCorner: Offset get() = Offset(hall.widthCm, hall.turnWidthCm)

    private val gap: Float get() = max(hall.widthCm, hall.turnWidthCm) * 0.25f

    /** Where the first hallway's width is written: across it, past the object. */
    val widthLineY: Float get() = max(footprint.maxOf { it.y }, hall.turnWidthCm) + gap

    /** Where the second hallway's width is written: across it, past the object. */
    val turnLineX: Float get() = max(footprint.maxOf { it.x }, hall.widthCm) + gap

    /** What the drawing frames: the corner, some of both hallways, the object and both widths. */
    val bounds: Rect
        get() {
            var r = Rect(-WALL_CM, -WALL_CM, turnLineX + 2.2f * gap, widthLineY + gap)
            for (p in footprint) {
                r = Rect(minOf(r.left, p.x), minOf(r.top, p.y), max(r.right, p.x), max(r.bottom, p.y))
            }
            return r
        }

    companion object {
        const val WALL_CM = 12f
    }
}

/**
 * Where the object stands at the tightest point: its outer side against both outer walls, its
 * inner side on the inner corner, centred on the length the turn leaves. Too wide for a hallway,
 * it waits straight in the first one, against the corner.
 */
fun hallPlan(dims: Dimensions, hall: Hall, verdict: HallVerdict): HallPlan {
    val across = dims.side(verdict.carry.across)
    val along = dims.side(verdict.carry.along)
    if (verdict.limit == HallLimit.WIDTH && !verdict.fits || across > minOf(hall.widthCm, hall.turnWidthCm)) {
        val left = (hall.widthCm - across) / 2
        val top = hall.turnWidthCm + 6f
        return HallPlan(
            hall,
            listOf(Offset(left, top), Offset(left + across, top), Offset(left + across, top + along), Offset(left, top + along)),
        )
    }
    // The derivation runs y up, outer walls x = 0 and y = b, inner corner (a, 0): flipped below.
    val a = hall.widthCm
    val b = hall.turnWidthCm
    val t = verdict.angleDeg * PI.toFloat() / 180f
    val s = sin(t)
    val c = cos(t)
    val room = turn(a, b, across).longestCm
    val c0 = a * s - across
    val start = Offset(0f, -c0 / c) // on the outer wall x = 0
    val u = Offset(c, s) // along the object
    val n = Offset(s, -c) // towards the inner corner
    val p0 = start + u * ((room - along) / 2)
    val p1 = p0 + u * along
    val math = listOf(p0, p1, p1 + n * across, p0 + n * across)
    return HallPlan(hall, math.map { Offset(it.x, b - it.y) })
}

/** The turn from above, the object at its tightest point, framed inside [contentPadding]. */
@Composable
fun HallScene(
    dims: Dimensions,
    hall: Hall,
    verdict: HallVerdict,
    labels: HallLabels,
    accent: Color,
    palette: RoomPalette,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val plan = hallPlan(dims, hall, verdict)
    Canvas(modifier) {
        val area = Rect(
            contentPadding.calculateLeftPadding(direction).toPx(),
            contentPadding.calculateTopPadding().toPx(),
            size.width - contentPadding.calculateRightPadding(direction).toPx(),
            size.height - contentPadding.calculateBottomPadding().toPx(),
        )
        drawHall(plan, verdict, labels, accent, palette, measurer, area, with(density) { 1.dp.toPx() })
    }
}

/** The two hallway widths, written in the person's unit. */
data class HallLabels(val width: String, val turnWidth: String)

/** The same drawing as [HallScene], into a picture to share. */
fun hallPicture(
    dims: Dimensions,
    hall: Hall,
    verdict: HallVerdict,
    labels: HallLabels,
    accent: Color,
    palette: RoomPalette,
    measurer: TextMeasurer,
    density: Density,
    width: Int = 1080,
    height: Int = 1350,
): ImageBitmap {
    val image = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    // Drawn at the scale of a 360 dp wide phone, whatever the phone's own density.
    val pictureDensity = Density(width / 360f, density.fontScale)
    CanvasDrawScope().draw(pictureDensity, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), size) {
        val pad = 24.dp.toPx()
        // The caption card covers the bottom: 45 hundredths of the width with three lines, see
        // savePhoto. The plan stays above it.
        val caption = size.width * 0.48f
        drawHall(
            hallPlan(dims, hall, verdict), verdict, labels, accent, palette, measurer,
            Rect(pad, pad, size.width - pad, size.height - caption), 1.dp.toPx(),
        )
    }
    return image
}

private fun DrawScope.drawHall(
    plan: HallPlan,
    verdict: HallVerdict,
    labels: HallLabels,
    accent: Color,
    palette: RoomPalette,
    measurer: TextMeasurer,
    area: Rect,
    dp: Float,
) {
    val hall = plan.hall
    val wallMass = if (palette.isNight) Color(0xFF3A2F27) else Color(0xFFE4D6C3)
    val wallLine = if (palette.isNight) Color(0xFF221B16) else Color(0xFF8A7866)
    val ink = if (palette.isNight) Color(0xFFF3E9DD) else Color(0xFF3B3129)
    val miss = Color(0xFFD9534F)
    drawRect(wallMass)

    val bounds = plan.bounds
    val scale = minOf(area.width / bounds.width, area.height / bounds.height)
    // Centred in the area; the hallways run on to the edges of the canvas.
    val origin = Offset(
        area.left + (area.width - bounds.width * scale) / 2 - bounds.left * scale,
        area.top + (area.height - bounds.height * scale) / 2 - bounds.top * scale,
    )
    fun at(p: Offset) = origin + p * scale
    val far = max(size.width, size.height) / scale + bounds.width + bounds.height

    // Floor of both hallways.
    val a = hall.widthCm
    val b = hall.turnWidthCm
    val floor = Path().apply {
        moveTo(at(Offset(0f, far)).x, at(Offset(0f, far)).y)
        lineTo(at(Offset(0f, 0f)).x, at(Offset(0f, 0f)).y)
        lineTo(at(Offset(far, 0f)).x, at(Offset(far, 0f)).y)
        lineTo(at(Offset(far, b)).x, at(Offset(far, b)).y)
        lineTo(at(Offset(a, b)).x, at(Offset(a, b)).y)
        lineTo(at(Offset(a, far)).x, at(Offset(a, far)).y)
        close()
    }
    drawPath(floor, palette.floor)
    // Floorboards, along each hallway, so the floor reads as a floor.
    val board = if (palette.isNight) Color(0x22000000) else Color(0x1A000000)
    var x = 18f
    while (x < a) {
        drawLine(board, at(Offset(x, b)), at(Offset(x, far)), 1 * dp)
        x += 18f
    }
    var y = 18f
    while (y < b) {
        drawLine(board, at(Offset(0f, y)), at(Offset(far, y)), 1 * dp)
        y += 18f
    }
    drawPath(floor, wallLine, style = Stroke(width = 3 * dp))

    // The way it goes: up the first hallway, round the corner, along the second.
    val way = Path().apply {
        val start = at(Offset(a / 2, plan.widthLineY + a))
        val bend = at(Offset(a / 2, b / 2))
        val end = at(Offset(plan.turnLineX - max(a, b) * 0.1f, b / 2))
        moveTo(start.x, start.y)
        lineTo(at(Offset(a / 2, b)).x, at(Offset(a / 2, b)).y)
        quadraticTo(bend.x, bend.y, at(Offset(a, b / 2)).x, at(Offset(a, b / 2)).y)
        lineTo(end.x, end.y)
    }
    val wayInk = ink.copy(alpha = 0.35f)
    drawPath(way, wayInk, style = Stroke(width = 3 * dp, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10 * dp, 8 * dp))))
    val tip = at(Offset(plan.turnLineX - max(a, b) * 0.1f, b / 2))
    val head = 9 * dp
    drawPath(
        Path().apply {
            moveTo(tip.x + head, tip.y)
            lineTo(tip.x - head * 0.6f, tip.y - head * 0.8f)
            lineTo(tip.x - head * 0.6f, tip.y + head * 0.8f)
            close()
        },
        wayInk,
    )

    // The object, at the tightest point.
    val shape = Path().apply {
        plan.footprint.forEachIndexed { i, p -> if (i == 0) moveTo(at(p).x, at(p).y) else lineTo(at(p).x, at(p).y) }
        close()
    }
    val fill = if (verdict.fits) accent else miss
    drawPath(shape, fill.copy(alpha = 0.88f))
    drawPath(shape, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2 * dp))

    // The two widths, across each hallway, away from the object.
    val dash = PathEffect.dashPathEffect(floatArrayOf(6 * dp, 4 * dp))
    fun dimension(from: Offset, to: Offset, text: String, vertical: Boolean) {
        drawLine(ink.copy(alpha = 0.7f), at(from), at(to), 1.5f * dp, StrokeCap.Round, dash)
        val layout = measurer.measure(text, TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        val mid = (at(from) + at(to)) / 2f
        val offset = if (vertical) Offset(6 * dp, -layout.size.height / 2f) else Offset(-layout.size.width / 2f, -layout.size.height - 4 * dp)
        drawText(layout, topLeft = mid + offset)
    }
    dimension(Offset(0f, plan.widthLineY), Offset(a, plan.widthLineY), labels.width, vertical = false)
    dimension(Offset(plan.turnLineX, 0f), Offset(plan.turnLineX, b), labels.turnWidth, vertical = true)
}
