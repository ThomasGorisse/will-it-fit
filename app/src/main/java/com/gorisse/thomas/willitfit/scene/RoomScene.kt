package com.gorisse.thomas.willitfit.scene

import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import io.github.sceneview.rememberModelInstance
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.filament.LightManager
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.shape
import io.github.sceneview.SceneScope
import io.github.sceneview.SceneView
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Room colours. Day for the light theme, a lamp-lit evening for the dark one. */
data class RoomPalette(
    val wall: Color,
    val floor: Color,
    val skirting: Color,
    val window: Color,
    val lamp: Color,
    val isNight: Boolean,
) {
    companion object {
        val Day = RoomPalette(
            wall = Color(0xFFEDE7DF), floor = Color(0xFFCDAE88), skirting = Color(0xFFF7F4EF),
            window = Color(0xFFFFF7E6), lamp = Color(0xFFFFE2B0), isNight = false,
        )
        val Night = RoomPalette(
            wall = Color(0xFF5B5550), floor = Color(0xFF6E5038), skirting = Color(0xFF6A645E),
            window = Color(0xFF22324F), lamp = Color(0xFFFFC27A), isNight = true,
        )
    }
}

// The room is fixed; the object stands against the back wall, as you would push it.
private const val BACK_WALL_Z = -1.5f
private const val LEFT_WALL_X = -2.3f
private const val ROOM_WIDTH = 6.5f
private const val ROOM_DEPTH = 6f
private const val WALL_HEIGHT = 2.6f
private const val WALL_T = 0.1f

/**
 * The first screen: a room corner, the object standing in it at its size with its dimensions
 * written on the floor. One finger turns around it, a pinch comes closer. Each new size grows
 * the object into place; between touches the room sways a few degrees so it never reads as a
 * still image.
 */
@Composable
fun RoomScene(
    dims: Dimensions,
    unit: LengthUnit,
    accent: Color,
    palette: RoomPalette,
    labelText: Color,
    labelBackground: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    // Animated size: a typed value or a new preset morphs the object rather than swapping it.
    val spec = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow)
    val w by animateFloatAsState(dims.widthCm, spec, label = "w")
    val d by animateFloatAsState(dims.depthCm, spec, label = "d")
    val h by animateFloatAsState(dims.heightCm, spec, label = "h")
    val shown = dims.copy(widthCm = w, depthCm = d, heightCm = h)

    // A short pop each time the silhouette changes (sofa -> bed).
    val pop = remember { Animatable(1f) }
    LaunchedEffect(dims.shape) {
        pop.snapTo(0.82f)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }

    // Idle sway, paused for a few seconds after every touch.
    var lastTouch by remember { mutableLongStateOf(0L) }
    var sway by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val idle = android.os.SystemClock.uptimeMillis() - lastTouch > 3_500
                val target = if (idle) (sin((now - start) / 2600.0) * 7.0).toFloat() else sway
                // Ease towards the target so a resumed sway never jumps.
                sway += (target - sway) * 0.04f
            }
        }
    }

    // Object pushed against the back wall, centred left-right.
    val objZ = BACK_WALL_Z + WALL_T / 2f + 0.04f + dims.depthCm / 200f
    val size = max(dims.widthCm, max(dims.depthCm, dims.heightCm)) / 100f
    val dist = 1.55f * size + 1.7f
    val target = Position(0f, dims.heightCm / 100f * 0.42f, objZ)
    val dir = Triple(0.62f, 0.5f, 1f).let { (x, y, z) ->
        val n = sqrt(x * x + y * y + z * z); Triple(x / n, y / n, z / n)
    }
    val eye = Position(target.x + dir.first * dist, target.y + dir.second * dist, target.z + dir.third * dist)
    val manipulator = rememberCameraManipulator(orbitHomePosition = eye, targetPosition = target)
    val cameraNode = rememberCameraNode(engine)

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        autoCenterContent = false,
        contentPadding = contentPadding,
        cameraNode = cameraNode,
        cameraManipulator = manipulator,
        mainLightNode = rememberMainLightNode(engine) {
            intensity = if (palette.isNight) 28_000f else 80_000f
            // Low afternoon sun from the window side: the object throws a real shadow sideways
            // onto the floor, as in the target picture, instead of straight down under itself.
            lightDirection = Direction(-0.6f, -0.72f, -0.18f)
        },
        onTouchEvent = { e: MotionEvent, _ ->
            lastTouch = android.os.SystemClock.uptimeMillis()
            false
        },
    ) {
        Node(rotation = Rotation(y = sway), position = Position(0f, 0f, objZ)) {
            Node(position = Position(0f, 0f, -objZ)) {
                Room(palette)
            }
            val look = rememberFurnitureLook(accent)
            Node(scale = Scale(pop.value)) {
                Furniture(shown, look)
            }
            DimensionMarks(
                dims = shown, unit = unit,
                lineColor = Color.White,
                labelText = labelText, labelBackground = labelBackground,
                labelScale = (size / 1.4f).coerceIn(1f, 1.8f),
                facing = { cameraNode.worldPosition },
            )
        }
    }
}

