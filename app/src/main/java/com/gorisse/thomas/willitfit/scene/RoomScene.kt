package com.gorisse.thomas.willitfit.scene

import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalWindowInfo
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
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberView
import io.github.sceneview.DEFAULT_FILL_LIGHT_INTENSITY
import com.google.android.filament.Colors
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
    val rug: Color,
    val curtain: Color,
    val isNight: Boolean,
) {
    companion object {
        val Day = RoomPalette(
            wall = Color(0xFFF5E9D9), floor = Color(0xFFB59C80), skirting = Color(0xFFF7F4EF),
            window = Color(0xFFFFF7E6), lamp = Color(0xFFFFE2B0), rug = Color(0xFFC4B8A6),
            curtain = Color(0xFFF2EADF), isNight = false,
        )
        val Night = RoomPalette(
            // Warm taupe walls, a dusky window and a dim rug: lamp-lit, not a cold grey box.
            wall = Color(0xFF7A6656), floor = Color(0xFF6E5038), skirting = Color(0xFF7A6C60),
            window = Color(0xFF2C3646), lamp = Color(0xFFFFC27A), rug = Color(0xFF6B5F52),
            curtain = Color(0xFF7C6C5C), isNight = true,
        )
    }
}

// The room is fixed; the object stands against the back wall, as you would push it.
private const val BACK_WALL_Z = -1.5f
private const val LEFT_WALL_X = -2.3f
private const val ROOM_WIDTH = 6.5f
private const val ROOM_DEPTH = 6f
// 3 m, not 2.6: framing a 2 m wardrobe, the camera looked over a 2.6 m wall into the black.
private const val WALL_HEIGHT = 3f
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
    // Near eye level and three-quarter front, like the product shot of the target picture.
    // Framed on what the camera actually sees: the width and depth as they turn towards it, or
    // the height with its tag on top, whichever is larger; deep objects (a bed) come nearer the
    // lens at the front, so they stand further back. Aimed a little right of the object, so it
    // sits left of centre and its depth and height tags keep the room on the right.
    val ow = dims.widthCm / 100f; val od = dims.depthCm / 100f; val oh = dims.heightCm / 100f
    val seen = max(0.91f * ow + 0.41f * od, oh + 0.27f * od + 0.3f)
    // Taller, narrower phones (a Pixel 4a or 9, about 2.2:1) see less width than a 2:1 screen,
    // so the camera steps back in proportion and the object and its tags keep their margins.
    // The whole window, edge to edge: screenHeightDp leaves the system bars out and reads 2:1.
    val window = LocalWindowInfo.current.containerSize
    val narrow = (window.height.toFloat() / window.width.coerceAtLeast(1)).coerceIn(2f, 2.6f) / 2f
    // A step further back than a product shot: the room (rug, lamp, window) gives the scale.
    val dist = (1.2f * seen + 0.5f * od + 0.9f) * narrow * 1.1f
    val target = Position(0.11f * seen, oh * 0.5f, objZ)
    val dir = Triple(0.45f, 0.3f, 1f).let { (x, y, z) ->
        val n = sqrt(x * x + y * y + z * z); Triple(x / n, y / n, z / n)
    }
    val eye = Position(target.x + dir.first * dist, target.y + dir.second * dist, target.z + dir.third * dist)
    val manipulator = rememberCameraManipulator(orbitHomePosition = eye, targetPosition = target)
    val cameraNode = rememberCameraNode(engine)

    // Evening: the lamp is the light. The sky's ambient (IBL) and the fill light drop low, so the
    // room falls dark towards its edges around the lamp's warm pool, as in the target.
    val view = rememberView(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(environmentLoader)
    val dayIbl = remember(environment) { environment.indirectLight?.intensity ?: 30_000f }
    SideEffect { environment.indirectLight?.intensity = if (palette.isNight) dayIbl * 0.45f else dayIbl }

    val sun = rememberMainLightNode(engine) {
        intensity = if (palette.isNight) 12_000f else 80_000f
        // Evening light is the lamp's: warm, like the target's dark picture.
        if (palette.isNight) color = io.github.sceneview.math.Color(1f, 0.85f, 0.68f, 1f)
        // Low afternoon sun from the window side: the object throws a real shadow sideways
        // onto the floor, as in the target picture, instead of straight down under itself.
        // It comes partly from the front too: grazing the fronts edge-on (z -0.18), it left
        // them dark and hatched with shadow acne.
        lightDirection = Direction(-0.55f, -0.7f, -0.45f)
    }
    // Set once, through SceneView: the raw LightManager.setShadowOptions of Filament 1.77 writes the
    // shadow transform in the wrong order (google/filament#10565). Re-applied on every frame of the
    // sway, it flipped the sun's shadows from run to run: the whole room shaded some runs, a grey
    // square or L under the height tag on a wardrobe's door others. A finer map kept to the room:
    // at the default 1024 over the whole view, a wardrobe's own frame laid a jagged streak down its
    // door.
    LaunchedEffect(sun) { sun.updateShadowOptions { mapSize = 2048; shadowFar = 10f; normalBias = 3f } }
    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        view = view,
        environment = environment,
        fillLightNode = rememberFillLightNode(engine) {
            intensity = if (palette.isNight) 600f else DEFAULT_FILL_LIGHT_INTENSITY
        },
        autoCenterContent = false,
        contentPadding = contentPadding,
        cameraNode = cameraNode,
        cameraManipulator = manipulator,
        mainLightNode = sun,
        onTouchEvent = { e: MotionEvent, _ ->
            lastTouch = android.os.SystemClock.uptimeMillis()
            false
        },
    ) {
        Node(rotation = Rotation(y = sway), position = Position(0f, 0f, objZ)) {
            Node(position = Position(0f, 0f, -objZ)) {
                // The floor lamp stands right of the object and of its height line, as in the
                // target, whatever its width, and stays whole in the frame.
                Room(palette, lampX = shown.widthCm / 200f + 0.4f)
            }
            val look = rememberFurnitureLook(accent, roomLit = true)
            Node(scale = Scale(pop.value)) {
                Furniture(shown, look)
            }
            DimensionMarks(
                dims = shown, unit = unit,
                lineColor = Color.White,
                labelText = labelText, labelBackground = labelBackground,
                labelScale = (size / 1.4f).coerceIn(1f, 1.8f),
                facing = { cameraNode.worldPosition },
                heightOffset = 0.2f,
                // The camera's right, flat: its view direction turned a quarter.
                heightTagSide = sqrt(dir.first * dir.first + dir.third * dir.third).let { Direction(dir.third / it, 0f, -dir.first / it) },
            )
        }
    }
    // After SceneView, so its render-quality preset (applied once) does not undo it: a little
    // more bloom in the evening, for the lamp's halo. No vignette: the view's centre sits under
    // the sheet, so a screen vignette darkens the whole visible room, not its edges.
    LaunchedEffect(view, palette.isNight) {
        view.bloomOptions = view.bloomOptions.apply { strength = if (palette.isNight) 0.2f else 0.1f }
    }
}

