package com.gorisse.thomas.willitfit.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.Hall
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.dimensionsIn
import com.gorisse.thomas.willitfit.fit.withMatchingPreset
import java.util.Locale

/** The last object, recent sizes, the spaces and the unit. On the phone only; nothing leaves it. */
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

    var recentSizes: List<Dimensions>
        get() = RecentSizes.decode(runCatching { p.getString(KEY_RECENT_SIZES, null) }.getOrNull())
        set(value) = p.edit().putString(KEY_RECENT_SIZES, RecentSizes.encode(value)).apply()

    /** The person's own things, by name, for moving day (see [MyThings]). */
    var myThings: List<Thing>
        get() = MyThings.decode(runCatching { p.getString(KEY_MY_THINGS, null) }.getOrNull())
        set(value) = p.edit().putString(KEY_MY_THINGS, MyThings.encode(value)).apply()

    var door: Door
        get() {
            val typical = Door.typicalFor(unit)
            return Door(p.getFloat("door_w", typical.widthCm), p.getFloat("door_h", typical.heightCm))
                .takeIf { it.isValid } ?: typical
        }
        set(d) = p.edit().putFloat("door_w", d.widthCm).putFloat("door_h", d.heightCm).apply()

    /** The person typed a door in. Until then the door is the typical one for the unit. */
    val doorTyped: Boolean get() = p.contains("door_w")

    var carBoot: Boolean
        get() = p.getBoolean("car_boot", false)
        set(value) = p.edit().putBoolean("car_boot", value).apply()

    /** The hallway turn was the last space checked; it wins over [carBoot]. */
    var hallway: Boolean
        get() = p.getBoolean("hallway", false)
        set(value) = p.edit().putBoolean("hallway", value).apply()

    /** The person typed a hallway in. Until then it is the typical one. */
    val hallTyped: Boolean get() = p.contains("hall_w")

    var hall: Hall
        get() {
            val typical = Hall.typicalFor(unit)
            return Hall(
                p.getFloat("hall_w", typical.widthCm), p.getFloat("hall_t", typical.turnWidthCm),
                p.getFloat("hall_c", typical.ceilingCm),
            ).takeIf { it.isValid } ?: typical
        }
        set(h) = p.edit().putFloat("hall_w", h.widthCm).putFloat("hall_t", h.turnWidthCm)
            .putFloat("hall_c", h.ceilingCm).apply()

    var liftSelected: Boolean
        get() = p.getBoolean("lift_selected", false)
        set(value) = p.edit { putBoolean("lift_selected", value) }

    val liftTyped: Boolean get() = p.contains("lift_dw")

    var lift: Lift
        get() {
            val t = Lift.typicalFor(unit)
            return Lift(p.getFloat("lift_dw", t.doorWidthCm), p.getFloat("lift_dh", t.doorHeightCm),
                p.getFloat("lift_cw", t.cabinWidthCm), p.getFloat("lift_cd", t.cabinDepthCm),
                p.getFloat("lift_ch", t.cabinHeightCm)).takeIf { it.isValid } ?: t
        }
        set(value) = p.edit {
            putFloat("lift_dw", value.doorWidthCm); putFloat("lift_dh", value.doorHeightCm)
            putFloat("lift_cw", value.cabinWidthCm); putFloat("lift_cd", value.cabinDepthCm)
            putFloat("lift_ch", value.cabinHeightCm)
        }

    var boot: Boot
        get() {
            val example = Boot.ESTATE
            return Boot(
                p.getFloat("boot_ow", example.openingWidthCm), p.getFloat("boot_oh", example.openingHeightCm),
                p.getFloat("boot_l", example.lengthCm), p.getFloat("boot_w", example.widthCm),
                p.getFloat("boot_h", example.heightCm),
            ).takeIf { it.isValid } ?: example
        }
        set(value) = p.edit().putFloat("boot_ow", value.openingWidthCm).putFloat("boot_oh", value.openingHeightCm)
            .putFloat("boot_l", value.lengthCm).putFloat("boot_w", value.widthCm)
            .putFloat("boot_h", value.heightCm).putBoolean("boot_typed", true).apply()

    val bootTyped: Boolean get() = p.getBoolean("boot_typed", false)

    fun useExampleBoot() {
        boot = Boot.ESTATE
        p.edit().putBoolean("boot_typed", false).apply()
    }

    /** Play's install referrer was read: it is read once, on the first launch that reaches Play. */
    var referrerRead: Boolean
        get() = p.getBoolean("referrer_read", false)
        set(value) = p.edit().putBoolean("referrer_read", value).apply()

    /**
     * News and tips (see [News]). Android 13 and later ask the person first, so it starts off
     * there; before 13 there is no such question, and every note carries its Turn off.
     */
    var newsOn: Boolean
        get() = p.getBoolean("news_on", Build.VERSION.SDK_INT < 33)
        set(value) = p.edit().putBoolean("news_on", value).apply()

    /** The offer card was shown once: it never comes back, whatever the answer. */
    var newsOffered: Boolean
        get() = p.getBoolean("news_offered", false)
        set(value) = p.edit().putBoolean("news_offered", value).apply()

    var lastOpenAt: Long
        get() = p.getLong("last_open_at", 0L)
        set(value) = p.edit().putLong("last_open_at", value).apply()

    var updatedAt: Long
        get() = p.getLong("updated_at", 0L)
        set(value) = p.edit().putLong("updated_at", value).apply()

    var newsSentAt: List<Long>
        get() = p.getString("news_sent_at", null)?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        set(value) = p.edit().putString("news_sent_at", value.joinToString(",")).apply()

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

    /** Kept synchronously: a process death must never make an early user decide twice. */
    fun decideGrandfathered(firstInstall: Long, lastUpdate: Long): Boolean {
        val existing = if (p.contains("premium_grandfathered")) p.getBoolean("premium_grandfathered", false) else null
        val early = grandfatherDecision(firstInstall, lastUpdate, answers, myThings.isNotEmpty(),
            p.getInt(KEY_REVIEW_REQUESTS, 0).coerceAtLeast(if (p.getBoolean(KEY_LEGACY_REVIEW_ASKED, false)) 1 else 0),
            recentSizes.isNotEmpty(), existing)
        if (existing == null) p.edit(commit = true) { putBoolean("premium_grandfathered", early) }
        return early
    }

    /** Debug screenshots start at the welcome screens without erasing the person's objects. */
    fun resetPremiumWelcome() {
        p.edit(commit = true) { remove("premium_grandfathered"); remove("onboarding_done"); remove("onboarding_spaces") }
    }

    /** A fresh QA install must not inherit the real install's early-user decision. */
    fun decideFreshForQa() { p.edit(commit = true) { putBoolean("premium_grandfathered", false) } }

    var onboardingDone: Boolean
        get() = p.getBoolean("onboarding_done", false)
        set(value) = p.edit { putBoolean("onboarding_done", value) }

    var onboardingSpaces: Set<String>
        get() = p.getString("onboarding_spaces", null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        set(value) = p.edit { putString("onboarding_spaces", value.joinToString(",")) }

    /** An expired subscription still leaves the saved home available to read. */
    var hadPremium: Boolean
        get() = p.getBoolean("had_premium", false)
        set(value) = p.edit { putBoolean("had_premium", value) }

    var trialLaunchAt: Long
        get() = p.getLong("trial_launch_at", 0)
        set(value) = p.edit { putLong("trial_launch_at", value) }
    var trialToken: String?
        get() = p.getString("trial_token", null)
        set(value) = p.edit { putString("trial_token", value) }
    var trialPrice: String?
        get() = p.getString("trial_price", null)
        set(value) = p.edit { putString("trial_price", value) }
    var trialRemindedToken: String?
        get() = p.getString("trial_reminded_token", null)
        set(value) = p.edit { putString("trial_reminded_token", value) }

    private companion object {
        const val KEY_RECENT_SIZES = "recent_sizes_v1"
        const val KEY_MY_THINGS = "my_things_v1"
        const val KEY_UNIT = "unit"
        const val KEY_LEGACY_REVIEW_ASKED = "review_asked"
        const val KEY_REVIEW_REQUESTS = "review_requests"
        const val KEY_REVIEW_LAST_AT = "review_last_at"
        const val KEY_REVIEW_ANSWERS_AT_LAST = "review_answers_at_last"
    }
}
