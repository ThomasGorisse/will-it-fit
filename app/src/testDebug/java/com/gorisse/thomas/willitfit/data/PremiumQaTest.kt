package com.gorisse.thomas.willitfit.data

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Guard the offline offer against accidentally changing the real-market QA fixtures. */
class PremiumQaTest {
    @Test fun europeanOfferHasOnlyTheYearlyTrial() {
        val plans = samplePremiumPlans(Locale.FRANCE)
        assertEquals(listOf("yearly", "monthly", "weekly"), plans.map { it.id })
        assertEquals(listOf("P1Y", "P1M", "P1W"), plans.map { it.billingPeriod })
        assertEquals(listOf("69,89 €", "22,89 €", "9,39 €"), plans.map { it.formattedPrice })
        assertEquals(listOf(true, false, false), plans.map { it.trial })
        assertEquals("1,34 €", plans.first().perWeekPrice)
        assertTrue(plans.drop(1).all { it.perWeekPrice == null })
    }

    @Test fun onlyEnglishUnitedStatesUsesTheUsdPrices() {
        assertEquals(listOf("69.89 $", "19.89 $", "7.89 $"),
            samplePremiumPlans(Locale.US).map { it.formattedPrice })
        assertEquals("1.34 $", samplePremiumPlans(Locale.US).first().perWeekPrice)
        listOf(Locale.GERMANY, Locale.forLanguageTag("es-ES"), Locale.ITALY,
            Locale.forLanguageTag("pt-BR"), Locale.UK).forEach { locale ->
            assertTrue(samplePremiumPlans(locale).all { it.formattedPrice.endsWith(" €") })
        }
    }
}
