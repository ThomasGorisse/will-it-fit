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
                durationMillis = 5200
                0f at 0
                0f at 700
                stopAt at 3000 using FastOutSlowInEasing
                stopAt at 4300
                0f at 5200 using FastOutSlowInEasing
            },
            RepeatMode.Restart,
        ),
        label = "progress",
    )
    val startZ = 0.4f + along / 2f + 0.6f
    val endZ = -(along / 2f + 0.5f)
    val contactZ = WALL_THICKNESS / 2f + along / 2f + 0.005f
    val z = if (verdict.fits) startZ + (endZ - startZ) * progress
    else startZ + (contactZ - startZ) * (progress / stopAt)

    val size = max(max(dw, dh), max(across, up))
    val dist = 1.4f * size + 2.4f
    val manipulator = rememberCameraManipulator(
        orbitHomePosition = Position(dist * 0.55f, dh * 0.55f + dist * 0.28f, dist * 0.8f),
        targetPosition = Position(0f, dh * 0.45f, 0f),
    )
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
        CubeNode(size = Size(8f, 0.04f, 8f), position = Position(0f, -0.02f, 0f), materialInstance = floor)
        val wallW = 6f; val wallH = max(2.6f, dh + 0.4f)
        val half = dw / 2f
        CubeNode(size = Size(wallW / 2f - half, wallH, WALL_THICKNESS), position = Position(-(half + (wallW / 2f - half) / 2f), wallH / 2f, 0f), materialInstance = wall)
        CubeNode(size = Size(wallW / 2f - half, wallH, WALL_THICKNESS), position = Position(half + (wallW / 2f - half) / 2f, wallH / 2f, 0f), materialInstance = wall)
        CubeNode(size = Size(dw, wallH - dh, WALL_THICKNESS), position = Position(0f, dh + (wallH - dh) / 2f, 0f), materialInstance = wall)
        // Casing around the opening.
        CubeNode(size = Size(0.06f, dh, WALL_THICKNESS + 0.03f), position = Position(-half - 0.03f, dh / 2f, 0f), materialInstance = frame)
        CubeNode(size = Size(0.06f, dh, WALL_THICKNESS + 0.03f), position = Position(half + 0.03f, dh / 2f, 0f), materialInstance = frame)
        CubeNode(size = Size(dw + 0.12f, 0.06f, WALL_THICKNESS + 0.03f), position = Position(0f, dh + 0.03f, 0f), materialInstance = frame)

        val look = rememberFurnitureLook(if (verdict.fits) accent else Color(0xFFB3261E))
        // Carried: lifted by half its "up" side, turned about its own centre.
        Node(position = Position(0f, up / 2f, z)) {
            Node(rotation = outerRotation(verdict.carry)) {
                Node(rotation = innerRotation(verdict.carry)) {
                    Node(position = Position(0f, -dims.heightCm / 200f, 0f)) {
                        Furniture(dims, look)
                    }
                }
            }
        }
    }
}

private const val WALL_THICKNESS = 0.12f

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
