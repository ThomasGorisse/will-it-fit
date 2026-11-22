package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
