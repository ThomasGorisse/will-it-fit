package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBootCheckTest {
    private val corridor = Boot(110f, 70f, 120f, 110f, 70f)
    private val longBox = Dimensions(140f, 10f, 60f)

    @Test fun `a long narrow object fits diagonally through the opening and on the floor`() {
        val v = checkCarBoot(longBox, corridor)
        assertTrue(v.fits)
        assertTrue(v.diagonal)
        assertFalse(v.rearSeatsDown)
        assertInside(longBox, corridor, v)
    }

    @Test fun `a floor diagonal cannot bypass a narrow hatch`() {
        val v = checkCarBoot(longBox, corridor.copy(openingWidthCm = 40f))
        assertFalse(v.fits)
    }

    @Test fun `a floor diagonal cannot bypass opening or interior height`() {
        val cube = Dimensions(60f, 60f, 60f)
        assertFalse(checkCarBoot(cube, corridor.copy(openingHeightCm = 59f)).fits)
        assertFalse(checkCarBoot(cube, corridor.copy(heightCm = 59f)).fits)
    }

    @Test fun `straight placements win before diagonals and rear seats stay up`() {
        val v = checkCarBoot(Dimensions(50f, 40f, 30f), corridor.copy(seatsDownLengthCm = 180f))
        assertTrue(v.fits)
        assertFalse(v.diagonal)
        assertFalse(v.rearSeatsDown)
        assertEquals(checkBoot(Dimensions(50f, 40f, 30f), corridor), v)
    }

    @Test fun `a diagonal with seats up wins over folding seats`() {
        val v = checkCarBoot(longBox, corridor.copy(seatsDownLengthCm = 180f))
        assertTrue(v.fits)
        assertTrue(v.diagonal)
        assertFalse(v.rearSeatsDown)
    }

    @Test fun `folding seats is recommended only when it makes the object fit`() {
        val boot = BootPreset.ESTATE.boot
        val obj = Dimensions(160f, 40f, 50f)
        assertFalse(checkBoot(obj, boot).fits)
        val v = checkCarBoot(obj, boot)
        assertTrue(v.fits)
        assertTrue(v.rearSeatsDown)
        assertFalse(v.diagonal)
        assertEquals(183.6f, boot.forVerdict(v).lengthCm, 0.001f)
        assertInside(obj, boot.forVerdict(v), v)
        assertFalse(checkCarBoot(Dimensions(250f, 150f, 120f), boot).fits)
    }

    @Test fun `folded seats and diagonal can both be required`() {
        val obj = Dimensions(205f, 10f, 60f)
        val boot = corridor.copy(lengthCm = 100f, seatsDownLengthCm = 190f)
        val v = checkCarBoot(obj, boot)
        assertTrue(v.fits)
        assertTrue(v.rearSeatsDown)
        assertTrue(v.diagonal)
        assertInside(obj, boot.forVerdict(v), v)
    }

    @Test fun `no folded length means no folding recommendation`() {
        val boot = BootPreset.ESTATE.boot.copy(seatsDownLengthCm = null)
        assertFalse(checkCarBoot(Dimensions(160f, 40f, 50f), boot).fits)
        assertFalse(checkCarBoot(longBox, corridor.copy(seatsDownLengthCm = 100f)).rearSeatsDown)
    }

    @Test fun `invalid input never fits and never produces a nonfinite margin`() {
        for (bad in listOf(0f, -1f, 0.9f, 1001f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val boots = listOf(corridor.copy(openingWidthCm = bad), corridor.copy(openingHeightCm = bad),
                corridor.copy(lengthCm = bad), corridor.copy(widthCm = bad), corridor.copy(heightCm = bad),
                corridor.copy(seatsDownLengthCm = bad))
            for (boot in boots) {
                val v = checkCarBoot(longBox, boot)
                assertFalse(v.fits)
                assertTrue(v.marginCm.isFinite())
            }
            for (obj in listOf(longBox.copy(widthCm = bad), longBox.copy(depthCm = bad), longBox.copy(heightCm = bad))) {
                assertFalse(checkCarBoot(obj, corridor).fits)
            }
        }
    }

    @Test fun `all catalogue presets have valid sizes and folding only extends the floor`() {
        assertEquals(25, BootPreset.entries.size)
        for (preset in BootPreset.entries) {
            assertTrue(preset.boot.isValid)
            preset.boot.seatsDownLengthCm?.let { assertTrue(it > preset.boot.lengthCm) }
        }
        assertEquals(null, BootPreset.VAN.boot.seatsDownLengthCm)
    }

    @Test fun `every positive result in a size grid clears all five constraints`() {
        for (preset in BootPreset.entries) {
            for (w in listOf(10f, 60f, 105f, 140f, 190f, 250f)) {
                for (d in listOf(10f, 50f, 90f)) {
                    for (h in listOf(30f, 60f, 100f, 150f)) {
                        val obj = Dimensions(w, d, h)
                        val v = checkCarBoot(obj, preset.boot)
                        if (v.fits) assertInside(obj, preset.boot.forVerdict(v), v)
                    }
                }
            }
        }
    }

    private fun assertInside(obj: Dimensions, boot: Boot, v: BootVerdict) {
        // Independently project the box corners, rather than calling the production footprint.
        val angle = Math.toRadians(v.angleDeg.toDouble())
        val xs = mutableListOf<Double>()
        val zs = mutableListOf<Double>()
        for (x in listOf(-0.5, 0.5)) for (z in listOf(-0.5, 0.5)) {
            val across = obj.side(v.carry.across) * x
            val along = obj.side(v.carry.along) * z
            xs += across * kotlin.math.cos(angle) + along * kotlin.math.sin(angle)
            zs += -across * kotlin.math.sin(angle) + along * kotlin.math.cos(angle)
        }
        val width = xs.max() - xs.min()
        val length = zs.max() - zs.min()
        assertTrue(width <= boot.openingWidthCm + 0.0001)
        assertTrue(width <= boot.widthCm + 0.0001)
        assertTrue(length <= boot.lengthCm + 0.0001)
        assertTrue(obj.side(v.carry.up) <= boot.openingHeightCm)
        assertTrue(obj.side(v.carry.up) <= boot.heightCm)
        assertTrue(v.marginCm >= 0f)
    }
}
