package com.gorisse.thomas.willitfit.fit

import kotlin.math.sqrt

/** The field receiving a length from the AR tape. */
enum class TapeTarget {
    WIDTH, DEPTH, HEIGHT, DOOR_WIDTH, DOOR_HEIGHT,
    BOOT_OPENING_WIDTH, BOOT_OPENING_HEIGHT, BOOT_LENGTH, BOOT_WIDTH, BOOT_HEIGHT,
    HALL_WIDTH, HALL_TURN_WIDTH, HALL_CEILING,
    CABIN_WIDTH, CABIN_DEPTH, CABIN_HEIGHT;

    companion object {
        val OBJECT = listOf(WIDTH, DEPTH, HEIGHT)
        val DOOR = listOf(DOOR_WIDTH, DOOR_HEIGHT)
        val BOOT = listOf(BOOT_OPENING_WIDTH, BOOT_OPENING_HEIGHT, BOOT_LENGTH, BOOT_WIDTH, BOOT_HEIGHT)
        val HALL = listOf(HALL_WIDTH, HALL_TURN_WIDTH, HALL_CEILING)
        val LIFT = listOf(DOOR_WIDTH, DOOR_HEIGHT, CABIN_WIDTH, CABIN_DEPTH, CABIN_HEIGHT)
    }
}

/** Saved with the route so rotation keeps the tape's destination. */
enum class TapeSet(val targets: List<TapeTarget>) {
    OBJECT(TapeTarget.OBJECT), DOOR(TapeTarget.DOOR), BOOT(TapeTarget.BOOT),
    HALL(TapeTarget.HALL), LIFT(TapeTarget.LIFT),
}

/** A point in the AR world, in metres. */
data class TapePoint(val x: Float, val y: Float, val z: Float)

/** The straight distance from [a] to [b], in centimetres. */
fun tapeCm(a: TapePoint, b: TapePoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val dz = b.z - a.z
    return sqrt(dx * dx + dy * dy + dz * dz) * 100f
}

/**
 * A length the tape can hand on: at least [Dimensions.MIN_CM], at most [Dimensions.MAX_CM],
 * rounded to the millimetre. Below 1 cm the two points are the same point (a double tap).
 */
fun usableTapeCm(cm: Float): Float? =
    cm.takeIf { it.isFinite() && it in Dimensions.MIN_CM..Dimensions.MAX_CM }
        ?.let { Math.round(it * 10f) / 10f }

/** Updates only the named object side. */
fun Dimensions.withTape(target: TapeTarget, cm: Float): Dimensions = when (target) {
    TapeTarget.WIDTH -> withWidth(cm)
    TapeTarget.DEPTH -> withDepth(cm)
    TapeTarget.HEIGHT -> withHeight(cm)
    else -> this
}

/** Updates only the named door side; ignores lengths outside the field range. */
fun Door.withTape(target: TapeTarget, cm: Float): Door {
    if (usableTapeCm(cm) == null) return this
    return when (target) {
        TapeTarget.DOOR_WIDTH -> copy(widthCm = cm)
        TapeTarget.DOOR_HEIGHT -> copy(heightCm = cm)
        else -> this
    }
}

/** Updates only the named boot side. */
fun Boot.withTape(target: TapeTarget, cm: Float): Boot {
    if (usableTapeCm(cm) == null) return this
    return when (target) {
        TapeTarget.BOOT_OPENING_WIDTH -> copy(openingWidthCm = cm)
        TapeTarget.BOOT_OPENING_HEIGHT -> copy(openingHeightCm = cm)
        TapeTarget.BOOT_LENGTH -> copy(lengthCm = cm)
        TapeTarget.BOOT_WIDTH -> copy(widthCm = cm)
        TapeTarget.BOOT_HEIGHT -> copy(heightCm = cm)
        else -> this
    }
}

/** Updates only the named hallway side. */
fun Hall.withTape(target: TapeTarget, cm: Float): Hall {
    if (usableTapeCm(cm) == null) return this
    return when (target) {
        TapeTarget.HALL_WIDTH -> copy(widthCm = cm)
        TapeTarget.HALL_TURN_WIDTH -> copy(turnWidthCm = cm)
        TapeTarget.HALL_CEILING -> copy(ceilingCm = cm)
        else -> this
    }
}

/** Door targets describe the lift opening too. */
fun Lift.withTape(target: TapeTarget, cm: Float): Lift {
    if (usableTapeCm(cm) == null) return this
    return when (target) {
        TapeTarget.DOOR_WIDTH -> copy(doorWidthCm = cm)
        TapeTarget.DOOR_HEIGHT -> copy(doorHeightCm = cm)
        TapeTarget.CABIN_WIDTH -> copy(cabinWidthCm = cm)
        TapeTarget.CABIN_DEPTH -> copy(cabinDepthCm = cm)
        TapeTarget.CABIN_HEIGHT -> copy(cabinHeightCm = cm)
        else -> this
    }
}

/**
 * Where [p] lands on a [width] × [height] px view, through ARCore's column-major [viewProjection]
 * matrix: (x, y) in px from the top left, or null behind the camera.
 */
fun projectToView(viewProjection: FloatArray, p: TapePoint, width: Float, height: Float): Pair<Float, Float>? {
    val m = viewProjection
    val cx = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
    val cy = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
    val cw = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
    if (cw <= 1e-4f) return null
    return ((cx / cw + 1f) / 2f * width) to ((1f - cy / cw) / 2f * height)
}
