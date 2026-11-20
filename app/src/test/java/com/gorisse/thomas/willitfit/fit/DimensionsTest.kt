package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DimensionsTest {

    @Test
    fun `parses centimetres with a comma, a point, spaces and a unit`() {
        assertEquals(220f, parseLength("220", LengthUnit.CM)!!, 0.001f)
        assertEquals(86.5f, parseLength("86,5", LengthUnit.CM)!!, 0.001f)
        assertEquals(86.5f, parseLength(" 86.5 cm ", LengthUnit.CM)!!, 0.001f)
        assertEquals(1200f, parseLength("1 200", LengthUnit.CM)!!, 0.001f)
    }

    @Test
    fun `parses inches into centimetres`() {
        assertEquals(254f, parseLength("100", LengthUnit.INCH)!!, 0.01f)
        assertEquals(87.63f, parseLength("34.5\"", LengthUnit.INCH)!!, 0.01f)
    }

    @Test
    fun `refuses what is not a positive length`() {
        listOf("", " ", "abc", "-3", "0", "1.2.3", "12a", ",").forEach {
            assertNull("\"$it\" should not parse", parseLength(it, LengthUnit.CM))
        }
    }

    @Test
    fun `formats whole centimetres and inches to one decimal`() {
        assertEquals("220 cm", formatLength(219.6f, LengthUnit.CM))
        assertEquals("86.6 in", formatLength(220f, LengthUnit.INCH))
        assertEquals("35 in", formatLength(88.9f, LengthUnit.INCH))
        assertEquals("220 × 95 × 85 cm", formatDimensions(Preset.SOFA_3.dimensions, LengthUnit.CM))
    }

    @Test
    fun `a typed value survives a round trip through the text field`() {
        for (unit in LengthUnit.entries) {
            val cm = parseLength("72", unit)!!
            assertEquals("72", formatNumber(cm, unit))
        }
    }

    @Test
    fun `a field takes only a length some object or door can have`() {
        assertEquals(220f, parseUsableLength("220", LengthUnit.CM))
        assertEquals(1f, parseUsableLength("1", LengthUnit.CM))
        assertEquals(1000f, parseUsableLength("1000", LengthUnit.CM))
        listOf("0.1", "0,5", "1001", "5000").forEach {
            assertNull("\"$it\" cm is out of range", parseUsableLength(it, LengthUnit.CM))
        }
        assertNull(parseUsableLength("400", LengthUnit.INCH)) // 1016 cm
    }

    @Test
    fun `a door margin under a centimetre keeps its tenth`() {
        assertEquals("0.4 cm", formatMargin(-0.4f, LengthUnit.CM))
        assertEquals("0.4 cm", formatMargin(0.4f, LengthUnit.CM))
        assertEquals("0 cm", formatMargin(0.04f, LengthUnit.CM))
        assertEquals("1 cm", formatMargin(0.96f, LengthUnit.CM))
        assertEquals("12 cm", formatMargin(-12.3f, LengthUnit.CM))
        assertEquals("0.2 in", formatMargin(0.4f, LengthUnit.INCH))
        // The 78.4 cm object at a 78 cm door: the margin no longer reads "0 cm".
        val verdict = checkDoor(Dimensions(78.4f, 80f, 210f), Door(78f, 200f))
        assertFalse(verdict.fits)
        assertEquals("0.4 cm", formatMargin(verdict.marginCm, LengthUnit.CM))
    }

    @Test
    fun `validity bounds`() {
        assertTrue(Dimensions(60f, 60f, 85f).isValid)
        assertFalse(Dimensions(0f, 60f, 85f).isValid)
        assertFalse(Dimensions(60f, 60f, 1001f).isValid)
        assertFalse(Dimensions(Float.NaN, 60f, 85f).isValid)
    }

    @Test
    fun `editing a preset keeps its silhouette and marks the size as typed`() {
        val edited = Preset.SOFA_3.dimensions.withWidth(240f)
        assertEquals(Shape.SOFA, edited.shape)
        assertEquals(DimensionSource.TYPED, edited.source)
        assertEquals(Shape.BOX, Dimensions(50f, 40f, 30f).shape)
    }

    @Test
    fun `default unit follows the country`() {
        assertEquals(LengthUnit.INCH, LengthUnit.defaultFor(Locale.US))
        assertEquals(LengthUnit.CM, LengthUnit.defaultFor(Locale.FRANCE))
        assertEquals(LengthUnit.CM, LengthUnit.defaultFor(Locale.UK))
    }

    @Test
    fun `every preset is valid and has a unique id`() {
        assertTrue(Preset.entries.all { it.dimensions.isValid && it.dimensions.presetId == it.id })
        assertEquals(Preset.entries.size, Preset.entries.map { it.id }.toSet().size)
    }

    @Test
    fun `a saved size equal to a preset becomes that preset again`() {
        val saved = Dimensions(60f, 90f, 105f, DimensionSource.TYPED, null)
        assertEquals("stroller", saved.withMatchingPreset().presetId)
        assertEquals(DimensionSource.PRESET, saved.withMatchingPreset().source)
        val own = Dimensions(61f, 90f, 105f, DimensionSource.TYPED, null)
        assertNull(own.withMatchingPreset().presetId)
        val edited = Dimensions(60f, 90f, 105f, DimensionSource.TYPED, "sofa3")
        assertEquals("sofa3", edited.withMatchingPreset().presetId)
    }

    @Test
    fun `typical sizes read in whole inches where people measure in inches`() {
        val sofa = Preset.SOFA_3.dimensionsIn(LengthUnit.INCH)
        assertEquals("87 × 37 × 33 in", formatDimensions(sofa, LengthUnit.INCH))
        assertEquals(DimensionSource.PRESET, sofa.source)
        assertEquals("sofa3", sofa.presetId)
        Preset.entries.forEach { preset ->
            val d = preset.dimensionsIn(LengthUnit.INCH)
            listOf(d.widthCm, d.depthCm, d.heightCm).forEach {
                assertFalse("${preset.id}: $it cm is not whole inches", formatNumber(it, LengthUnit.INCH).contains('.'))
            }
        }
        // Centimetres are the source and stay as they are.
        assertEquals(Preset.SOFA_3.dimensions, Preset.SOFA_3.dimensionsIn(LengthUnit.CM))
    }

    @Test
    fun `a saved size equal to a preset in whole inches becomes that preset again`() {
        val saved = Preset.STROLLER.dimensionsIn(LengthUnit.INCH).copy(source = DimensionSource.TYPED, presetId = null)
        assertEquals("stroller", saved.withMatchingPreset().presetId)
    }

    @Test
    fun `the typical door follows the unit`() {
        assertEquals(Door.TYPICAL, Door.typicalFor(LengthUnit.CM))
        val us = Door.typicalFor(LengthUnit.INCH)
        assertEquals("30", formatNumber(us.widthCm, LengthUnit.INCH))
        assertEquals("80", formatNumber(us.heightCm, LengthUnit.INCH))
        assertTrue(us.isValid)
    }
}
