package com.gorisse.thomas.willitfit.scene

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.MaterialInstance
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.Shape
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.fit.shape
import io.github.sceneview.SceneScope
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.ContactShadowContext
import io.github.sceneview.node.RenderableNode
import io.github.sceneview.rememberModelInstance
import kotlin.math.atan2
import kotlin.math.hypot
import io.github.sceneview.node.Node as NodeImpl

/**
 * The materials of one object. [opacity] fades them in AR (placement and tracking loss);
 * [transparent] picks the blended material, which the fade needs and the 3D room does not.
 */
class FurnitureLook internal constructor(
    val body: MaterialInstance,
    val soft: MaterialInstance,
    val wood: MaterialInstance,
    val dark: MaterialInstance,
    val metal: MaterialInstance,
    val white: MaterialInstance,
    val glass: MaterialInstance,
    /** Touches on a part reach the AR placement pivot instead of moving the part. */
    val selectable: Boolean,
) {
    /** The colours the modelled furniture's fabrics take, and how present it is (AR fade). */
    var accent: Color by mutableStateOf(FurnitureTeal)
        internal set
    var accentSoft: Color by mutableStateOf(FurnitureTeal)
        internal set
    var opacity: Float by mutableFloatStateOf(1f)
        internal set

    val part: RenderableNode.() -> Unit = {
        isShadowCaster = true
        if (selectable) makeFixedSelectable()
    }
}

/** Selectable, but every gesture goes to the parent pivot (AutoPlacementNode's contract). */
internal fun NodeImpl.makeFixedSelectable() {
    isEditable = true
    isPositionEditable = false
    isRotationEditable = false
    isScaleEditable = false
}

/**
 * The object's own fabric, the same on every phone (not the wallpaper's colour): a deep teal,
 * darker than the UI's, because the room's sun lifts it and a lighter cloth reads as cyan.
 */
val FurnitureTeal = Color(0xFF225254)

private val Oak = Color(0xFFB98B5E)
private val Charcoal = Color(0xFF2A2B2F)
private val Steel = Color(0xFFB9BEC5)
private val Linen = Color(0xFFF3F0EA)
private val Screen = Color(0xFF07090D)

@Composable
fun SceneScope.rememberFurnitureLook(
    accent: Color,
    opacity: Float = 1f,
    transparent: Boolean = false,
    selectable: Boolean = false,
): FurnitureLook {
    // Cushions a breath lighter than the frame, as on the target: same cloth, more light.
    val soft = lerp(accent, Color.White, 0.04f)
    val start = if (transparent) 0f else 1f
    val look = remember(materialLoader, transparent, selectable) {
        fun mat(c: Color, metallic: Float = 0f, roughness: Float = 0.75f) =
            materialLoader.createColorInstance(c.copy(alpha = start), metallic = metallic, roughness = roughness)
        FurnitureLook(
            body = mat(accent, roughness = 0.85f),
            soft = mat(soft, roughness = 0.9f),
            wood = mat(Oak, roughness = 0.55f),
            dark = mat(Charcoal, roughness = 0.6f),
            metal = mat(Steel, metallic = 0.9f, roughness = 0.3f),
            white = mat(Linen, roughness = 0.9f),
            glass = mat(Screen, roughness = 0.08f),
            selectable = selectable,
        )
    }
    SideEffect {
        look.accent = accent
        look.accentSoft = soft
        look.opacity = opacity
        val a = if (transparent) opacity else 1f
        look.body.setColor(accent.copy(alpha = a))
        look.soft.setColor(soft.copy(alpha = a))
        look.wood.setColor(Oak.copy(alpha = a))
        look.dark.setColor(Charcoal.copy(alpha = a))
        look.metal.setColor(Steel.copy(alpha = a))
        look.white.setColor(Linen.copy(alpha = a))
        look.glass.setColor(Screen.copy(alpha = a))
    }
    return look
}

/**
 * The object at [dims], bottom-centre on the origin, front toward +Z, in metres. The modelled
 * furniture (`assets/models/<name>.glb`, made by `tools/furniture/make_furniture.py`) is drawn
 * when it ships with the app; the silhouette built from boxes stands in for anything missing.
 * Either way the object is drawn once at its authored size and stretched to the dimensions, so
 * the bounding box is exactly width × height × depth whatever the user types.
 */
