package com.gorisse.thomas.willitfit.scene

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.LiftVerdict
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.side
import io.github.sceneview.Aabb
import io.github.sceneview.DEFAULT_FILL_LIGHT_INTENSITY
import io.github.sceneview.SceneView
import io.github.sceneview.fitCameraToBounds
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.verticalFovDegreesForFocalLength
import kotlinx.coroutines.delay

/** Open cabin, seen from the landing; the roof is omitted so the carry remains visible. */
@Composable
fun LiftScene(
    dims: Dimensions,
    lift: Lift,
    verdict: LiftVerdict,
    accent: Color,
    palette: RoomPalette,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val w = lift.cabinWidthCm / 100f
    val d = lift.cabinDepthCm / 100f
    val h = lift.cabinHeightCm / 100f
    val ow = lift.doorWidthCm / 100f
    val oh = lift.doorHeightCm / 100f
    val up = dims.side(verdict.carry.up) / 100f
    val across = dims.side(verdict.carry.across) / 100f
    val along = dims.side(verdict.carry.along) / 100f
    val angle = verdict.angleDeg * kotlin.math.PI / 180
    val footprintW = (across * kotlin.math.cos(angle) + along * kotlin.math.sin(angle)).toFloat()
    val footprintD = (across * kotlin.math.sin(angle) + along * kotlin.math.cos(angle)).toFloat()
    val outsideZ = footprintD / 2f + 0.08f
    val front = if (verdict.fits) 0.1f else footprintD + 0.08f
    val bounds = Aabb(
        center = Position(0f, maxOf(h, up) / 2f, (front - d) / 2f),
        halfExtent = Position(maxOf(w, ow * 2f, footprintW) / 2f + 0.15f, maxOf(h, up) / 2f + 0.1f, (front + d) / 2f + 0.1f),
    )
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val coveredPx = with(LocalDensity.current) {
        contentPadding.calculateTopPadding().toPx() + contentPadding.calculateBottomPadding().toPx()
    }
    val visibleAspect = if (viewSize.width > 0 && viewSize.height > 0) {
        viewSize.width / (viewSize.height - coveredPx).coerceAtLeast(viewSize.height * 0.1f)
    } else 0.6f
    var aspect by remember { mutableFloatStateOf(visibleAspect) }
    LaunchedEffect(visibleAspect) { delay(180); aspect = visibleAspect }
    val view = fitCameraToBounds(
        bounds, direction = VIEW_DIRECTION,
        verticalFovDegrees = verticalFovDegreesForFocalLength(DEFAULT_FOCAL_LENGTH_MM),
        aspect = aspect.toDouble(), widthFill = 0.88f, heightFill = 0.78f,
    )
    val manipulator = rememberCameraManipulator(
        orbitHomePosition = view?.eye ?: Position(2f, 2f, 3f),
        targetPosition = view?.target ?: bounds.center,
    )
    val environmentLoader = rememberEnvironmentLoader(engine)
    // Opaque like the door's scene: a transparent SceneView is drawn above the Compose sheet, and
    // the ground then hides the verdict and the fields.
    val environment = rememberEnvironment(environmentLoader)
    val dayIbl = remember(environment) { environment.indirectLight?.intensity ?: 30_000f }
    SideEffect { environment.indirectLight?.intensity = if (palette.isNight) dayIbl * 0.6f else dayIbl }
    val sun = rememberMainLightNode(engine) {
        intensity = if (palette.isNight) 30_000f else 100_000f
        // Set in both branches: a theme change keeps this light, so day must undo night's.
        color = if (palette.isNight) io.github.sceneview.math.Color(1f, 0.86f, 0.7f, 1f) else io.github.sceneview.DEFAULT_MAIN_LIGHT_COLOR
        lightDirection = Direction(-0.55f, -0.7f, -0.45f)
    }
    LaunchedEffect(sun) { sun.updateShadowOptions { mapSize = 2048; shadowFar = 12f; normalBias = 3f } }
    SceneView(
        modifier = modifier.onSizeChanged { viewSize = it },
        environmentLoader = environmentLoader, environment = environment,
        engine = engine, modelLoader = modelLoader, materialLoader = materialLoader,
        autoCenterContent = false, contentPadding = contentPadding, cameraManipulator = manipulator,
        mainLightNode = sun,
        fillLightNode = rememberFillLightNode(engine) {
            intensity = if (palette.isNight) 900f else DEFAULT_FILL_LIGHT_INTENSITY
        },
    ) {
        val wallColor = if (palette.isNight) Color(0xFF888A88) else Color(0xFFCDD0CB)
        val wall = remember(materialLoader) { materialLoader.createColorInstance(wallColor, metallic = 0.35f, roughness = 0.6f) }
        val floor = remember(materialLoader) { materialLoader.createColorInstance(palette.floor, roughness = 0.95f) }
        val backdrop = remember(materialLoader) { materialLoader.createColorInstance(palette.wall, roughness = 1f) }
        SideEffect { wall.setColor(wallColor); floor.setColor(palette.floor); backdrop.setColor(palette.wall) }
        CubeNode(size = Size(40f, 0.04f, 40f), position = Position(0f, -0.06f, 0f), materialInstance = floor, apply = { isShadowReceiver = true })
        CubeNode(size = Size(40f, 20f, 0.1f), position = Position(0f, 9f, -d - 2f), materialInstance = backdrop)
        CubeNode(size = Size(w, 0.04f, d), position = Position(0f, -0.02f, -d / 2f), materialInstance = floor, apply = { isShadowReceiver = true })
        CubeNode(size = Size(w + 0.08f, h, 0.04f), position = Position(0f, h / 2f, -d - 0.02f), materialInstance = wall)
        for (side in listOf(-1f, 1f)) {
            CubeNode(size = Size(0.04f, h, d), position = Position(side * (w / 2f + 0.02f), h / 2f, -d / 2f), materialInstance = wall)
            // Retracted doors sit outside the clear opening, even in a narrow cabin.
            CubeNode(size = Size(ow / 2f, oh, 0.06f), position = Position(side * ow * 0.75f, oh / 2f, 0.03f), materialInstance = wall)
        }
        CubeNode(size = Size(ow + 0.12f, 0.08f, 0.1f), position = Position(0f, oh + 0.04f, 0f), materialInstance = wall)
        CylinderNode(radius = 0.018f, height = w * 0.8f, position = Position(0f, minOf(1f, h * 0.5f), -d + 0.09f), rotation = Rotation(z = 90f), materialInstance = wall)
        val look = rememberFurnitureLook(if (verdict.fits) accent else Color(0xFFB65D54), roomLit = true)
        Node(position = Position(0f, up / 2f, if (verdict.fits) -d / 2f else outsideZ), rotation = Rotation(y = verdict.angleDeg)) {
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
