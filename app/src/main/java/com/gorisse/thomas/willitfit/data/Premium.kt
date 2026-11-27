package com.gorisse.thomas.willitfit.data

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A plan returned by Play, with its own eligible offer token and recurring price. */
data class PremiumPlan(val id: String, val details: ProductDetails, val offerToken: String,
    val price: ProductDetails.PricingPhase, val trial: Boolean)

/** Keep Play's price verbatim and compute only the explicitly labelled yearly comparison. */
fun PremiumPlan.toUi(locale: Locale): PremiumPlanUi {
    val priceCurrency = Currency.getInstance(price.priceCurrencyCode)
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        currency = priceCurrency
        maximumFractionDigits = priceCurrency.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits = maximumFractionDigits
    }
    return PremiumPlanUi(id, price.formattedPrice, price.billingPeriod, trial,
        if (price.billingPeriod == "P1Y") formatter.format(yearlyPricePerWeek(price.priceAmountMicros)) else null)
}

/**
 * Play owns purchases and eligibility. This connection lives only while the activity is started;
 * every resume reads purchases again, including returns from payment or subscription settings.
 */
class Billing(context: Context, private val prefs: Prefs, grandfathered: Boolean,
    private val qaState: PremiumState? = null) : DefaultLifecycleObserver {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val early = grandfathered.also { if (it && qaState == null) prefs.hadPremium = true }
    private val _state = MutableStateFlow(qaState ?: if (early) PremiumState.GRANDFATHERED else PremiumState.UNKNOWN)
    val state = _state.asStateFlow()
    private val _plans = MutableStateFlow<List<PremiumPlan>>(emptyList())
    val plans = _plans.asStateFlow()
    private val _available = MutableStateFlow(false)
    val available = _available.asStateFlow()
    private var client: BillingClient? = null
    private var started = false
    private var connecting = false
    private var retryMs = 1_000L
    private val acknowledging = mutableSetOf<String>()
    private val reconnect = Runnable { connect() }

    override fun onStart(owner: LifecycleOwner) { started = true; connect() }
    override fun onResume(owner: LifecycleOwner) { if (client?.isReady == true) refresh() else connect() }
    override fun onStop(owner: LifecycleOwner) {
        started = false
        handler.removeCallbacksAndMessages(null)
        runCatching { client?.endConnection() }; client = null; connecting = false
        acknowledging.clear()
    }

    /** Failed stores leave the free app usable, and try again while this screen is still alive. */
    private fun failed() {
        _available.value = false
        _plans.value = emptyList()
        if (_state.value == PremiumState.UNKNOWN) setState(PremiumState.FREE)
        if (started) {
            handler.removeCallbacks(reconnect)
            handler.postDelayed(reconnect, retryMs)
            retryMs = (retryMs * 2).coerceAtMost(60_000L)
        }
    }

    /** Early access and the debug override survive every Play response. */
    private fun setState(value: PremiumState) {
        _state.value = qaState ?: if (early) PremiumState.GRANDFATHERED else value
        if (_state.value == PremiumState.PURCHASED || _state.value == PremiumState.GRANDFATHERED) prefs.hadPremium = true
    }

    /** A bounded retry delay avoids polling a disconnected store. */
    private fun connect() {
        if (!started || connecting) return
        if (client?.isReady == true) { refresh(); return }
        runCatching { client?.endConnection() }
        val store = runCatching { BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                handler.post {
                    if (!started) return@post
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchases?.filter { PRODUCT_ID in it.products }?.forEach { accept(it) }
                        if (client?.isReady == true) refresh()
                    } else {
                        prefs.trialLaunchAt = 0
                        if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) failed()
                    }
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build() }.getOrElse { failed(); return }
        client = store
        connecting = true
        runCatching {
            store.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    handler.post {
                        if (client !== store || !started) return@post
                        connecting = false
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            retryMs = 1_000L; refresh()
                        } else failed()
                    }
                }
                override fun onBillingServiceDisconnected() {
                    handler.post { if (client === store) { connecting = false; failed() } }
                }
            })
            handler.postDelayed({ if (client === store && connecting) { connecting = false; failed() } }, 15_000L)
        }.onFailure { connecting = false; failed() }
    }

    /** The result is authoritative only on success; a busy store must not revoke a purchase. */
    private fun refresh() {
        val store = client ?: return
        runCatching {
            store.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
                handler.post {
                    if (client !== store || !started) return@post
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) { failed(); return@post }
                    val owned = purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    setState(if (owned.isEmpty()) PremiumState.FREE else PremiumState.PURCHASED)
                    if (owned.isEmpty()) {
                        TrialReminder.cancel(context)
                        prefs.trialToken = null
                    } else owned.forEach { accept(it) }
                }
            }
            val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build()
            store.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, products ->
                handler.post {
                    if (client !== store || !started) return@post
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) { failed(); return@post }
                    val details = products.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                    _plans.value = details?.let { plansFrom(it) } ?: emptyList()
                    _available.value = _plans.value.isNotEmpty()
                }
            }
        }.onFailure { failed() }
    }

    /** Pending payments unlock nothing. Acknowledgement is retried on the next purchase read. */
    private fun accept(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        setState(PremiumState.PURCHASED)
        if (prefs.trialLaunchAt > 0 && purchase.purchaseTime >= prefs.trialLaunchAt && prefs.trialPrice != null) {
            prefs.trialToken = purchase.purchaseToken
            prefs.trialLaunchAt = 0
        }
        if (purchase.isAcknowledged) { remind(purchase); return }
        if (!acknowledging.add(purchase.purchaseToken)) return
        val store = client ?: return
        runCatching {
            store.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { result ->
                handler.post {
                    acknowledging.remove(purchase.purchaseToken)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) remind(purchase)
                }
            }
        }.onFailure { acknowledging.remove(purchase.purchaseToken) }
    }

    /** Only a trial selected on this phone has a known price and start time. */
    private fun remind(purchase: Purchase) {
        if (prefs.trialToken == purchase.purchaseToken) prefs.trialPrice?.let {
            TrialReminder.schedule(context, purchase.purchaseToken, purchase.purchaseTime, it)
        }
    }

    /** The button passes Play's exact offer token; no locally invented trial can be purchased. */
    fun purchase(activity: Activity, plan: PremiumPlan) {
        val store = client?.takeIf { it.isReady } ?: run { failed(); return }
        prefs.trialLaunchAt = if (plan.trial) System.currentTimeMillis() else 0
        prefs.trialPrice = plan.price.formattedPrice.takeIf { plan.trial }
        val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(plan.details)
            .setOfferToken(plan.offerToken).build()
        runCatching {
            store.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
        }.onSuccess { if (it.responseCode != BillingClient.BillingResponseCode.OK) { prefs.trialLaunchAt = 0; failed() } }
            .onFailure { prefs.trialLaunchAt = 0; failed() }
    }

    companion object {
        const val PRODUCT_ID = "wif_premium"

        /** Ignore other offers: only the specified three-day yearly offer may advertise a trial. */
        fun plansFrom(details: ProductDetails): List<PremiumPlan> = listOf("yearly", "monthly", "weekly").mapNotNull { id ->
            val period = when (id) { "yearly" -> "P1Y"; "monthly" -> "P1M"; else -> "P1W" }
            val offers = details.subscriptionOfferDetails.orEmpty().filter { it.basePlanId == id &&
                it.pricingPhases.pricingPhaseList.lastOrNull()?.billingPeriod == period }
            val offer = offers.firstOrNull { id == "yearly" && it.offerId == "yearly-trial3d" &&
                it.pricingPhases.pricingPhaseList.firstOrNull()?.let { phase -> phase.priceAmountMicros == 0L && phase.billingPeriod == "P3D" } == true }
                ?: offers.firstOrNull { it.offerId == null } ?: return@mapNotNull null
            val price = offer.pricingPhases.pricingPhaseList.lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
                ?: return@mapNotNull null
            PremiumPlan(id, details, offer.offerToken, price, offer.offerId == "yearly-trial3d")
        }
    }
}
