package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.TapeSet
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PremiumPolicyTest {
    @Test fun earlyInstallOrAnyPriorDataQualifies() {
        assertFalse(grandfatherDecision(100_000, 160_000, 0, false, 0, false))
        assertTrue(grandfatherDecision(99_999, 160_000, 0, false, 0, false))
        assertTrue(grandfatherDecision(1, 1, 1, false, 0, false))
        assertTrue(grandfatherDecision(1, 1, 0, true, 0, false))
        assertTrue(grandfatherDecision(1, 1, 0, false, 1, false))
        assertTrue(grandfatherDecision(1, 1, 0, false, 0, true))
        assertFalse(grandfatherDecision(1, 1, 0, false, 0, false))
        // After the cutoff, an update before the first opening is not an early user; data still is.
        assertFalse(grandfatherDecision(PREMIUM_CUTOFF_MS + 1, PREMIUM_CUTOFF_MS + 999_999, 0, false, 0, false))
        assertTrue(grandfatherDecision(PREMIUM_CUTOFF_MS + 1, PREMIUM_CUTOFF_MS + 1, 3, false, 0, false))
    }

    @Test fun aSavedDecisionWinsOverLaterUsageOrInstallChanges() {
        assertFalse(grandfatherDecision(1, 999_999, 10, true, 4, true, previousDecision = false))
        assertTrue(grandfatherDecision(1, 1, 0, false, 0, false, previousDecision = true))
    }

    @Test fun allFeaturesAndStatesHaveAnExplicitAccessRule() {
        PremiumFeature.entries.forEach { feature ->
            assertTrue(canUse(feature, PremiumState.PURCHASED))
            assertTrue(canUse(feature, PremiumState.GRANDFATHERED))
            val always = feature in setOf(PremiumFeature.DOOR, PremiumFeature.ROOM, PremiumFeature.SHARE, PremiumFeature.SETTINGS)
            listOf(PremiumState.FREE, PremiumState.UNKNOWN).forEach { state ->
                assertEquals(always, canUse(feature, state))
                assertEquals(always || feature in setOf(PremiumFeature.HALL, PremiumFeature.BOOT, PremiumFeature.LIFT),
                    canUse(feature, state, openedFromSharedLink = true))
            }
        }
    }

    @Test fun everyTapeSetAndStateHasAnExplicitAccessRule() {
        TapeSet.entries.forEach { set ->
            PremiumState.entries.forEach { state ->
                val expected = set in setOf(TapeSet.OBJECT, TapeSet.DOOR) ||
                    state in setOf(PremiumState.PURCHASED, PremiumState.GRANDFATHERED)
                assertEquals("$set / $state", expected, canUseTape(set, state))
            }
        }
    }

    @Test fun weeklyComparisonUsesMicrosWithoutDroppingCents() {
        assertEquals(BigDecimal("1.000000"), yearlyPricePerWeek(52_000_000))
        assertEquals(BigDecimal("0.961346"), yearlyPricePerWeek(49_990_000))
        assertEquals(BigDecimal("0.000000"), yearlyPricePerWeek(0))
    }

    @Test fun reminderUsesLocalEveningAcrossDaylightSavingAndClampsLateAcknowledgement() {
        val purchase = Instant.parse("2026-03-27T23:30:00Z").toEpochMilli()
        val expected = Instant.parse("2026-03-30T16:00:00Z").toEpochMilli()
        assertEquals(expected, trialReminderAt(purchase, purchase, ZoneId.of("Europe/Paris")))
        assertEquals(expected + 1, trialReminderAt(purchase, expected + 1, ZoneId.of("Europe/Paris")))
        assertEquals(Instant.parse("2026-03-29T18:00:00Z").toEpochMilli(),
            trialReminderAt(purchase, purchase, ZoneId.of("UTC")))
    }
}
