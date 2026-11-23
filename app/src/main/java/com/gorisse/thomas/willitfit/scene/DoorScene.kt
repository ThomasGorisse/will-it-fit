package com.gorisse.thomas.willitfit.scene

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gorisse.thomas.willitfit.fit.Carry
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.DoorVerdict
import com.gorisse.thomas.willitfit.fit.side
import io.github.sceneview.Aabb
import io.github.sceneview.SceneView
import io.github.sceneview.fitCameraToBounds
import io.github.sceneview.DEFAULT_FILL_LIGHT_INTENSITY
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.verticalFovDegreesForFocalLength
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * A wall with the door opening, and the object carried towards it the way the verdict says.
 * When it fits it slides through and comes back; when it does not, it stops against the frame.
 * [hold] stops it on that answer — in the doorway, or against the frame — for a picture.
 */
@Composable
fun DoorScene(
    dims: Dimensions,
    door: Door,
    verdict: DoorVerdict,
    accent: Color,
    palette: RoomPalette,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    hold: Boolean = false,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    val across = dims.side(verdict.carry.across) / 100f
    val up = dims.side(verdict.carry.up) / 100f
    val along = dims.side(verdict.carry.along) / 100f
    val dw = door.widthCm / 100f
    val dh = door.heightCm / 100f

    // 0 = in front of the door, 1 = through it. A miss stops at the frame.
    val stopAt = if (verdict.fits) 1f else 0.45f
    val progress by rememberInfiniteTransition(label = "carry").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 5600
                0f at 0
                0f at 700
                if (verdict.fits) {
                    // A beat in the doorway, framed by it: that is the answer to the question.
                    0.5f at 2100 using FastOutSlowInEasing
                    0.5f at 3100
                    1f at 4200 using FastOutSlowInEasing
                    1f at 4800
                } else {
                    stopAt at 3000 using FastOutSlowInEasing
                    stopAt at 4300
                }
                0f at 5600 using FastOutSlowInEasing
            },
            RepeatMode.Restart,
        ),
        label = "progress",
    )
    val startZ = along / 2f + START_GAP
    val endZ = -(along / 2f + 0.5f)
    val contactZ = WALL_THICKNESS / 2f + along / 2f + 0.005f
    // Held for a picture: the object in the doorway, or against the frame.
    val at = if (hold) (if (verdict.fits) 0.5f else stopAt) else progress
    val z = if (verdict.fits) startZ + (endZ - startZ) * at
    else startZ + (contactZ - startZ) * (at / stopAt)

    // Framed on the part of the view the sheet below and the buttons above leave, and on what it
    // leaves once the keyboard pushes the sheet up: the door and the whole object, never cut at an
    // edge. Read once the keyboard has settled, not on every frame of its slide: a new framing
    // glides the camera.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val coveredPx = with(LocalDensity.current) {
        contentPadding.calculateTopPadding().toPx() + contentPadding.calculateBottomPadding().toPx()
    }
    val visibleAspect = if (viewSize.width > 0 && viewSize.height > 0) {
        viewSize.width / (viewSize.height - coveredPx).coerceAtLeast(viewSize.height * 0.1f)
    } else 0.6f
    var aspect by remember { mutableFloatStateOf(visibleAspect) }
    LaunchedEffect(visibleAspect) { delay(180); aspect = visibleAspect }
    val view = doorView(dw = dw, dh = dh, across = across, up = up, along = along, aspect = aspect)
    val manipulator = rememberCameraManipulator(orbitHomePosition = view.eye, targetPosition = view.target)
    // The 3D room's light: the sky's ambient, a low sun from the front right that throws the
    // object's shadow onto the floor, warm in the evening. With one flat white light the wall read
    // as a plain brown board and the bed as a beige box.
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader)
    val dayIbl = remember(environment) { environment.indirectLight?.intensity ?: 30_000f }
    SideEffect { environment.indirectLight?.intensity = if (palette.isNight) dayIbl * 0.6f else dayIbl }
    val sun = rememberMainLightNode(engine) {
        intensity = if (palette.isNight) 30_000f else 100_000f
        if (palette.isNight) color = io.github.sceneview.math.Color(1f, 0.86f, 0.7f, 1f)
        lightDirection = Direction(-0.55f, -0.7f, -0.45f)
    }
    // As in the room, through SceneView (google/filament#10565), and a finer map.
    LaunchedEffect(sun) { sun.updateShadowOptions { mapSize = 2048; shadowFar = 12f; normalBias = 3f } }
    SceneView(
        modifier = modifier.onSizeChanged { viewSize = it },
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        autoCenterContent = false,
        contentPadding = contentPadding,
        cameraManipulator = manipulator,
        environmentLoader = environmentLoader,
        environment = environment,
        mainLightNode = sun,
        fillLightNode = rememberFillLightNode(engine) {
            intensity = if (palette.isNight) 900f else DEFAULT_FILL_LIGHT_INTENSITY
        },
    ) {
        val wall = remember(materialLoader) { materialLoader.createColorInstance(palette.wall, roughness = 0.95f) }
        val floor = remember(materialLoader) { materialLoader.createColorInstance(palette.floor, roughness = 0.5f) }
        val frame = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFF4F1EC), roughness = 0.45f) }
        val skirting = remember(materialLoader) { materialLoader.createColorInstance(palette.skirting, roughness = 0.6f) }
        val leaf = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFEDE8E0), roughness = 0.5f) }
        val brass = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFB89A62), metallic = 0.8f, roughness = 0.35f) }
        val oak = remember(materialLoader) { materialLoader.createColorInstance(RoomOak, roughness = 0.55f) }
        SideEffect { wall.setColor(palette.wall); floor.setColor(palette.floor); skirting.setColor(palette.skirting) }
        CubeNode(
            size = Size(OUTER_W, 0.04f, OUTER_W), position = Position(0f, -0.02f, 0f), materialInstance = floor,
            apply = { isShadowReceiver = true },
        )
        Parquet(palette.isNight, Position(0f, 0.001f, 1.2f))
        // A wide view (a tablet or an unfolded phone in landscape) saw the 6.5 × 6 m parquet end
        // on a plain floor, at the sides and in front: copies around it, only then. Phones never
        // see past it.
        if (visibleAspect > 1f) {
            for (i in -1..1) for (j in 0..1) {
                if (i == 0 && j == 0) continue
                Parquet(palette.isNight, Position(i * PARQUET_W, 0.001f, 1.2f + j * PARQUET_D))
            }
        }
        // Tall and wide enough that the camera never sees past it, and a far wall in the next
        // room, so neither the top of the frame nor the doorway opens onto the void. Framed
        // between the buttons and the keyboard, the camera stands further back: at 10 m wide the
        // wall ended at the left edge.
        val wallW = 16f; val wallH = max(4.5f, dh + 0.4f)
        CubeNode(size = Size(wallW, wallH, WALL_THICKNESS), position = Position(0f, wallH / 2f, -3.6f), materialInstance = wall)
        val half = dw / 2f
        CubeNode(size = Size(wallW / 2f - half, wallH, WALL_THICKNESS), position = Position(-(half + (wallW / 2f - half) / 2f), wallH / 2f, 0f), materialInstance = wall)
        CubeNode(size = Size(wallW / 2f - half, wallH, WALL_THICKNESS), position = Position(half + (wallW / 2f - half) / 2f, wallH / 2f, 0f), materialInstance = wall)
        CubeNode(size = Size(dw, wallH - dh, WALL_THICKNESS), position = Position(0f, dh + (wallH - dh) / 2f, 0f), materialInstance = wall)
        // The same wall, higher and wider, out of the light's way (no shadow cast), so the light
        // through the doorway stays as it was. Above the keyboard the camera stands so far back
        // that a 16 × 4.5 m wall still left a black corner at the top left (3.0.5, Pixel 9).
        val sideW = (OUTER_W - wallW) / 2f
        CubeNode(
            size = Size(OUTER_W, OUTER_H - wallH, WALL_THICKNESS), position = Position(0f, wallH + (OUTER_H - wallH) / 2f, 0f),
            materialInstance = wall, apply = { isShadowCaster = false },
        )
        CubeNode(
            size = Size(sideW, wallH, WALL_THICKNESS), position = Position(-(wallW / 2f + sideW / 2f), wallH / 2f, 0f),
            materialInstance = wall, apply = { isShadowCaster = false },
        )
        CubeNode(
            size = Size(sideW, wallH, WALL_THICKNESS), position = Position(wallW / 2f + sideW / 2f, wallH / 2f, 0f),
            materialInstance = wall, apply = { isShadowCaster = false },
        )
        // A doorway, not a white frame on a board: casing standing proud of the wall on both
        // faces, the jambs lining the opening, an oak threshold, skirting along the wall, and the
        // door itself, open into the next room, with its handle.
        val t = WALL_THICKNESS
        val cw = 0.07f // casing width
        for (face in listOf(1f, -1f)) {
            val z = face * (t / 2f + 0.009f)
            CubeNode(size = Size(cw, dh + cw, 0.018f), position = Position(-half - cw / 2f, (dh + cw) / 2f, z), materialInstance = frame)
            CubeNode(size = Size(cw, dh + cw, 0.018f), position = Position(half + cw / 2f, (dh + cw) / 2f, z), materialInstance = frame)
            CubeNode(size = Size(dw + 2f * cw, cw, 0.018f), position = Position(0f, dh + cw / 2f, z), materialInstance = frame)
            // Skirting, stopping at the casing.
            val run = wallW / 2f - half - cw
            CubeNode(size = Size(run, 0.08f, 0.014f), position = Position(-(half + cw + run / 2f), 0.04f, face * (t / 2f + 0.007f)), materialInstance = skirting)
            CubeNode(size = Size(run, 0.08f, 0.014f), position = Position(half + cw + run / 2f, 0.04f, face * (t / 2f + 0.007f)), materialInstance = skirting)
        }
        // Jamb liners 2 mm thin, so the opening stays the width typed.
        CubeNode(size = Size(0.002f, dh, t), position = Position(-half + 0.001f, dh / 2f, 0f), materialInstance = frame)
        CubeNode(size = Size(0.002f, dh, t), position = Position(half - 0.001f, dh / 2f, 0f), materialInstance = frame)
        CubeNode(size = Size(dw, 0.002f, t), position = Position(0f, dh - 0.001f, 0f), materialInstance = frame)
        CubeNode(size = Size(dw, 0.003f, t + 0.02f), position = Position(0f, 0.0015f, 0f), materialInstance = oak)
        // The door, hinged on the left jamb at the far face and swung open past square, along the
        // next room's side of the wall: seen through the opening, never in the object's way.
        Node(position = Position(-half - 0.005f, 0f, -t / 2f), rotation = Rotation(y = 105f)) {
            val leafW = dw - 0.02f
            CubeNode(size = Size(leafW, dh - 0.01f, 0.04f), position = Position(leafW / 2f, (dh - 0.01f) / 2f, -0.02f), materialInstance = leaf)
            CylinderNode(radius = 0.009f, height = 0.13f, position = Position(leafW - 0.07f, 1.02f, 0.012f), rotation = Rotation(z = 90f), materialInstance = brass)
            CylinderNode(radius = 0.009f, height = 0.13f, position = Position(leafW - 0.07f, 1.02f, -0.052f), rotation = Rotation(z = 90f), materialInstance = brass)
        }
        // A light switch beside the frame, at hand height: the scale of a real wall.
        CubeNode(size = Size(0.08f, 0.08f, 0.01f), position = Position(half + cw + 0.16f, 1.1f, t / 2f + 0.005f), materialInstance = frame)

        // The object in the room's own cloth and wood, whatever the answer: in brick, a bed on its
        // side looked like a red-sided prototype box. The answer is the sheet's, and the stop.
        val look = rememberFurnitureLook(accent, roomLit = true)
        // Carried: lifted by half its "up" side, turned about its own centre.
        Node(position = Position(0f, up / 2f, z)) {
            Node(rotation = outerRotation(verdict.carry)) {
                Node(rotation = innerRotation(verdict.carry)) {
                    Node(position = Position(0f, -dims.heightCm / 200f, 0f)) {
                        Furniture(dims, look, contactShadow = false)
                    }
                }
            }
        }
    }
}

