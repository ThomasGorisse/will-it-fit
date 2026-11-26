package com.gorisse.thomas.willitfit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.TapePoint
import com.gorisse.thomas.willitfit.fit.TapeTarget
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.fit.projectToView
import com.gorisse.thomas.willitfit.fit.tapeCm
import com.gorisse.thomas.willitfit.fit.usableTapeCm
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlin.math.roundToInt

/**
 * The AR tape: aim the dot in the middle of the screen at one end, tap +, aim at the other end,
 * tap + again. The length shows on the line as you aim. Then it goes where [targets] say: a side
 * of the object, or of the door. [onUse] takes it in at once; the tape clears for the next one,
 * and the side it went to gets a tick, so the three sides of a box can be taken in a row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasureScreen(
    unit: LengthUnit,
    targets: List<TapeTarget>,
    onUse: (TapeTarget, Float) -> Unit,
    onBack: () -> Unit,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val tape = remember { TapeSession() }
    // No detach on leaving: the AR scene is already closing its session by then, and ARCore
    // forbids touching its anchors once it closes. Closing the session releases them.

    Box(Modifier.fillMaxSize().onSizeChanged { tape.viewSize = it }) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            // The dotted surfaces show where the dot can land.
            planeRenderer = true,
            sessionConfiguration = { session, config -> configureTape(session, config) },
            onSessionUpdated = { _, frame -> tape.onFrame(frame) },
        )
        TapeOverlay(tape, unit, targets, onUse, onBack)
    }
}

/**
 * Everything drawn over the camera. Its own composable: it redraws on every ARCore frame, and the
 * AR scene above it does not recompose with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TapeOverlay(
    tape: TapeSession,
    unit: LengthUnit,
    targets: List<TapeTarget>,
    onUse: (TapeTarget, Float) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val used = remember { mutableStateListOf<TapeTarget>() }
    var lastUse by remember { mutableStateOf<Pair<TapeTarget, Float>?>(null) }
    val anchors = tape.anchors
    val aim = tape.aim
    val tracking = tape.tracking
    val viewSize = tape.viewSize
    val viewProjection = tape.viewProjection
    val add: () -> Unit = add@{
        if (tape.add()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    fun clear() = tape.clear()

    Box(Modifier.fillMaxSize()) {

        // What the anchors and the aim are on screen this frame (read, so each frame redraws).
        @Suppress("UNUSED_VARIABLE") val frame = tape.tick
        // A point ARCore lost for a moment keeps its last place and its slot: dropped, the tape read
        // as one end short and the next + cleared both. One lost for good leaves on the next frame.
        val points = anchors.filter { it.trackingState != TrackingState.STOPPED }.map { it.pose.toTapePoint() }
        val aimPoint = aim?.pose?.toTapePoint()
        val w = viewSize.width.toFloat()
        val h = viewSize.height.toFloat()
        fun screen(p: TapePoint) = if (tracking) projectToView(viewProjection, p, w, h)?.let { Offset(it.first, it.second) } else null
        // The segment being drawn: both ends set, or the first end to where the dot aims.
        val end = when {
            points.size >= 2 -> points[1]
            points.size == 1 -> aimPoint
            else -> null
        }
        val start = points.firstOrNull()
        val lengthCm = if (start != null && end != null) tapeCm(start, end) else null
        val startPx = start?.let(::screen)
        val endPx = end?.let(::screen)
        val done = points.size >= 2

        Canvas(Modifier.fillMaxSize()) {
            if (startPx != null && endPx != null) {
                drawLine(Color.Black.copy(alpha = 0.35f), startPx, endPx, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, startPx, endPx, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            listOfNotNull(startPx, if (done) endPx else null).forEach {
                drawCircle(Color.White, 7.dp.toPx(), it)
                drawCircle(TapeAccent, 4.5.dp.toPx(), it)
            }
            // The aim, in the middle: a ring, filled once it lands on a surface.
            if (!done) {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Color.Black.copy(alpha = 0.3f), 18.dp.toPx(), c, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, 18.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                if (aimPoint != null) drawCircle(Color.White, 4.dp.toPx(), c)
            }
        }

        // The length, on the line.
        if (lengthCm != null && startPx != null && endPx != null) {
            val mid = Offset((startPx.x + endPx.x) / 2f, (startPx.y + endPx.y) / 2f)
            Surface(
                color = Color(0xE0141218),
                contentColor = Color.White,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        // Just above the line's middle, never off the screen.
                        val x = (mid.x - p.width / 2f).coerceIn(0f, (constraints.maxWidth - p.width).toFloat())
                        val y = (mid.y - p.height - 10.dp.toPx()).coerceIn(0f, (constraints.maxHeight - p.height).toFloat())
                        p.place(x.roundToInt(), y.roundToInt())
                    }
                },
            ) {
                Text(
                    formatLength(lengthCm, unit),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp),
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).formWidth().statusBarsPadding().padding(top = 18.dp, start = 72.dp, end = 72.dp),
            color = Color(0xCC141218),
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.tape_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(
                        when {
                            done -> R.string.tape_pick_side
                            !tracking || aimPoint == null && points.isEmpty() -> R.string.tape_searching
                            points.isEmpty() -> R.string.tape_aim_first
                            else -> R.string.tape_aim_second
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).formWidth().fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lastUse?.takeIf { !done && points.isEmpty() }?.let { (target, cm) ->
                Surface(
                    color = Color(0xCC141218), contentColor = Color.White, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    Text(
                        stringResource(R.string.tape_set, stringResource(target.label()), formatLength(cm, unit)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            AnimatedContent(done, label = "tape") { measured ->
                if (measured && lengthCm != null) {
                    val usable = usableTapeCm(lengthCm)
                    Surface(
                        color = Color(0xE6141218),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 420.dp),
                    ) {
                        // Five space targets must stay reachable with large text or a short screen.
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
                            Text(formatLength(lengthCm, unit), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.tape_accuracy),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                targets.forEach { target ->
                                    FilledTonalButton(
                                        onClick = {
                                            val cm = usable ?: return@FilledTonalButton
                                            onUse(target, cm)
                                            if (target !in used) used += target
                                            lastUse = target to cm
                                            clear()
                                        },
                                        enabled = usable != null,
                                    ) {
                                        if (target in used) {
                                            Icon(Icons.Outlined.Check, null, Modifier.size(ButtonDefaults.IconSize))
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        }
                                        Text(stringResource(R.string.tape_use_as, stringResource(target.label())))
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { clear() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                                    Text(stringResource(R.string.tape_again))
                                }
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = { anchors.removeLastOrNull()?.detach() },
                            enabled = anchors.isNotEmpty(),
                            modifier = Modifier.size(56.dp),
                        ) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.tape_undo)) }
                        AddPoint(enabled = aimPoint != null, label = stringResource(R.string.tape_add), onClick = add)
                        if (used.isNotEmpty()) {
                            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Outlined.Check, stringResource(R.string.tape_done))
                            }
                        } else {
                            Spacer(Modifier.size(56.dp))
                        }
                    }
                }
            }
        }
    }
}

private val TapeAccent = Color(0xFF4DB6AC)

/** Where the dot aims: a surface ARCore tracks, and the pose on it. */
private class Aim(val trackable: Trackable, val pose: Pose)

