package com.gorisse.thomas.willitfit.ui

import android.app.Activity
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.capture.captureScene
import com.gorisse.thomas.willitfit.capture.savePhoto
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.scene.DimensionMarks
import com.gorisse.thomas.willitfit.scene.Furniture
import com.gorisse.thomas.willitfit.scene.rememberFurnitureLook
import io.github.sceneview.ar.ARHapticFeedback
import io.github.sceneview.ar.AutoPlacementNode
import io.github.sceneview.ar.AutoPlacementScene
import io.github.sceneview.ar.AutoPlacementState
import io.github.sceneview.ar.PlacementPhase
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the AR camera does. One tool today: the object at real size. The two-point tape measure
 * goes here next, as a second tool sharing the same camera, bottom bar and photo.
 */
enum class ArTool { OBJECT /*, MEASURE */ }

/**
 * Screen 2, "In my room". The object lands on the floor at real size by itself — no tap — with its
 * dimensions on the floor around it. Drag slides it, twist turns it; the size is locked.
 */
@Composable
fun ArScreen(
    dims: Dimensions,
    unit: LengthUnit,
    onBack: () -> Unit,
    onPhoto: (File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    // Furniture never changes size in AR: pinch, double-tap and scaleTo are off.
    val state = remember { AutoPlacementState().apply { allowsScaling = false } }
    ARHapticFeedback(state)
    @Suppress("UNUSED_VARIABLE") val tool = ArTool.OBJECT

    var capturing by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasPlacement) {
        if (state.hasPlacement) { showHint = true; delay(4_000); showHint = false }
    }
    val placed = state.phase == PlacementPhase.PLACED || state.phase == PlacementPhase.ADJUSTING
    val scheme = MaterialTheme.colorScheme
    val name = objectName(dims)
    val summary = formatDimensions(dims, unit)
    val brand = stringResource(R.string.photo_brand)

    Box(Modifier.fillMaxSize()) {
        AutoPlacementScene(
            assetReady = true,
            modifier = Modifier.fillMaxSize(),
            state = state,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            coachingContentPadding = PaddingValues(bottom = 132.dp),
        ) { placement ->
            AutoPlacementNode(placement, state) { opacity ->
                val look = rememberFurnitureLook(
                    scheme.primary, opacity = opacity, transparent = true, selectable = true,
                )
                Furniture(dims, look)
                DimensionMarks(
                    dims = dims, unit = unit,
                    lineColor = Color.White,
                    labelText = Color.White,
                    labelBackground = Color(0xE0141218),
                    selectable = true,
                )
            }
        }

        if (!capturing) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp),
                color = Color(0xCC141218),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.ar_real_size, summary), style = MaterialTheme.typography.labelMedium)
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(showHint && placed, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        color = Color(0xCC141218), contentColor = Color.White, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.ar_hint_placed),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { state.rotateBy(90f) },
                        enabled = placed,
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Outlined.Rotate90DegreesCw, stringResource(R.string.ar_turn)) }
                    Shutter(
                        enabled = placed,
                        label = stringResource(R.string.ar_photo),
                        onClick = {
                            val activity = context as? Activity ?: return@Shutter
                            scope.launch {
                                capturing = true
                                val bitmap = captureScene(activity)
                                capturing = false
                                if (bitmap != null) onPhoto(savePhoto(context, bitmap, name, summary, brand))
                            }
                        },
                    )
                    FilledTonalIconButton(
                        onClick = { state.resetPlacement(SystemClock.uptimeMillis()) },
                        enabled = state.hasPlacement,
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Outlined.Refresh, stringResource(R.string.ar_reset)) }
                }
            }
        }
    }
}

/** The camera-app shutter: a white disc in a white ring, 76 dp. */
@Composable
private fun Shutter(enabled: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f)),
        modifier = Modifier.size(76.dp).semantics { contentDescription = label },
    ) {
        Box(Modifier.padding(8.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.fillMaxSize()) {}
        }
    }
}
