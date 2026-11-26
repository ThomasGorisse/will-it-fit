package com.gorisse.thomas.willitfit.ui

import android.app.Activity
import android.opengl.Matrix
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.capture.PhotoFailure
import com.gorisse.thomas.willitfit.capture.PhotoResult
import com.gorisse.thomas.willitfit.capture.captureScene
import com.gorisse.thomas.willitfit.capture.savePhoto
import com.gorisse.thomas.willitfit.capture.takePhoto
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.fit.toSize
import io.github.sceneview.DimensionMarks
import io.github.sceneview.DimensionTagClearance
import com.gorisse.thomas.willitfit.scene.Furniture
import com.gorisse.thomas.willitfit.scene.rememberFurnitureLook
import io.github.sceneview.ar.ARHapticFeedback
import io.github.sceneview.ar.AutoPlacementNode
import io.github.sceneview.ar.AutoPlacementScene
import io.github.sceneview.ar.AutoPlacementState
import io.github.sceneview.ar.PlacementPhase
import io.github.sceneview.ar.replay.ARSessionProbe
import io.github.sceneview.ar.replay.LocalARSessionProbe
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Job
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the AR camera does: the object at real size. The two-point tape is its own screen
 * ([MeasureScreen]): it needs walls and depth, and no object in the way.
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
    // The size is the answer here: the marks stay on whenever the object stands (#5209).
    val state = remember { AutoPlacementState().apply { allowsScaling = false; alwaysShowsDimensions = true } }
    ARHapticFeedback(state)
    @Suppress("UNUSED_VARIABLE") val tool = ArTool.OBJECT
    // Below a QA replay probe, if a harness provides one: ours passes everything on to it.
    val outerProbe = LocalARSessionProbe.current
    val camera = remember(outerProbe) { CameraWatch(outerProbe) }

    var capturing by remember { mutableStateOf(false) }
    // Where the shutter row (and its hint) begins, from the top of the screen: a tag on the floor
    // in front of the object slid under the buttons when the phone came close.
    var screenHeight by remember { mutableIntStateOf(0) }
    var controlsTop by remember { mutableFloatStateOf(Float.NaN) }
    // And where the back button and the title end: up close to a washer, its height tag grew
    // past them at the top of the screen.
    var backBottom by remember { mutableFloatStateOf(Float.NaN) }
    var titleBottom by remember { mutableFloatStateOf(Float.NaN) }
    val clearance = remember(camera) {
        DimensionTagClearance(
            screenY = camera::screenY,
            bottomLimit = {
                val h = screenHeight
                // A photo has no buttons over it: every tag stays in it.
                if (capturing || h <= 0 || controlsTop.isNaN()) -1f else 1f - 2f * controlsTop / h
            },
            topLimit = {
                val h = screenHeight
                val bar = maxOf(backBottom, titleBottom)
                if (capturing || h <= 0 || bar.isNaN()) 1f else 1f - 2f * bar / h
            },
        )
    }
    // The last photo that did not come out, and why: a card with Try again until it is dealt with.
    var photoFailure by remember { mutableStateOf<PhotoFailure?>(null) }
    var shot by remember { mutableStateOf<Job?>(null) }
    // SceneView draws its own card while ARCore cannot run ("Couldn't start AR" and its Try
    // again, install, update): ours stays away then, so the screen says one thing at a time.
    var arCoreCard by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(state.hasPlacement) {
        if (state.hasPlacement) { showHint = true; delay(4_000); showHint = false }
    }
    val placed = state.phase == PlacementPhase.PLACED || state.phase == PlacementPhase.ADJUSTING
    val scheme = MaterialTheme.colorScheme
    val name = objectName(dims)
    val summary = formatDimensions(dims, unit)
    val realSize = stringResource(R.string.ar_real_size, summary)
    val signature = stringResource(R.string.photo_signature)
    val shoot: () -> Unit = shoot@{
        val activity = context as? Activity ?: return@shoot
        if (shot?.isActive == true) return@shoot // one photo at a time, saving included
        photoFailure = null
        shot = scope.launch {
            val result = takePhoto(
                setBusy = { capturing = it },
                capture = { captureScene(activity) },
                save = { savePhoto(context, it, name, realSize, emptyList(), signature) },
            )
            when (result) {
                is PhotoResult.Success -> onPhoto(result.value)
                is PhotoResult.Failure -> photoFailure = result.reason
            }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { screenHeight = it.height }) {
        CompositionLocalProvider(LocalARSessionProbe provides camera) {
            AutoPlacementScene(
                assetReady = true,
                modifier = Modifier.fillMaxSize(),
                state = state,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                coachingContentPadding = PaddingValues(bottom = 132.dp),
                // The light-estimated shadow fell on ARCore's plane polygons, which cut it with
                // hard straight edges and swung it away under a lamp: the object keeps only its
                // soft contact shadow, as Scene Viewer and AR Quick Look draw.
                groundShadows = false,
                onARCoreAvailability = { arCoreCard = it != null },
            ) { placement ->
                // SceneView draws the contact shadow and the footprint ring for a sized node.
                AutoPlacementNode(placement, state, size = dims.toSize()) { opacity ->
                    // Walk into the washer and its white top filled the whole screen: the object
                    // gives way when the phone comes within 15 cm of it and returns past 30 cm,
                    // leaving its lines and sizes to show where it stands.
                    var near by remember { mutableStateOf(false) }
                    val box by rememberUpdatedState(dims)
                    Node(apply = {
                        onFrame = {
                            val gap = gapToBox(getLocalPosition(camera.position()), box)
                            if (!near && gap < NEAR_HIDE_M) near = true
                            else if (near && gap > NEAR_SHOW_M) near = false
                        }
                    })
                    val presence by animateFloatAsState(if (near) 0f else 1f, tween(150), label = "near")
                    val look = rememberFurnitureLook(
                        FurnitureTeal, opacity = opacity * presence, transparent = true, selectable = true,
                    )
                    // The marks wait for the object, should it still be loading.
                    var objectShown by remember { mutableStateOf(false) }
                    Furniture(dims, look, contactShadow = false, onShown = { objectShown = true })
                    if (objectShown && state.guide.dimensions) DimensionMarks(
                        size = dims.toSize(),
                        format = { formatLength(it * 100f, unit) },
                        lineColor = Color.White,
                        labelText = Color.White,
                        labelBackground = Color(0xE0141218),
                        selectable = true,
                        // Seen from behind or from the side, the tags read backwards ("mo 501"):
                        // they turn to the camera, as in the 3D room.
                        facing = camera::position,
                        clearance = clearance,
                    )
                }
            }
        }

        if (!capturing) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp)
                    .onGloballyPositioned { backBottom = it.positionInParent().y + it.size.height },
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).formWidth().statusBarsPadding().padding(top = 18.dp)
                    .onGloballyPositioned { titleBottom = it.positionInParent().y + it.size.height },
                color = Color(0xCC141218),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, style = MaterialTheme.typography.labelLarge)
                    Text(realSize, style = MaterialTheme.typography.labelMedium)
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).formWidth().fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)
                    .onGloballyPositioned { controlsTop = it.positionInParent().y },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The states SceneView leaves to the app: no coaching runs then, so without a card
                // the camera just sat there with greyed buttons after 10 s without a floor.
                val card = if (arCoreCard) null else when (state.phase) {
                    PlacementPhase.NO_SURFACE -> ArCard.NO_FLOOR
                    PlacementPhase.RECOVERY_FAILED -> ArCard.LOST
                    PlacementPhase.CAMERA_ERROR -> ArCard.CAMERA
                    else -> null
                }
                // A failed photo speaks only when the scene has nothing more urgent to say.
                val failure = photoFailure.takeIf { card == null && !arCoreCard }
                var shownFailure by remember { mutableStateOf(PhotoFailure.COPY_FAILED) }
                if (failure != null) shownFailure = failure
                AnimatedVisibility(
                    failure != null,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut(),
                ) {
                    PhotoFailedCard(
                        reason = shownFailure,
                        onRetry = shoot,
                        onDismiss = { photoFailure = null },
                        retryEnabled = placed,
                    )
                }
                // Kept through the exit fade, so the card does not go blank while it leaves.
                var shownCard by remember { mutableStateOf(ArCard.NO_FLOOR) }
                if (card != null) shownCard = card
                AnimatedVisibility(
                    card != null,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut(),
                ) {
                    StatusCard(
                        card = shownCard,
                        onKeepLooking = { state.keepScanning(SystemClock.uptimeMillis()) },
                        onPlaceAgain = { state.resetPlacement(SystemClock.uptimeMillis()) },
                        onBack = onBack,
                    )
                }
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
                        onClick = shoot,
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