@Composable
private fun SceneScope.Room(p: RoomPalette, lampX: Float) {
    val wall = remember(materialLoader) { materialLoader.createColorInstance(p.wall, roughness = 0.95f) }
    val floor = remember(materialLoader) { materialLoader.createColorInstance(p.floor, roughness = 0.5f) }
    val skirting = remember(materialLoader) { materialLoader.createColorInstance(p.skirting, roughness = 0.6f) }
    val window = remember(materialLoader) { materialLoader.createUnlitColorInstance(p.window) }
    val lamp = remember(materialLoader) { materialLoader.createUnlitColorInstance(p.lamp) }
    // Brighter than white (linear HDR): the bloom turns the lit shade into a glow.
    val glow = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0xFFFFF1DA)) }
    val halo = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0x1FFFC98A)) }
    val haloWide = remember(materialLoader) { materialLoader.createUnlitColorInstance(Color(0x0FFFB870)) }
    val shade = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFF3ECE2), roughness = 0.9f) }
    val bronze = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFF4A4038), metallic = 0.4f, roughness = 0.5f) }
    val curtain = remember(materialLoader) { materialLoader.createColorInstance(p.curtain, roughness = 1f) }
    val frame = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFF4F1EC), roughness = 0.5f) }
    SideEffect {
        wall.setColor(p.wall); floor.setColor(p.floor); skirting.setColor(p.skirting)
        window.setColor(p.window); lamp.setColor(p.lamp); curtain.setColor(p.curtain)
        glow.setParameter("color", Colors.RgbaType.LINEAR, 4f, 3.3f, 2.4f, 1f)
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
    // Floor-length linen curtains on a rod: what makes the dark pane read as a window at night.
    val cz0 = backZ + WALL_T / 2f + 0.07f
    Curtain(curtain, winX0 - 0.2f, winX0 + 0.16f, cz0)
    Curtain(curtain, winX1 - 0.16f, winX1 + 0.2f, cz0)
    CylinderNode(
        radius = 0.012f, height = winX1 - winX0 + 0.7f, position = Position((winX0 + winX1) / 2f, 2.44f, cz0),
        rotation = Rotation(z = 90f), materialInstance = bronze,
    )
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
    if (!KnitRug(p, Position(-0.9f, 0.002f, 1.25f), Rotation(y = 8f))) {
        val rug = remember(materialLoader) { materialLoader.createColorInstance(p.rug, roughness = 1f) }
        SideEffect { rug.setColor(p.rug) }
        CubeNode(size = Size(2.2f, 0.012f, 1.5f), position = Position(-0.9f, 0.006f, 1.25f), rotation = Rotation(y = 8f), materialInstance = rug)
    }
    val art = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFF8FA89A), roughness = 0.9f) }
    val artSky = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFE9E1D3), roughness = 0.9f) }
    // A small print above the object, as in the target, not a poster cut by the top of the view.
    CubeNode(size = Size(0.62f, 0.46f, 0.03f), position = Position(-0.15f, 1.52f, backZ + WALL_T / 2f + 0.015f), materialInstance = frame)
    CubeNode(size = Size(0.54f, 0.38f, 0.01f), position = Position(-0.15f, 1.52f, backZ + WALL_T / 2f + 0.031f), materialInstance = artSky)
    CubeNode(size = Size(0.54f, 0.14f, 0.012f), position = Position(-0.15f, 1.40f, backZ + WALL_T / 2f + 0.033f), materialInstance = art)
    val pot = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFE8E2DA), roughness = 0.7f) }
    val leaf = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFF3F6B45), roughness = 0.8f) }
    val plantX = 2.85f; val plantZ = BACK_WALL_Z + 0.4f
    CylinderNode(radius = 0.17f, height = 0.38f, position = Position(plantX, 0.19f, plantZ), materialInstance = pot, apply = { isShadowCaster = true })
    SphereNode(radius = 0.3f, position = Position(plantX, 0.72f, plantZ), materialInstance = leaf, apply = { isShadowCaster = true })
    SphereNode(radius = 0.22f, position = Position(plantX - 0.12f, 1.0f, plantZ + 0.05f), materialInstance = leaf, apply = { isShadowCaster = true })
    SphereNode(radius = 0.18f, position = Position(plantX + 0.1f, 1.18f, plantZ - 0.05f), materialInstance = leaf, apply = { isShadowCaster = true })

    // Floor lamp beside the object: a linen drum by day, the evening's warm light by night,
    // throwing a pool of light on the floor next to the object, as in the target.
    val lampZ = BACK_WALL_Z + 0.32f
    CylinderNode(radius = 0.14f, height = 0.02f, position = Position(lampX, 0.011f, lampZ), materialInstance = bronze, apply = { isShadowCaster = true })
    CylinderNode(radius = 0.012f, height = 1.28f, position = Position(lampX, 0.66f, lampZ), materialInstance = bronze, apply = { isShadowCaster = true })
    CylinderNode(
        radius = 0.15f, height = 0.24f, position = Position(lampX, 1.42f, lampZ),
        // The shade casts no shadow: the low sun would lay it as a dark blotch across the object.
        materialInstance = if (p.isNight) glow else shade, apply = { isShadowCaster = false },
    )
    if (p.isNight) {
        // A soft halo round the lit shade, then the light itself just under it: a warm pool on
        // the floor, the wall behind and the object's near side, raking across the velvet.
        SphereNode(radius = 0.24f, position = Position(lampX, 1.42f, lampZ), materialInstance = halo, apply = { isShadowCaster = false; isShadowReceiver = false })
        SphereNode(radius = 0.36f, position = Position(lampX, 1.42f, lampZ), materialInstance = haloWide, apply = { isShadowCaster = false; isShadowReceiver = false })
        LightNode(
            type = LightManager.Type.POINT,
            intensity = 300_000f,
            position = Position(lampX, 1.22f, lampZ + 0.12f),
            color = io.github.sceneview.math.Color(1f, 0.78f, 0.52f, 1f),
            apply = { falloff(3.8f) },
        )
        // And a cone from the shade down and forward: the warm pool on the floor in front of
        // the object's near end, where the camera sees it (the lamp's own foot is behind it).
        LightNode(
            type = LightManager.Type.SPOT,
            intensity = 220_000f,
            position = Position(lampX, 1.3f, lampZ + 0.05f),
            direction = Direction(-0.2f, -1f, 0.7f),
            color = io.github.sceneview.math.Color(1f, 0.76f, 0.5f, 1f),
            apply = { falloff(4f); spotLightCone(0.35f, 0.95f) },
        )
    }
}

