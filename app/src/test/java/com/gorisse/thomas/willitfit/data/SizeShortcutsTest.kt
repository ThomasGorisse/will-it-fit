package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import org.junit.Assert.*
import org.junit.Test

class SizeShortcutsTest {
    private val size = Dimensions(200f, 90f, 85f)

    private fun entries(sizes: List<Dimensions>, unit: LengthUnit = LengthUnit.CM, max: Int = 5) =
        SizeShortcuts.entries(sizes, unit, max) { "Localized sofa" }

    @Test
    fun `labels use the chosen unit and compact only the short label`() {
        val cm = entries(listOf(size)).single()
        assertEquals("200×90×85", cm.shortLabel)
        assertEquals("200 × 90 × 85 cm", cm.longLabel)
        val inches = entries(listOf(Dimensions(203.2f, 91.44f, 86.36f)), LengthUnit.INCH).single()
        assertEquals("80×36×34", inches.shortLabel)
        assertEquals("80 × 36 × 34 in", inches.longLabel)
        assertEquals("78.7×35.4×33.5", entries(listOf(size), LengthUnit.INCH).single().shortLabel)
    }

    @Test
    fun `a preset keeps its thumbnail identity and localized name`() {
        val entry = entries(listOf(size.copy(presetId = Preset.SOFA_3.id))).single()
        assertEquals(Preset.SOFA_3, entry.preset)
        assertEquals("Localized sofa · 200 × 90 × 85 cm", entry.longLabel)
        assertEquals(Preset.SOFA_3.id, entry.extras[SizeShortcuts.PRESET])
        val unknown = entries(listOf(size.copy(presetId = "gone"))).single()
        assertNull(unknown.preset)
        assertFalse(unknown.extras.containsKey(SizeShortcuts.PRESET))
    }

    @Test
    fun `ids name the size, the list keeps recency, and both limits apply`() {
        val sizes = (0..4).map { size.copy(widthCm = 200f + it) }
        assertEquals(listOf("size_2000x900x850", "size_2010x900x850", "size_2020x900x850"), entries(sizes).map { it.id })
        // A newer size in front does not hand an older size's id to another object.
        val later = entries(listOf(size.copy(widthCm = 150f)) + sizes).map { it.id }
        assertEquals("size_2000x900x850", later[1])
        assertEquals(listOf(200.0, 201.0), entries(sizes, max = 2).map { it.extras[SizeShortcuts.WIDTH] })
        assertTrue(entries(sizes, max = 0).isEmpty())
        assertTrue(entries(sizes, max = -1).isEmpty())
        assertTrue(entries(emptyList()).isEmpty())
    }

    @Test
    fun `intent extras round trip exact centimetres even with an inch label`() {
        val original = size.copy(widthCm = 200.25f, presetId = Preset.SOFA_3.id)
        val entry = entries(listOf(original), LengthUnit.INCH).single()
        assertEquals(original, SizeShortcuts.dimensions(SizeShortcuts.ACTION, entry.extras::get))
        assertEquals(entry.id, SizeShortcuts.usedId(entry.extras::get))
        assertNull(SizeShortcuts.dimensions("other", entry.extras::get))
        assertNull(SizeShortcuts.dimensions(null, entry.extras::get))
    }

    @Test
    fun `invalid sizes do not consume a shortcut slot`() {
        val invalid = listOf(0f, -1f, 1001f, Float.NaN, Float.POSITIVE_INFINITY)
            .map { size.copy(depthCm = it) }
        assertEquals(listOf("size_2000x900x850"), entries(invalid + size).map { it.id })
        invalid.forEach { dims ->
            val extras = mapOf(SizeShortcuts.WIDTH to dims.widthCm,
                SizeShortcuts.DEPTH to dims.depthCm, SizeShortcuts.HEIGHT to dims.heightCm)
            assertNull(SizeShortcuts.dimensions(SizeShortcuts.ACTION, extras::get))
        }
    }

    @Test
    fun `missing or wrongly typed extras cannot open a check`() {
        val extras = entries(listOf(size)).single().extras
        listOf(SizeShortcuts.WIDTH, SizeShortcuts.DEPTH, SizeShortcuts.HEIGHT).forEach { key ->
            assertNull(SizeShortcuts.dimensions(SizeShortcuts.ACTION, (extras - key)::get))
            assertNull(SizeShortcuts.dimensions(SizeShortcuts.ACTION, (extras + (key to "200"))::get))
        }
        assertEquals(size, SizeShortcuts.dimensions(SizeShortcuts.ACTION, (extras + (SizeShortcuts.PRESET to "gone"))::get))
        assertNull(SizeShortcuts.usedId { "recent_3" })
        assertEquals("size_2000x900x850", SizeShortcuts.usedId { "size_2000x900x850" })
        assertNull(SizeShortcuts.usedId { null })
    }

    @Test
    fun `extras are types a PersistableBundle accepts`() {
        // A Float here made the system throw while building the shortcut, closing the app on start.
        val entry = entries(listOf(size.copy(presetId = Preset.SOFA_3.id))).single()
        assertTrue(entry.extras.values.all { it is String || it is Double })
    }
}
