package com.gorisse.thomas.willitfit.scene

import org.junit.Assert.assertTrue
import org.junit.Test

class DoorViewTest {

    @Test
    fun `a sofa that does not go through is seen whole, not from inside it`() {
        // Three-seat sofa carried on its back, 220 cm along the way you walk, stopped at a 78 cm door.
        val along = 2.2f
        val view = doorView(dw = 0.78f, dh = 2f, along = along, fits = false)
        // At rest against the frame its near end is at about z = along: the camera stands well behind it.
        assertTrue("eye z ${view.eye.z}", view.eye.z > along + 1.5f)
        // And looks over it: above the doorway's height.
        assertTrue("eye y ${view.eye.y}", view.eye.y > 2f)
    }

    @Test
    fun `a long object that goes through is seen whole from where it starts`() {
        val along = 2.2f
        val view = doorView(dw = 1f, dh = 2f, along = along, fits = true)
        // It starts with its centre at along / 2 + 0.55 m from the wall: its near end stays ahead of the camera.
        assertTrue("eye z ${view.eye.z}", view.eye.z > along + 0.55f + 1f)
    }

    @Test
    fun `a small object keeps the doorway close`() {
        val view = doorView(dw = 0.78f, dh = 2f, along = 0.6f, fits = true)
        assertTrue("eye z ${view.eye.z}", view.eye.z < 4f)
    }
}
