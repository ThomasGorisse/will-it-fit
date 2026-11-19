package com.gorisse.thomas.willitfit.capture

import android.view.PixelCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class PhotoResultTest {

    /** Every value setBusy received, in order. */
    private val busy = mutableListOf<Boolean>()

    @Test
    fun `a photo read back and saved is a success with the saved file`() = runBlocking {
        val result = takePhoto(
            setBusy = { busy += it },
            capture = { PhotoResult.Success("pixels") },
            save = { "$it.jpg" },
        )
        assertEquals(PhotoResult.Success("pixels.jpg"), result)
        assertEquals(listOf(true, false), busy)
    }

    @Test
    fun `a failed read back is passed on, nothing is saved, and the screen is not left busy`() = runBlocking {
        val result = takePhoto<String, String>(
            setBusy = { busy += it },
            capture = { PhotoResult.Failure(PhotoFailure.NOT_READY) },
            save = { fail("nothing to save"); it },
        )
        assertEquals(PhotoFailure.NOT_READY, (result as PhotoResult.Failure).reason)
        assertEquals(listOf(true, false), busy)
    }

    @Test
    fun `a capture that throws becomes a copy failure and still clears busy`() = runBlocking {
        val result = takePhoto<String, String>(
            setBusy = { busy += it },
            capture = { throw IllegalArgumentException("surface not valid") },
            save = { it },
        )
        result as PhotoResult.Failure
        assertEquals(PhotoFailure.COPY_FAILED, result.reason)
        assertTrue(result.cause!!.contains("surface not valid"))
        assertEquals(listOf(true, false), busy)
    }

    @Test
    fun `a save that throws becomes a save failure`() = runBlocking {
        val result = takePhoto<String, String>(
            setBusy = { busy += it },
            capture = { PhotoResult.Success("pixels") },
            save = { throw IOException("No space left on device") },
        )
        assertEquals(PhotoFailure.SAVE_FAILED, (result as PhotoResult.Failure).reason)
        assertEquals(listOf(true, false), busy)
    }

    @Test
    fun `leaving the screen mid-capture cancels, without a failure card, and clears busy`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<PhotoResult<String>>()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            takePhoto(setBusy = { busy += it }, capture = { started.complete(Unit); never.await() }, save = { it })
        }
        started.await()
        job.cancel()
        try {
            job.await()
            fail("a cancelled photo is not a result")
        } catch (_: CancellationException) {
        }
        assertEquals(listOf(true, false), busy)
    }

    @Test
    fun `PixelCopy's answers map to what the person can do about them`() {
        assertEquals(PhotoResult.Success("b"), pixelCopyResult(PixelCopy.SUCCESS, "b"))
        for (code in listOf(PixelCopy.ERROR_SOURCE_NO_DATA, PixelCopy.ERROR_SOURCE_INVALID)) {
            assertEquals(PhotoFailure.NOT_READY, (pixelCopyResult(code, "b") as PhotoResult.Failure).reason)
        }
        for (code in listOf(PixelCopy.ERROR_UNKNOWN, PixelCopy.ERROR_TIMEOUT, PixelCopy.ERROR_DESTINATION_INVALID, 99)) {
            val r = pixelCopyResult(code, "b") as PhotoResult.Failure
            assertEquals(PhotoFailure.COPY_FAILED, r.reason)
            assertTrue(r.cause!!.contains("$code"))
        }
    }

    @Test
    fun `every failure has its own message`() {
        val messages = PhotoFailure.entries.map { it.message }
        assertEquals(messages.size, messages.toSet().size)
        messages.forEach { assertNotEquals(0, it) }
        assertFalse(messages.isEmpty())
    }
}