/** The tape's state between ARCore frames and the overlay: written on frames, read by Compose. */
private class TapeSession {
    var viewSize by mutableStateOf(IntSize.Zero)
    var aim by mutableStateOf<Aim?>(null)
    var tracking by mutableStateOf(false)
    var tick by mutableIntStateOf(0)
    val anchors = mutableStateListOf<Anchor>()
    val viewProjection = FloatArray(16)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)

    fun onFrame(frame: Frame) {
        anchors.removeAll { it.trackingState == TrackingState.STOPPED }
        val camera = frame.camera
        tracking = camera.trackingState == TrackingState.TRACKING
        val size = viewSize
        if (tracking && size.width > 0) {
            aim = aimAt(frame, size.width / 2f, size.height / 2f)
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(projection, 0, 0.05f, 100f)
            android.opengl.Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
        } else {
            aim = null
        }
        tick++
    }

    /** A point where the dot aims; a third one starts a new measure. False when nothing is aimed at. */
    fun add(): Boolean {
        val a = aim ?: return false
        if (anchors.size >= 2) clear()
        val anchor = runCatching { a.trackable.createAnchor(a.pose) }.getOrNull() ?: return false
        anchors += anchor
        return true
    }

    fun clear() {
        anchors.forEach { it.detach() }
        anchors.clear()
    }
}

