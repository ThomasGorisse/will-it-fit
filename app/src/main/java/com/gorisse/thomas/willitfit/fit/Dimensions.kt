package com.gorisse.thomas.willitfit.fit

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a set of dimensions came from. A two-point tape and a room scan will add their own. */
enum class DimensionSource {
    /** Typed by the user. */
    TYPED,

    /** A typical object, picked from the presets. */
    PRESET,
    // MEASURED: reserved for the AR tape measure (two points on the floor). Not built in v3.0.
}

/**
 * The one "dimensions" class of the app: width (left to right as you face the object), depth
 * (front to back) and height, in centimetres. Every screen reads and writes this type.
 */
data class Dimensions(
    val widthCm: Float,
    val depthCm: Float,
    val heightCm: Float,
    val source: DimensionSource = DimensionSource.TYPED,
    val presetId: String? = null,
) {
    val isValid: Boolean
        get() = listOf(widthCm, depthCm, heightCm).all { it.isFinite() && it in MIN_CM..MAX_CM }

    /** The three sides, smallest first. */
    fun sortedCm(): List<Float> = listOf(widthCm, depthCm, heightCm).sorted()

    fun withWidth(cm: Float) = copy(widthCm = cm, source = DimensionSource.TYPED)
    fun withDepth(cm: Float) = copy(depthCm = cm, source = DimensionSource.TYPED)
    fun withHeight(cm: Float) = copy(heightCm = cm, source = DimensionSource.TYPED)

    companion object {
        const val MIN_CM = 1f
        const val MAX_CM = 1000f
    }
}

enum class LengthUnit(val symbol: String) {
    CM("cm"),
    INCH("in");

    fun toCm(value: Float): Float = if (this == CM) value else value * CM_PER_INCH
    fun fromCm(cm: Float): Float = if (this == CM) cm else cm / CM_PER_INCH

    companion object {
        const val CM_PER_INCH = 2.54f

        /** Inches where the locale lives with them (US, Liberia, Myanmar), centimetres elsewhere. */
        fun defaultFor(locale: Locale): LengthUnit =
            if (locale.country in setOf("US", "LR", "MM")) INCH else CM
    }
}

/**
 * Reads a length typed in [unit] and returns it in centimetres, or null when it is not a
 * usable positive number. Accepts a comma or a point as decimal separator, spaces, and a
 * trailing unit ("220 cm", "86,5in", "34\"").
 */
fun parseLength(text: String, unit: LengthUnit): Float? {
    val cleaned = text.trim().lowercase(Locale.ROOT)
        .removeSuffix("cm").removeSuffix("in").removeSuffix("\"")
        .replace(" ", "").replace(' '.toString(), "")
        .replace(',', '.')
    if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
    if (!cleaned.all { it.isDigit() || it == '.' }) return null
    val value = cleaned.toFloatOrNull() ?: return null
    if (value <= 0f || !value.isFinite()) return null
    return unit.toCm(value)
}

/**
 * The number alone, as it goes back into a text field: whole centimetres, inches to one
 * decimal ("34.5", "35").
 */
fun formatNumber(cm: Float, unit: LengthUnit): String = when (unit) {
    LengthUnit.CM -> cm.roundToInt().toString()
    LengthUnit.INCH -> {
        val tenths = (unit.fromCm(cm) * 10f).roundToInt()
        if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${abs(tenths % 10)}"
    }
}

/**
 * A length a field can take: [parseLength], within [Dimensions.MIN_CM]..[Dimensions.MAX_CM].
 * "0.1" or "5000" parse, but no object or door has that size: the field shows them as errors.
 */
fun parseUsableLength(text: String, unit: LengthUnit): Float? =
    parseLength(text, unit)?.takeIf { it in Dimensions.MIN_CM..Dimensions.MAX_CM }

/** "220 cm", "86.6 in". */
fun formatLength(cm: Float, unit: LengthUnit): String = "${formatNumber(cm, unit)} ${unit.symbol}"

/**
 * A door margin, without its sign: like [formatLength], except that a gap under one centimetre
 * keeps its tenth. A 78.4 cm object and a 78 cm door read "too big by 0.4 cm", not "by 0 cm".
 */
fun formatMargin(cm: Float, unit: LengthUnit): String {
    val tenths = (abs(cm) * 10f).roundToInt()
    return if (unit == LengthUnit.CM && tenths in 1..9) "0.$tenths ${unit.symbol}" else formatLength(abs(cm), unit)
}

/** "220 × 95 × 85 cm" — width × depth × height. */
fun formatDimensions(d: Dimensions, unit: LengthUnit): String =
    "${formatNumber(d.widthCm, unit)} × ${formatNumber(d.depthCm, unit)} × ${formatNumber(d.heightCm, unit)} ${unit.symbol}"
