package com.gorisse.thomas.willitfit.data

/**
 * The offer's display contract contains no Play handles: previews and offline QA render the
 * same cards, while only Billing retains the product details and eligible purchase token.
 * Prices are already formatted; the UI must never reconstruct Play's recurring price.
 */
data class PremiumPlanUi(
    val id: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val trial: Boolean,
    val perWeekPrice: String? = null,
)
