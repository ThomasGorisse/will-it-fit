package com.gorisse.thomas.willitfit

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.ar.core.ArCoreApk
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
import com.gorisse.thomas.willitfit.fit.TapeTarget
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
private enum class Route { HOME, AR, RESULT, DOOR, SETTINGS, MEASURE, MY_HOME }

class MainActivity : ComponentActivity() {
    /** A rating flow is running: going home again meanwhile does not start a second one. */
    private var reviewInFlight = false

    /** A check from Play's install referrer: it arrives a moment after the first launch. */
    private var referredCheck by mutableStateOf<SharedCheck?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(applicationContext)
        // Text from a listing, shared or selected in another app. Read once: not again when the
        // activity is recreated.
        val incoming = if (savedInstanceState == null) intent.sharedText() else null
        val shortcut = if (savedInstanceState == null) intent.shortcutDimensions() else null
        // A check someone sent: its link opens the same check, once, before anything is read.
        val linked = if (savedInstanceState == null) intent.sharedCheck() else null
        linked?.let { prefs.takeCheck(it) }
        // A note from the app (News): it opens the screen of what it speaks of.
        val noted = if (savedInstanceState == null) intent.getStringExtra(News.EXTRA_OPEN) else null
        when (noted) {
            News.OPEN_DOOR, News.OPEN_TAPE -> { prefs.carBoot = false; prefs.hallway = false }
            News.OPEN_BOOT -> { prefs.carBoot = true; prefs.hallway = false }
            News.OPEN_HALL -> prefs.hallway = true
        }
        if (linked == null && savedInstanceState == null && !prefs.referrerRead) readInstallReferrer(prefs)
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
                                noted == News.OPEN_MY_HOME -> Route.MY_HOME
                                noted == News.OPEN_TAPE && arAvailable -> Route.MEASURE
                                shortcut != null || linked?.space != null || noted != null -> Route.DOOR
                                else -> Route.HOME
                            },
                        )
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
                        if (route != Route.HOME) return@LaunchedEffect
                        prefs.takeCheck(check)
                        dims = prefs.dimensions; door = prefs.door
                        if (check.space != null) route = Route.DOOR
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
                    // The AR tape opens from home (the object) or from the door, and goes back there.
                    var measureDoor by rememberSaveable { mutableStateOf(noted == News.OPEN_TAPE) }
                    // A space opened from My home goes back there.
                    var doorFromMyHome by rememberSaveable { mutableStateOf(false) }
                    BackHandler(enabled = route != Route.HOME) {
                        route = when {
                            route == Route.RESULT -> Route.AR
                            route == Route.MEASURE && measureDoor -> Route.DOOR
                            route == Route.DOOR && doorFromMyHome -> Route.MY_HOME
                            else -> Route.HOME
                        }
                    }
                    // No cross-fade: two scenes (or a scene and the AR camera) never run at once.
                    run {
                        when (route) {
                            Route.HOME -> HomeScreen(
                                dims = dims, unit = unit, arAvailable = arAvailable, recentSizes = recentSizes,
                                onDims = { dims = it; if (it.isValid) prefs.dimensions = it },
                                onAr = { route = Route.AR },
                                onDoor = { doorVerdictSeen = false; doorFromMyHome = false; route = Route.DOOR },
                                onSettings = { route = Route.SETTINGS },
                                onMyHome = { route = Route.MY_HOME },
                                onMeasure = { measureDoor = false; route = Route.MEASURE },
                            )
                            Route.AR -> ArScreen(
                                dims = dims, unit = unit,
                                onBack = { route = Route.HOME },
                                onPhoto = { photoPath = it.path; route = Route.RESULT },
                            )
                            Route.RESULT -> photoPath?.let { File(it) }?.takeIf { it.exists() }?.let { file ->
                                ResultScreen(file, dims, unit, onBack = { route = Route.HOME }, onRetake = { route = Route.AR })
                            } ?: LaunchedEffect(Unit) { route = Route.HOME }
                            Route.DOOR -> DoorScreen(
                                dims = dims, door = door, unit = unit,
                                onDoor = { door = it; if (it.isValid) prefs.door = it },
                                onVerdictShown = { doorVerdictSeen = true },
                                onBack = { route = if (doorFromMyHome) Route.MY_HOME else Route.HOME },
                                arAvailable = arAvailable,
                                onMeasure = { measureDoor = true; route = Route.MEASURE },
                            )
                            Route.MEASURE -> MeasureScreen(
                                unit = unit,
                                targets = if (measureDoor) TapeTarget.DOOR else TapeTarget.OBJECT,
                                onUse = { target, cm ->
                                    if (measureDoor) {
                                        door = door.withTape(target, cm); prefs.door = door
                                    } else {
                                        dims = dims.withTape(target, cm); if (dims.isValid) prefs.dimensions = dims
                                    }
                                },
                                onBack = { route = if (measureDoor) Route.DOOR else Route.HOME },
                            )
                            Route.MY_HOME -> {
                                var things by remember { mutableStateOf(prefs.myThings) }
                                MyHomeScreen(
                                    home = Home(door, prefs.hall, prefs.boot),
                                    typed = HomeTyped(prefs.doorTyped, prefs.hallTyped, prefs.bootTyped),
                                    things = things,
                                    current = dims,
                                    unit = unit,
                                    onAdd = { things = MyThings.add(things, it); prefs.myThings = things },
                                    onRemove = { things = MyThings.remove(things, it.name); prefs.myThings = things },
                                    onRename = { thing, name ->
                                        things = MyThings.rename(things, thing.name, name); prefs.myThings = things
                                    },
                                    onPick = { dims = it; prefs.dimensions = it; route = Route.HOME },
                                    onSpace = { space ->
                                        prefs.carBoot = space == 1; prefs.hallway = space == 2
                                        doorVerdictSeen = false; doorFromMyHome = true; route = Route.DOOR
                                    },
                                    onBack = { route = Route.HOME },
                                )
                            }
                            Route.SETTINGS -> SettingsScreen(
                                unit = unit,
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
        val client = runCatching { InstallReferrerClient.newBuilder(this).build() }.getOrNull() ?: return
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
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        }
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
    when (check.space) {
        Space.DOOR -> { check.door?.let { door = it }; carBoot = false; hallway = false }
        Space.BOOT -> { check.boot?.let { boot = it }; carBoot = true; hallway = false }
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
