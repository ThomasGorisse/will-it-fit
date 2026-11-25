package com.gorisse.thomas.willitfit.fit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A right-angle turn between two hallways, wall to wall, in centimetres: the [widthCm] of the
 * hallway you carry it along, the [turnWidthCm] of the one you turn into (or of the door you turn
 * through), and the [ceilingCm] above both.
 */
data class Hall(val widthCm: Float, val turnWidthCm: Float, val ceilingCm: Float) {
    val isValid: Boolean
        get() = listOf(widthCm, turnWidthCm, ceilingCm).all { it.isFinite() && it in Dimensions.MIN_CM..Dimensions.MAX_CM }

    companion object {
        /** A common hallway in Europe: 90 cm wall to wall, 2.40 m under the ceiling. */
        val TYPICAL = Hall(widthCm = 90f, turnWidthCm = 90f, ceilingCm = 240f)

        /** A common hallway in the US: 36 in wall to wall, 8 ft under the ceiling, in whole inches. */
        val TYPICAL_INCH = Hall(
            widthCm = 36 * LengthUnit.CM_PER_INCH,
            turnWidthCm = 36 * LengthUnit.CM_PER_INCH,
            ceilingCm = 96 * LengthUnit.CM_PER_INCH,
        )

        fun typicalFor(unit: LengthUnit): Hall = if (unit == LengthUnit.INCH) TYPICAL_INCH else TYPICAL
    }
}

/** What stops the object first in the turn. */
enum class HallLimit { WIDTH, TURN, CEILING }

/**
 * The answer to "does it make the turn?".
 *
 * @property angleDeg where the turn is tightest: the object's angle to the hallway it turns into,
 *   0° once it is in. Where the object is drawn.
 */
data class HallVerdict(
    val fits: Boolean,
    val carry: Carry,
    val marginCm: Float,
    val limit: HallLimit,
    val angleDeg: Float,
) {
    val isTight: Boolean get() = fits && marginCm < DoorVerdict.TIGHT_CM
}

/** The tightest point of the turn for an object [acrossCm] wide: the longest it can be, and where. */
data class Turn(val longestCm: Float, val angleDeg: Float)

/**
 * The longest object, [acrossCm] wide, that can be carried level around a right-angle turn from
 * a hallway [widthCm] wide into one [turnWidthCm] wide: its outer side slides along both outer
 * walls while its inner side grazes the inner corner. At angle θ to the second hallway that
 * length is (a·sin θ + b·cos θ − w) / (sin θ · cos θ); the object makes the turn when it is no
 * longer than the smallest of those lengths. Negative when it is too wide for either hallway.
 *
 * Both hallways are taken as running on past the corner: a door is shorter than a hallway, so
 * turning into one is never harder than this says.
 */
fun turn(widthCm: Float, turnWidthCm: Float, acrossCm: Float): Turn {
    if (acrossCm > widthCm || acrossCm > turnWidthCm) return Turn(minOf(widthCm, turnWidthCm) - acrossCm, 45f)
    val a = widthCm.toDouble()
    val b = turnWidthCm.toDouble()
    val w = acrossCm.toDouble()
    var best = Double.MAX_VALUE
    var bestDeg = 45.0
    // A tenth of a degree is under a millimetre of length for anything carried indoors.
    for (i in 1 until 900) {
        val deg = i / 10.0
        val t = deg * PI / 180
        val s = sin(t)
        val c = cos(t)
        val length = (a * s + b * c - w) / (s * c)
        if (length < best) {
            best = length
            bestDeg = deg
        }
    }
    return Turn(best.toFloat(), bestDeg.toFloat())
}

/**
 * Carried level around the turn, in each of the six ways: the side up must clear the ceiling,
 * the side across must fit both hallways and the side along must make the turn. Upright carries
 * win over lying ones, then the larger margin. When nothing passes, the verdict names the
 * closest miss.
 */
fun checkHall(obj: Dimensions, hall: Hall): HallVerdict {
    fun verdict(carry: Carry): HallVerdict {
        val across = obj.side(carry.across)
        val turn = turn(hall.widthCm, hall.turnWidthCm, across)
        val gaps = listOf(
            HallLimit.CEILING to hall.ceilingCm - obj.side(carry.up),
            HallLimit.WIDTH to minOf(hall.widthCm, hall.turnWidthCm) - across,
            HallLimit.TURN to turn.longestCm - obj.side(carry.along),
        )
        val (limit, margin) = gaps.minBy { it.second }
        return HallVerdict(margin >= 0f, carry, margin, limit, turn.angleDeg)
    }

    val all = Carry.entries.map(::verdict)
    val fitting = all.filter { it.fits }
    if (fitting.isNotEmpty()) {
        return fitting.sortedWith(
            compareByDescending<HallVerdict> { it.carry.isUpright }
                .thenByDescending { it.marginCm }
                .thenBy { it.carry.ordinal },
        ).first()
    }
    return all.sortedWith(compareByDescending<HallVerdict> { it.marginCm }.thenBy { it.carry.ordinal }).first()
}
