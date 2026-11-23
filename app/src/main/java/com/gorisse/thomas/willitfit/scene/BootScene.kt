package com.gorisse.thomas.willitfit.scene

import androidx.compose.foundation.background
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
import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.BootVerdict
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.side
import io.github.sceneview.Aabb
import io.github.sceneview.SceneView
import io.github.sceneview.fitCameraToBounds
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.verticalFovDegreesForFocalLength
import kotlinx.coroutines.delay

/** Rear opening faces the viewer; the seats close the far end. */
@Composable
fun BootScene(
    dims: Dimensions,
    boot: Boot,
    verdict: BootVerdict,
    accent: Color,
    palette: RoomPalette,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val w = boot.widthCm / 100f
    val h = boot.heightCm / 100f
    val l = boot.lengthCm / 100f
    val ow = boot.openingWidthCm / 100f
    val oh = boot.openingHeightCm / 100f
    val across = dims.side(verdict.carry.across) / 100f
    val up = dims.side(verdict.carry.up) / 100f
    val along = dims.side(verdict.carry.along) / 100f
    val top = maxOf(h, oh, up) + 0.06f
    val halfW = maxOf(w, ow, across) / 2f + 0.06f
    // A miss projects out of the opening rather than disappearing inside the seats.
    val front = maxOf(0f, along - l)
    val bounds = Aabb(
        center = Position(0f, top / 2f, (front - l) / 2f),
        halfExtent = Position(halfW, top / 2f, (front + l) / 2f + 0.06f),
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
    val environment = rememberEnvironment(environmentLoader, isOpaque = false)
    val dayIbl = remember(environment) { environment.indirectLight?.intensity ?: 30_000f }
    SideEffect { environment.indirectLight?.intensity = if (palette.isNight) dayIbl * 0.6f else dayIbl }
    SceneView(
        modifier = modifier.background(palette.wall).onSizeChanged { viewSize = it },
        isOpaque = false, environmentLoader = environmentLoader, environment = environment,
        engine = engine, modelLoader = modelLoader, materialLoader = materialLoader,
        autoCenterContent = false, contentPadding = contentPadding, cameraManipulator = manipulator,
    ) {
        val plasticColor = if (palette.isNight) Color(0xFF25272A) else Color(0xFF505256)
        val plastic = remember(materialLoader) { materialLoader.createColorInstance(plasticColor, roughness = 0.9f) }
        val sealColor = if (palette.isNight) Color(0xFF131517) else Color(0xFF282A2D)
        val seal = remember(materialLoader) { materialLoader.createColorInstance(sealColor, roughness = 0.95f) }
        SideEffect { plastic.setColor(plasticColor); seal.setColor(sealColor) }
        val t = 0.035f
        CubeNode(size = Size(w + 2f * t, t, l), position = Position(0f, -t / 2f, -l / 2f), materialInstance = plastic)
        for (side in listOf(-1f, 1f)) {
            CubeNode(size = Size(t, h, l), position = Position(side * (w + t) / 2f, h / 2f, -l / 2f), materialInstance = plastic)
            CubeNode(size = Size(t, oh + t, t), position = Position(side * (ow + t) / 2f, oh / 2f, 0f), materialInstance = seal)
        }
        CubeNode(size = Size(w, h, t), position = Position(0f, h / 2f, -l - t / 2f), materialInstance = plastic)
        // A translucent roof keeps the height visible without hiding the object from above.
        val roofColor = plasticColor.copy(alpha = 0.22f)
        val roof = remember(materialLoader) { materialLoader.createColorInstance(roofColor, roughness = 0.9f) }
        SideEffect { roof.setColor(roofColor) }
        CubeNode(size = Size(w, t, l), position = Position(0f, h + t / 2f, -l / 2f), materialInstance = roof)
        CubeNode(size = Size(ow + 2f * t, t, t), position = Position(0f, oh + t / 2f, 0f), materialInstance = seal)
        CubeNode(size = Size(ow, t, t), position = Position(0f, -t / 2f, 0f), materialInstance = seal)
        val look = rememberFurnitureLook(if (verdict.fits) accent else Color(0xFFB65D54), roomLit = true)
        Node(position = Position(0f, up / 2f, -l + along / 2f)) {
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
