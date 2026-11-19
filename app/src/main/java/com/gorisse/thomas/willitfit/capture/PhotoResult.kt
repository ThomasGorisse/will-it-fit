package com.gorisse.thomas.willitfit.capture

import android.util.Log
import android.view.PixelCopy
import com.gorisse.thomas.willitfit.R
import kotlin.coroutines.cancellation.CancellationException

/**
 * A photo taken, or why not. Before, a failed photo was a null and the screen said nothing: the
 * shutter seemed not to work. Each reason has its own message and the screen offers to try again.
 */
sealed interface PhotoResult<out T> {
    data class Success<T>(val value: T) : PhotoResult<T>
    data class Failure(val reason: PhotoFailure, val cause: String? = null) : PhotoResult<Nothing>
}

enum class PhotoFailure {
    /** No scene on screen to read yet, or it has no frame: a moment later usually works. */
    NOT_READY,

    /** The scene was there but reading it back failed (PixelCopy error or timeout). */
    COPY_FAILED,

    /** The picture was taken but could not be written: often a phone short on space. */
    SAVE_FAILED,
}

/** The line the screen shows under "No photo this time". */
val PhotoFailure.message: Int
    get() = when (this) {
        PhotoFailure.NOT_READY -> R.string.photo_failed_not_ready
        PhotoFailure.COPY_FAILED -> R.string.photo_failed_copy
        PhotoFailure.SAVE_FAILED -> R.string.photo_failed_save
    }

/** What PixelCopy's answer means for the person. */
fun <B> pixelCopyResult(code: Int, bitmap: B): PhotoResult<B> = when (code) {
    PixelCopy.SUCCESS -> PhotoResult.Success(bitmap)
    // The surface is gone or has not drawn a frame yet: the scene is still starting.
    PixelCopy.ERROR_SOURCE_NO_DATA, PixelCopy.ERROR_SOURCE_INVALID ->
        PhotoResult.Failure(PhotoFailure.NOT_READY, "PixelCopy $code")
    else -> PhotoResult.Failure(PhotoFailure.COPY_FAILED, "PixelCopy $code")
}

/**
 * The shutter's whole path: busy while the scene is read back, not busy again whatever happens
 * (a failure, an exception, the screen left mid-capture), then the picture saved. Any exception
 * becomes a [PhotoResult.Failure]; a cancellation stays a cancellation.
 */
suspend fun <B, T> takePhoto(
    setBusy: (Boolean) -> Unit,
    capture: suspend () -> PhotoResult<B>,
    save: suspend (B) -> T,
): PhotoResult<T> {
    val shot = try {
        setBusy(true)
        capture()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w("WillItFit", "photo not taken", e)
        PhotoResult.Failure(PhotoFailure.COPY_FAILED, e.toString())
    } finally {
        setBusy(false)
    }
    return when (shot) {
        is PhotoResult.Failure -> shot
        is PhotoResult.Success -> try {
            PhotoResult.Success(save(shot.value))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("WillItFit", "photo not saved", e)
            PhotoResult.Failure(PhotoFailure.SAVE_FAILED, e.toString())
        }
    }
}
