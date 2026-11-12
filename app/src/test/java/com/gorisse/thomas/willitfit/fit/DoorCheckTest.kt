package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoorCheckTest {

    private val door = Door(widthCm = 78f, heightCm = 200f)

    @Test
    fun `a washing machine goes through upright, facing the door`() {
        val v = checkDoor(Dimensions(60f, 60f, 85f), door)
        assertTrue(v.fits)
        assertEquals(Carry.UPRIGHT, v.carry)
        assertEquals(18f, v.marginCm, 0.001f)
        assertFalse(v.isTight)
    }

    @Test
    fun `a wide object turns sideways before it is laid down`() {
        // 100 × 60 × 201 through 78 × 204: 100 is too wide, 60 deep passes, still standing.
        val v = checkDoor(Preset.WARDROBE.dimensions, Door(78f, 204f))
        assertTrue(v.fits)
        assertEquals(Carry.UPRIGHT_SIDEWAYS, v.carry)
        assertEquals(3f, v.marginCm, 0.001f)
    }

    @Test
    fun `a wardrobe one centimetre taller than the door goes through lying down`() {
        val v = checkDoor(Preset.WARDROBE.dimensions, door)
        assertTrue(v.fits)
        assertEquals(Carry.ON_ITS_SIDE_SIDEWAYS, v.carry)
        assertEquals(18f, v.marginCm, 0.001f)
    }

    @Test
    fun `too tall to stand, it passes on its back`() {
        // 210 cm tall, door 200: upright fails on height; on its back the 60 cm depth stands up.
        val v = checkDoor(Dimensions(widthCm = 70f, depthCm = 60f, heightCm = 210f), door)
        assertTrue(v.fits)
        assertFalse(v.carry.isUpright)
        assertTrue(v.marginCm >= 0f)
    }

    @Test
    fun `upright wins over a larger margin lying down`() {
        // Upright: 1 cm to spare. On its side: 38 cm. People carry it standing.
        val v = checkDoor(Dimensions(widthCm = 77f, depthCm = 40f, heightCm = 199f), door)
        assertEquals(Carry.UPRIGHT, v.carry)
        assertEquals(1f, v.marginCm, 0.001f)
        assertTrue(v.isTight)
    }

    @Test
    fun `an exact fit fits, and is tight`() {
        val v = checkDoor(Dimensions(78f, 50f, 200f), door)
        assertTrue(v.fits)
        assertEquals(0f, v.marginCm, 0.001f)
        assertTrue(v.isTight)
    }

    @Test
    fun `one millimetre too wide on every side does not fit`() {
        val v = checkDoor(Dimensions(78.1f, 78.1f, 200.1f), door)
        assertFalse(v.fits)
        assertEquals(-0.1f, v.marginCm, 0.01f)
    }

    @Test
    fun `a three-seat sofa does not go straight through a standard door`() {
        // 220 × 95 × 85: its two smallest sides, 85 and 95, both exceed 78.
        val v = checkDoor(Preset.SOFA_3.dimensions, door)
        assertFalse(v.fits)
        assertEquals(Carry.ON_ITS_BACK_SIDEWAYS, v.carry)
        assertEquals(-7f, v.marginCm, 0.001f)
    }

    @Test
    fun `a wider door lets the same sofa through on its back`() {
        val v = checkDoor(Preset.SOFA_3.dimensions, Door(90f, 204f))
        assertTrue(v.fits)
        assertEquals(Carry.ON_ITS_BACK_SIDEWAYS, v.carry)
        assertEquals(5f, v.marginCm, 0.001f)
    }

    @Test
    fun `whether it fits never depends on which side is called width`() {
        val sides = listOf(60f, 90f, 120f)
        val big = listOf(80f, 90f, 120f)
        for ((w, d, h) in permutations(sides)) assertTrue(checkDoor(Dimensions(w, d, h), door).fits)
        for ((w, d, h) in permutations(big)) assertFalse(checkDoor(Dimensions(w, d, h), door).fits)
    }

    private fun permutations(s: List<Float>) = listOf(
        Triple(s[0], s[1], s[2]), Triple(s[0], s[2], s[1]), Triple(s[1], s[0], s[2]),
        Triple(s[1], s[2], s[0]), Triple(s[2], s[0], s[1]), Triple(s[2], s[1], s[0]),
    )

    @Test
    fun `every carry uses the three sides once`() {
        Carry.entries.forEach { c ->
            assertEquals(setOf(Side.WIDTH, Side.DEPTH, Side.HEIGHT), setOf(c.across, c.up, c.along))
        }
        assertEquals(6, Carry.entries.map { Triple(it.across, it.up, it.along) }.toSet().size)
    }
}
