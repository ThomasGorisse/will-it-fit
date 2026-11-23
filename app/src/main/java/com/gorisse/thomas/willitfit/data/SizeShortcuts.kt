package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.formatDimensions

/**
 * The launcher and the activity share centimetres, regardless of the label's unit. Sides travel
 * as Double: a shortcut's intent extras go into a PersistableBundle, which refuses Float.
 */
object SizeShortcuts {
    const val ACTION = "com.gorisse.thomas.willitfit.action.CHECK_SIZE"
    const val WIDTH = "width_cm"
    const val DEPTH = "depth_cm"
    const val HEIGHT = "height_cm"
    const val PRESET = "preset_id"
    const val ID = "shortcut_id"

    data class Entry(
        val id: String,
        val shortLabel: String,
        val longLabel: String,
        val preset: Preset?,
        val extras: Map<String, Any>,
    )

    fun entries(
        recentSizes: List<Dimensions>,
        unit: LengthUnit,
        maxCount: Int,
        presetName: (Preset) -> String,
    ): List<Entry> = recentSizes.filter { it.isValid }.take(maxCount.coerceIn(0, 3))
        .mapIndexed { index, dims ->
            val id = "recent_$index"
            val preset = Preset.byId(dims.presetId)
            val formatted = formatDimensions(dims, unit)
            Entry(
                id = id,
                // Keep all three sides readable, even when decimals need more than ten characters.
                shortLabel = formatted.removeSuffix(" ${unit.symbol}").replace(" ", ""),
                longLabel = preset?.let { "${presetName(it)} · $formatted" } ?: formatted,
                preset = preset,
                extras = buildMap {
                    put(ID, id)
                    put(WIDTH, dims.widthCm.toDouble())
                    put(DEPTH, dims.depthCm.toDouble())
                    put(HEIGHT, dims.heightCm.toDouble())
                    preset?.let { put(PRESET, it.id) }
                },
            )
        }

    fun dimensions(action: String?, extra: (String) -> Any?): Dimensions? {
        if (action != ACTION) return null
        val width = (extra(WIDTH) as? Number)?.toFloat() ?: return null
        val depth = (extra(DEPTH) as? Number)?.toFloat() ?: return null
        val height = (extra(HEIGHT) as? Number)?.toFloat() ?: return null
        val preset = (extra(PRESET) as? String)?.takeIf { Preset.byId(it) != null }
        return Dimensions(width, depth, height, presetId = preset).takeIf { it.isValid }
    }

    fun usedId(extra: (String) -> Any?): String? =
        (extra(ID) as? String)?.takeIf { it in setOf("recent_0", "recent_1", "recent_2") }
}
