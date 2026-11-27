package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.TapeSet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

/** Loading never opens an offer by itself; Play's answer replaces it when it arrives. */
enum class PremiumState { FREE, PURCHASED, GRANDFATHERED, UNKNOWN }

/** Only the space checks may be read through a link without an entitlement. */
enum class PremiumFeature { DOOR, HALL, BOOT, LIFT, TAPE, MY_HOME, ROOM, SHARE, SETTINGS }

/**
 * Free versions were the only ones in production until this date (UTC); premium ships after it.
 * Data written by a free version always counts. Without data, an install from before this date
 * that was updated since is an early user; a later install that only met an update before its
 * first opening is not.
 */
const val PREMIUM_CUTOFF_MS = 1_795_910_400_000L // 2026-11-29T00:00:00Z, the day after premium reaches production

/** The decision is made before this version writes any data, then kept for good. */
fun grandfatherDecision(firstInstallTime: Long, lastUpdateTime: Long, answers: Int,
    hasThings: Boolean, reviewRequests: Int, hasRecentSizes: Boolean, previousDecision: Boolean? = null): Boolean =
    previousDecision ?: (answers > 0 || hasThings || reviewRequests > 0 || hasRecentSizes ||
        firstInstallTime < PREMIUM_CUTOFF_MS && firstInstallTime < lastUpdateTime - 60_000L)

/** A shared check is a promise to show that check; editing and other checks need their own access. */
fun canUse(feature: PremiumFeature, state: PremiumState, openedFromSharedLink: Boolean = false): Boolean =
    state == PremiumState.PURCHASED || state == PremiumState.GRANDFATHERED ||
        feature in setOf(PremiumFeature.DOOR, PremiumFeature.ROOM, PremiumFeature.SHARE, PremiumFeature.SETTINGS) ||
        openedFromSharedLink && feature in setOf(PremiumFeature.HALL, PremiumFeature.BOOT, PremiumFeature.LIFT)

/** The listing promises a ruler; only spaces with premium checks keep a premium tape. */
fun canUseTape(set: TapeSet, state: PremiumState): Boolean =
    set == TapeSet.OBJECT || set == TapeSet.DOOR || canUse(PremiumFeature.TAPE, state)

/** Play supplies micros, so the comparison never parses a translated price label. A year is 52 weeks. */
fun yearlyPricePerWeek(priceAmountMicros: Long): BigDecimal =
    BigDecimal.valueOf(priceAmountMicros).divide(BigDecimal.valueOf(52_000_000L), 6, RoundingMode.HALF_UP)

/** Two local calendar days after purchase, at 18:00; a late acknowledgement is reminded now. */
fun trialReminderAt(purchaseTime: Long, now: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(purchaseTime).atZone(zone).toLocalDate().plusDays(2).atTime(18, 0)
        .atZone(zone).toInstant().toEpochMilli().coerceAtLeast(now)