private const val WALL_THICKNESS = 0.12f

/**
 * How far the wall and the floor reach, metres: past anything the camera sees, keyboard up. At
 * 48 m a landscape tablet still saw past the wall's end, a black corner at the top left.
 */
private const val OUTER_W = 96f

/** The parquet model's width and depth (tools/furniture, FLOOR_SIZE), metres. */
private const val PARQUET_W = 6.5f
private const val PARQUET_D = 6f
private const val OUTER_H = 16f

/** Where the camera stands and what it looks at. */
internal data class DoorView(val eye: Position, val target: Position)

/**
 * From the front-right and above, looking over the object into the doorway: where the camera
 * stands for the doorway and the object's whole path up to it to fill the visible area, [aspect]
 * wide for 1 high. Before, a fixed distance from the object's length left a bed carried on its
 * side cut at the left edge, and the keyboard cut the top of the door.
 *
 * The object starts [START_GAP] in front of the wall, so its near end is [along] further, and it
 * is [across] wide and [up] high; the frame stands just outside the opening. Room is kept at the
 * top for the back and share buttons.
 */
internal fun doorView(dw: Float, dh: Float, across: Float, up: Float, along: Float, aspect: Float): DoorView {
    val halfW = max(dw / 2f + 0.06f, across / 2f)
    val top = max(dh + 0.06f, up)
    val back = -(WALL_THICKNESS / 2f + 0.015f)
    val front = along + START_GAP
    val bounds = Aabb(
        center = Position(0f, top / 2f, (front + back) / 2f),
        halfExtent = Position(halfW, top / 2f, (front - back) / 2f),
    )
    val fit = fitCameraToBounds(
        bounds,
        direction = VIEW_DIRECTION,
        verticalFovDegrees = verticalFovDegreesForFocalLength(DEFAULT_FOCAL_LENGTH_MM),
        aspect = aspect.toDouble(),
        widthFill = 0.88f,
        heightFill = 0.78f,
    )
    return if (fit != null) DoorView(eye = fit.eye, target = fit.target)
    else DoorView(eye = Position(2f, 2.5f, along + 4f), target = Position(0f, dh * 0.42f, along * 0.3f))
}

