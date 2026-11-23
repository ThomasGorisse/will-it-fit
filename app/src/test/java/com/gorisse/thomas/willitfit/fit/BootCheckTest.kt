package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCheckTest {
    @Test
    fun `a small box fits the example boot`() {
        val v = checkBoot(Dimensions(50f, 40f, 30f), Boot.ESTATE)
        assertTrue(v.fits)
        assertEquals(Carry.UPRIGHT, v.carry)
        assertEquals(38f, v.marginCm, 0.001f)
        assertEquals(BootLimit.HEIGHT, v.limit)
        assertFalse(v.isTight)
    }

    @Test
    fun `each deciding side is named on a miss`() {
        val roomy = Boot(100f, 100f, 100f, 100f, 100f)
        val cases = listOf(
            roomy.copy(openingWidthCm = 56f) to BootLimit.OPENING_WIDTH,
            roomy.copy(openingHeightCm = 56f) to BootLimit.OPENING_HEIGHT,
            roomy.copy(lengthCm = 56f) to BootLimit.LENGTH,
            roomy.copy(widthCm = 56f) to BootLimit.WIDTH,
            roomy.copy(heightCm = 56f) to BootLimit.HEIGHT,
        )
        for ((boot, limit) in cases) {
            val v = checkBoot(Dimensions(60f, 60f, 60f), boot)
            assertFalse(v.fits)
            assertEquals(limit, v.limit)
            assertEquals(-4f, v.marginCm, 0.001f)
        }
    }

    @Test
    fun `largest margin wins even when upright fits`() {
        val v = checkBoot(Dimensions(70f, 40f, 60f), Boot(100f, 100f, 100f, 100f, 80f))
        assertTrue(v.fits)
        assertEquals(Carry.ON_ITS_BACK, v.carry)
        assertEquals(30f, v.marginCm, 0.001f)
    }

    @Test
    fun `equal margins keep the declared order`() {
        val v = checkBoot(Dimensions(60f, 60f, 60f), Boot(100f, 100f, 100f, 100f, 100f))
        assertEquals(Carry.UPRIGHT, v.carry)
    }

    @Test
    fun `a narrower opening still stops a box that fits inside`() {
        val v = checkBoot(Dimensions(60f, 60f, 60f), Boot(58f, 80f, 103f, 104f, 68f))
        assertFalse(v.fits)
        assertEquals(BootLimit.OPENING_WIDTH, v.limit)
        assertEquals(-2f, v.marginCm, 0.001f)
    }

    @Test
    fun `exact fit and under two centimetres are tight`() {
        for (gap in listOf(0f, 1.9f, 2f)) {
            val v = checkBoot(Dimensions(60f, 60f, 60f), Boot(80f, 80f, 60f + gap, 80f, 80f))
            assertTrue(v.fits)
            assertEquals(gap < 2f, v.isTight)
        }
    }

    @Test
    fun `all six orientations can be the only fit`() {
        val dims = Dimensions(70f, 40f, 100f)
        for (carry in Carry.entries) {
            val boot = Boot(
                dims.side(carry.across), dims.side(carry.up), dims.side(carry.along),
                dims.side(carry.across), dims.side(carry.up),
            )
            val v = checkBoot(dims, boot)
            assertTrue(v.fits)
            assertEquals(carry, v.carry)
        }
    }

    @Test
    fun `invalid boot sides are rejected`() {
        assertTrue(Boot.ESTATE.isValid)
        for (bad in listOf(0f, -1f, 0.9f, 1001f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(Boot.ESTATE.copy(openingWidthCm = bad).isValid)
            assertFalse(Boot.ESTATE.copy(openingHeightCm = bad).isValid)
            assertFalse(Boot.ESTATE.copy(lengthCm = bad).isValid)
            assertFalse(Boot.ESTATE.copy(widthCm = bad).isValid)
            assertFalse(Boot.ESTATE.copy(heightCm = bad).isValid)
        }
    }
}
