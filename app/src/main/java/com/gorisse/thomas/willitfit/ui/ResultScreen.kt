package com.gorisse.thomas.willitfit.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.capture.GALLERY_ALBUM
import com.gorisse.thomas.willitfit.fit.SharedCheck
import com.gorisse.thomas.willitfit.capture.canSaveToGallery
import com.gorisse.thomas.willitfit.capture.saveToGallery
import com.gorisse.thomas.willitfit.capture.sharePhoto
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.formatDimensions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 3, "Result": the photo with its dimensions written on it, ready to send. The message
 * carries the same line and a link to the app, which messaging apps keep as the photo's caption.
 * "Save" keeps the photo in the gallery (Pictures/Will It Fit) without going through the share
 * sheet: once saved, the button says so and is not offered twice for the same photo.
 */
@Composable
fun ResultScreen(file: File, dims: Dimensions, unit: LengthUnit, onBack: () -> Unit, onRetake: () -> Unit) {
    val context = LocalContext.current
    // Decoded off the main thread: a full-screen JPEG takes a moment, the screen does not freeze.
    val loaded by produceState<Loaded?>(null, file) {
        value = Loaded(withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() })
    }
    val bitmap = loaded?.bitmap
    val chooser = stringResource(R.string.share_chooser)
    val scope = rememberCoroutineScope()
    // Keyed by the file: a retake gives a new photo, which can be saved again.
    var saved by rememberSaveable(file.path) { mutableStateOf(false) }
    var saving by remember(file.path) { mutableStateOf(false) }
    val savedText = stringResource(R.string.result_saved_where, GALLERY_ALBUM)
    val saveFailedText = stringResource(R.string.result_save_failed)
    val message = objectName(dims) + " · " + stringResource(R.string.ar_real_size, formatDimensions(dims, unit)) + "\n" + SharedCheck(dims, unit = unit).link()
    AppScaffold(title = stringResource(R.string.result_title), onBack = onBack) { padding ->
        // Wide windows (tablets, unfolded phones): the photo and its buttons stay phone-wide, centred.
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.formWidth().fillMaxSize().navigationBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    if (loaded != null && bitmap == null) Text(stringResource(R.string.result_missing), style = MaterialTheme.typography.bodyLarge)
                    bitmap?.let {
                        Image(
                            it, contentDescription = stringResource(R.string.result_title),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onRetake, modifier = Modifier.weight(1f).height(56.dp)) {
                            Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.result_retake), maxLines = 1)
                        }
                        if (canSaveToGallery) {
                            FilledTonalButton(
                                onClick = {
                                    saving = true
                                    scope.launch {
                                        val ok = saveToGallery(context, file)
                                        saving = false
                                        saved = ok
                                        Toast.makeText(context, if (ok) savedText else saveFailedText, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = bitmap != null && !saved && !saving,
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) {
                                Icon(if (saved) Icons.Outlined.Check else Icons.Outlined.Download, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(if (saved) R.string.result_saved else R.string.result_save), maxLines = 1)
                            }
                        }
                    }
                    Button(onClick = { sharePhoto(context, file, chooser, message) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.result_share))
                    }
                }
            }
        }
    }
}

/** The photo once read: [bitmap] is null when the file could not be decoded. */
private class Loaded(val bitmap: ImageBitmap?)
