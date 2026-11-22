package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * One check per built-in object: the size the app shows, against the figure its source gives
 * (the notes in Presets.kt). A preset edited out of its market range fails here, by name.
 */
class PresetSourcesTest {

    /** A side and the range its source gives, in centimetres. */
    private class Side(val name: String, val cm: Float, val min: Float, val max: Float)

    private fun Preset.sides(w: ClosedFloatingPointRange<Float>, d: ClosedFloatingPointRange<Float>, h: ClosedFloatingPointRange<Float>) =
        listOf(
            Side("width", dimensions.widthCm, w.start, w.endInclusive),
            Side("depth", dimensions.depthCm, d.start, d.endInclusive),
            Side("height", dimensions.heightCm, h.start, h.endInclusive),
        )

    /** Width of a 16:9 panel of this diagonal, in centimetres. */
    private fun panelWidthCm(inches: Float) = inches * LengthUnit.CM_PER_INCH * 16f / sqrt(16f * 16f + 9f * 9f)
    private fun panelHeightCm(inches: Float) = inches * LengthUnit.CM_PER_INCH * 9f / sqrt(16f * 16f + 9f * 9f)

    private val sources: Map<Preset, List<Side>> = mapOf(
        // Two-seat sofas sell between 150 and 190 cm wide, 85-95 deep, 80-90 high.
        Preset.SOFA_2 to Preset.SOFA_2.sides(150f..190f, 85f..95f, 80f..90f),
        // Three-seat: 200-240 wide, same depth and height.
        Preset.SOFA_3 to Preset.SOFA_3.sides(200f..240f, 85f..95f, 80f..90f),
        // A frame is the mattress plus up to about 15 cm a side, headboard included in the length.
        Preset.BED_140 to Preset.BED_140.sides(140f..155f, 190f..215f, 80f..110f),
        Preset.BED_160 to Preset.BED_160.sides(160f..175f, 200f..225f, 80f..110f),
        // The most sold two-door modular frame: 100 × 58 × 201 cm, a few centimetres either way.
        Preset.WARDROBE to Preset.WARDROBE.sides(98f..102f, 56f..62f, 199f..203f),
        // The European 60 cm module, 185-200 high; depth 60-70 with the door.
        Preset.FRIDGE to Preset.FRIDGE.sides(59f..61f, 60f..70f, 185f..200f),
        // Under-counter washing machine: 60 × 60 × 85.
        Preset.WASHER to Preset.WASHER.sides(59f..61f, 55f..62f, 84f..86f),
        // The panel from its diagonal, plus up to 3 cm of bezel; the stand adds height and depth.
        Preset.TV_55 to Preset.TV_55.sides(
            panelWidthCm(55f)..panelWidthCm(55f) + 3f, 20f..30f, panelHeightCm(55f)..panelHeightCm(55f) + 12f,
        ),
        Preset.TV_65 to Preset.TV_65.sides(
            panelWidthCm(65f)..panelWidthCm(65f) + 3f, 22f..32f, panelHeightCm(65f)..panelHeightCm(65f) + 12f,
        ),
        // Adult city bike, drawn lengthwise: 175-185 long, about 60 at the handlebar, 100-110 high.
        Preset.BIKE to Preset.BIKE.sides(175f..185f, 55f..65f, 100f..110f),
        // Open pram: about 60 wide, 90 long, 105 high at the handle.
        Preset.STROLLER to Preset.STROLLER.sides(55f..65f, 85f..95f, 100f..110f),
    )

    @Test
    fun `every built-in object has a source`() {
        assertEquals(Preset.entries.toSet(), sources.keys)
    }

    @Test
    fun `every side the app shows lies in its source range`() {
        val outside = sources.flatMap { (preset, sides) ->
            sides.filterNot { it.cm in it.min..it.max }.map {
                "${preset.id} ${it.name} ${formatLength(it.cm, LengthUnit.CM)} not in ${it.min}..${it.max}"
            }
        }
        assertTrue(outside.joinToString("\n"), outside.isEmpty())
    }

    @Test
    fun `the sizes shown in inches stay within half an inch of the centimetres`() {
        Preset.entries.forEach { preset ->
            val cm = preset.dimensions
            val inch = preset.dimensionsIn(LengthUnit.INCH)
            listOf(cm.widthCm to inch.widthCm, cm.depthCm to inch.depthCm, cm.heightCm to inch.heightCm).forEach { (a, b) ->
                assertTrue("${preset.id}: $a cm shown as ${formatLength(b, LengthUnit.INCH)}", kotlin.math.abs(a - b) <= LengthUnit.CM_PER_INCH / 2f + 0.01f)
            }
        }
    }

    @Test
    fun `the TV panels match their diagonals`() {
        assertEquals(121.8f, panelWidthCm(55f), 0.1f)
        assertEquals(68.5f, panelHeightCm(55f), 0.1f)
        assertEquals(143.9f, panelWidthCm(65f), 0.1f)
        assertEquals(80.9f, panelHeightCm(65f), 0.1f)
    }
}
