package com.gorisse.thomas.willitfit.fit

/** The clear opening of a door, frame to frame, in centimetres. */
data class Door(val widthCm: Float, val heightCm: Float) {
    val isValid: Boolean
        get() = listOf(widthCm, heightCm).all { it.isFinite() && it in Dimensions.MIN_CM..Dimensions.MAX_CM }

    companion object {
        /** A common interior door in Europe: 83 cm leaf, about 78 × 200 cm of clear opening. */
        val TYPICAL = Door(widthCm = 78f, heightCm = 200f)

        /**
         * A common interior door in the US: 32 in leaf, about 30 × 80 in of clear opening. Typed
         * in whole inches, as people there measure it.
         */
        val TYPICAL_INCH = Door(widthCm = 30 * LengthUnit.CM_PER_INCH, heightCm = 80 * LengthUnit.CM_PER_INCH)

        /** The typical door where people measure in [unit]. */
        fun typicalFor(unit: LengthUnit): Door = if (unit == LengthUnit.INCH) TYPICAL_INCH else TYPICAL
    }
}

/** One side of the object. */
enum class Side { WIDTH, DEPTH, HEIGHT }

/**
 * How the object is carried through the opening: which side goes [across] the door, which one
 * stands [up], and which one goes [along] the way you walk. Listed from the most natural to the
 * least: standing on its feet first, then on its back or its side.
 */
enum class Carry(val across: Side, val up: Side, val along: Side) {
    UPRIGHT(Side.WIDTH, Side.HEIGHT, Side.DEPTH),
    UPRIGHT_SIDEWAYS(Side.DEPTH, Side.HEIGHT, Side.WIDTH),
    ON_ITS_SIDE(Side.HEIGHT, Side.WIDTH, Side.DEPTH),
    ON_ITS_SIDE_SIDEWAYS(Side.DEPTH, Side.WIDTH, Side.HEIGHT),
    ON_ITS_BACK(Side.WIDTH, Side.DEPTH, Side.HEIGHT),
    ON_ITS_BACK_SIDEWAYS(Side.HEIGHT, Side.DEPTH, Side.WIDTH);

    val isUpright: Boolean get() = up == Side.HEIGHT
}

fun Dimensions.side(side: Side): Float = when (side) {
    Side.WIDTH -> widthCm
    Side.DEPTH -> depthCm
    Side.HEIGHT -> heightCm
}

/**
 * The answer to "does it go through the door?".
 *
 * @property fits true when at least one way of carrying it clears the opening.
 * @property carry the way to carry it: the best one that fits, or the closest miss.
 * @property marginCm the smallest gap left around it (on the tighter of the two sides), or,
 *   when it does not fit, minus how much it is too big.
 */
data class DoorVerdict(val fits: Boolean, val carry: Carry, val marginCm: Float) {
    /** Fits, with less than [TIGHT_CM] to spare: hands and door frame will touch. */
    val isTight: Boolean get() = fits && marginCm < TIGHT_CM

    companion object {
        const val TIGHT_CM = 2f
    }
}

/**
 * Straight through, without tilting it diagonally: the object passes when the side across is no
 * wider than the opening and the side up is no taller. Upright carries win over lying ones, then
 * the larger margin. When nothing passes, the verdict names the closest miss.
 */
fun checkDoor(obj: Dimensions, door: Door): DoorVerdict {
    fun gaps(c: Carry) = (door.widthCm - obj.side(c.across)) to (door.heightCm - obj.side(c.up))
    fun margin(c: Carry) = gaps(c).let { (a, b) -> minOf(a, b) }

    val fitting = Carry.entries.filter { margin(it) >= 0f }
    if (fitting.isNotEmpty()) {
        val best = fitting.sortedWith(
            compareByDescending<Carry> { it.isUpright }
                .thenByDescending { margin(it) }
                .thenBy { it.ordinal },
        ).first()
        return DoorVerdict(fits = true, carry = best, marginCm = margin(best))
    }
    val closest = Carry.entries.sortedWith(compareByDescending<Carry> { margin(it) }.thenBy { it.ordinal }).first()
    return DoorVerdict(fits = false, carry = closest, marginCm = margin(closest))
}