/** Front-right and above, a three-quarter view down into the doorway. */
internal val VIEW_DIRECTION = Position(-0.42f, -0.42f, -0.8f)

/** SceneView's default lens, which this scene keeps. */
internal const val DEFAULT_FOCAL_LENGTH_MM = 28.0

/** The gap between the wall and the object's far end where it starts, metres. */
internal const val START_GAP = 0.55f

// Object axes: x = width, y = height, z = depth. Inner rotation first, then the outer yaw.
// See Carry: each maps (across, up, along) onto world (x, y, z).
private fun innerRotation(c: Carry): Rotation = when (c) {
    Carry.UPRIGHT, Carry.UPRIGHT_SIDEWAYS -> Rotation()
    Carry.ON_ITS_SIDE, Carry.ON_ITS_SIDE_SIDEWAYS -> Rotation(z = 90f)
    Carry.ON_ITS_BACK, Carry.ON_ITS_BACK_SIDEWAYS -> Rotation(x = -90f)
}

private fun outerRotation(c: Carry): Rotation = when (c) {
    Carry.UPRIGHT, Carry.ON_ITS_SIDE, Carry.ON_ITS_BACK -> Rotation()
    Carry.UPRIGHT_SIDEWAYS, Carry.ON_ITS_SIDE_SIDEWAYS, Carry.ON_ITS_BACK_SIDEWAYS -> Rotation(y = 90f)
}
