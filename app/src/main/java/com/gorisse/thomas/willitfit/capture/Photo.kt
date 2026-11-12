package com.gorisse.thomas.willitfit.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * What the scene renders — the camera image and the object, composited by Filament in the
 * scene's SurfaceView — read back with PixelCopy. The Compose controls are not in it, by design:
 * the caption is drawn on the photo afterwards. Needs no permission.
 */
suspend fun captureScene(activity: Activity): Bitmap? {
    val surface = findSurfaceView(activity.window.decorView) ?: return null
    if (surface.width == 0 || surface.height == 0) return null
    val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { cont ->
        PixelCopy.request(surface, bitmap, { result ->
            cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
        }, Handler(Looper.getMainLooper()))
    }
}

private fun findSurfaceView(view: View): SurfaceView? {
    if (view is SurfaceView && view.isShown) return view
    if (view is ViewGroup) for (i in 0 until view.childCount) findSurfaceView(view.getChildAt(i))?.let { return it }
    return null
}

/** Writes the caption on the photo (so it survives sharing) and saves it as a JPEG. */
suspend fun savePhoto(context: Context, bitmap: Bitmap, title: String, caption: String, brand: String): File =
    withContext(Dispatchers.Default) {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val u = out.width / 100f
        val pad = 4f * u
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 4.6f * u; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 4.0f * u; typeface = Typeface.DEFAULT
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt(); textSize = 3.0f * u; typeface = Typeface.DEFAULT
        }
        val boxH = 18f * u
        val bottom = out.height - 10f * u
        val rect = RectF(pad, bottom - boxH, out.width - pad, bottom)
        c.drawRoundRect(rect, 3f * u, 3f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9141218.toInt() })
        c.drawText(title, rect.left + pad, rect.top + 6.5f * u, titlePaint)
        c.drawText(caption, rect.left + pad, rect.top + 12f * u, captionPaint)
        c.drawText(brand, rect.left + pad, rect.top + 16f * u, brandPaint)
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest photo
        val file = File(dir, "will-it-fit-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file
    }

fun sharePhoto(context: Context, file: File, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
