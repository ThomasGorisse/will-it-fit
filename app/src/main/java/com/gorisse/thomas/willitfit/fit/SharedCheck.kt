package com.gorisse.thomas.willitfit.fit

import java.net.URLDecoder
import java.util.Locale
import kotlin.math.roundToInt

/** The space a check was about. */
enum class Space(val key: String) { DOOR("door"), BOOT("boot"), HALL("hall"), LIFT("lift") }

/**
 * A check, as a link carries it: the object, and the door, boot, hallway or lift it was checked
 * against. Sizes travel in centimetres to the tenth; the receiver reads them in their own unit.
 * [unit] is the sender's, only for the web page that shows the sizes as they were typed.
 *
 * The link is the share text's: `https://thomasgorisse.github.io/willitfit/check/#o=220x95x85&door=80x204`.
 * The sizes ride after the "#", which a browser never sends: opening the page tells the site
 * nothing about them. The app opens the link straight into the same check. Without the app, the
 * page offers Play, whose install referrer brings the same sizes to the first launch.
 */
data class SharedCheck(
    val dims: Dimensions,
    val space: Space? = null,
    val door: Door? = null,
    val boot: Boot? = null,
    val hall: Hall? = null,
    val lift: Lift? = null,
    val unit: LengthUnit = LengthUnit.CM,
) {
    /** The sizes as the link carries them, without "#". Keys are short: the link goes into a chat message. */
    fun query(): String = buildList {
        add("o=" + join(dims.widthCm, dims.depthCm, dims.heightCm))
        dims.presetId?.let { add("p=$it") }
        when (space) {
            Space.DOOR -> door?.let { add("door=" + join(it.widthCm, it.heightCm)) }
            Space.BOOT -> boot?.let {
                add("boot=" + join(it.openingWidthCm, it.openingHeightCm, it.lengthCm, it.widthCm, it.heightCm))
                it.seatsDownLengthCm?.let { length -> add("bootDown=" + join(length)) }
            }
            Space.HALL -> hall?.let { add("hall=" + join(it.widthCm, it.turnWidthCm, it.ceilingCm)) }
            Space.LIFT -> lift?.let { add("lift=" + join(it.doorWidthCm, it.doorHeightCm, it.cabinWidthCm, it.cabinDepthCm, it.cabinHeightCm)) }
            null -> Unit
        }
        if (unit == LengthUnit.INCH) add("u=in")
    }.joinToString("&")

    fun link(): String = "$LINK_BASE#${query()}"

    companion object {
        const val HOST = "thomasgorisse.github.io"
        const val PATH = "/willitfit/check/"
        const val LINK_BASE = "https://$HOST$PATH"

        /** At most this many characters are read: a link is short, anything longer is not ours. */
        private const val MAX_QUERY = 512

        /**
         * Reads a link's sizes ("o=…&door=…", with or without its "#" or "?"), or Play's install
         * referrer, where it sits next to utm_source. Null when it carries no valid object; a
         * space that does not read is left out, the object alone still opens.
         */
        fun parse(query: String?): SharedCheck? {
            if (query.isNullOrBlank() || query.length > MAX_QUERY) return null
            val params = query.removePrefix("#").removePrefix("?").split('&').mapNotNull { pair ->
                val i = pair.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val key = pair.substring(0, i)
                val value = runCatching { URLDecoder.decode(pair.substring(i + 1), "UTF-8") }.getOrNull()
                    ?: return@mapNotNull null
                key to value
            }.toMap()
            val o = numbers(params["o"], 3) ?: return null
            val preset = Preset.byId(params["p"])
            val typed = Dimensions(o[0], o[1], o[2])
            if (!typed.isValid) return null
            // The preset's name comes along only while the sizes are still the preset's.
            // Sent to the tenth: 87 in is 220.98 cm, back as 221.
            fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.06f
            val isPreset = preset != null && LengthUnit.entries.map { preset.dimensionsIn(it) }.any {
                near(it.widthCm, typed.widthCm) && near(it.depthCm, typed.depthCm) && near(it.heightCm, typed.heightCm)
            }
            // Its name only, and always one: as PRESET, or nameless sizes that happen to be a
            // preset's, the receiver's unit would round them again (87 in is not 220 cm) and the
            // check would no longer be the one that was sent.
            val name = if (preset != null && isPreset) preset.id else typed.withMatchingPreset().presetId
            val dims = typed.copy(presetId = name)
            val unit = if (params["u"] == "in") LengthUnit.INCH else LengthUnit.CM
            numbers(params["door"], 2)?.let { Door(it[0], it[1]) }?.takeIf { it.isValid }?.let {
                return SharedCheck(dims, Space.DOOR, door = it, unit = unit)
            }
            numbers(params["boot"], 5)?.let {
                // A malformed optional extension must not discard an otherwise valid old check.
                val down = numbers(params["bootDown"], 1)?.firstOrNull()
                    ?.takeIf { cm -> cm in Dimensions.MIN_CM..Dimensions.MAX_CM }
                Boot(it[0], it[1], it[2], it[3], it[4], down)
            }?.takeIf { it.isValid }?.let {
                return SharedCheck(dims, Space.BOOT, boot = it, unit = unit)
            }
            numbers(params["hall"], 3)?.let { Hall(it[0], it[1], it[2]) }?.takeIf { it.isValid }?.let {
                return SharedCheck(dims, Space.HALL, hall = it, unit = unit)
            }
            numbers(params["lift"], 5)?.let { Lift(it[0], it[1], it[2], it[3], it[4]) }?.takeIf { it.isValid }?.let {
                return SharedCheck(dims, Space.LIFT, lift = it, unit = unit)
            }
            return SharedCheck(dims, unit = unit)
        }

        private fun numbers(text: String?, count: Int): List<Float>? {
            val parts = text?.split('x') ?: return null
            if (parts.size != count) return null
            val values = parts.map { part ->
                if (part.isEmpty() || part.length > 7 || !part.all { it.isDigit() || it == '.' }) return null
                part.toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
            }
            return values
        }

        /** Centimetres to the tenth, without a trailing ".0": "220", "87.6". */
        private fun join(vararg cm: Float): String = cm.joinToString("x") { value ->
            val tenths = (value * 10f).roundToInt()
            if (tenths % 10 == 0) (tenths / 10).toString() else String.format(Locale.ROOT, "%d.%d", tenths / 10, tenths % 10)
        }
    }
}