/**
 * Where the AR camera stands, in the scene's world, for the tags to face it. AutoPlacementScene
 * keeps its camera node to itself; [ARSessionProbe] is SceneView's public per-frame hook, called on
 * the main thread after the scene is updated. Until the camera tracks, the tags look at eye height
 * in front of the origin; they only appear once an object is placed, which needs tracking.
 * A probe provided further up (a QA replay) still gets its dataset and every frame.
 */
private class CameraWatch(private val outer: ARSessionProbe?) : ARSessionProbe {
    private var last = Position(0f, 1.5f, 1f)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val point = FloatArray(4)
    private val clip = FloatArray(4)
    private var projects = false
    fun position(): Position = last
    override val playbackDataset: File? get() = outer?.playbackDataset

    /**
     * How high [world] appears on the screen, -1 at the bottom edge and 1 at the top, or null
     * behind the camera or before it tracks.
     */
    fun screenY(world: Position): Float? {
        if (!projects) return null
        point[0] = world.x; point[1] = world.y; point[2] = world.z; point[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProjection, 0, point, 0)
        return if (clip[3] > 0f) clip[1] / clip[3] else null
    }

    override fun onSessionFrame(session: Session, frame: Frame, nodes: List<io.github.sceneview.node.Node>) {
        outer?.onSessionFrame(session, frame, nodes)
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return
        val pose = camera.pose
        last = Position(pose.tx(), pose.ty(), pose.tz())
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(projection, 0, 0.05f, 100f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        projects = true
    }
}

private const val NEAR_HIDE_M = 0.15f
private const val NEAR_SHOW_M = 0.30f

/**
 * How far [p] (metres, in the object's frame: bottom-centre at the origin, +Y up) is from the
 * object's box, 0 inside it.
 */
internal fun gapToBox(p: Position, dims: Dimensions): Float {
    val dx = max(abs(p.x) - dims.widthCm / 200f, 0f)
    val dy = max(max(-p.y, p.y - dims.heightCm / 100f), 0f)
    val dz = max(abs(p.z) - dims.depthCm / 200f, 0f)
    return sqrt(dx * dx + dy * dy + dz * dz)
}

private enum class ArCard { NO_FLOOR, LOST, CAMERA }

/** What went wrong, in one line and one hint, with the way on: above the bottom bar. */
@Composable
private fun StatusCard(card: ArCard, onKeepLooking: () -> Unit, onPlaceAgain: () -> Unit, onBack: () -> Unit) {
    val (title, detail) = when (card) {
        ArCard.NO_FLOOR -> R.string.ar_no_floor_title to R.string.ar_no_floor_detail
        ArCard.LOST -> R.string.ar_lost_title to R.string.ar_lost_detail
        ArCard.CAMERA -> R.string.ar_camera_title to R.string.ar_camera_detail
    }
    Surface(
        color = Color(0xE6141218),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp).widthIn(max = 420.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(detail),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (card != ArCard.CAMERA) {
                    TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(stringResource(R.string.action_back))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Button(
                    onClick = when (card) {
                        ArCard.NO_FLOOR -> onKeepLooking
                        ArCard.LOST -> onPlaceAgain
                        ArCard.CAMERA -> onBack
                    },
                ) {
                    Text(
                        stringResource(
                            when (card) {
                                ArCard.NO_FLOOR -> R.string.ar_keep_looking
                                ArCard.LOST -> R.string.ar_reset
                                ArCard.CAMERA -> R.string.action_back
                            },
                        ),
                    )
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
