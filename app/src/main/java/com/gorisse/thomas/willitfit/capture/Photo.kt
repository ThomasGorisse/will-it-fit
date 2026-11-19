package com.gorisse.thomas.willitfit.capture

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
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
 * the caption is drawn on the photo afterwards. Needs no permission. Never null: a failure says why.
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

/**
 * Where whoever receives a picture finds the app: the release build's store page, whatever build
 * shares. The referrer marks the installs that came from a shared picture in Play Console's
 * acquisition report (utm_source "share"); nothing is sent anywhere by the app.
 */
const val STORE_LINK = "https://play.google.com/store/apps/details?id=com.gorisse.thomas.willitfit&referrer=utm_source%3Dshare"

/**
 * Writes the caption card on the picture (so it survives sharing) and saves it as a JPEG: the app
 * icon, a title ("Sofa, 3 seats", or the door's answer in its colour), a line or three under it,
 * and where the app comes from, for whoever receives it.
 */
suspend fun savePhoto(
    context: Context,
    bitmap: Bitmap,
    title: String,
    lines: List<String>,
    signature: String,
    titleColor: Int = 0xFFFFFFFF.toInt(),
): File =
    withContext(Dispatchers.Default) {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        drawCaptionCard(context, Canvas(out), out.width.toFloat(), out.height.toFloat(), title, titleColor, lines, signature)
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest photo
        val file = File(dir, "will-it-fit-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file
    }

private fun drawCaptionCard(
    context: Context, c: Canvas, w: Float, h: Float,
    title: String, titleColor: Int, lines: List<String>, signature: String,
) {
    val u = w / 100f // every size is in hundredths of the photo's width
    val margin = 4f * u
    val pad = 4f * u
    val iconSize = 13f * u
    val lineStep = 5.2f * u
    // The row is the icon's height, or the text's when it runs longer.
    val rowHeight = maxOf(iconSize, 10.1f * u + (lines.size - 1).coerceAtLeast(0) * lineStep + 1.6f * u)
    val bottom = h - 9f * u // clear of the gesture bar
    val card = RectF(margin, bottom - (pad + rowHeight + 6.3f * u + 3.6f * u), w - margin, bottom)
    c.drawRoundRect(card, 5f * u, 5f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6141218.toInt() })

    // The launcher icon as a launcher draws it: the middle two thirds of the adaptive foreground
    // on its teal, in a rounded square.
    val icon = RectF(card.left + pad, card.top + pad, card.left + pad + iconSize, card.top + pad + iconSize)
    val foreground = BitmapFactory.decodeResource(
        context.resources, R.mipmap.ic_launcher_foreground, BitmapFactory.Options().apply { inScaled = false },
    )
    c.save()
    c.clipPath(Path().apply { addRoundRect(icon, 3f * u, 3f * u, Path.Direction.CW) })
    c.drawColor(context.getColor(R.color.ic_launcher_background))
    if (foreground != null) {
        val s = foreground.width
        c.drawBitmap(foreground, Rect(s / 6, s / 6, s * 5 / 6, s * 5 / 6), icon, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    c.restore()

    val textLeft = icon.right + 3.5f * u
    val textWidth = card.right - pad - textLeft
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = titleColor; textSize = 5f * u; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    c.drawText(fit(title, titlePaint, textWidth), textLeft, icon.top + 4.4f * u, titlePaint)
    lines.forEachIndexed { i, line ->
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEBFFFFFF.toInt(); textSize = 3.9f * u }
        c.drawText(fit(line, paint, textWidth), textLeft, icon.top + 10.1f * u + i * lineStep, paint)
    }

    val rule = icon.top + rowHeight + 2.2f * u
    c.drawRect(card.left + pad, rule, card.right - pad, rule + 0.15f * u, Paint().apply { color = 0x29FFFFFF })
    val signaturePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9FE3E6.toInt(); textSize = 3.1f * u }
    c.drawText(fit(signature, signaturePaint, card.width() - 2 * pad), card.left + pad, rule + 4.4f * u, signaturePaint)
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
