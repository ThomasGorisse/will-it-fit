package com.gorisse.thomas.willitfit.scene

import io.github.sceneview.math.Position
import io.github.sceneview.verticalFovDegreesForFocalLength
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

class DoorViewTest {

    // The visible area above the sheet on a Pixel 9 (about 1080 × 1500 px), and what is left once
    // the keyboard pushes the sheet up (about 1080 × 870 px).
    private val sheetOnly = 0.72f
    private val withKeyboard = 1.24f

    @Test
    fun `a bed carried on its side is seen whole, door included, with or without the keyboard`() {
        // Bed 160 (170 × 215 × 95 cm) on its side at a 78 × 200 cm door: 95 across, 170 up, 215 along.
        for (aspect in listOf(sheetOnly, withKeyboard)) {
            val view = doorView(dw = 0.78f, dh = 2f, across = 0.95f, up = 1.7f, along = 2.15f, aspect = aspect)
            assertWhole(view, aspect, halfW = 0.475f, top = 2.06f, front = 2.15f + START_GAP)
        }
    }

    @Test
    fun `a three-seat sofa on its back is seen whole, not from inside it`() {
        val along = 2.2f
        val view = doorView(dw = 0.78f, dh = 2f, across = 0.85f, up = 0.95f, along = along, aspect = sheetOnly)
        assertWhole(view, sheetOnly, halfW = 0.45f, top = 2.06f, front = along + START_GAP)
        // Behind the object's near end, and above the doorway.
        assertTrue("eye z ${view.eye.z}", view.eye.z > along + START_GAP)
        assertTrue("eye y ${view.eye.y}", view.eye.y > 2f)
    }

    @Test
    fun `a small object keeps the doorway close`() {
        val view = doorView(dw = 0.78f, dh = 2f, across = 0.6f, up = 0.85f, along = 0.6f, aspect = sheetOnly)
        val d = view.eye - view.target
        assertTrue("distance ${sqrt(d.x * d.x + d.y * d.y + d.z * d.z)}", sqrt(d.x * d.x + d.y * d.y + d.z * d.z) < 6f)
    }

    /** Every corner of the doorway-and-path box projects inside the visible area. */
    private fun assertWhole(view: DoorView, aspect: Float, halfW: Float, top: Float, front: Float) {
        val tanY = tan(Math.toRadians(verticalFovDegreesForFocalLength(DEFAULT_FOCAL_LENGTH_MM)) / 2.0).toFloat()
        val f = normalize(view.target - view.eye)
        val r = normalize(Position(-f.z, 0f, f.x))
        val u = Position(r.y * f.z - r.z * f.y, r.z * f.x - r.x * f.z, r.x * f.y - r.y * f.x)
        for (x in listOf(-halfW, halfW)) for (y in listOf(0f, top)) for (z in listOf(-0.075f, front)) {
            val p = Position(x, y, z) - view.eye
            val depth = dot(p, f)
            assertTrue("corner ($x, $y, $z) behind the camera", depth > 0f)
            val sx = dot(p, r) / depth / (tanY * aspect)
            val sy = dot(p, u) / depth / tanY
            assertTrue("corner ($x, $y, $z) at x $sx, aspect $aspect", abs(sx) <= 1.001f)
            assertTrue("corner ($x, $y, $z) at y $sy, aspect $aspect", abs(sy) <= 1.001f)
        }
    }

    private fun dot(a: Position, b: Position) = a.x * b.x + a.y * b.y + a.z * b.z
    private fun normalize(a: Position): Position {
        val n = sqrt(dot(a, a)); return Position(a.x / n, a.y / n, a.z / n)
    }
}
