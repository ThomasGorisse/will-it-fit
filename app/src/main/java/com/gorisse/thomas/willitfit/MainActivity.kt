package com.gorisse.thomas.willitfit

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.ar.core.ArCoreApk
import com.gorisse.thomas.willitfit.data.Prefs
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.dimensionsIn
import com.gorisse.thomas.willitfit.ui.ArScreen
import com.gorisse.thomas.willitfit.ui.DoorScreen
import com.gorisse.thomas.willitfit.ui.HomeScreen
import com.gorisse.thomas.willitfit.ui.ResultScreen
import com.gorisse.thomas.willitfit.ui.SettingsScreen
import com.gorisse.thomas.willitfit.ui.WillItFitTheme
import java.io.File

/** The five screens. Home is the root; the others return to where they came from. */
private enum class Route { HOME, AR, RESULT, DOOR, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = Prefs(applicationContext)
        setContent {
            WillItFitTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    var route by rememberSaveable { mutableStateOf(Route.HOME) }
                    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
                    var dims by remember { mutableStateOf(prefs.dimensions) }
                    var door by remember { mutableStateOf(prefs.door) }
                    var unit by remember { mutableStateOf(prefs.unit) }
                    val context = LocalContext.current
                    val arAvailable = remember {
                        runCatching { !ArCoreApk.getInstance().checkAvailability(context).isUnsupported }.getOrDefault(false)
                    }
                    // An answer is an AR photo taken or a door checked. Back home after the second
                    // one is a pause after something that worked: Play's rating sheet is asked for
                    // then, once in the app's life.
                    var lastRoute by remember { mutableStateOf(route) }
                    LaunchedEffect(route) {
                        if (route == Route.RESULT || (route == Route.HOME && lastRoute == Route.DOOR)) prefs.answers += 1
                        if (route == Route.HOME && lastRoute != Route.HOME) {
                            if (prefs.answers >= 2 && !prefs.reviewAsked) {
                                prefs.reviewAsked = true
                                askForRating(this@MainActivity)
                            }
                        }
                        lastRoute = route
                    }
                    BackHandler(enabled = route != Route.HOME) {
                        route = if (route == Route.RESULT) Route.AR else Route.HOME
                    }
                    // No cross-fade: two scenes (or a scene and the AR camera) never run at once.
                    run {
                        when (route) {
                            Route.HOME -> HomeScreen(
                                dims = dims, unit = unit, arAvailable = arAvailable,
                                onDims = { dims = it; if (it.isValid) prefs.dimensions = it },
                                onAr = { route = Route.AR },
                                onDoor = { route = Route.DOOR },
                                onSettings = { route = Route.SETTINGS },
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
                                onBack = { route = Route.HOME },
                            )
                            Route.SETTINGS -> SettingsScreen(
                                unit = unit,
                                onUnit = { new ->
                                    // A typical size and the typical door follow the unit, to the
                                    // whole inch or centimetre; what the person typed stays theirs.
                                    val preset = Preset.byId(dims.presetId)
                                    if (dims.source == DimensionSource.PRESET && preset != null) {
                                        dims = preset.dimensionsIn(new); prefs.dimensions = dims
                                    }
                                    if (door == Door.typicalFor(unit)) { door = Door.typicalFor(new); prefs.door = door }
                                    unit = new; prefs.unit = new
                                },
                                onBack = { route = Route.HOME },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Play's own rating sheet. Play decides whether it shows (a quota per person, nothing on a build
 * not installed from Play) and never says whether the person rated: the flow just ends.
 */
private fun askForRating(activity: Activity) {
    val manager = ReviewManagerFactory.create(activity)
    manager.requestReviewFlow().addOnCompleteListener { request ->
        if (!request.isSuccessful) {
            Log.i("WillItFit", "rating sheet not available: ${request.exception?.message}")
            return@addOnCompleteListener
        }
        if (activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
        manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
            Log.i("WillItFit", "rating flow done")
        }
    }
}
