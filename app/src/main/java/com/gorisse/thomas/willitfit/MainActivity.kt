package com.gorisse.thomas.willitfit

import android.Manifest
import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.ar.core.ArCoreApk
import com.gorisse.thomas.willitfit.data.Billing
import com.gorisse.thomas.willitfit.data.PremiumFeature
import com.gorisse.thomas.willitfit.data.samplePremiumPlans
import com.gorisse.thomas.willitfit.data.toUi
import com.gorisse.thomas.willitfit.data.PremiumState
import com.gorisse.thomas.willitfit.data.TrialReminder
import com.gorisse.thomas.willitfit.data.canUse
import com.gorisse.thomas.willitfit.ui.OnboardingScreen
import com.gorisse.thomas.willitfit.ui.PaywallScreen
import com.gorisse.thomas.willitfit.data.News
import com.gorisse.thomas.willitfit.data.publishSizeShortcuts
import com.gorisse.thomas.willitfit.data.shortcutDimensions
import com.gorisse.thomas.willitfit.data.sizeShortcutId
import com.gorisse.thomas.willitfit.data.RecentSizes
import com.gorisse.thomas.willitfit.data.Prefs
import com.gorisse.thomas.willitfit.data.ReviewPolicy
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.SharedCheck
import com.gorisse.thomas.willitfit.fit.Space
import com.gorisse.thomas.willitfit.fit.TapeSet
import com.gorisse.thomas.willitfit.fit.withTape
import com.gorisse.thomas.willitfit.fit.dimensionsIn
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.sizesFromText
import com.gorisse.thomas.willitfit.ui.ArScreen
import com.gorisse.thomas.willitfit.ui.DoorScreen
import com.gorisse.thomas.willitfit.ui.HomeScreen
import com.gorisse.thomas.willitfit.ui.MeasureScreen
import com.gorisse.thomas.willitfit.ui.HomeTyped
import com.gorisse.thomas.willitfit.ui.MyHomeScreen
import com.gorisse.thomas.willitfit.data.MyThings
import com.gorisse.thomas.willitfit.fit.Home
import com.gorisse.thomas.willitfit.ui.ResultScreen
import com.gorisse.thomas.willitfit.ui.SettingsScreen
import com.gorisse.thomas.willitfit.ui.WillItFitTheme
import java.io.File

/** The screens. Home is the root; the others return to where they came from. */
private enum class Route { HOME, AR, RESULT, DOOR, SETTINGS, MEASURE, MY_HOME, START, ONBOARD_OBJECT, ONBOARD_SPACES }

class MainActivity : ComponentActivity() {
    private lateinit var billing: Billing
    private var referrerPending by mutableStateOf(false)

    /** A rating flow is running: going home again meanwhile does not start a second one. */
    private var reviewInFlight = false

    /** A check from Play's install referrer: it arrives a moment after the first launch. */
    private var referredCheck by mutableStateOf<SharedCheck?>(null)