@Composable
fun SceneScope.Furniture(
    dims: Dimensions,
    look: FurnitureLook,
    shape: Shape = dims.shape,
    contactShadow: Boolean = true,
) {
    // The soft pool under the object that makes it stand on the floor instead of hovering.
    // A unit quad scaled to the footprint: resizing never rebuilds it.
    if (contactShadow) {
        // Just above the parquet's top face (1 mm up), or the planks hide it and the object floats:
        // a broad soft pool, and a tight darker one where it meets the floor.
        ContactShadow(
            size = Size(1f, 0f, 1f),
            context = ContactShadowContext.Floor,
            intensity = 0.5f * look.opacity,
            position = Position(y = 0.004f),
            scale = Scale(dims.widthCm / 100f + 0.45f, 1f, dims.depthCm / 100f + 0.45f),
        )
        ContactShadow(
            size = Size(1f, 0f, 1f),
            context = ContactShadowContext.Floor,
            intensity = 0.7f * look.opacity,
            position = Position(y = 0.005f),
            scale = Scale(dims.widthCm / 100f + 0.06f, 1f, dims.depthCm / 100f + 0.06f),
        )
    }
    val file = modelFile(shape, dims.presetId)
    val context = LocalContext.current
    val bundled = remember(file) { file != null && context.hasAsset(file) }
    if (file != null && bundled) {
        // A new file gets its own load; nothing is drawn for the few frames it takes.
        key(file) { ModelFurniture(file, dims, look) }
        return
    }
    val base = baseSize(shape, dims)
    Node(
        scale = Scale(
            dims.widthCm / 100f / base.x,
            dims.heightCm / 100f / base.y,
            dims.depthCm / 100f / base.z,
        ),
    ) {
        when (shape) {
            Shape.SOFA -> Sofa(base, look, seats = if (dims.presetId == Preset.SOFA_2.id) 2 else 3)
            Shape.BED -> Bed(base, look)
            Shape.WARDROBE -> Wardrobe(base, look)
            Shape.FRIDGE -> Fridge(base, look)
            Shape.WASHER -> Washer(base, look)
            Shape.TV -> Tv(base, look)
            Shape.BIKE -> Bike(base, look)
            Shape.STROLLER -> Stroller(base, look)
            Shape.BOX -> Parcel(base, look)
        }
    }
}

/** The modelled object for a shape, as an asset path. */
private fun modelFile(shape: Shape, presetId: String?): String? = when (shape) {
    Shape.SOFA -> if (presetId == Preset.SOFA_2.id) "models/sofa2.glb" else "models/sofa3.glb"
    Shape.BED -> "models/bed.glb"
    Shape.WARDROBE -> "models/wardrobe.glb"
    Shape.FRIDGE -> "models/fridge.glb"
    Shape.WASHER -> "models/washer.glb"
    Shape.TV -> "models/tv.glb"
    Shape.BIKE -> "models/bike.glb"
    Shape.STROLLER -> "models/stroller.glb"
    Shape.BOX -> "models/box.glb"
}

private fun Context.hasAsset(path: String): Boolean =
    runCatching { assets.open(path).close() }.isSuccess

/**
 * One glTF object, stretched from its own bounding box to [dims]. Its `fabric` and
 * `fabric_soft` materials take the look's colours, so the object wears the brand teal like the
 * silhouettes did. glTF materials are opaque, so the AR fade becomes a cut: the object shows
 * once it is more than half there.
 */
