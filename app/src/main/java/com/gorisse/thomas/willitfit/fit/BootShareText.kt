package com.gorisse.thomas.willitfit.fit

import java.util.Locale
import java.text.DecimalFormatSymbols

/** Templates are supplied by Android resources; this formatter has no Android dependency. */
data class BootShareLabels(
    val fits: String,
    val misses: String,
    val diagonal: String,
    val seatsDown: String,
    val seatsDownDiagonal: String,
    val spare: String,
    val over: String,
    val signature: String,
)

data class BootShareText(val title: String, val clearance: String, val signature: String) {
    val caption: String get() = "$title · $clearance · $signature"
}

fun bootShareText(
    car: String, verdict: BootVerdict, unit: LengthUnit, labels: BootShareLabels, locale: Locale,
): BootShareText {
    val template = when {
        !verdict.fits -> labels.misses
        verdict.rearSeatsDown && verdict.diagonal -> labels.seatsDownDiagonal
        verdict.rearSeatsDown -> labels.seatsDown
        verdict.diagonal -> labels.diagonal
        else -> labels.fits
    }
    // Match the precision of the on-screen margin; unit conversion and rounding live in one place.
    val margin = formatMargin(verdict.marginCm, unit)
        .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    return BootShareText(
        String.format(locale, template, car),
        String.format(locale, if (verdict.fits) labels.spare else labels.over, margin),
        labels.signature,
    )
}
