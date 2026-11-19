package com.gorisse.thomas.willitfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notice is Google's, word for word: https://developers.google.com/ar/develop/privacy-requirements */
class ArCoreNoticeTest {

    private fun linked(url: String): String {
        val link = ArCoreNotice.links.single { it.url == url }
        return ArCoreNotice.TEXT.substring(link.start, link.end)
    }

    @Test
    fun `the notice reads as Google gives it`() {
        assertEquals(
            "This application runs on Google Play Services for AR (ARCore), which is provided by Google " +
                "and governed by the Google Privacy Policy.",
            ArCoreNotice.TEXT,
        )
    }

    @Test
    fun `the privacy policy and ARCore are linked on their own words`() {
        assertEquals("Google Privacy Policy", linked("https://policies.google.com/privacy"))
        assertEquals("ARCore", linked("https://developers.google.com/ar"))
        assertEquals(
            "Google Play Services for AR",
            linked("https://play.google.com/store/apps/details?id=com.google.ar.core"),
        )
    }

    @Test
    fun `links do not overlap and stay inside the text`() {
        val sorted = ArCoreNotice.links.sortedBy { it.start }
        sorted.forEach { assertTrue(it.start >= 0 && it.end <= ArCoreNotice.TEXT.length && it.start < it.end) }
        sorted.zipWithNext().forEach { (a, b) -> assertTrue("$a overlaps $b", a.end <= b.start) }
    }
}
