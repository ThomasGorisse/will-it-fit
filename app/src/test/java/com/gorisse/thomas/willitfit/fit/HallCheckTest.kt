package com.gorisse.thomas.willitfit.fit

import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HallCheckTest {

    private val hall90 = Hall(90f, 90f, 240f)

    @Test
    fun `a thin pole around a square corner is the ladder problem`() {
        val t = turn(100f, 100f, 0f)
        assertEquals(200 * sqrt(2f), t.longestCm, 0.01f)
        assertEquals(45f, t.angleDeg, 0.1f)
    }

    @Test
    fun `a thin pole around an uneven corner`() {
        // (a^2/3 + b^2/3)^3/2, the longest ladder carried flat round the corner.
        val expected = (80.0.pow(2.0 / 3) + 120.0.pow(2.0 / 3)).pow(1.5).toFloat()
        assertEquals(expected, turn(80f, 120f, 0f).longestCm, 0.01f)
    }

    @Test
    fun `the wider the object, the shorter it must be`() {
        // 2·(90·√2 − 80): an object 80 wide around two 90 cm hallways.
        assertEquals(2 * (90 * sqrt(2f) - 80), turn(90f, 90f, 80f).longestCm, 0.01f)
    }

    @Test
    fun `too wide for one hallway, it never makes the turn`() {
        assertTrue(turn(90f, 70f, 80f).longestCm < 0f)
    }

    @Test
    fun `a washing machine makes the turn upright`() {
        val v = checkHall(Preset.WASHER.dimensions, Hall(78f, 78f, 240f))
        assertTrue(v.fits)
        assertEquals(Carry.UPRIGHT, v.carry)
        assertEquals(HallLimit.WIDTH, v.limit)
        assertEquals(18f, v.marginCm, 0.01f)
        assertFalse(v.isTight)
    }

    @Test
    fun `a two-seat sofa stood on its end makes a 100 cm turn`() {
        val v = checkHall(Preset.SOFA_2.dimensions, Hall(100f, 100f, 240f))
        assertTrue(v.fits)
        assertEquals(Carry.ON_ITS_SIDE, v.carry)
        assertEquals(15f, v.marginCm, 0.01f)
    }

    @Test
    fun `a three-seat sofa misses a 90 cm turn, even on its end`() {
        val v = checkHall(Preset.SOFA_3.dimensions, hall90)
        assertFalse(v.fits)
        assertEquals(Carry.ON_ITS_SIDE, v.carry)
        assertEquals(HallLimit.TURN, v.limit)
        assertEquals(2 * (90 * sqrt(2f) - 85) - 95, v.marginCm, 0.01f)
    }

    @Test
    fun `a wardrobe one centimetre taller than the ceiling is told so`() {
        val v = checkHall(Preset.WARDROBE.dimensions, Hall(90f, 90f, 200f))
        assertFalse(v.fits)
        assertEquals(Carry.UPRIGHT_SIDEWAYS, v.carry)
        assertEquals(HallLimit.CEILING, v.limit)
        assertEquals(-1f, v.marginCm, 0.01f)
    }

    @Test
    fun `the typical hallways are valid in both units`() {
        assertTrue(Hall.typicalFor(LengthUnit.CM).isValid)
        assertTrue(Hall.typicalFor(LengthUnit.INCH).isValid)
        assertFalse(Hall(0f, 90f, 240f).isValid)
    }
}
