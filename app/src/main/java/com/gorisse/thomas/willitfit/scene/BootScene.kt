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
import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.BootVerdict
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
    // Width is clear between the arches; the trim above them sits further out.
    val interiorW = maxOf(w + 0.24f, ow)
    val bodyW = maxOf(interiorW + 0.12f, ow + 0.24f)
    val trimH = minOf(h, oh) * 0.65f
    val archH = minOf(0.24f, trimH * 0.7f)
    val seatH = maxOf(h, oh)
    val top = maxOf(seatH + 0.18f, up) + 0.06f
    val bottom = -0.44f
    val halfW = maxOf(bodyW, across) / 2f + 0.06f
    // Include the bumper, reclined seats and any object projecting through the opening.
    // A miss waits on the ground behind the car, in the way it would go in: inside, the solid
    // body would hide whatever is too big. The frame takes it in.
    val outsideZ = 0.4f + along / 2f
    val front = if (verdict.fits) 0.24f else outsideZ + along / 2f
    val rear = l + seatH * 0.18f + 0.12f
    val bounds = Aabb(
        center = Position(0f, (top + bottom) / 2f, (front - rear) / 2f),
        halfExtent = Position(halfW, (top - bottom) / 2f, (front + rear) / 2f + 0.06f),
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
        if (palette.isNight) color = io.github.sceneview.math.Color(1f, 0.86f, 0.7f, 1f)
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
        // At night the boot stood as one dark block on a dark floor: the paint and the trim keep
        // enough light to read as a car, the seal alone stays dark to draw the opening.
        val carpetColor = if (palette.isNight) Color(0xFF46423E) else Color(0xFF625D57)
        val trimColor = if (palette.isNight) Color(0xFF403E3B) else Color(0xFF4C4A47)
        val seatColor = if (palette.isNight) Color(0xFF363431) else Color(0xFF44413E)
        val bodyColor = if (palette.isNight) Color(0xFF9AA1A6) else Color(0xFFC4CBCF)
        val sealColor = if (palette.isNight) Color(0xFF191B1D) else Color(0xFF2D3032)
        val groundColor = if (palette.isNight) Color(0xFF514D48) else Color(0xFFB6B2AA)
        val carpet = remember(materialLoader) { materialLoader.createColorInstance(carpetColor, roughness = 1f) }
        val trim = remember(materialLoader) { materialLoader.createColorInstance(trimColor, roughness = 0.85f) }
        val seats = remember(materialLoader) { materialLoader.createColorInstance(seatColor, roughness = 1f) }
        val body = remember(materialLoader) { materialLoader.createColorInstance(bodyColor, metallic = 0.45f, roughness = 0.3f) }
        val seal = remember(materialLoader) { materialLoader.createColorInstance(sealColor, roughness = 0.95f) }
        val ground = remember(materialLoader) { materialLoader.createColorInstance(groundColor, roughness = 0.95f) }
        SideEffect {
            carpet.setColor(carpetColor); trim.setColor(trimColor); seats.setColor(seatColor)
            body.setColor(bodyColor); seal.setColor(sealColor); ground.setColor(groundColor)
        }
        // The floor stays at zero so every verdict orientation rests on the carpet.
        CubeNode(
            size = Size(interiorW, 0.04f, l), position = Position(0f, -0.02f, -l / 2f),
            materialInstance = carpet, apply = { isShadowReceiver = true },
        )
        val groundSpan = maxOf(40f, (rear + front + bodyW + up) * 8f)
        // A wall in the room's colour behind the car fills the view above the ground.
        val backdrop = remember(materialLoader) { materialLoader.createColorInstance(palette.wall, roughness = 0.95f) }
        SideEffect { backdrop.setColor(palette.wall) }
        CubeNode(
            size = Size(groundSpan, groundSpan / 2f, 0.1f), position = Position(0f, bottom + groundSpan / 4f, -rear - 4f),
            materialInstance = backdrop, apply = { isShadowCaster = false },
        )
        CubeNode(
            size = Size(groundSpan, 0.04f, groundSpan), position = Position(0f, bottom - 0.02f, -l / 2f),
            materialInstance = ground, apply = { isShadowReceiver = true; isShadowCaster = false },
        )
        // A solid lower body connects the load floor to the ground behind the bumper.
        CubeNode(size = Size(bodyW, 0.4f, l + 0.18f), position = Position(0f, -0.24f, -l / 2f + 0.05f), materialInstance = body)
        CubeNode(size = Size(bodyW, 0.08f, 0.24f), position = Position(0f, -0.08f, 0.08f), materialInstance = body)
        CubeNode(size = Size(ow, 0.025f, 0.18f), position = Position(0f, -0.0125f, 0.055f), materialInstance = trim)
        val archW = (interiorW - w) / 2f
        val pillarW = (bodyW - ow) / 2f
        for (side in listOf(-1f, 1f)) {
            CubeNode(
                size = Size(0.06f, trimH, l),
                position = Position(side * (interiorW + 0.06f) / 2f, trimH / 2f, -l / 2f), materialInstance = trim,
            )
            // The inner arch faces leave exactly the entered usable width.
            CubeNode(
                size = Size(archW, archH, l * 0.55f),
                position = Position(side * (w + archW) / 2f, archH / 2f, -l * 0.55f), materialInstance = carpet,
            )
            CubeNode(
                size = Size(archW * 0.75f, 0.025f, l * 0.43f),
                position = Position(side * (interiorW / 2f - archW * 0.375f), archH + 0.0125f, -l * 0.55f), materialInstance = trim,
            )
            CubeNode(
                size = Size(pillarW, oh + 0.08f, 0.12f),
                position = Position(side * (ow + pillarW) / 2f, (oh + 0.08f) / 2f, -0.035f), materialInstance = body,
            )
            // Rubber sits outside the clear opening, rather than reducing its dimensions.
            CubeNode(size = Size(0.018f, oh, 0.025f), position = Position(side * (ow / 2f + 0.009f), oh / 2f, 0.035f), materialInstance = seal)
            CubeNode(size = Size(0.07f, 0.035f, l), position = Position(side * (interiorW / 2f + 0.01f), trimH, -l / 2f), materialInstance = body)
        }
        CubeNode(size = Size(ow, 0.08f, 0.12f), position = Position(0f, oh + 0.04f, -0.035f), materialInstance = body)
        CubeNode(size = Size(ow, 0.018f, 0.025f), position = Position(0f, oh + 0.009f, 0.035f), materialInstance = seal)
        // Reclined split seat backs and headrests identify the far end as a car interior.
        Node(position = Position(0f, 0f, -l - 0.05f), rotation = Rotation(x = -10f)) {
            for (side in listOf(-1f, 1f)) {
                CubeNode(size = Size(interiorW / 2f - 0.012f, seatH, 0.08f), position = Position(side * interiorW / 4f, seatH / 2f, 0f), materialInstance = seats)
                CubeNode(size = Size(interiorW * 0.22f, 0.14f, 0.09f), position = Position(side * interiorW / 4f, seatH + 0.06f, -0.015f), materialInstance = seats)
            }
        }
        // Shelf edges mark the height without a roof hiding the load from above.
        for (side in listOf(-1f, 1f)) {
            CubeNode(size = Size(0.018f, 0.018f, l), position = Position(side * (interiorW / 2f - 0.009f), h + 0.009f, -l / 2f), materialInstance = trim)
        }
        CubeNode(size = Size(interiorW, 0.018f, 0.025f), position = Position(0f, h + 0.009f, -l * 0.12f), materialInstance = trim)
        CubeNode(size = Size(interiorW, 0.018f, 0.025f), position = Position(0f, h + 0.009f, -l + 0.0125f), materialInstance = trim)
        val look = rememberFurnitureLook(if (verdict.fits) accent else Color(0xFFB65D54), roomLit = true)
        Node(position = if (verdict.fits) Position(0f, up / 2f, -l + along / 2f) else Position(0f, bottom + up / 2f, outsideZ)) {
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