@Composable
private fun SceneScope.ModelFurniture(file: String, dims: Dimensions, look: FurnitureLook) {
    val asked = remember(file) { SystemClock.uptimeMillis() }
    val instance = rememberModelInstance(modelLoader, file)
    LaunchedEffect(instance) {
        // Load time on real phones, read from logcat: the emulator is far slower.
        if (instance != null) Log.i("WillItFit", "$file ready in ${SystemClock.uptimeMillis() - asked} ms")
    }
    if (instance == null) return
    val box = remember(instance) { instance.asset.boundingBox }
    val he = box.halfExtent
    val bw = (he[0] * 2f).coerceAtLeast(0.01f)
    val bh = (he[1] * 2f).coerceAtLeast(0.01f)
    val bd = (he[2] * 2f).coerceAtLeast(0.01f)
    SideEffect {
        instance.materialInstances.forEach { mi ->
            when (mi.name) {
                "fabric" -> mi.setColor("baseColorFactor", look.accent)
                "fabric_soft" -> mi.setColor("baseColorFactor", look.accentSoft)
            }
        }
    }
    Node(
        scale = Scale(dims.widthCm / 100f / bw, dims.heightCm / 100f / bh, dims.depthCm / 100f / bd),
    ) {
        ModelNode(
            modelInstance = instance,
            autoAnimate = false,
            // Bottom-centre on the origin, whatever the file's own pivot.
            centerOrigin = Position(0f, -1f, 0f),
            isVisible = look.opacity > 0.5f,
            apply = {
                isShadowCaster = true
                isShadowReceiver = true
                if (look.selectable) {
                    makeFixedSelectable()
                    renderableNodes.forEach { it.makeFixedSelectable() }
                }
            },
        )
    }
}

/** The size a silhouette is authored at, metres (x = width, y = height, z = depth). */
private fun baseSize(shape: Shape, dims: Dimensions): Size {
    val preset = Preset.byId(dims.presetId)?.takeIf { it.shape == shape }
        ?: Preset.entries.firstOrNull { it.shape == shape }
    val d = preset?.dimensions ?: return Size(1f, 1f, 1f)
    return Size(d.widthCm / 100f, d.heightCm / 100f, d.depthCm / 100f)
}

// ---- Silhouettes. Origin bottom-centre, +Y up, +Z front. -------------------------------------

@Composable
private fun SceneScope.Box(look: FurnitureLook, mat: MaterialInstance, x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float) {
    CubeNode(
        size = Size(x1 - x0, y1 - y0, z1 - z0),
        position = Position((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f),
        materialInstance = mat,
        apply = look.part,
    )
}

/** A bar between two points of the XY plane at depth [z], square section [t]. */
@Composable
private fun SceneScope.Bar(look: FurnitureLook, mat: MaterialInstance, x0: Float, y0: Float, x1: Float, y1: Float, z: Float, t: Float) {
    val len = hypot(x1 - x0, y1 - y0)
    val angle = Math.toDegrees(atan2(y1 - y0, x1 - x0).toDouble()).toFloat()
    CubeNode(
        size = Size(len, t, t),
        position = Position((x0 + x1) / 2f, (y0 + y1) / 2f, z),
        rotation = Rotation(z = angle),
        materialInstance = mat,
        apply = look.part,
    )
}

/** A ring standing in the XY plane (facing ±Z), like a wheel seen from the side. */
@Composable
private fun SceneScope.Ring(look: FurnitureLook, mat: MaterialInstance, at: Position, radius: Float, tube: Float, facingX: Boolean = false) {
    TorusNode(
        majorRadius = radius,
        minorRadius = tube,
        majorSegments = 40,
        minorSegments = 10,
        position = at,
        rotation = if (facingX) Rotation(z = 90f) else Rotation(x = 90f),
        materialInstance = mat,
        apply = look.part,
    )
}

@Composable
private fun SceneScope.Sofa(s: Size, look: FurnitureLook, seats: Int) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    val arm = 0.2f; val leg = 0.08f; val back = 0.22f
    val armTop = h * 0.72f
    for (x in listOf(-hw + 0.06f, hw - 0.1f)) for (z in listOf(-hd + 0.06f, hd - 0.1f)) {
        Box(look, look.wood, x, x + 0.04f, 0f, leg, z, z + 0.04f)
    }
    Box(look, look.body, -hw, hw, leg, leg + 0.24f, -hd, hd)                       // base
    Box(look, look.body, -hw + arm, hw - arm, leg + 0.2f, h, -hd, -hd + back)       // back
    Box(look, look.body, -hw, -hw + arm, leg, armTop, -hd, hd)                      // arms
    Box(look, look.body, hw - arm, hw, leg, armTop, -hd, hd)
    val inner = w - 2 * arm
    val gap = 0.012f
    val cw = inner / seats
    for (i in 0 until seats) {
        val x0 = -hw + arm + i * cw + gap
        Box(look, look.soft, x0, x0 + cw - 2 * gap, leg + 0.24f, leg + 0.4f, -hd + back, hd - 0.02f)       // seat
        Box(look, look.soft, x0, x0 + cw - 2 * gap, leg + 0.4f, h - 0.06f, -hd + back, -hd + back + 0.16f) // back cushion
    }
}

