package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Preset
import java.net.URLDecoder
import java.net.URLEncoder

/** One of the person's own things, by the name they gave it ("Grandma's sofa"). Sides in centimetres. */
data class Thing(val name: String, val dims: Dimensions)

/**
 * The things the person keeps for moving day, in the order they were added. On the phone only.
 * Stored one per line: the name, URL-encoded so no name can break the line, then the sides.
 */
object MyThings {
    const val MAX = 30
    const val MAX_NAME = 40

    fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    /** Adds [thing] at the end, or replaces the one of the same name (case aside). */
    fun add(things: List<Thing>, thing: Thing): List<Thing> {
        val name = cleanName(thing.name)
        if (name.isEmpty() || !thing.dims.isValid) return things
        val entry = Thing(name, thing.dims.copy(source = DimensionSource.TYPED))
        val i = things.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        return if (i >= 0) things.toMutableList().also { it[i] = entry } else (things + entry).take(MAX)
    }

    fun remove(things: List<Thing>, name: String): List<Thing> = things.filterNot { it.name == name }

    fun canName(things: List<Thing>, name: String, exceptName: String? = null): Boolean {
        val cleaned = cleanName(name)
        return cleaned.isNotEmpty() && things.none {
            it.name != exceptName && it.name.equals(cleaned, ignoreCase = true)
        }
    }

    /** Renaming keeps the dimensions and insertion position, without replacing another thing. */
    fun rename(things: List<Thing>, oldName: String, name: String): List<Thing> {
        if (!canName(things, name, exceptName = oldName)) return things
        return things.map { if (it.name == oldName) it.copy(name = cleanName(name)) else it }
    }

    fun encode(things: List<Thing>): String = things.joinToString("\n") { t ->
        val sides = listOfNotNull(t.dims.widthCm, t.dims.depthCm, t.dims.heightCm, t.dims.presetId).joinToString(",")
        URLEncoder.encode(t.name, "UTF-8") + "|" + sides
    }

    /** A damaged line does not hide the other things. */
    fun decode(stored: String?): List<Thing> {
        var things = emptyList<Thing>()
        stored?.lineSequence()?.forEach { line ->
            val bar = line.indexOf('|')
            if (bar <= 0) return@forEach
            val name = runCatching { URLDecoder.decode(line.substring(0, bar), "UTF-8") }.getOrNull() ?: return@forEach
            val sides = line.substring(bar + 1).split(',')
            if (sides.size !in 3..4) return@forEach
            val w = sides[0].toFloatOrNull() ?: return@forEach
            val d = sides[1].toFloatOrNull() ?: return@forEach
            val h = sides[2].toFloatOrNull() ?: return@forEach
            val preset = sides.getOrNull(3)?.takeIf { Preset.byId(it) != null }
            things = add(things, Thing(name, Dimensions(w, d, h, source = DimensionSource.TYPED, presetId = preset)))
        }
        return things
    }
}