    /**
     * Light or dark changes while the app is open (by hand, or at sunset): Compose recolours
     * the screens and the room in place. Recreating the activity instead would tear down the 3D
     * engine and leave the room black until a new one has loaded the object again. The bars'
     * icons are chosen once by enableEdgeToEdge, so they are chosen again here.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(applicationContext)
        val qa = if (BuildConfig.DEBUG) intent.getStringExtra("qa_premium") else null
        if (BuildConfig.DEBUG && qa == "fresh" && savedInstanceState == null) { prefs.resetPremiumWelcome(); prefs.decideFreshForQa() }
        val installed = packageManager.getPackageInfo(packageName, 0)
        val early = prefs.decideGrandfathered(installed.firstInstallTime, installed.lastUpdateTime)
        val qaState = if (BuildConfig.DEBUG) when (qa) {
            "on" -> PremiumState.PURCHASED
            "off", "fresh" -> PremiumState.FREE
            else -> null
        } else null
        billing = Billing(applicationContext, prefs, early, qaState)
        // The sample offer is offline and cannot enter Billing, even with a ready Play store.
        val qaPlans = BuildConfig.DEBUG && intent.getStringExtra("qa_plans") == "sample"
        if (!qaPlans) lifecycle.addObserver(billing)
        // Text from a listing, shared or selected in another app. Read once: not again when the
        // activity is recreated.
        val incoming = if (savedInstanceState == null) intent.sharedText() else null
        val shortcut = if (savedInstanceState == null) intent.shortcutDimensions() else null
        // A check someone sent: its link opens the same check, once, before anything is read.
        val linked = if (savedInstanceState == null) intent.sharedCheck() else null
        linked?.let { prefs.takeCheck(it) }
        // A note from the app (News): it opens the screen of what it speaks of.
        val noted = if (savedInstanceState == null) intent.getStringExtra(News.EXTRA_OPEN) else null
        if (linked != null || shortcut != null || incoming != null || noted != null) prefs.onboardingDone = true
        when (noted) {
            News.OPEN_DOOR, News.OPEN_TAPE -> { prefs.liftSelected = false; prefs.carBoot = false; prefs.hallway = false }
            News.OPEN_BOOT -> { prefs.liftSelected = false; prefs.carBoot = true; prefs.hallway = false }
            News.OPEN_HALL -> { prefs.liftSelected = false; prefs.hallway = true }
            News.OPEN_LIFT -> prefs.liftSelected = true
        }
        if (linked == null && !prefs.referrerRead) {
            referrerPending = true
            readInstallReferrer(prefs)
            // A missing store never holds up the app. A late shared check still replaces welcome.
            Handler(Looper.getMainLooper()).postDelayed({ referrerPending = false }, 2_000)
        }
        if (shortcut != null) {
            prefs.dimensions = shortcut
            intent.sizeShortcutId()?.let { ShortcutManagerCompat.reportShortcutUsed(this, it) }
        }
        publishSizeShortcuts(this, prefs)
        setContent {
            WillItFitTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    val context = LocalContext.current
                    val arAvailable = remember {
                        runCatching { !ArCoreApk.getInstance().checkAvailability(context).isUnsupported }.getOrDefault(false)
                    }
                    var route by rememberSaveable {
                        mutableStateOf(
                            when {
                                noted == TrialReminder.OPEN_SETTINGS -> Route.SETTINGS
                                shortcut != null || linked?.space != null || noted == News.OPEN_DOOR -> Route.DOOR
                                linked != null || incoming != null || noted != null -> Route.HOME
                                !prefs.onboardingDone && !early -> Route.START
                                else -> Route.HOME
                            },
                        )
                    }
                    val premiumState by billing.state.collectAsStateWithLifecycle()
                    val plans by billing.plans.collectAsStateWithLifecycle()
                    val billingAvailable by billing.available.collectAsStateWithLifecycle()
                    val locale = LocalConfiguration.current.locales[0]
                    val offerPlans = if (BuildConfig.DEBUG && qaPlans) samplePremiumPlans(locale)
                        else plans.map { it.toUi(locale) }
                    val premium = canUse(PremiumFeature.MY_HOME, premiumState)
                    var paywall by rememberSaveable { mutableStateOf<PremiumFeature?>(null) }
                    var onboardingOffer by rememberSaveable { mutableStateOf(false) }
                    var readOnlyCheck by rememberSaveable { mutableStateOf(linked != null) }
                    var doorRevision by rememberSaveable { mutableStateOf(0) }
                    var selectedPreset by rememberSaveable { mutableStateOf(prefs.dimensions.presetId) }
                    var chosenSpaces by rememberSaveable { mutableStateOf(prefs.onboardingSpaces.ifEmpty { setOf("DOOR") }.toList()) }
                    LaunchedEffect(referrerPending) {
                        if (route == Route.START && !referrerPending && referredCheck == null) {
                            prefs.onboardingDone = true
                            route = Route.ONBOARD_OBJECT
                        }
                    }
                    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
                    var recentSizes by remember { mutableStateOf(prefs.recentSizes) }
                    var dims by remember { mutableStateOf(prefs.dimensions) }
                    var door by remember { mutableStateOf(prefs.door) }
                    var unit by remember { mutableStateOf(prefs.unit) }
                    LaunchedEffect(Unit) {
                        if (linked != null) Toast.makeText(this@MainActivity, R.string.link_opened, Toast.LENGTH_LONG).show()
                    }
                    // Installed from a shared link: the first launch opens the check it carried,
                    // unless the person already went somewhere.
                    LaunchedEffect(referredCheck) {
                        val check = referredCheck ?: return@LaunchedEffect
                        referredCheck = null
                        if (route !in setOf(Route.HOME, Route.START, Route.ONBOARD_OBJECT, Route.ONBOARD_SPACES)) return@LaunchedEffect
                        prefs.onboardingDone = true
                        paywall = null
                        readOnlyCheck = true
                        prefs.takeCheck(check)
                        dims = prefs.dimensions; door = prefs.door
                        route = if (check.space != null) Route.DOOR else Route.HOME
                        Toast.makeText(this@MainActivity, R.string.link_opened, Toast.LENGTH_LONG).show()
                    }
                    LaunchedEffect(Unit) {
                        if (incoming == null) return@LaunchedEffect
                        // Text from anywhere: a text it cannot read finds nothing, never closes the app.
                        val found = runCatching { sizesFromText(incoming, unit) }.getOrNull()
                        val message = if (found == null) {
                            getString(R.string.text_sizes_none)
                        } else {
                            dims = found.applyTo(dims)
                            if (dims.isValid) prefs.dimensions = dims
                            getString(R.string.text_sizes_found, formatDimensions(dims, unit))
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                    }
                    // An answer is an AR photo taken or a door verdict seen. Back home after the
                    // second one is a pause after something that worked: Play's rating sheet is
                    // asked for then, and once more much later (ReviewPolicy).
                    var lastRoute by rememberSaveable { mutableStateOf(route) }
                    var doorVerdictSeen by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(route) {
                        // Same route again: the activity was recreated (rotation), nothing happened.
                        if (route == lastRoute) return@LaunchedEffect
                        // A verdict seen counts on the way back, whether to home or to My home that opened it.
                        val backFromVerdict = (route == Route.HOME || route == Route.MY_HOME) && lastRoute == Route.DOOR && doorVerdictSeen
                        if (route == Route.RESULT || backFromVerdict) {
                            prefs.answers += 1
                            recentSizes = RecentSizes.add(recentSizes, dims)
                            prefs.recentSizes = recentSizes
                            publishSizeShortcuts(this@MainActivity, prefs)
                        }
                        if (route == Route.HOME && !reviewInFlight && ReviewPolicy.shouldAsk(prefs.review, System.currentTimeMillis())) {
                            reviewInFlight = true
                            askForRating(this@MainActivity) { ran ->
                                reviewInFlight = false
                                // Counted only once Play's flow ran to its end, not when asked.
                                if (ran) prefs.review = ReviewPolicy.requested(prefs.review, System.currentTimeMillis())
                            }
                        }
                        lastRoute = route
                    }
                    // News and tips, as Settings shows it: on, and let through by Android.
                    var newsOn by remember { mutableStateOf(News.allowed(this@MainActivity, prefs)) }
                    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        // Refused before: Android no longer asks, its settings are where it comes back.
                        if (!granted) openNotificationSettings()
                        newsOn = News.allowed(this@MainActivity, prefs)
                    }
                    LifecycleResumeEffect(Unit) {
                        newsOn = News.allowed(this@MainActivity, prefs)
                        onPauseOrDispose { }
                    }
                    // Which sizes the tape fills, kept across rotation; a tape note measures the door.
                    var measureSet by rememberSaveable { mutableStateOf(if (noted == News.OPEN_TAPE) TapeSet.DOOR else TapeSet.OBJECT) }
                    // A space opened from My home goes back there.
                    var doorFromMyHome by rememberSaveable { mutableStateOf(false) }
                    /** Space indices stay in Prefs so the check and tape agree. */
                    fun selectSpace(feature: PremiumFeature) {
                        prefs.liftSelected = feature == PremiumFeature.LIFT
                        prefs.hallway = feature == PremiumFeature.HALL
                        prefs.carBoot = feature == PremiumFeature.BOOT
                    }
                    /** A purchase continues the feature that opened the offer. */
                    fun openFeature(feature: PremiumFeature) {
                        when (feature) {
                            PremiumFeature.TAPE -> route = if (arAvailable) Route.MEASURE else Route.HOME
                            PremiumFeature.MY_HOME -> route = Route.MY_HOME
                            else -> {
                                selectSpace(feature); doorRevision++; readOnlyCheck = false
                                doorVerdictSeen = false; route = Route.DOOR
                            }
                        }
                    }
                    /** Only a deliberate premium action opens an offer after welcome. */
                    fun requireFeature(feature: PremiumFeature) {
                        if (canUse(feature, premiumState)) openFeature(feature) else {
                            onboardingOffer = false; paywall = feature
                        }
                    }
                    /** Closing or skipping welcome always gives the door answer first. */
                    fun finishWelcome() {
                        prefs.onboardingDone = true
                        selectSpace(PremiumFeature.DOOR)
                        readOnlyCheck = false; doorFromMyHome = false; doorRevision++
                        route = Route.DOOR
                    }
                    /** Back and the visible close button do exactly the same thing. */
                    fun closeOffer() {
                        paywall = null
                        if (onboardingOffer) { onboardingOffer = false; finishWelcome() }
                    }
                    LaunchedEffect(premiumState) {
                        if (premium && paywall != null) {
                            val feature = paywall ?: return@LaunchedEffect
                            paywall = null; onboardingOffer = false; prefs.onboardingDone = true
                            openFeature(feature)
                        }
                    }
                    LaunchedEffect(Unit) {
                        val feature = when (noted) {
                            News.OPEN_HALL -> PremiumFeature.HALL
                            News.OPEN_BOOT -> PremiumFeature.BOOT
                            News.OPEN_LIFT -> PremiumFeature.LIFT
                            News.OPEN_TAPE -> PremiumFeature.TAPE
                            News.OPEN_MY_HOME -> PremiumFeature.MY_HOME
                            else -> null
                        }
                        if (feature != null) requireFeature(feature)
                    }
                    BackHandler(enabled = route != Route.HOME && paywall == null) {
                        if (route == Route.ONBOARD_OBJECT || route == Route.ONBOARD_SPACES || route == Route.START) {
                            if (route == Route.ONBOARD_SPACES) route = Route.ONBOARD_OBJECT else finishWelcome()
                            return@BackHandler
                        }
                        route = when {
                            route == Route.RESULT -> Route.AR
                            route == Route.MEASURE && measureSet != TapeSet.OBJECT -> Route.DOOR
                            route == Route.DOOR && doorFromMyHome -> Route.MY_HOME
                            else -> Route.HOME
                        }
                    }
                    // No cross-fade: two scenes (or a scene and the AR camera) never run at once.
                    if (paywall == null) run {
                        when (route) {
                            Route.START -> Unit
                            Route.ONBOARD_OBJECT, Route.ONBOARD_SPACES -> OnboardingScreen(
                                step = if (route == Route.ONBOARD_OBJECT) 1 else 2,
                                selectedPreset = selectedPreset, spaces = chosenSpaces.toSet(),
                                onPreset = { preset ->
                                    selectedPreset = preset?.id
                                    dims = preset?.dimensionsIn(unit) ?: dims.copy(source = DimensionSource.TYPED, presetId = null)
                                    prefs.dimensions = dims
                                },
                                onSpaces = { chosenSpaces = it.toList(); prefs.onboardingSpaces = it },
                                onNext = {
                                    if (route == Route.ONBOARD_OBJECT) route = Route.ONBOARD_SPACES else {
                                        prefs.onboardingDone = true
                                        val feature = chosenSpaces.firstNotNullOfOrNull { name ->
                                            PremiumFeature.entries.firstOrNull { it.name == name && it != PremiumFeature.DOOR }
                                        } ?: PremiumFeature.DOOR
                                        if (premium) openFeature(feature) else { onboardingOffer = true; paywall = feature }
                                    }
                                },
                                onSkip = { finishWelcome() },
                                onBack = { if (route == Route.ONBOARD_SPACES) route = Route.ONBOARD_OBJECT else finishWelcome() },
                            )
                            Route.HOME -> HomeScreen(
                                dims = dims, unit = unit, arAvailable = arAvailable, recentSizes = recentSizes, premium = premium,
                                onDims = { dims = it; if (it.isValid) prefs.dimensions = it },
                                onAr = { route = Route.AR },
                                onDoor = {
                                    readOnlyCheck = false; doorVerdictSeen = false; doorFromMyHome = false
                                    if (!premium) selectSpace(PremiumFeature.DOOR)
                                    route = Route.DOOR
                                },
                                onSettings = { route = Route.SETTINGS },
                                onMyHome = {
                                    if (premium || prefs.hadPremium || prefs.myThings.isNotEmpty()) route = Route.MY_HOME
                                    else requireFeature(PremiumFeature.MY_HOME)
                                },
                                onMeasure = { measureSet = TapeSet.OBJECT; requireFeature(PremiumFeature.TAPE) },
                            )
                            Route.AR -> ArScreen(
                                dims = dims, unit = unit,
                                onBack = { route = Route.HOME },
                                onPhoto = { photoPath = it.path; route = Route.RESULT },
                            )
                            Route.RESULT -> photoPath?.let { File(it) }?.takeIf { it.exists() }?.let { file ->
                                ResultScreen(file, dims, unit, onBack = { route = Route.HOME }, onRetake = { route = Route.AR })
                            } ?: LaunchedEffect(Unit) { route = Route.HOME }
                            Route.DOOR -> key(doorRevision) { DoorScreen(
                                dims = dims, door = door, unit = unit,
                                onDoor = { door = it; if (it.isValid) prefs.door = it },
                                onVerdictShown = { doorVerdictSeen = true },
                                onBack = { route = if (doorFromMyHome) Route.MY_HOME else Route.HOME },
                                arAvailable = arAvailable,
                                onMeasure = { measureSet = it; requireFeature(PremiumFeature.TAPE) },
                                premiumState = premiumState, openedReadOnly = readOnlyCheck,
                                onRequirePremium = { requireFeature(it) },
                                onSegmentChanged = { readOnlyCheck = false },
                            ) }
                            Route.MEASURE -> if (!premium) {
                                LaunchedEffect(premiumState) {
                                    route = Route.HOME
                                    if (premiumState == PremiumState.FREE) requireFeature(PremiumFeature.TAPE)
                                }
                            } else MeasureScreen(
                                unit = unit,
                                targets = measureSet.targets,
                                onUse = { target, cm ->
                                    if (!premium) { requireFeature(PremiumFeature.TAPE); return@MeasureScreen }
                                    when (measureSet) {
                                        TapeSet.DOOR -> { door = door.withTape(target, cm); prefs.door = door }
                                        TapeSet.BOOT -> prefs.boot = prefs.boot.withTape(target, cm)
                                        TapeSet.HALL -> prefs.hall = prefs.hall.withTape(target, cm)
                                        TapeSet.LIFT -> prefs.lift = prefs.lift.withTape(target, cm)
                                        TapeSet.OBJECT -> {
                                            dims = dims.withTape(target, cm); if (dims.isValid) prefs.dimensions = dims
                                        }
                                    }
                                },
                                onBack = { route = if (measureSet != TapeSet.OBJECT) Route.DOOR else Route.HOME },
                            )
                            Route.MY_HOME -> {
                                var things by remember { mutableStateOf(prefs.myThings) }
                                MyHomeScreen(
                                    home = Home(door, prefs.hall, prefs.boot, prefs.lift.takeIf { prefs.liftTyped }),
                                    typed = HomeTyped(prefs.doorTyped, prefs.hallTyped, prefs.bootTyped, prefs.liftTyped),
                                    things = things,
                                    current = dims,
                                    unit = unit,
                                    premium = premium,
                                    onRequirePremium = { requireFeature(PremiumFeature.MY_HOME) },
                                    onAdd = { if (premium) { things = MyThings.add(things, it); prefs.myThings = things } },
                                    onRemove = { if (premium) { things = MyThings.remove(things, it.name); prefs.myThings = things } },
                                    onRename = { thing, name ->
                                        if (premium) { things = MyThings.rename(things, thing.name, name); prefs.myThings = things }
                                    },
                                    onPick = {
                                        dims = it; prefs.dimensions = it
                                        if (premium) route = Route.HOME else {
                                            selectSpace(PremiumFeature.DOOR); readOnlyCheck = true
                                            doorFromMyHome = true; doorVerdictSeen = false; route = Route.DOOR
                                        }
                                    },
                                    onSpace = { space ->
                                        val saved = when (space) { 1 -> prefs.bootTyped; 2 -> prefs.hallTyped; 3 -> prefs.liftTyped; else -> prefs.doorTyped }
                                        val feature = when (space) { 1 -> PremiumFeature.BOOT; 2 -> PremiumFeature.HALL; 3 -> PremiumFeature.LIFT; else -> PremiumFeature.DOOR }
                                        if (!premium && !saved && space != 0) { requireFeature(feature); return@MyHomeScreen }
                                        prefs.liftSelected = space == 3; prefs.carBoot = space == 1; prefs.hallway = space == 2
                                        readOnlyCheck = !premium && saved
                                        doorVerdictSeen = false; doorFromMyHome = true; route = Route.DOOR
                                    },
                                    onBack = { route = Route.HOME },
                                )
                            }
                            Route.SETTINGS -> SettingsScreen(
                                unit = unit,
                                premiumState = premiumState,
                                onPremium = { onboardingOffer = false; paywall = PremiumFeature.DOOR },
                                onManagePremium = {
                                    runCatching { startActivity(Intent(Intent.ACTION_VIEW,
                                        "https://play.google.com/store/account/subscriptions?sku=${Billing.PRODUCT_ID}&package=$packageName".toUri())) }
                                },
                                newsOn = newsOn,
                                onNews = { on ->
                                    if (!on) {
                                        News.turnOff(this@MainActivity); newsOn = false
                                    } else {
                                        // The person's yes is kept; Android may still have to let
                                        // the notes through. The switch shows on once it does
                                        // (checked again on every return, Android's settings included).
                                        News.turnOn(this@MainActivity)
                                        when {
                                            Build.VERSION.SDK_INT >= 33 && !notificationsGranted() ->
                                                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            !News.allowed(this@MainActivity) -> openNotificationSettings()
                                        }
                                        newsOn = News.allowed(this@MainActivity)
                                    }
                                },
                                onUnit = { new ->
                                    // A typical size and the typical door follow the unit, to the
                                    // whole inch or centimetre; what the person typed stays theirs.
                                    val preset = Preset.byId(dims.presetId)
                                    if (dims.source == DimensionSource.PRESET && preset != null) {
                                        dims = preset.dimensionsIn(new); prefs.dimensions = dims
                                    }
                                    // A door never typed in is the default, not a measure: it follows too.
                                    if (!prefs.doorTyped) door = Door.typicalFor(new)
                                    unit = new; prefs.unit = new
                                    publishSizeShortcuts(this@MainActivity, prefs)
                                },
                                onBack = { route = Route.HOME },
                            )
                        }
                    }
                    paywall?.let { feature ->
                        Dialog(onDismissRequest = { closeOffer() }, properties = DialogProperties(
                            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                        )) {
                            PaywallScreen(feature, offerPlans, billingAvailable || (BuildConfig.DEBUG && qaPlans),
                                dims = dims, unit = unit, hall = prefs.hall, boot = prefs.boot, lift = prefs.lift,
                                onClose = { closeOffer() }, onBuy = { chosen ->
                                    if (BuildConfig.DEBUG && qaPlans) {
                                        Toast.makeText(this@MainActivity, "QA: no purchase", Toast.LENGTH_SHORT).show()
                                    } else plans.firstOrNull { it.id == chosen.id }?.let {
                                        billing.purchase(this@MainActivity, it)
                                    }
                                })
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Opened: no note today, and the week's reminder starts again from now.
        News.onOpened(this)
    }

    private fun notificationsGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Android's own page for the app's notifications: where a refusal is undone. */
    private fun openNotificationSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    /**
     * Play's install referrer, read once in the app's life: after an install from a shared link
     * it carries the check (see [SharedCheck]); otherwise just where the install came from, and
     * nothing happens. Read on the phone from Play; nothing is sent.
     */
    private fun readInstallReferrer(prefs: Prefs) {
        val client = runCatching { InstallReferrerClient.newBuilder(this).build() }.getOrNull()
            ?: run { referrerPending = false; return }
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    when (code) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            val referrer = runCatching { client.installReferrer.installReferrer }.getOrNull()
                            prefs.referrerRead = true
                            SharedCheck.parse(referrer)?.let { check -> runOnUiThread { referredCheck = check } }
                        }
                        // No Play, or a Play too old to tell: asking again will not help.
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                        InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR -> prefs.referrerRead = true
                        else -> Unit // Play busy or away: the next launch asks again.
                    }
                    runCatching { client.endConnection() }
                    runOnUiThread { referrerPending = false }
                }

                override fun onInstallReferrerServiceDisconnected() { runOnUiThread { referrerPending = false } }
            })
        }.onFailure { referrerPending = false; runCatching { client.endConnection() } }
    }
}

