package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCatalogueTest {
    @Test fun requestedVehiclesAndFiveGenericShapesRemainAvailable() {
        val generic = BootPreset.entries.filter { it.generic }.toSet()
        assertEquals(setOf(BootPreset.CITY, BootPreset.COMPACT, BootPreset.GENERIC_ESTATE, BootPreset.SUV, BootPreset.VAN), generic)
        assertEquals(20, BootPreset.entries.count { !it.generic })
        generic.forEach { assertEquals(BootMeasure.entries.toSet(), it.estimated) }
    }
    @Test fun namedProfilesAlwaysExposeAReferenceAndIdentifyUnpublishedDimensions() {
        BootPreset.entries.filterNot { it.generic }.forEach { preset ->
            assertTrue(preset.sourceUrl!!.startsWith("https://"))
            assertFalse("${preset.name} must not claim every field is measured", preset.estimated.isEmpty())
            if (preset.sourceUrl.contains("ridc.org.uk")) assertEquals(setOf(BootMeasure.HEIGHT), preset.estimated)
        }
    }
    @Test fun original308RidcMeasurementsAndEstimatedHeightArePreservedExactly() {
        assertEquals(Boot(107.4f, 72.9f, 105.5f, 104.3f, 68f, 183.6f), BootPreset.ESTATE.boot)
        assertEquals(setOf(BootMeasure.HEIGHT), BootPreset.ESTATE.estimated)
    }
    @Test fun pickupUsesTheSpecifiedShortBedBelowRailsWithNoFoldedSeatExtension() {
        val bed = BootPreset.F150.boot
        assertEquals(67.1f * LengthUnit.CM_PER_INCH, bed.lengthCm, 0.0001f)
        assertEquals(50.6f * LengthUnit.CM_PER_INCH, bed.widthCm, 0.0001f)
        assertEquals(21.4f * LengthUnit.CM_PER_INCH, bed.heightCm, 0.0001f)
        assertEquals(null, bed.seatsDownLengthCm)
        assertEquals(setOf(BootMeasure.OPENING_WIDTH, BootMeasure.OPENING_HEIGHT), BootPreset.F150.estimated)
        assertEquals(null, BootPreset.VAN_6M3.boot.seatsDownLengthCm)
    }
}
