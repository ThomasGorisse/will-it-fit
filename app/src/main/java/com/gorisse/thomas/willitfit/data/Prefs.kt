package com.gorisse.thomas.willitfit.data

import android.content.Context
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.withMatchingPreset
import java.util.Locale

/** The last object, the last door and the unit. On the phone only; nothing leaves it. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("will_it_fit", Context.MODE_PRIVATE)

    var unit: LengthUnit
        get() = p.getString(KEY_UNIT, null)?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() }
            ?: LengthUnit.defaultFor(Locale.getDefault())
        set(value) = p.edit().putString(KEY_UNIT, value.name).apply()

    var dimensions: Dimensions
        get() {
            if (!p.contains("w")) return Preset.DEFAULT.dimensions
            val d = Dimensions(
                widthCm = p.getFloat("w", 0f), depthCm = p.getFloat("d", 0f), heightCm = p.getFloat("h", 0f),
                source = p.getString("src", null)?.let { runCatching { DimensionSource.valueOf(it) }.getOrNull() }
                    ?: DimensionSource.TYPED,
                presetId = p.getString("preset", null),
            )
            return if (d.isValid) d.withMatchingPreset() else Preset.DEFAULT.dimensions
        }
        set(d) = p.edit().putFloat("w", d.widthCm).putFloat("d", d.depthCm).putFloat("h", d.heightCm)
            .putString("src", d.source.name).putString("preset", d.presetId).apply()

    var door: Door
        get() = Door(p.getFloat("door_w", Door.TYPICAL.widthCm), p.getFloat("door_h", Door.TYPICAL.heightCm))
            .takeIf { it.isValid } ?: Door.TYPICAL
        set(d) = p.edit().putFloat("door_w", d.widthCm).putFloat("door_h", d.heightCm).apply()

    private companion object {
        const val KEY_UNIT = "unit"
    }
}
