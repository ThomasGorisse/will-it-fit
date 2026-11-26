package com.gorisse.thomas.willitfit.capture

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import com.gorisse.thomas.willitfit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * What the scene renders — the camera image and the object, composited by Filament in the
 * scene's SurfaceView — read back with PixelCopy. The Compose controls are not in it, by design:
 * the card is drawn around the photo afterwards. Needs no permission. Never null: a failure says why.
 */
suspend fun captureScene(activity: Activity): PhotoResult<Bitmap> {
    val surface = findSurfaceView(activity.window.decorView)
        ?: return PhotoResult.Failure(PhotoFailure.NOT_READY, "no scene on screen")
    if (surface.width == 0 || surface.height == 0 || !surface.holder.surface.isValid) {
        return PhotoResult.Failure(PhotoFailure.NOT_READY, "scene not laid out")
    }
    val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
    val result = suspendCancellableCoroutine { cont ->
        PixelCopy.request(surface, bitmap, { code ->
            if (cont.isActive) cont.resume(pixelCopyResult(code, bitmap))
        }, Handler(Looper.getMainLooper()))
    }
    if (result is PhotoResult.Failure) {
        bitmap.recycle()
        Log.w("WillItFit", "photo not taken: ${result.reason} (${result.cause})")
    }
    return result
}

private fun findSurfaceView(view: View): SurfaceView? {
    if (view is SurfaceView && view.isShown) return view
    if (view is ViewGroup) for (i in 0 until view.childCount) findSurfaceView(view.getChildAt(i))?.let { return it }
    return null
}

/** Earlier photos kept next to the new one, for apps that read a shared photo late. */
private const val KEPT_PHOTOS = 4

/** How the card answers: a check, a check on amber when it is tight, a cross. Null for an AR photo. */
enum class CardVerdict { FITS, TIGHT, NO }

/** The card is a phone screen, 9:16: what a story, a status or a chat shows whole. */
const val CARD_WIDTH = 1080
const val CARD_HEIGHT = 1920
/** The picture's frame at the top of the card. */
const val CARD_SCENE_HEIGHT = 1200

/**
 * Lays the picture out as a 9:16 card and saves it as a JPEG: the scene (or the AR photo) on top,
 * then the answer in its colour, the margin in large, the object and the space, and at the foot
 * the app's icon, its name on Google Play and the store address, for whoever receives it.
 * [crop] is the part of [bitmap] worth showing (the scene above the sheet); the whole picture if null.
 */
suspend fun savePhoto(
    context: Context,
    bitmap: Bitmap,
    title: String,
    headline: String?,
    lines: List<String>,
    signature: String,
    titleColor: Int = 0xFFFFFFFF.toInt(),
    verdict: CardVerdict? = null,
    background: Int? = null,
    crop: Rect? = null,
): File =
    withContext(Dispatchers.Default) {
        val card = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        drawCard(context, Canvas(card), bitmap, crop, background, title, titleColor, verdict, headline, lines, signature)
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        // The last few stay: an app the photo was shared to may read it a while later.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEPT_PHOTOS)?.forEach { it.delete() }
        val file = File(dir, "will-it-fit-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        card.recycle()
        file
    }

/** The card's own dark: the app's night surface. */
private const val CARD_BACKGROUND = 0xFF141218.toInt()
private const val STORE_ADDRESS = "play.google.com/store/apps/details?id=com.gorisse.thomas.willitfit"

private fun drawCard(
    context: Context, c: Canvas, scene: Bitmap, crop: Rect?, background: Int?,
    title: String, titleColor: Int, verdict: CardVerdict?, headline: String?, lines: List<String>, signature: String,
) {
    val w = CARD_WIDTH.toFloat()
    val left = 72f
    val right = w - 72f
    c.drawColor(CARD_BACKGROUND)

    // The scene, cut to fill its frame without stretching. A scene drawn over a Compose
    // background reads back transparent there: it lies on the colour the screen shows behind it.
    val frame = RectF(0f, 0f, w, CARD_SCENE_HEIGHT.toFloat())
    if (background != null) c.drawRect(frame, Paint().apply { color = background })
    val source = crop?.takeIf { !it.isEmpty } ?: Rect(0, 0, scene.width, scene.height)
    c.drawBitmap(scene, centerCrop(source, frame.width() / frame.height()), frame, Paint(Paint.FILTER_BITMAP_FLAG))
    // Its foot melts into the card, so the answer reads as part of the picture.
    c.drawRect(
        RectF(0f, frame.bottom - 220f, w, frame.bottom),
        Paint().apply {
            shader = LinearGradient(0f, frame.bottom - 220f, 0f, frame.bottom, CARD_BACKGROUND and 0x00FFFFFF, CARD_BACKGROUND, Shader.TileMode.CLAMP)
        },
    )

    // The answer: its mark and its words, in the verdict's colour.
    var y = frame.bottom + 40f
    var textLeft = left
    if (verdict != null) {
        drawMark(c, RectF(left, y, left + 84f, y + 84f), verdict, titleColor)
        textLeft = left + 84f + 28f
    }
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor; textSize = 64f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    c.drawText(fit(title, titlePaint, right - textLeft), textLeft, y + 64f, titlePaint)
    y += 84f + 36f
    if (headline != null) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 54f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        y += 54f
        c.drawText(fit(headline, paint, right - left), left, y, paint)
        y += 30f
    }
    lines.forEach { line ->
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt(); textSize = 40f }
        y += 58f
        c.drawText(fit(line, paint, right - left), left, y, paint)
    }

    // The foot: where the card comes from.
    val rule = 1676f
    c.drawRect(left, rule, right, rule + 2f, Paint().apply { color = 0x29FFFFFF })
    val icon = RectF(left, rule + 44f, left + 96f, rule + 140f)
    drawAppIcon(context, c, icon)
    val signaturePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 40f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    c.drawText(fit(signature, signaturePaint, right - icon.right - 28f), icon.right + 28f, icon.centerY() - 6f, signaturePaint)
    val addressPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9FE3E6.toInt(); textSize = 26f }
    c.drawText(fit(STORE_ADDRESS, addressPaint, right - icon.right - 28f), icon.right + 28f, icon.centerY() + 38f, addressPaint)
}