@Composable
private fun SceneScope.Bed(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    Box(look, look.wood, -hw, hw, 0.06f, 0.3f, -hd + 0.06f, hd)                      // frame
    for (x in listOf(-hw + 0.02f, hw - 0.07f)) for (z in listOf(-hd + 0.08f, hd - 0.07f)) {
        Box(look, look.wood, x, x + 0.05f, 0f, 0.06f, z, z + 0.05f)
    }
    Box(look, look.body, -hw, hw, 0.06f, h, -hd, -hd + 0.08f)                        // headboard
    Box(look, look.white, -hw + 0.05f, hw - 0.05f, 0.3f, 0.52f, -hd + 0.1f, hd - 0.04f) // mattress
    Box(look, look.soft, -hw + 0.03f, hw - 0.03f, 0.3f, 0.56f, -hd + d * 0.42f, hd - 0.02f) // duvet
    val pw = (w - 0.3f) / 2f
    Box(look, look.white, -hw + 0.1f, -hw + 0.1f + pw, 0.52f, 0.64f, -hd + 0.14f, -hd + 0.5f)
    Box(look, look.white, hw - 0.1f - pw, hw - 0.1f, 0.52f, 0.64f, -hd + 0.14f, -hd + 0.5f)
}

@Composable
private fun SceneScope.Wardrobe(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    Box(look, look.dark, -hw + 0.02f, hw - 0.02f, 0f, 0.08f, -hd, hd - 0.03f)        // plinth
    Box(look, look.body, -hw, hw, 0.08f, h, -hd, hd - 0.02f)                          // carcass
    val g = 0.006f
    Box(look, look.soft, -hw + g, -g, 0.1f, h - 0.02f, hd - 0.02f, hd)                // doors
    Box(look, look.soft, g, hw - g, 0.1f, h - 0.02f, hd - 0.02f, hd)
    Box(look, look.wood, -0.05f, -0.03f, h * 0.42f, h * 0.62f, hd, hd + 0.025f)        // handles
    Box(look, look.wood, 0.03f, 0.05f, h * 0.42f, h * 0.62f, hd, hd + 0.025f)
}

@Composable
private fun SceneScope.Fridge(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    Box(look, look.dark, -hw + 0.03f, hw - 0.03f, 0f, 0.04f, -hd, hd - 0.05f)
    Box(look, look.body, -hw, hw, 0.04f, h, -hd, hd - 0.05f)                          // cabinet
    val split = h * 0.36f
    Box(look, look.soft, -hw, hw, 0.05f, split - 0.006f, hd - 0.05f, hd - 0.01f)      // freezer door
    Box(look, look.soft, -hw, hw, split + 0.006f, h, hd - 0.05f, hd - 0.01f)          // fridge door
    Box(look, look.metal, hw - 0.07f, hw - 0.05f, split + 0.08f, split + 0.5f, hd - 0.01f, hd)
    Box(look, look.metal, hw - 0.07f, hw - 0.05f, split - 0.3f, split - 0.06f, hd - 0.01f, hd)
}

@Composable
private fun SceneScope.Washer(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    Box(look, look.body, -hw, hw, 0.02f, h, -hd, hd - 0.01f)
    Box(look, look.dark, -hw + 0.02f, hw - 0.02f, 0f, 0.02f, -hd, hd - 0.03f)
    Box(look, look.soft, -hw + 0.01f, hw - 0.01f, h - 0.11f, h - 0.01f, hd - 0.01f, hd)   // control panel
    Box(look, look.dark, hw - 0.16f, hw - 0.06f, h - 0.08f, h - 0.04f, hd, hd + 0.005f)  // display
    val cy = h * 0.46f
    Ring(look, look.metal, Position(0f, cy, hd + 0.005f), radius = w * 0.3f, tube = 0.022f)
    CylinderNode(
        radius = w * 0.27f, height = 0.01f,
        position = Position(0f, cy, hd),
        rotation = Rotation(x = 90f),
        materialInstance = look.glass,
        apply = look.part,
    )
}

