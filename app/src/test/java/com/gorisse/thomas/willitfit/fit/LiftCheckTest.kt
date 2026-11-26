package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class LiftCheckTest {
    @Test fun washerFitsSmallLiftUpright() {
        val v = checkLift(Dimensions(60f, 60f, 85f), Lift.TYPICAL)
        assertTrue(v.fits)
        assertTrue(v.carry.isUpright)
        assertFalse(v.diagonal)
        assertEquals(20f, v.marginCm, 0.001f)
    }

    @Test fun longSofaNeedsEnoughHeightToStandOnEnd() {
        val sofa = Dimensions(220f, 95f, 85f)
        val low = Lift(100f, 230f, 100f, 125f, 210f)
        assertFalse(checkLift(sofa, low).fits)
        val high = checkLift(sofa, low.copy(cabinHeightCm = 230f))
        assertTrue(high.fits)
        assertEquals(Side.WIDTH, high.carry.up)
        assertFalse(checkLift(sofa, low.copy(doorHeightCm = 210f, cabinHeightCm = 230f)).fits)
    }

    @Test fun longThinObjectOnlyFitsDiagonally() {
        val obj = Dimensions(140f, 10f, 200f)
        val v = checkLift(obj, Lift(20f, 210f, 110f, 110f, 210f))
        assertTrue(v.fits)
        assertTrue(v.diagonal)
        assertEquals(Carry.UPRIGHT_SIDEWAYS, v.carry)
        assertEquals(45f, v.angleDeg, 0.01f)
        assertEquals(110f - 150f / kotlin.math.sqrt(2f), v.marginCm, 0.01f)
    }

    @Test fun diagonalBoundsMatchTheChosenAngle() {
        val (gap, angle) = liftFloor(12f, 145f, 100f, 125f)
        val a = angle * PI / 180
        assertTrue(gap >= 0f)
        assertTrue(12 * cos(a) + 145 * sin(a) <= 100.001)
        assertTrue(12 * sin(a) + 145 * cos(a) <= 125.001)
        assertTrue(liftFloor(30f, 160f, 100f, 125f).first < 0f)
    }

    @Test fun turnedFloorPlacementIsNotDiagonal() {
        val v = checkLift(Dimensions(90f, 120f, 180f), Lift(100f, 200f, 125f, 100f, 210f))
        assertTrue(v.fits)
        assertEquals(90f, v.angleDeg, 0f)
        assertFalse(v.diagonal)
    }

    @Test fun tallWardrobeCannotClearDoorOrCabin() {
        assertFalse(checkLift(Dimensions(150f, 100f, 250f), Lift.TYPICAL).fits)
    }

    @Test fun invalidDimensionsAreRejected() {
        for (bad in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 1001f)) {
            val t = Lift.TYPICAL
            assertFalse(t.copy(doorWidthCm = bad).isValid)
            assertFalse(t.copy(doorHeightCm = bad).isValid)
            assertFalse(t.copy(cabinWidthCm = bad).isValid)
            assertFalse(t.copy(cabinDepthCm = bad).isValid)
            assertFalse(t.copy(cabinHeightCm = bad).isValid)
        }
        assertThrows(IllegalArgumentException::class.java) { checkLift(Dimensions(0f, 1f, 1f), Lift.TYPICAL) }
    }

    @Test fun missesNameTheLimitingSpace() {
        val tall = Dimensions(60f, 60f, 220f)
        assertEquals(LiftLimit.CABIN_HEIGHT, checkLift(tall, Lift(80f, 240f, 100f, 125f, 210f)).limit)
        assertEquals(LiftLimit.DOOR_HEIGHT, checkLift(tall, Lift(80f, 210f, 100f, 125f, 240f)).limit)
        assertEquals(LiftLimit.DOOR_WIDTH, checkLift(Dimensions(60f, 60f, 85f), Lift.TYPICAL.copy(doorWidthCm = 50f)).limit)
        assertEquals(LiftLimit.CABIN_FLOOR, checkLift(Dimensions(90f, 150f, 200f), Lift.TYPICAL.copy(doorWidthCm = 160f, doorHeightCm = 210f)).limit)
    }

    @Test fun exactStraightClearanceCountsAsATightFit() {
        val v = checkLift(Dimensions(60f, 80f, 100f), Lift(60f, 100f, 80f, 60f, 100f))
        assertTrue(v.fits)
        assertTrue(v.isTight)
        assertEquals(90f, v.angleDeg, 0f)
    }

    @Test fun typicalSizesFollowTheUnit() {
        assertEquals(Lift.TYPICAL, Lift.typicalFor(LengthUnit.CM))
        assertEquals(36f, Lift.typicalFor(LengthUnit.INCH).doorWidthCm / 2.54f, 0.001f)
        assertTrue(Lift.TYPICAL_INCH.isValid)
    }
}
