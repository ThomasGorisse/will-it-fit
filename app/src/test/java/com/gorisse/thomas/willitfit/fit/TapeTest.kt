package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapeTest {
    @Test fun lengthIsTheStraightDistanceInCentimetres() {
        assertEquals(50f, tapeCm(TapePoint(0f, 0f, 0f), TapePoint(0.3f, 0f, 0.4f)), 1e-3f)
        assertEquals(200f, tapeCm(TapePoint(1f, 0f, -2f), TapePoint(1f, 2f, -2f)), 1e-3f)
    }

    @Test fun samePointOrAbsurdLengthIsNotUsable() {
        assertNull(usableTapeCm(0.4f))
        assertNull(usableTapeCm(1200f))
        assertNull(usableTapeCm(Float.NaN))
        assertEquals(182.4f, usableTapeCm(182.43f)!!, 1e-4f)
    }

    @Test fun aMeasureGoesToTheSideItNames() {
        val d = Dimensions(200f, 90f, 85f, DimensionSource.PRESET, "sofa3")
        val w = d.withTape(TapeTarget.WIDTH, 182f)
        assertEquals(182f, w.widthCm)
        assertEquals(DimensionSource.TYPED, w.source)
        assertEquals(77f, d.withTape(TapeTarget.HEIGHT, 77f).heightCm)
        assertEquals(d, d.withTape(TapeTarget.DOOR_WIDTH, 70f))
        val door = Door(78f, 200f)
        assertEquals(Door(81f, 200f), door.withTape(TapeTarget.DOOR_WIDTH, 81f))
        assertEquals(Door(78f, 204f), door.withTape(TapeTarget.DOOR_HEIGHT, 204f))
        assertEquals(door, door.withTape(TapeTarget.DEPTH, 10f))
    }

    @Test fun bootMeasurementsChangeOnlyTheirOwnField() {
        val boot = Boot(101f, 82f, 153f, 114f, 95f)
        val expected = mapOf(
            TapeTarget.BOOT_OPENING_WIDTH to boot.copy(openingWidthCm = 123.4f),
            TapeTarget.BOOT_OPENING_HEIGHT to boot.copy(openingHeightCm = 123.4f),
            TapeTarget.BOOT_LENGTH to boot.copy(lengthCm = 123.4f),
            TapeTarget.BOOT_WIDTH to boot.copy(widthCm = 123.4f),
            TapeTarget.BOOT_HEIGHT to boot.copy(heightCm = 123.4f),
        )
        TapeTarget.entries.forEach { target ->
            assertEquals(target.name, expected[target] ?: boot, boot.withTape(target, 123.4f))
        }
        assertEquals(expected.keys.toList(), TapeTarget.BOOT)
    }

    @Test fun hallwayMeasurementsChangeOnlyTheirOwnField() {
        val hall = Hall(101f, 112f, 243f)
        val expected = mapOf(
            TapeTarget.HALL_WIDTH to hall.copy(widthCm = 123.4f),
            TapeTarget.HALL_TURN_WIDTH to hall.copy(turnWidthCm = 123.4f),
            TapeTarget.HALL_CEILING to hall.copy(ceilingCm = 123.4f),
        )
        TapeTarget.entries.forEach { target ->
            assertEquals(target.name, expected[target] ?: hall, hall.withTape(target, 123.4f))
        }
        assertEquals(expected.keys.toList(), TapeTarget.HALL)
    }

    @Test fun liftMeasurementsChangeOnlyTheirOwnField() {
        val lift = Lift(81f, 202f, 113f, 144f, 235f)
        val expected = mapOf(
            TapeTarget.DOOR_WIDTH to lift.copy(doorWidthCm = 123.4f),
            TapeTarget.DOOR_HEIGHT to lift.copy(doorHeightCm = 123.4f),
            TapeTarget.CABIN_WIDTH to lift.copy(cabinWidthCm = 123.4f),
            TapeTarget.CABIN_DEPTH to lift.copy(cabinDepthCm = 123.4f),
            TapeTarget.CABIN_HEIGHT to lift.copy(cabinHeightCm = 123.4f),
        )
        TapeTarget.entries.forEach { target ->
            assertEquals(target.name, expected[target] ?: lift, lift.withTape(target, 123.4f))
        }
        assertEquals(expected.keys.toList(), TapeTarget.LIFT)
    }

    @Test fun invalidMeasurementsLeaveEverySpaceUntouched() {
        val door = Door.TYPICAL
        val boot = Boot.ESTATE
        val hall = Hall.typicalFor(LengthUnit.CM)
        val lift = Lift.typicalFor(LengthUnit.CM)
        listOf(0f, -1f, 0.9f, 1000.1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { cm ->
            TapeTarget.entries.forEach { target ->
                assertEquals(door, door.withTape(target, cm))
                assertEquals(boot, boot.withTape(target, cm))
                assertEquals(hall, hall.withTape(target, cm))
                assertEquals(lift, lift.withTape(target, cm))
            }
        }
    }

    @Test fun fieldRangeBoundariesAreAcceptedForEverySpaceTarget() {
        listOf(Dimensions.MIN_CM, Dimensions.MAX_CM).forEach { cm ->
            TapeTarget.DOOR.forEach { target ->
                assertEquals(cm, if (target == TapeTarget.DOOR_WIDTH) Door.TYPICAL.withTape(target, cm).widthCm
                    else Door.TYPICAL.withTape(target, cm).heightCm)
            }
            val boot = Boot.ESTATE
            val hall = Hall.typicalFor(LengthUnit.CM)
            val lift = Lift.typicalFor(LengthUnit.CM)
            TapeTarget.BOOT.forEach { target ->
                val measured = boot.withTape(target, cm)
                assertTrue(measured.isValid)
                assertNotEquals(boot, measured)
            }
            TapeTarget.HALL.forEach { target ->
                val measured = hall.withTape(target, cm)
                assertTrue(measured.isValid)
                assertNotEquals(hall, measured)
            }
            TapeTarget.LIFT.forEach { target ->
                val measured = lift.withTape(target, cm)
                assertTrue(measured.isValid)
                assertNotEquals(lift, measured)
            }
        }
    }

    @Test fun projectionLandsOnTheViewAndNotBehindIt() {
        // Identity view, a projection whose w is -z: a point 1 m straight ahead lands mid-screen.
        val m = FloatArray(16)
        m[0] = 1f; m[5] = 1f; m[10] = -1f; m[11] = -1f; m[14] = -0.1f
        val mid = projectToView(m, TapePoint(0f, 0f, -1f), 1000f, 2000f)!!
        assertEquals(500f, mid.first, 1e-3f)
        assertEquals(1000f, mid.second, 1e-3f)
        // Up and to the right on screen means smaller y.
        val ur = projectToView(m, TapePoint(0.5f, 0.5f, -1f), 1000f, 2000f)!!
        assertEquals(750f, ur.first, 1e-3f)
        assertEquals(500f, ur.second, 1e-3f)
        assertNull(projectToView(m, TapePoint(0f, 0f, 1f), 1000f, 2000f))
    }
}