@Composable
private fun SceneScope.Room(p: RoomPalette) {
    val wall = remember(materialLoader) { materialLoader.createColorInstance(p.wall, roughness = 0.95f) }
    val floor = remember(materialLoader) { materialLoader.createColorInstance(p.floor, roughness = 0.5f) }
    val skirting = remember(materialLoader) { materialLoader.createColorInstance(p.skirting, roughness = 0.6f) }
    val window = remember(materialLoader) { materialLoader.createUnlitColorInstance(p.window) }
    val lamp = remember(materialLoader) { materialLoader.createUnlitColorInstance(p.lamp) }
    val frame = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFF4F1EC), roughness = 0.5f) }
    SideEffect {
        wall.setColor(p.wall); floor.setColor(p.floor); skirting.setColor(p.skirting)
        window.setColor(p.window); lamp.setColor(p.lamp)
    }
    val cx = LEFT_WALL_X + ROOM_WIDTH / 2f
    val cz = BACK_WALL_Z + ROOM_DEPTH / 2f
    // Floor: modelled oak planks when they ship with the app (tools/furniture), else a flat
    // colour with thin seams. The flat floor stays underneath as the base either way.
    CubeNode(
        size = Size(ROOM_WIDTH, 0.04f, ROOM_DEPTH), position = Position(cx, -0.02f, cz), materialInstance = floor,
        apply = { isShadowReceiver = true },
    )
    if (!Parquet(p.isNight, Position(cx, 0.001f, cz))) {
        var x = LEFT_WALL_X + 0.18f
        while (x < LEFT_WALL_X + ROOM_WIDTH) {
            CubeNode(size = Size(0.004f, 0.001f, ROOM_DEPTH), position = Position(x, 0.0005f, cz), materialInstance = skirting)
            x += 0.18f
        }
    }
    // Back wall with a window on the right.
    val winX0 = 1.35f; val winX1 = 2.55f; val winY0 = 0.85f; val winY1 = 2.15f
    val backZ = BACK_WALL_Z
    val right = LEFT_WALL_X + ROOM_WIDTH
    wallPiece(wall, LEFT_WALL_X, winX0, 0f, WALL_HEIGHT, backZ)
    wallPiece(wall, winX1, right, 0f, WALL_HEIGHT, backZ)
    wallPiece(wall, winX0, winX1, 0f, winY0, backZ)
    wallPiece(wall, winX0, winX1, winY1, WALL_HEIGHT, backZ)
    CubeNode(size = Size(winX1 - winX0, winY1 - winY0, 0.01f), position = Position((winX0 + winX1) / 2f, (winY0 + winY1) / 2f, backZ - 0.03f), materialInstance = window)
    CubeNode(size = Size(0.04f, winY1 - winY0, 0.06f), position = Position((winX0 + winX1) / 2f, (winY0 + winY1) / 2f, backZ), materialInstance = frame)
    CubeNode(size = Size(winX1 - winX0 + 0.1f, 0.04f, 0.16f), position = Position((winX0 + winX1) / 2f, winY0 - 0.02f, backZ + 0.05f), materialInstance = frame)
    // Left wall with a doorway, nearer the viewer.
    val doorZ0 = 0.1f; val doorZ1 = 0.93f; val doorH = 2.04f
    val lx = LEFT_WALL_X
    sideWallPiece(wall, lx, BACK_WALL_Z, doorZ0, 0f, WALL_HEIGHT)
    sideWallPiece(wall, lx, doorZ1, BACK_WALL_Z + ROOM_DEPTH, 0f, WALL_HEIGHT)
    sideWallPiece(wall, lx, doorZ0, doorZ1, doorH, WALL_HEIGHT)
    // Door casing.
    CubeNode(size = Size(0.14f, doorH, 0.05f), position = Position(lx, doorH / 2f, doorZ0 - 0.025f), materialInstance = frame)
    CubeNode(size = Size(0.14f, doorH, 0.05f), position = Position(lx, doorH / 2f, doorZ1 + 0.025f), materialInstance = frame)
    CubeNode(size = Size(0.14f, 0.05f, doorZ1 - doorZ0 + 0.1f), position = Position(lx, doorH + 0.025f, (doorZ0 + doorZ1) / 2f), materialInstance = frame)
    // Skirting boards.
    CubeNode(size = Size(ROOM_WIDTH, 0.08f, 0.015f), position = Position(cx, 0.04f, backZ + WALL_T / 2f + 0.0075f), materialInstance = skirting)
    CubeNode(size = Size(0.015f, 0.08f, doorZ0 - BACK_WALL_Z), position = Position(lx + WALL_T / 2f + 0.0075f, 0.04f, (BACK_WALL_Z + doorZ0) / 2f), materialInstance = skirting)
    // Rug in front, picture above where the object stands, a plant by the window.
    val rug = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFD9CFC2), roughness = 1f) }
    CubeNode(size = Size(2.2f, 0.012f, 1.5f), position = Position(-0.9f, 0.006f, 1.25f), rotation = Rotation(y = 8f), materialInstance = rug)
    val art = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFF8FA89A), roughness = 0.9f) }
    val artSky = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFE9E1D3), roughness = 0.9f) }
    CubeNode(size = Size(0.9f, 0.62f, 0.03f), position = Position(-0.2f, 1.75f, backZ + WALL_T / 2f + 0.015f), materialInstance = frame)
    CubeNode(size = Size(0.8f, 0.52f, 0.01f), position = Position(-0.2f, 1.75f, backZ + WALL_T / 2f + 0.031f), materialInstance = artSky)
    CubeNode(size = Size(0.8f, 0.2f, 0.012f), position = Position(-0.2f, 1.59f, backZ + WALL_T / 2f + 0.033f), materialInstance = art)
    val pot = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFE8E2DA), roughness = 0.7f) }
    val leaf = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFF3F6B45), roughness = 0.8f) }
    val plantX = 2.85f; val plantZ = BACK_WALL_Z + 0.4f
    CylinderNode(radius = 0.17f, height = 0.38f, position = Position(plantX, 0.19f, plantZ), materialInstance = pot, apply = { isShadowCaster = true })
    SphereNode(radius = 0.3f, position = Position(plantX, 0.72f, plantZ), materialInstance = leaf, apply = { isShadowCaster = true })
    SphereNode(radius = 0.22f, position = Position(plantX - 0.12f, 1.0f, plantZ + 0.05f), materialInstance = leaf, apply = { isShadowCaster = true })
    SphereNode(radius = 0.18f, position = Position(plantX + 0.1f, 1.18f, plantZ - 0.05f), materialInstance = leaf, apply = { isShadowCaster = true })

    // Floor lamp in the back-left corner: the evening light of the dark theme.
    val lampX = LEFT_WALL_X + 0.35f; val lampZ = BACK_WALL_Z + 0.35f
    CylinderNode(radius = 0.13f, height = 0.02f, position = Position(lampX, 0.01f, lampZ), materialInstance = skirting)
    CylinderNode(radius = 0.012f, height = 1.45f, position = Position(lampX, 0.73f, lampZ), materialInstance = skirting)
    CylinderNode(radius = 0.17f, height = 0.26f, position = Position(lampX, 1.55f, lampZ), materialInstance = lamp)
    if (p.isNight) {
        LightNode(
            type = LightManager.Type.POINT,
            intensity = 60_000f,
            position = Position(lampX, 1.5f, lampZ + 0.1f),
            color = io.github.sceneview.math.Color(1f, 0.78f, 0.52f, 1f),
            apply = { falloff(6f) },
        )
    }
}

