package com.gorisse.thomas.willitfit.data

import android.content.Context
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.dimensionsIn
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
            val u = unit
            if (!p.contains("w")) return Preset.DEFAULT.dimensionsIn(u)
            val d = Dimensions(
                widthCm = p.getFloat("w", 0f), depthCm = p.getFloat("d", 0f), heightCm = p.getFloat("h", 0f),
                source = p.getString("src", null)?.let { runCatching { DimensionSource.valueOf(it) }.getOrNull() }
                    ?: DimensionSource.TYPED,
                presetId = p.getString("preset", null),
            )
            if (!d.isValid) return Preset.DEFAULT.dimensionsIn(u)
            // A typical size untouched since it was picked reads in the unit of the day: 3.0.x
            // kept the centimetre size for everyone, so inches showed 86.6 × 37.4 × 33.5.
            val matched = d.withMatchingPreset()
            val preset = Preset.byId(matched.presetId)
            return if (matched.source == DimensionSource.PRESET && preset != null) preset.dimensionsIn(u) else matched
        }
        set(d) = p.edit().putFloat("w", d.widthCm).putFloat("d", d.depthCm).putFloat("h", d.heightCm)
            .putString("src", d.source.name).putString("preset", d.presetId).apply()

    var door: Door
        get() {
            val typical = Door.typicalFor(unit)
            return Door(p.getFloat("door_w", typical.widthCm), p.getFloat("door_h", typical.heightCm))
                .takeIf { it.isValid } ?: typical
        }
        set(d) = p.edit().putFloat("door_w", d.widthCm).putFloat("door_h", d.heightCm).apply()

    /** Answers the person got: a door checked, an AR photo taken. Counted on the way back home. */
    var answers: Int
        get() = p.getInt("answers", 0)
        set(value) = p.edit().putInt("answers", value).apply()

    /** Play's rating sheet was asked for once already: never again, Play decides from there. */
    var reviewAsked: Boolean
        get() = p.getBoolean("review_asked", false)
        set(value) = p.edit().putBoolean("review_asked", value).apply()

    private companion object {
        const val KEY_UNIT = "unit"
    }
}
