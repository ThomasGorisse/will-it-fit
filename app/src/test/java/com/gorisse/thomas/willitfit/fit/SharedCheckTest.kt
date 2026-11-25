package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedCheckTest {
    private val sofa = Preset.SOFA_3.dimensions

    @Test fun doorRoundTrips() {
        val check = SharedCheck(sofa, Space.DOOR, door = Door(80f, 204f))
        assertEquals("o=220x95x85&p=sofa3&door=80x204", check.query())
        val back = SharedCheck.parse(check.query())!!
        assertEquals(check.copy(dims = sofa.copy(source = DimensionSource.TYPED)), back)
        assertEquals(back.query(), check.query())
    }

    @Test fun linkIsOnTheSite() {
        assertEquals(
            "https://thomasgorisse.github.io/willitfit/check/#o=220x95x85&p=sofa3&door=80x204",
            SharedCheck(sofa, Space.DOOR, door = Door(80f, 204f)).link(),
        )
    }

    @Test fun bootAndHallRoundTrip() {
        val boot = SharedCheck(Dimensions(60f, 60f, 85f), Space.BOOT, boot = Boot.ESTATE)
        assertEquals("o=60x60x85&boot=108x80x103x104x68", boot.query())
        assertEquals(boot, SharedCheck.parse(boot.query()))
        val hall = SharedCheck(Dimensions(200f, 90f, 80f), Space.HALL, hall = Hall.TYPICAL)
        assertEquals(hall, SharedCheck.parse("#" + hall.query()))
    }

    @Test fun inchesTravelToTheTenth() {
        val check = SharedCheck(Preset.SOFA_3.dimensionsIn(LengthUnit.INCH), Space.HALL, hall = Hall.TYPICAL_INCH, unit = LengthUnit.INCH)
        assertEquals("o=221x94x83.8&p=sofa3&hall=91.4x91.4x243.8&u=in", check.query())
        val back = SharedCheck.parse(check.query())!!
        assertEquals("sofa3", back.dims.presetId)
        // The sizes sent, not the receiver's rounding of the preset.
        assertEquals(DimensionSource.TYPED, back.dims.source)
        assertEquals(221f, back.dims.widthCm)
        assertEquals(83.8f, back.dims.heightCm)
        assertEquals(LengthUnit.INCH, back.unit)
        assertEquals(36f, LengthUnit.INCH.fromCm(back.hall!!.widthCm), 0.05f)
    }

    @Test fun typedSizesKeepNoPresetName() {
        val back = SharedCheck.parse("o=200x90x80&p=sofa3&door=80x204")!!
        assertNull(back.dims.presetId)
        assertEquals(DimensionSource.TYPED, back.dims.source)
    }

    @Test fun readsPlayReferrerNextToUtm() {
        val back = SharedCheck.parse("utm_source=share&o=220x95x85&door=80x204")!!
        assertEquals(Space.DOOR, back.space)
        assertEquals(80f, back.door!!.widthCm)
    }

    @Test fun objectAloneStillOpens() {
        val back = SharedCheck.parse("o=220x95x85&door=80")!!
        assertNull(back.space)
        assertEquals(220f, back.dims.widthCm)
    }

    @Test fun refusesWhatIsNotACheck() {
        listOf(null, "", "utm_source=share", "o=220x95", "o=0x95x85", "o=220x95x9999", "o=-1x2x3",
            "o=1e3x2x3", "o=NaNx2x3", "o=%ZZ", "o=" + "1".repeat(600)).forEach {
            assertNull(it, SharedCheck.parse(it))
        }
    }
}
