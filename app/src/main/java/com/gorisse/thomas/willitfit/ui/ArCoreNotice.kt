package com.gorisse.thomas.willitfit.ui

/**
 * The notice Google requires of every ARCore app, in the main menu or on a notice screen:
 * https://developers.google.com/ar/develop/privacy-requirements. Google gives it in English and
 * with these links, so it is shown as given, in every language. The links are spans of [TEXT].
 */
object ArCoreNotice {
    const val TEXT = "This application runs on Google Play Services for AR (ARCore), " +
        "which is provided by Google and governed by the Google Privacy Policy."

    const val ARCORE_PLAY_URL = "https://play.google.com/store/apps/details?id=com.google.ar.core"
    const val ARCORE_URL = "https://developers.google.com/ar"
    const val PRIVACY_URL = "https://policies.google.com/privacy"

    data class Link(val start: Int, val end: Int, val url: String)

    val links: List<Link> = listOf(
        linkOn("Google Play Services for AR", ARCORE_PLAY_URL),
        linkOn("ARCore", ARCORE_URL),
        linkOn("Google Privacy Policy", PRIVACY_URL),
    )

    private fun linkOn(words: String, url: String): Link {
        val start = TEXT.indexOf(words)
        check(start >= 0) { "\"$words\" is not in the notice" }
        return Link(start, start + words.length, url)
    }
}
