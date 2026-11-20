package com.gorisse.thomas.willitfit.scene

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagShownTest {

    // The Pixel 9 in AR: the shutter row starts at 0.73 of the height, the title ends at 0.15.
    private val bottomLimit = -0.73f
    private val topLimit = 0.85f

    @Test
    fun `a tag between the bars shows`() {
        assertTrue(tagShown(true, bottom = -0.2f, top = -0.1f, bottomLimit, topLimit))
        assertTrue(tagShown(false, bottom = -0.2f, top = -0.1f, bottomLimit, topLimit))
    }

    @Test
    fun `a tag grown past the title hides, even with its centre below it`() {
        // Up close to a washer, the height tag spanned 0.66 to 0.92: its centre was clear.
        assertFalse(tagShown(true, bottom = 0.66f, top = 0.92f, bottomLimit, topLimit))
    }

    @Test
    fun `a tag reaching into the shutter row hides`() {
        assertFalse(tagShown(true, bottom = -0.75f, top = -0.68f, bottomLimit, topLimit))
    }

    @Test
    fun `a hidden tag waits to be clear before it comes back`() {
        assertFalse(tagShown(false, bottom = -0.70f, top = -0.6f, bottomLimit, topLimit))
        assertFalse(tagShown(false, bottom = 0.6f, top = 0.82f, bottomLimit, topLimit))
        assertTrue(tagShown(false, bottom = -0.6f, top = -0.5f, bottomLimit, topLimit))
        assertTrue(tagShown(false, bottom = 0.6f, top = 0.75f, bottomLimit, topLimit))
    }

    @Test
    fun `with no bars every tag on screen shows`() {
        assertTrue(tagShown(true, bottom = -0.99f, top = 0.99f, bottomLimit = -1f, topLimit = 1f))
    }
}