private fun Pose.toTapePoint() = TapePoint(tx(), ty(), tz())

/**
 * The surface under the screen point ([x], [y] in view px): a plane inside its outline first, then
 * a depth point, then a feature point with a surface normal. Floors, walls, door frames and the
 * edges of furniture all show up as one of these.
 */
private fun aimAt(frame: Frame, x: Float, y: Float): Aim? = runCatching {
    frame.hitTest(x, y).firstOrNull { hit ->
        when (val t = hit.trackable) {
            is Plane -> t.trackingState == TrackingState.TRACKING && t.subsumedBy == null && t.isPoseInPolygon(hit.hitPose)
            is DepthPoint -> t.trackingState == TrackingState.TRACKING
            is Point -> t.trackingState == TrackingState.TRACKING &&
                t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
            else -> false
        }
    }?.let { Aim(it.trackable, it.hitPose) }
}.getOrNull()

/** Floors and walls, and depth where the phone has it: the dot lands on more than the floor. */
private fun configureTape(session: Session, config: Config) {
    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
    config.focusMode = Config.FocusMode.AUTO
    config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
}

/** The camera-app shutter, with a + in it: 76 dp. */
@Composable
private fun AddPoint(enabled: Boolean, label: String, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, Color.White.copy(alpha = alpha)),
        modifier = Modifier.size(76.dp).semantics { contentDescription = label },
    ) {
        Box(Modifier.padding(8.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = alpha), modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, null, tint = Color(0xFF141218), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/** "width", "door height": the side a length goes to, as the buttons name it. */
fun TapeTarget.label(): Int = when (this) {
    TapeTarget.WIDTH -> R.string.tape_side_width
    TapeTarget.DEPTH -> R.string.tape_side_depth
    TapeTarget.HEIGHT -> R.string.tape_side_height
    TapeTarget.DOOR_WIDTH -> R.string.tape_side_door_width
    TapeTarget.DOOR_HEIGHT -> R.string.tape_side_door_height
    TapeTarget.BOOT_OPENING_WIDTH -> R.string.tape_side_boot_opening_width
    TapeTarget.BOOT_OPENING_HEIGHT -> R.string.tape_side_boot_opening_height
    TapeTarget.BOOT_LENGTH -> R.string.tape_side_boot_length
    TapeTarget.BOOT_WIDTH -> R.string.tape_side_boot_width
    TapeTarget.BOOT_HEIGHT -> R.string.tape_side_boot_height
    TapeTarget.HALL_WIDTH -> R.string.tape_side_hall_width
    TapeTarget.HALL_TURN_WIDTH -> R.string.tape_side_hall_turn_width
    TapeTarget.HALL_CEILING -> R.string.tape_side_hall_ceiling
    TapeTarget.CABIN_WIDTH -> R.string.tape_side_cabin_width
    TapeTarget.CABIN_DEPTH -> R.string.tape_side_cabin_depth
    TapeTarget.CABIN_HEIGHT -> R.string.tape_side_cabin_height
}
