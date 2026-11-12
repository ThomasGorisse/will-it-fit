package com.gorisse.thomas.willitfit.ui

import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.capture.sharePhoto
import java.io.File

/** Screen 3, "Result": the photo with its dimensions written on it, ready to send. */
@Composable
fun ResultScreen(file: File, onBack: () -> Unit, onRetake: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(file) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
    val chooser = stringResource(R.string.share_chooser)
    AppScaffold(title = stringResource(R.string.result_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        it, contentDescription = stringResource(R.string.result_title),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onRetake, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.result_retake))
                }
                Button(onClick = { sharePhoto(context, file, chooser) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.result_share))
                }
            }
        }
    }
}
