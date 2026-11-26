package com.gorisse.thomas.willitfit.fit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Clear door opening and usable cabin dimensions, in centimetres. */
data class Lift(
    val doorWidthCm: Float,
    val doorHeightCm: Float,
    val cabinWidthCm: Float,
    val cabinDepthCm: Float,
    val cabinHeightCm: Float,
) {
    val isValid: Boolean
        get() = listOf(doorWidthCm, doorHeightCm, cabinWidthCm, cabinDepthCm, cabinHeightCm)
            .all { it.isFinite() && it in Dimensions.MIN_CM..Dimensions.MAX_CM }

    companion object {
        // Residential example, not an accessibility minimum: EN 81-70 type 2 is 110 × 140 cm.
        val TYPICAL = Lift(80f, 200f, 100f, 125f, 210f)
        // Common US passenger elevator dimensions, expressed in whole inches (36 × 84 opening).
        val TYPICAL_INCH = LengthUnit.CM_PER_INCH.let { i -> Lift(36 * i, 84 * i, 51 * i, 80 * i, 96 * i) }
        fun typicalFor(unit: LengthUnit): Lift = if (unit == LengthUnit.INCH) TYPICAL_INCH else TYPICAL
    }
}

enum class LiftLimit { DOOR_WIDTH, DOOR_HEIGHT, CABIN_FLOOR, CABIN_HEIGHT }

data class LiftVerdict(
    val fits: Boolean,
    val carry: Carry,
    val marginCm: Float,
    val limit: LiftLimit,
    val diagonal: Boolean,
    val angleDeg: Float = 0f,
) {
    val isTight: Boolean get() = fits && marginCm < DoorVerdict.TIGHT_CM
}

/** Best floor clearance and angle, measured from the object's across side to cabin width. */
internal fun liftFloor(across: Float, along: Float, width: Float, depth: Float): Pair<Float, Float> {
    fun gap(deg: Double): Float {
        val t = deg * PI / 180
        return minOf(width - across * cos(t) - along * sin(t), depth - across * sin(t) - along * cos(t)).toFloat()
    }
    val straight = minOf(width - across, depth - along)
    val turned = minOf(width - along, depth - across)
    // A straight placement is easier to carry and explain, even if a diagonal has more clearance.
    if (maxOf(straight, turned) >= 0f) return if (straight >= turned) straight to 0f else turned to 90f
    var bestAngle = 0.0
    var best = straight
    for (i in 1..900) {
        val angle = i / 10.0
        val margin = gap(angle)
        if (margin > best) { best = margin; bestAngle = angle }
    }
    // Refine around the sweep's maximum to resolve near-touching corners.
    val start = maxOf(0.0, bestAngle - 0.1)
    val end = minOf(90.0, bestAngle + 0.1)
    for (i in 0..200) {
        val angle = start + (end - start) * i / 200
        val margin = gap(angle)
        if (margin > best) { best = margin; bestAngle = angle }
    }
    return best to bestAngle.toFloat()
}

/** A static placement after passing the opening; it does not simulate the entry manoeuvre. */
fun checkLift(obj: Dimensions, lift: Lift): LiftVerdict {
    // No require(): the screen checks while the person types, and a check never crashes the app.
    // A half-typed size never fits (a NaN would otherwise lose every comparison and pass).
    if (!obj.isValid || !lift.isValid) return LiftVerdict(false, Carry.entries.first(), 0f, LiftLimit.DOOR_WIDTH, false)
    val all = Carry.entries.map { carry ->
        val across = obj.side(carry.across)
        val up = obj.side(carry.up)
        val (floor, angle) = liftFloor(across, obj.side(carry.along), lift.cabinWidthCm, lift.cabinDepthCm)
        val gaps = listOf(
            LiftLimit.DOOR_WIDTH to lift.doorWidthCm - across,
            LiftLimit.DOOR_HEIGHT to lift.doorHeightCm - up,
            LiftLimit.CABIN_HEIGHT to lift.cabinHeightCm - up,
            LiftLimit.CABIN_FLOOR to floor,
        )
        val (limit, margin) = gaps.minBy { it.second }
        LiftVerdict(margin >= 0f, carry, margin, limit, angle > 0f && angle < 90f, angle) to
            gaps.sumOf { minOf(it.second, 0f).toDouble() }
    }
    val fitting = all.map { it.first }.filter { it.fits }
    return if (fitting.isNotEmpty()) fitting.sortedWith(
        compareByDescending<LiftVerdict> { it.carry.isUpright }.thenByDescending { it.marginCm }.thenBy { it.carry.ordinal },
    ).first() else all.sortedWith(
        compareByDescending<Pair<LiftVerdict, Double>> { it.second }.thenBy { it.first.carry.ordinal },
    ).first().first
}