/**
 * The check a link carries, when it is one of ours: the shared link keeps the sizes after its "#"
 * (https://thomasgorisse.github.io/willitfit/check/#o=…); the page's own button hands them over
 * as a query, straight to the app.
 */
private fun Intent.sharedCheck(): SharedCheck? {
    if (action != Intent.ACTION_VIEW) return null
    val uri = data ?: return null
    if (uri.scheme != "https" || uri.host != SharedCheck.HOST) return null
    if (uri.path?.trimEnd('/') != SharedCheck.PATH.trimEnd('/')) return null
    return SharedCheck.parse(uri.encodedFragment ?: uri.encodedQuery)
}

/** Takes a check as the person's own: the object, and the space it was checked against. */
private fun Prefs.takeCheck(check: SharedCheck) {
    dimensions = check.dims
    if (check.space != null) liftSelected = check.space == Space.LIFT
    when (check.space) {
        Space.DOOR -> { check.door?.let { door = it }; carBoot = false; hallway = false }
        Space.BOOT -> { check.boot?.let { boot = it }; carBoot = true; hallway = false }
        Space.LIFT -> { check.lift?.let { lift = it }; liftSelected = true }
        Space.HALL -> { check.hall?.let { hall = it }; hallway = true }
        null -> Unit
    }
}

/** The text another app sent: a share, or a selection sent from the text menu. */
private fun Intent.sharedText(): String? = when (action) {
    Intent.ACTION_SEND -> listOfNotNull(getStringExtra(Intent.EXTRA_SUBJECT), getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
        .joinToString("\n").ifBlank { null }
    Intent.ACTION_PROCESS_TEXT -> getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.ifBlank { null }
    else -> null
}

/**
 * Play's own rating sheet. Play decides whether it shows (a quota per person, nothing on a build
 * not installed from Play) and never says whether the person rated: the flow just ends. [onDone]
 * gets true once Play ran the flow to its end, false when it could not start (not from Play,
 * offline, the screen gone): then nothing is counted and the next pause tries again.
 */
private fun askForRating(activity: Activity, onDone: (ran: Boolean) -> Unit) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { request ->
        if (!request.isSuccessful) {
            Log.i("WillItFit", "rating sheet not available: ${request.exception?.message}")
            onDone(false)
            return@addOnCompleteListener
        }
        if (activity.isFinishing || activity.isDestroyed) {
            onDone(false)
            return@addOnCompleteListener
        }
        manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
            Log.i("WillItFit", "rating flow done")
            onDone(it.isSuccessful)
        }
    }
}