@Composable
private fun SceneScope.Tv(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    Box(look, look.body, -w * 0.22f, w * 0.22f, 0f, 0.02f, -hd, hd)                 // stand foot
    Box(look, look.body, -0.04f, 0.04f, 0.02f, 0.1f, -0.03f, 0.01f)                  // neck
    Box(look, look.dark, -hw, hw, 0.08f, h, -0.02f, 0.015f)                           // panel
    Box(look, look.glass, -hw + 0.012f, hw - 0.012f, 0.092f, h - 0.012f, 0.015f, 0.02f) // screen
}

@Composable
private fun SceneScope.Bike(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val r = 0.34f; val t = 0.022f
    val rear = -w / 2f + r + 0.02f; val front = w / 2f - r - 0.02f
    val hub = r + 0.02f
    Ring(look, look.dark, Position(rear, hub, 0f), r, 0.025f)
    Ring(look, look.dark, Position(front, hub, 0f), r, 0.025f)
    val bb = Position(rear + 0.45f, 0.3f, 0f)            // bottom bracket
    val seat = Position(rear + 0.3f, h * 0.82f, 0f)
    val head = Position(front - 0.18f, h * 0.84f, 0f)
    Bar(look, look.body, rear, hub, bb.x, bb.y, 0f, t)                // chain stay
    Bar(look, look.body, rear, hub, seat.x, seat.y, 0f, t)            // seat stay
    Bar(look, look.body, bb.x, bb.y, seat.x, seat.y, 0f, t * 1.3f)    // seat tube
    Bar(look, look.body, bb.x, bb.y, head.x, head.y - 0.08f, 0f, t * 1.4f) // down tube
    Bar(look, look.body, seat.x, seat.y - 0.06f, head.x, head.y - 0.02f, 0f, t * 1.2f) // top tube
    Bar(look, look.metal, front, hub, head.x, head.y, 0f, t)          // fork
    Box(look, look.dark, seat.x - 0.13f, seat.x + 0.1f, seat.y, seat.y + 0.05f, -0.07f, 0.07f) // saddle
    Box(look, look.dark, head.x - 0.02f, head.x + 0.02f, h - 0.04f, h, -d / 2f, d / 2f)       // handlebar
    Bar(look, look.metal, head.x, head.y, head.x - 0.01f, h - 0.03f, 0f, t)                    // stem
}

@Composable
private fun SceneScope.Stroller(s: Size, look: FurnitureLook) {
    val w = s.x; val h = s.y; val d = s.z
    val hw = w / 2f; val hd = d / 2f
    val r = 0.1f
    for (x in listOf(-hw + 0.03f, hw - 0.03f)) for (z in listOf(-hd + r, hd - r)) {
        Ring(look, look.dark, Position(x, r, z), r, 0.025f, facingX = true)
    }
    Box(look, look.metal, -hw + 0.02f, hw - 0.02f, r - 0.01f, r + 0.01f, -hd + r, -hd + r + 0.02f)
    Box(look, look.metal, -hw + 0.02f, hw - 0.02f, r - 0.01f, r + 0.01f, hd - r - 0.02f, hd - r)
    Box(look, look.body, -hw + 0.07f, hw - 0.07f, 0.42f, 0.72f, -hd + 0.18f, hd - 0.08f)       // bassinet
    Box(look, look.soft, -hw + 0.07f, hw - 0.07f, 0.72f, 0.95f, -hd + 0.18f, -hd + 0.5f)       // hood
    // Frame from the wheels up to the handle at the back.
    for (x in listOf(-hw + 0.05f, hw - 0.05f)) {
        Box(look, look.metal, x - 0.012f, x + 0.012f, r, 0.45f, -0.01f, 0.01f)
    }
    Box(look, look.metal, -hw + 0.05f, hw - 0.05f, h - 0.03f, h, -hd + 0.02f, -hd + 0.05f)     // handle
    Box(look, look.metal, -hw + 0.04f, -hw + 0.065f, 0.45f, h, -hd + 0.03f, -hd + 0.05f)
    Box(look, look.metal, hw - 0.065f, hw - 0.04f, 0.45f, h, -hd + 0.03f, -hd + 0.05f)
}

