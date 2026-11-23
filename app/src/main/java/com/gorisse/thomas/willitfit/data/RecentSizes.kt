package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.withMatchingPreset
import kotlin.math.abs

/** The person's last three checked sizes, newest first. All sides stay in centimetres. */
object RecentSizes {
    fun matches(a: Dimensions, b: Dimensions): Boolean =
        abs(a.widthCm - b.widthCm) <= 0.5f &&
            abs(a.depthCm - b.depthCm) <= 0.5f &&
            abs(a.heightCm - b.heightCm) <= 0.5f

    fun add(history: List<Dimensions>, dims: Dimensions): List<Dimensions> {
        val entry = ownSize(dims) ?: return history
        return (listOf(entry) + history.filterNot { matches(it, entry) }).take(3)
    }

    // The object it started from rides along, so an edited sofa comes back as a sofa.
    fun encode(history: List<Dimensions>): String = history.joinToString(";") {
        listOfNotNull(it.widthCm, it.depthCm, it.heightCm, it.presetId).joinToString(",")
    }

    /** A damaged entry does not hide the other sizes. */
    fun decode(stored: String?): List<Dimensions> {
        val history = mutableListOf<Dimensions>()
        stored?.split(';')?.forEach { entry ->
            val sides = entry.split(',')
            if (sides.size !in 3..4) return@forEach
            val w = sides[0].toFloatOrNull() ?: return@forEach
            val d = sides[1].toFloatOrNull() ?: return@forEach
            val h = sides[2].toFloatOrNull() ?: return@forEach
            val preset = sides.getOrNull(3)?.takeIf { Preset.byId(it) != null }
            val dims = ownSize(Dimensions(w, d, h, presetId = preset)) ?: return@forEach
            if (history.size < 3 && history.none { matches(it, dims) }) history += dims
        }
        return history
    }

    private fun ownSize(dims: Dimensions): Dimensions? {
        if (!dims.isValid || dims.source != DimensionSource.TYPED) return null
        // An edited preset keeps its id for its look; only its actual sides say if it is typical.
        val typical = dims.copy(presetId = null).withMatchingPreset().source == DimensionSource.PRESET
        return dims.takeIf { !typical }
    }
}
