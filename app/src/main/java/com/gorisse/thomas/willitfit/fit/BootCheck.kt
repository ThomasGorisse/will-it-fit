package com.gorisse.thomas.willitfit.fit

/**
 * Clear opening and usable space, in centimetres. Length runs along the floor to the rear seats;
 * width is between the wheel arches. Height is to the shelf, or the roof with the shelf out.
 */
data class Boot(
    val openingWidthCm: Float,
    val openingHeightCm: Float,
    val lengthCm: Float,
    val widthCm: Float,
    val heightCm: Float,
    /** Optional usable floor length after folding the rear seats. Other clearances stay the same. */
    val seatsDownLengthCm: Float? = null,
) {
    val isValid: Boolean
        get() = listOfNotNull(openingWidthCm, openingHeightCm, lengthCm, widthCm, heightCm, seatsDownLengthCm)
            .all { it.isFinite() && it in Dimensions.MIN_CM..Dimensions.MAX_CM }

    companion object {
        val ESTATE = Boot(108f, 80f, 103f, 104f, 68f)
    }
}

enum class BootLimit { OPENING_WIDTH, OPENING_HEIGHT, LENGTH, WIDTH, HEIGHT }

data class BootVerdict(
    val fits: Boolean, val carry: Carry, val marginCm: Float, val limit: BootLimit,
    val angleDeg: Float = 0f,
    val rearSeatsDown: Boolean = false,
) {
    val isTight: Boolean get() = fits && marginCm < DoorVerdict.TIGHT_CM
    val diagonal: Boolean get() = angleDeg > 0f && angleDeg < 90f
}

/**
 * Seats up. Prefer a straight carry; otherwise try a floor diagonal with the same yaw throughout
 * entry. Its FULL projected width must clear both the opening and the wheel arches. This is a
 * conservative rectangular corridor, not a simulation of pivoting around the hatch or wheel arches.
 */
fun checkBoot(obj: Dimensions, boot: Boot): BootVerdict {
    if (!obj.isValid || !boot.isValid) return BootVerdict(false, Carry.UPRIGHT, 0f, BootLimit.OPENING_WIDTH)
    fun verdict(carry: Carry, angle: Float = 0f): BootVerdict {
        val (across, along) = bootFootprint(obj, carry, angle)
        val up = obj.side(carry.up)
        val gaps = listOf(
            BootLimit.OPENING_WIDTH to boot.openingWidthCm - across,
            BootLimit.OPENING_HEIGHT to boot.openingHeightCm - up,
            BootLimit.LENGTH to boot.lengthCm - along,
            BootLimit.WIDTH to boot.widthCm - across,
            BootLimit.HEIGHT to boot.heightCm - up,
        )
        val (limit, margin) = gaps.minBy { it.second }
        return BootVerdict(margin >= 0f, carry, margin, limit, angle)
    }
    val order = compareByDescending<BootVerdict> { it.marginCm }.thenBy { it.carry.ordinal }
    val straight = Carry.entries.map { verdict(it) }.sortedWith(order).first()
    if (straight.fits) return straight
    val diagonal = Carry.entries.map { carry ->
        // Reuse the lift's floor search, but constrain its width by the opening as well.
        val (_, angle) = liftFloor(obj.side(carry.across), obj.side(carry.along),
            minOf(boot.widthCm, boot.openingWidthCm), boot.lengthCm)
        verdict(carry, angle)
    }.sortedWith(order).first()
    return if (diagonal.fits) diagonal else straight
}

/** Recommend folding only when no seats-up placement fits; a miss still describes seats up. */
fun checkCarBoot(obj: Dimensions, boot: Boot): BootVerdict {
    val upright = checkBoot(obj, boot)
    if (upright.fits || !obj.isValid || !boot.isValid) return upright
    val length = boot.seatsDownLengthCm?.takeIf { it > boot.lengthCm } ?: return upright
    val folded = checkBoot(obj, boot.copy(lengthCm = length, seatsDownLengthCm = null))
    return if (folded.fits) folded.copy(rearSeatsDown = true) else upright
}

/** Width × depth of the oriented box on the floor, including its diagonal yaw. */
fun bootFootprint(obj: Dimensions, carry: Carry, angleDeg: Float): Pair<Float, Float> {
    if (angleDeg == 0f) return obj.side(carry.across) to obj.side(carry.along)
    val angle = Math.toRadians(angleDeg.toDouble())
    val c = kotlin.math.abs(kotlin.math.cos(angle))
    val s = kotlin.math.abs(kotlin.math.sin(angle))
    return (obj.side(carry.across) * c + obj.side(carry.along) * s).toFloat() to
        (obj.side(carry.across) * s + obj.side(carry.along) * c).toFloat()
}

/** The exact space drawn and captioned for the recommendation. */
fun Boot.forVerdict(verdict: BootVerdict): Boot =
    if (verdict.rearSeatsDown && seatsDownLengthCm != null) copy(lengthCm = seatsDownLengthCm) else this