@Composable
private fun SceneScope.Parcel(s: Size, look: FurnitureLook) {
    val hw = s.x / 2f; val hd = s.z / 2f
    Box(look, look.body, -hw, hw, 0f, s.y, -hd, hd)
}

// ---- Dimension annotations ------------------------------------------------------------------

/**
 * Dimension lines on the floor and up the right-front corner, with their values written next to
 * them: width along the front, depth along the right side, height up the corner. Drawn at the
 * object's real size, outside its stretched node, so the text never deforms.
 *
 * @param labelScale 1 in AR (a 30 cm tag); the 3D room passes more for large objects.
 */
@Composable
fun SceneScope.DimensionMarks(
    dims: Dimensions,
    unit: LengthUnit,
    lineColor: Color,
    labelText: Color,
    labelBackground: Color,
    selectable: Boolean = false,
    labelScale: Float = 1f,
    facing: (() -> Position)? = null,
) {
    val w = dims.widthCm / 100f; val h = dims.heightCm / 100f; val d = dims.depthCm / 100f
    val off = 0.07f
    val t = 0.008f
    val line = remember(materialLoader) { materialLoader.createUnlitColorInstance(lineColor) }
    SideEffect { line.setColor(lineColor) }
    val fixed: NodeImpl.() -> Unit = { if (selectable) makeFixedSelectable() }
    val front = d / 2f + off
    val right = w / 2f + off
    val tick = 0.06f
    // Width, along the floor in front.
    CubeNode(size = Size(w, t, t), position = Position(0f, t / 2f, front), materialInstance = line, apply = fixed)
    CubeNode(size = Size(t, tick, t), position = Position(-w / 2f, tick / 2f, front), materialInstance = line, apply = fixed)
    CubeNode(size = Size(t, tick, t), position = Position(w / 2f, tick / 2f, front), materialInstance = line, apply = fixed)
    // Depth, along the floor on the right.
    CubeNode(size = Size(t, t, d), position = Position(right, t / 2f, 0f), materialInstance = line, apply = fixed)
    CubeNode(size = Size(t, tick, t), position = Position(right, tick / 2f, -d / 2f), materialInstance = line, apply = fixed)
    // Height, up the right-front corner.
    CubeNode(size = Size(t, h, t), position = Position(right, h / 2f, front), materialInstance = line, apply = fixed)
    CubeNode(size = Size(tick, t, t), position = Position(right, h, front), materialInstance = line, apply = fixed)

    // Tags in the 4:1 bitmap TextNode draws (512 × 128 px), the figure filling most of it.
    val lw = 0.34f; val lh = 0.085f; val font = 82f
    val fg = labelText.toArgb(); val bg = labelBackground.toArgb()
    val s = Scale(labelScale)
    TextNode(
        text = formatLength(dims.widthCm, unit), fontSize = font, textColor = fg, backgroundColor = bg,
        widthMeters = lw, heightMeters = lh, scale = s, cameraPositionProvider = facing,
        position = Position(0f, lh * labelScale / 2f + 0.03f, front + 0.02f), apply = fixed,
    )
    // Outside the depth line by half a tag, so the object never hides it.
    Node(position = Position(right + 0.03f + lw * labelScale / 2f, lh * labelScale / 2f + 0.03f, 0f), rotation = Rotation(y = 90f)) {
        TextNode(
            text = formatLength(dims.depthCm, unit), fontSize = font, textColor = fg, backgroundColor = bg,
            widthMeters = lw, heightMeters = lh, scale = s, cameraPositionProvider = facing, apply = fixed,
        )
    }
    // Above the top of the height line, clear of the depth tag on the floor below it.
    Node(position = Position(right + 0.02f, h + lh * labelScale / 2f + 0.04f, front + 0.02f), rotation = Rotation(y = 45f)) {
        TextNode(
            text = formatLength(dims.heightCm, unit), fontSize = font, textColor = fg, backgroundColor = bg,
            widthMeters = lw, heightMeters = lh, scale = s, cameraPositionProvider = facing, apply = fixed,
        )
    }
}