/** The part of [source] with the frame's [aspect] (width / height), from its middle. */
private fun centerCrop(source: Rect, aspect: Float): Rect {
    val sw = source.width().toFloat()
    val sh = source.height().toFloat()
    return if (sw / sh > aspect) {
        val cw = (sh * aspect).toInt()
        val x = source.left + ((sw - cw) / 2).toInt()
        Rect(x, source.top, x + cw, source.bottom)
    } else {
        val ch = (sw / aspect).toInt()
        val y = source.top + ((sh - ch) / 2).toInt()
        Rect(source.left, y, source.right, y + ch)
    }
}

/** A filled disc in the verdict's colour, with a check or a cross cut in the card's dark. */
private fun drawMark(c: Canvas, r: RectF, verdict: CardVerdict, color: Int) {
    c.drawOval(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = CARD_BACKGROUND; style = Paint.Style.STROKE; strokeWidth = r.width() * 0.11f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val u = r.width() / 100f
    val path = Path()
    if (verdict == CardVerdict.NO) {
        path.moveTo(r.left + 32 * u, r.top + 32 * u); path.lineTo(r.left + 68 * u, r.top + 68 * u)
        path.moveTo(r.left + 68 * u, r.top + 32 * u); path.lineTo(r.left + 32 * u, r.top + 68 * u)
    } else {
        path.moveTo(r.left + 28 * u, r.top + 52 * u); path.lineTo(r.left + 44 * u, r.top + 67 * u); path.lineTo(r.left + 73 * u, r.top + 35 * u)
    }
    c.drawPath(path, stroke)
}

/** The launcher icon as a launcher draws it: the middle two thirds of the adaptive foreground on its teal. */
private fun drawAppIcon(context: Context, c: Canvas, icon: RectF) {
    val foreground = BitmapFactory.decodeResource(
        context.resources, R.mipmap.ic_launcher_foreground, BitmapFactory.Options().apply { inScaled = false },
    )
    c.save()
    c.clipPath(Path().apply { addRoundRect(icon, icon.width() * 0.23f, icon.width() * 0.23f, Path.Direction.CW) })
    c.drawColor(context.getColor(R.color.ic_launcher_background))
    if (foreground != null) {
        val s = foreground.width
        c.drawBitmap(foreground, Rect(s / 6, s / 6, s * 5 / 6, s * 5 / 6), icon, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    c.restore()
}

/** Shrinks the text a little if it is too wide, then cuts it with an ellipsis. */
private fun fit(text: String, paint: TextPaint, maxWidth: Float): String {
    val size = paint.textSize
    while (paint.measureText(text) > maxWidth && paint.textSize > size * 0.8f) paint.textSize *= 0.95f
    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
}

fun sharePhoto(context: Context, file: File, chooserTitle: String, text: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        // The share sheet previews only what the clip data carries, and reads it with the grant:
        // without it, the sheet showed no picture, so the person did not see what they sent.
        clipData = ClipData.newRawUri(null, uri)
        putExtra(Intent.EXTRA_TITLE, text.lineSequence().first())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/** The gallery album the photos go to: Pictures/Will It Fit. */
const val GALLERY_ALBUM = "Will It Fit"

/**
 * Saving to the gallery needs no permission from Android 10 on (the app writes only its own
 * pictures through MediaStore). On Android 9 it would need the storage permission, which the app
 * does not ask for: there the button is not shown, and the share sheet still offers to save.
 */
val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * Copies the captioned photo into Pictures/[GALLERY_ALBUM], where the gallery and Google Photos
 * find it. Pending until fully written, so a half-written picture never shows; removed if the
 * copy fails. Returns false when it could not be saved (often a phone short on space).
 */
suspend fun saveToGallery(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = try {
        resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    } catch (e: Exception) {
        Log.w("WillItFit", "photo not added to the gallery", e)
        null
    } ?: return@withContext false
    try {
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("no output stream")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        true
    } catch (e: Exception) {
        Log.w("WillItFit", "photo not saved to the gallery", e)
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}
