package com.gorisse.thomas.willitfit.ui

import com.gorisse.thomas.willitfit.fit.Dimensions
import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Test

class NearCameraTest {

    // A washer: 60 wide, 85 high, 60 deep, bottom-centre on the origin.
    private val washer = Dimensions(60f, 60f, 85f)

    @Test
    fun `the gap is zero inside the box`() {
        assertEquals(0f, gapToBox(Position(0f, 0.4f, 0f), washer), 1e-4f)
        assertEquals(0f, gapToBox(Position(0.3f, 0.85f, -0.3f), washer), 1e-4f)
    }

    @Test
    fun `the gap is measured to the nearest face, edge or corner`() {
        // 10 cm in front of the door.
        assertEquals(0.10f, gapToBox(Position(0f, 0.4f, 0.40f), washer), 1e-4f)
        // 20 cm above the top, the height a phone looks down from.
        assertEquals(0.20f, gapToBox(Position(0.1f, 1.05f, 0f), washer), 1e-4f)
        // Past a top edge: 30 cm out and 40 cm up.
        assertEquals(0.5f, gapToBox(Position(0.6f, 1.25f, 0f), washer), 1e-4f)
    }
}
