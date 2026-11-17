package com.gorisse.thomas.willitfit.scene

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.gorisse.thomas.willitfit.fit.Carry
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.DoorVerdict
import com.gorisse.thomas.willitfit.fit.side
import io.github.sceneview.SceneView
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
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
    val startZ = along / 2f + 0.55f
    val endZ = -(along / 2f + 0.5f)
    val contactZ = WALL_THICKNESS / 2f + along / 2f + 0.005f
    // Held for a picture: the object in the doorway, or against the frame.
    val at = if (hold) (if (verdict.fits) 0.5f else stopAt) else progress
    val z = if (verdict.fits) startZ + (endZ - startZ) * at
    else startZ + (contactZ - startZ) * (at / stopAt)

    val view = doorView(dw = dw, dh = dh, along = along, fits = verdict.fits)
    val manipulator = rememberCameraManipulator(orbitHomePosition = view.eye, targetPosition = view.target)
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        autoCenterContent = false,
        contentPadding = contentPadding,
        cameraManipulator = manipulator,
        mainLightNode = rememberMainLightNode(engine) {
            intensity = if (palette.isNight) 30_000f else 110_000f
        },
    ) {
        val wall = remember(materialLoader) { materialLoader.createColorInstance(palette.wall, roughness = 0.95f) }
        val floor = remember(materialLoader) { materialLoader.createColorInstance(palette.floor, roughness = 0.5f) }
        val frame = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFF4F1EC), roughness = 0.5f) }
        SideEffect { wall.setColor(palette.wall); floor.setColor(palette.floor) }
        CubeNode(size = Size(12f, 0.04f, 12f), position = Position(0f, -0.02f, 0f), materialInstance = floor)
        Parquet(palette.isNight, Position(0f, 0.001f, 1.2f))
        // Tall and wide enough that the camera never sees past it, and a far wall in the next
        // room, so neither the top of the frame nor the doorway opens onto the void.
        val wallW = 10f; val wallH = max(4.5f, dh + 0.4f)
        CubeNode(size = Size(wallW, wallH, WALL_THICKNESS), position = Position(0f, wallH / 2f, -3.6f), materialInstance = wall)
        val half = dw / 2f
        CubeNode(size = Size(wallW / 2f - half, wallH, WALL_THICKNESS), position = Position(-(half + (wallW / 2f - half) / 2f), wallH / 2f, 0f), materialInstance = wall)
        CubeNode(size = Size(wallW / 2f - half, wallH, WALL_THICKNESS), position = Position(half + (wallW / 2f - half) / 2f, wallH / 2f, 0f), materialInstance = wall)
        CubeNode(size = Size(dw, wallH - dh, WALL_THICKNESS), position = Position(0f, dh + (wallH - dh) / 2f, 0f), materialInstance = wall)
        // Casing around the opening.
        CubeNode(size = Size(0.06f, dh, WALL_THICKNESS + 0.03f), position = Position(-half - 0.03f, dh / 2f, 0f), materialInstance = frame)
        CubeNode(size = Size(0.06f, dh, WALL_THICKNESS + 0.03f), position = Position(half + 0.03f, dh / 2f, 0f), materialInstance = frame)
        CubeNode(size = Size(dw + 0.12f, 0.06f, WALL_THICKNESS + 0.03f), position = Position(0f, dh + 0.03f, 0f), materialInstance = frame)

        val look = rememberFurnitureLook(if (verdict.fits) accent else MissBrick, roomLit = true)
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
 * The object that does not go through: a warm brick that still says "no" at a glance, in the
 * room's own palette, instead of the alarm red of an error banner. The velvet sheen lifts it.
 */
private val MissBrick = Color(0xFF8C3A2E)

/** Where the camera stands and what it looks at. */
internal data class DoorView(val eye: Position, val target: Position)

/**
 * From the front-right and above, looking over the object into the doorway. The object comes
 * at the door along its own length, towards the camera: the camera stands back by that length
 * so the whole object and the opening are both in view, all the way through the carry (before,
 * a three-seat sofa filled the screen and the camera stood inside its path). A miss rests
 * against the frame, so the view leans a little more towards the object.
 */
internal fun doorView(dw: Float, dh: Float, along: Float, fits: Boolean): DoorView {
    val targetZ = along * (if (fits) 0.25f else 0.4f)
    val dist = max(max(dw, dh) + 1.4f, 1.4f * along + 1.6f)
    return DoorView(
        eye = Position(dist * 0.42f, dh * 0.45f + dist * 0.42f, targetZ + dist * 0.8f),
        target = Position(0f, dh * 0.42f, targetZ),
    )
}

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
