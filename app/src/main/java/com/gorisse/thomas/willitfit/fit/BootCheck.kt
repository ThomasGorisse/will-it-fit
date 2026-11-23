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
) {
    val isValid: Boolean
        get() = listOf(openingWidthCm, openingHeightCm, lengthCm, widthCm, heightCm)
            .all { it.isFinite() && it in Dimensions.MIN_CM..Dimensions.MAX_CM }

    companion object {
        val ESTATE = Boot(108f, 80f, 103f, 104f, 68f)
    }
}

enum class BootLimit { OPENING_WIDTH, OPENING_HEIGHT, LENGTH, WIDTH, HEIGHT }

data class BootVerdict(val fits: Boolean, val carry: Carry, val marginCm: Float, val limit: BootLimit) {
    val isTight: Boolean get() = fits && marginCm < DoorVerdict.TIGHT_CM
}

/** Straight in along the floor: the largest minimum gap wins, then the declared carry order. */
fun checkBoot(obj: Dimensions, boot: Boot): BootVerdict {
    fun verdict(carry: Carry): BootVerdict {
        val across = obj.side(carry.across)
        val up = obj.side(carry.up)
        val gaps = listOf(
            BootLimit.OPENING_WIDTH to boot.openingWidthCm - across,
            BootLimit.OPENING_HEIGHT to boot.openingHeightCm - up,
            BootLimit.LENGTH to boot.lengthCm - obj.side(carry.along),
            BootLimit.WIDTH to boot.widthCm - across,
            BootLimit.HEIGHT to boot.heightCm - up,
        )
        val (limit, margin) = gaps.minBy { it.second }
        return BootVerdict(margin >= 0f, carry, margin, limit)
    }
    return Carry.entries.map(::verdict).sortedWith(
        compareByDescending<BootVerdict> { it.marginCm }.thenBy { it.carry.ordinal },
    ).first()
}
