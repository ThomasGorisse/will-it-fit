package com.gorisse.thomas.willitfit.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.gorisse.thomas.willitfit.fit.BootShareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Draw a PNG directly in Kotlin, without reading a SurfaceView or waiting for a scene frame. */
suspend fun saveBootShareCard(
    context: Context, text: BootShareText, fits: Boolean, tight: Boolean, details: List<String>,
): File = withContext(Dispatchers.Default) {
    val width = 1080
    val inset = 72f
    val color = when { !fits -> 0xFFFFB4AB; tight -> 0xFFF2C46D; else -> 0xFF8DD99A }.toInt()
    fun layout(line: String, size: Float, ink: Int, bold: Boolean = false): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = ink
            textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(line, 0, line.length, paint, width - 144)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }
    // Wrapping and a content-sized bitmap preserve long car names and translated caveats.
    val blocks = listOf(
        layout(text.title, 66f, color, true),
        layout(text.clearance, 54f, Color.WHITE, true),
    ) + details.map { layout(it, 36f, 0xFFDCD8E0.toInt()) } +
        layout(text.signature, 38f, color, true)
    val height = (160 + blocks.sumOf { it.height + 40 } + 32).coerceAtLeast(1080)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF141218.toInt())
        canvas.drawRoundRect(inset, 64f, width - inset, 80f, 8f, 8f, Paint().apply { this.color = color })
        var y = 144f
        blocks.forEach { block ->
            canvas.save()
            canvas.translate(inset, y)
            block.draw(canvas)
            canvas.restore()
            y += block.height + 40f
        }
        val dir = File(context.cacheDir, "photos").apply { check(isDirectory || mkdirs()) }
        // Keep recent cards for recipients that open the granted URI later. Never remove active shares.
        dir.listFiles()?.filter { it.name.startsWith("boot-") && it.lastModified() < System.currentTimeMillis() - 7 * 86_400_000L }
            ?.forEach { it.delete() }
        val file = File(dir, "boot-${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        file
    } finally {
        bitmap.recycle()
    }
}
