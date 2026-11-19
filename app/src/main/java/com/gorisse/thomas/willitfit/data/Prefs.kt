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

    /** The person typed a door in. Until then the door is the typical one for the unit. */
    val doorTyped: Boolean get() = p.contains("door_w")

    /** Answers the person got: a door verdict seen, an AR photo taken. Counted on the way back home. */
    var answers: Int
        get() = p.getInt("answers", 0)
        set(value) = p.edit().putInt("answers", value).apply()

    /** Where the app stands with Play's rating sheet: see [ReviewPolicy]. */
    var review: ReviewState
        get() {
            if (!p.contains(KEY_REVIEW_REQUESTS)) {
                // First read since 3.0.2: carry its one-shot flag over, once.
                val migrated = ReviewPolicy.migrated(answers, p.getBoolean(KEY_LEGACY_REVIEW_ASKED, false), System.currentTimeMillis())
                review = migrated
                return migrated
            }
            return ReviewState(
                answers = answers,
                requests = p.getInt(KEY_REVIEW_REQUESTS, 0),
                lastRequestAt = p.getLong(KEY_REVIEW_LAST_AT, 0L),
                answersAtLastRequest = p.getInt(KEY_REVIEW_ANSWERS_AT_LAST, 0),
            )
        }
        set(value) = p.edit()
            .putInt(KEY_REVIEW_REQUESTS, value.requests)
            .putLong(KEY_REVIEW_LAST_AT, value.lastRequestAt)
            .putInt(KEY_REVIEW_ANSWERS_AT_LAST, value.answersAtLastRequest)
            .remove(KEY_LEGACY_REVIEW_ASKED)
            .apply()

    private companion object {
        const val KEY_UNIT = "unit"
        const val KEY_LEGACY_REVIEW_ASKED = "review_asked"
        const val KEY_REVIEW_REQUESTS = "review_requests"
        const val KEY_REVIEW_LAST_AT = "review_last_at"
        const val KEY_REVIEW_ANSWERS_AT_LAST = "review_answers_at_last"
    }
}
