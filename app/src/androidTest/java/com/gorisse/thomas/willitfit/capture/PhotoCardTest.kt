package com.gorisse.thomas.willitfit.capture

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.formatDimensions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The caption card written on a shared photo, in the six languages, on a wall-and-floor stand-in
 * for the camera image. The cards are kept in the app's external files ("photo-cards/") to be
 * looked at: `adb pull /sdcard/Android/data/com.gorisse.thomas.willitfit.debug/files/photo-cards`.
 */
@RunWith(AndroidJUnit4::class)
class PhotoCardTest {
    private val app: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theCardIsWrittenOnThePhotoInEveryLanguage() = runBlocking {
        val keep = File(app.getExternalFilesDir(null), "photo-cards").apply { deleteRecursively(); mkdirs() }
        for (tag in listOf("en-GB", "fr-FR", "de-DE", "es-ES", "it-IT", "pt-BR")) {
            val res = app.createConfigurationContext(
                Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
            )
            val unit = if (tag == "en-GB") LengthUnit.INCH else LengthUnit.CM
            for (preset in listOf(Preset.SOFA_3, Preset.WARDROBE)) {
                val title = res.getString(if (preset == Preset.SOFA_3) R.string.preset_sofa3 else R.string.preset_wardrobe)
                val caption = res.getString(R.string.ar_real_size, formatDimensions(preset.dimensions, unit))
                val file = savePhoto(res, standIn(), title, listOf(caption), res.getString(R.string.photo_signature))
                val photo = BitmapFactory.decodeFile(file.path)
                assertEquals(1080, photo.width)
                // The card's dark ground sits where the floor was light.
                val ground = photo.getPixel(60, photo.height - 140)
                assertTrue("no card at the bottom ($tag)", android.graphics.Color.luminance(ground) < 0.1f)
                file.copyTo(File(keep, "$tag-${preset.name.lowercase()}.jpg"), overwrite = true)
            }
            // The door's answer: a coloured title and three lines, the longest card there is.
            val door = savePhoto(
                res, standIn(), res.getString(R.string.door_no),
                listOf(
                    res.getString(R.string.door_object, res.getString(R.string.preset_wardrobe), formatDimensions(Preset.WARDROBE.dimensions, unit)),
                    res.getString(R.string.photo_door, "78 × 200 ${unit.symbol}"),
                    res.getString(R.string.door_margin_over, "3 ${unit.symbol}") + " · " + res.getString(R.string.carry_on_back_sideways),
                ),
                res.getString(R.string.photo_signature), 0xFFFFB4AB.toInt(),
            )
            door.copyTo(File(keep, "$tag-door.jpg"), overwrite = true)
        }
    }

    /** A pale wall over a wooden floor, the size of a Pixel's camera view. */
    private fun standIn(): Bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).also {
        val c = Canvas(it)
        c.drawRect(0f, 0f, 1080f, 1000f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 1000f, 0xFFE9E6E1.toInt(), 0xFFD8D4CD.toInt(), Shader.TileMode.CLAMP)
        })
        c.drawRect(0f, 1000f, 1080f, 2400f, Paint().apply {
            shader = LinearGradient(0f, 1000f, 0f, 2400f, 0xFFB08E68.toInt(), 0xFF8C6B4A.toInt(), Shader.TileMode.CLAMP)
        })
    }
}
