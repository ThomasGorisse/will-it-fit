package com.gorisse.thomas.willitfit.fit

import kotlin.math.sqrt

/**
 * Where an AR tape measurement goes once it is taken: one side of the object, or one side of the
 * door. The tape itself only knows a length in centimetres.
 */
enum class TapeTarget { WIDTH, DEPTH, HEIGHT, DOOR_WIDTH, DOOR_HEIGHT;

    companion object {
        val OBJECT = listOf(WIDTH, DEPTH, HEIGHT)
        val DOOR = listOf(DOOR_WIDTH, DOOR_HEIGHT)
    }
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

/** Puts a measured length into [dims] as the side [target] names. Door targets leave it as is. */
fun Dimensions.withTape(target: TapeTarget, cm: Float): Dimensions = when (target) {
    TapeTarget.WIDTH -> withWidth(cm)
    TapeTarget.DEPTH -> withDepth(cm)
    TapeTarget.HEIGHT -> withHeight(cm)
    TapeTarget.DOOR_WIDTH, TapeTarget.DOOR_HEIGHT -> this
}

/** Puts a measured length into [door] as the side [target] names. Object targets leave it as is. */
fun Door.withTape(target: TapeTarget, cm: Float): Door = when (target) {
    TapeTarget.DOOR_WIDTH -> copy(widthCm = cm)
    TapeTarget.DOOR_HEIGHT -> copy(heightCm = cm)
    else -> this
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
