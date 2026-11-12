package com.gorisse.thomas.willitfit

import android.os.Bundle
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
import com.google.ar.core.ArCoreApk
import com.gorisse.thomas.willitfit.data.Prefs
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
                                ResultScreen(file, onBack = { route = Route.HOME }, onRetake = { route = Route.AR })
                            } ?: LaunchedEffect(Unit) { route = Route.HOME }
                            Route.DOOR -> DoorScreen(
                                dims = dims, door = door, unit = unit,
                                onDoor = { door = it; if (it.isValid) prefs.door = it },
                                onBack = { route = Route.HOME },
                            )
                            Route.SETTINGS -> SettingsScreen(
                                unit = unit,
                                onUnit = { unit = it; prefs.unit = it },
                                onBack = { route = Route.HOME },
                            )
                        }
                    }
                }
            }
        }
    }
}
