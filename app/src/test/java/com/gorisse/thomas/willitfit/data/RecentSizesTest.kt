package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.dimensionsIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentSizesTest {
    private val sofa = Dimensions(200f, 90f, 85f)
    private val wardrobe = Dimensions(110f, 65f, 210f)
    private val table = Dimensions(140f, 80f, 75f)

    @Test
    fun `an answered typed size goes first with no preset id`() {
        assertEquals(listOf(sofa), RecentSizes.add(emptyList(), sofa))
        val edited = sofa.copy(presetId = Preset.SOFA_3.id)
        assertEquals(listOf(sofa, wardrobe), RecentSizes.add(listOf(wardrobe), edited))
    }

    @Test
    fun `a duplicate within half a centimetre moves to the front`() {
        val rechecked = sofa.copy(widthCm = 200.5f, depthCm = 89.5f, heightCm = 85.5f)
        assertEquals(listOf(rechecked, wardrobe), RecentSizes.add(listOf(wardrobe, sofa), rechecked))
        assertFalse(RecentSizes.matches(sofa, sofa.copy(heightCm = 85.51f)))
        assertFalse(RecentSizes.matches(sofa, Dimensions(90f, 200f, 85f)))
    }

    @Test
    fun `only the newest three sizes stay`() {
        val fourth = Dimensions(155f, 75f, 76f)
        assertEquals(listOf(fourth, sofa, wardrobe), RecentSizes.add(listOf(sofa, wardrobe, table), fourth))
    }

    @Test
    fun `typical sizes in either unit are ignored even when typed`() {
        val history = listOf(sofa)
        Preset.entries.forEach { preset ->
            LengthUnit.entries.forEach { unit ->
                val dims = preset.dimensionsIn(unit)
                assertEquals(history, RecentSizes.add(history, dims))
                assertEquals(history, RecentSizes.add(history, dims.copy(source = DimensionSource.TYPED, presetId = null)))
                assertEquals(history, RecentSizes.add(history, dims.copy(source = DimensionSource.TYPED)))
            }
        }
    }

    @Test
    fun `invalid sizes are not added`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 1001f).forEach { width ->
            assertTrue(RecentSizes.add(emptyList(), sofa.copy(widthCm = width)).isEmpty())
        }
    }

    @Test
    fun `stored sizes round trip in order without rounding`() {
        val history = listOf(sofa.copy(widthCm = 200.25f), wardrobe, table)
        assertEquals(history, RecentSizes.decode(RecentSizes.encode(history)))
    }

    @Test
    fun `missing and garbage storage is ignored`() {
        listOf(null, "", "garbage", "1,2", "1,2,3,4", "NaN,90,85", "Infinity,90,85", "0,90,85", "1001,90,85").forEach {
            assertTrue(RecentSizes.decode(it).isEmpty())
        }
        val stored = "bad;200,90,85;NaN,90,85;110,65,210;200.5,90,85;140,80,75;155,75,76"
        assertEquals(listOf(sofa, wardrobe, table), RecentSizes.decode(stored))
    }

    @Test
    fun `a stored typical size is ignored`() {
        assertTrue(RecentSizes.decode(RecentSizes.encode(listOf(Preset.DEFAULT.dimensions))).isEmpty())
    }
}