@Composable
private fun SceneScope.wallPiece(mat: com.google.android.filament.MaterialInstance, x0: Float, x1: Float, y0: Float, y1: Float, z: Float) {
    CubeNode(size = Size(x1 - x0, y1 - y0, WALL_T), position = Position((x0 + x1) / 2f, (y0 + y1) / 2f, z), materialInstance = mat)
}

@Composable
private fun SceneScope.sideWallPiece(mat: com.google.android.filament.MaterialInstance, x: Float, z0: Float, z1: Float, y0: Float, y1: Float) {
    CubeNode(size = Size(WALL_T, y1 - y0, z1 - z0), position = Position(x, (y0 + y1) / 2f, (z0 + z1) / 2f), materialInstance = mat)
}

/**
 * Modelled oak planks (tools/furniture, `--floor`), their top face on the floor plane, centred
 * on [centre]. Returns false when the files do not ship with the app, so the caller can draw
 * its plain floor instead.
 */
@Composable
internal fun SceneScope.Parquet(night: Boolean, centre: Position): Boolean {
    val file = if (night) "models/floor_night.glb" else "models/floor.glb"
    val context = LocalContext.current
    val bundled = remember(file) { runCatching { context.assets.open(file).close() }.isSuccess }
    if (!bundled) return false
    key(file) {
        rememberModelInstance(modelLoader, file)?.let { planks ->
            ModelNode(
                modelInstance = planks,
                autoAnimate = false,
                centerOrigin = Position(0f, 1f, 0f),
                position = centre,
                apply = { isShadowReceiver = true; isShadowCaster = false },
            )
        }
    }
    return true
}