/** One curtain panel, gathered in soft vertical folds, from the rod down to the floor. */
@Composable
private fun SceneScope.Curtain(mat: com.google.android.filament.MaterialInstance, x0: Float, x1: Float, z: Float) {
    val folds = 6
    val r = (x1 - x0) / (2f * folds) * 1.25f
    for (i in 0 until folds) {
        val x = x0 + (x1 - x0) * (i + 0.5f) / folds
        CylinderNode(
            radius = r, height = 2.38f, position = Position(x, 1.23f, z + if (i % 2 == 0) 0.012f else -0.008f),
            materialInstance = mat, apply = { isShadowCaster = true },
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
 * The knit rug (models/rug.glb, the one textured asset) in the foreground: its stitches give the
 * room depth. Dimmed in the evening through its base colour factor. False when not bundled.
 */
@Composable
private fun SceneScope.KnitRug(p: RoomPalette, position: Position, rotation: Rotation): Boolean {
    val file = "models/rug.glb"
    val context = LocalContext.current
    val bundled = remember { runCatching { context.assets.open(file).close() }.isSuccess }
    if (!bundled) return false
    rememberModelInstance(modelLoader, file)?.let { rug ->
        SideEffect {
            val tint = if (p.isNight) Color(0xFF6E655C) else Color(0xFFB5AB9F)
            rug.materialInstances.forEach { it.setColor("baseColorFactor", tint) }
        }
        ModelNode(
            modelInstance = rug,
            autoAnimate = false,
            position = position,
            rotation = rotation,
            apply = { isShadowReceiver = true; isShadowCaster = false },
        )
    }
    return true
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
