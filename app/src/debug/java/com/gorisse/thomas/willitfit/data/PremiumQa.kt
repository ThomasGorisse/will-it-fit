package com.gorisse.thomas.willitfit.data

import java.util.Locale
import java.text.NumberFormat

/** Debug-source-only prices make the real offer reproducible without a store connection. */
fun samplePremiumPlans(locale: Locale): List<PremiumPlanUi> {
    val usd = locale.language == "en" && locale.country == "US"
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    fun price(micros: Long): String = formatter.format(java.math.BigDecimal.valueOf(micros, 6)) +
        if (usd) " $" else " €"
    return listOf(
        PremiumPlanUi("yearly", price(69_890_000), "P1Y", true,
            formatter.format(yearlyPricePerWeek(69_890_000)) + if (usd) " $" else " €"),
        PremiumPlanUi("monthly", price(if (usd) 19_890_000 else 22_890_000), "P1M", false),
        PremiumPlanUi("weekly", price(if (usd) 7_890_000 else 9_390_000), "P1W", false),
    )
}
