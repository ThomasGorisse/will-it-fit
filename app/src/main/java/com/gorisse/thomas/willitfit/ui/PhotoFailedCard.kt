package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.capture.PhotoFailure
import com.gorisse.thomas.willitfit.capture.message

/**
 * A photo that did not come out: what happened in one line, and Try again. Same card as the AR
 * screen's status cards, so a failure reads like the app's other messages. Announced by TalkBack.
 */
@Composable
fun PhotoFailedCard(
    reason: PhotoFailure,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    retryEnabled: Boolean = true,
) {
    Surface(
        color = Color(0xE6141218),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
            .widthIn(max = 420.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp)) {
            Text(
                stringResource(R.string.photo_failed_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(reason.message),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                    Text(stringResource(R.string.action_dismiss))
                }
                Spacer(Modifier.size(8.dp))
                Button(onClick = onRetry, enabled = retryEnabled) {
                    Text(stringResource(R.string.action_try_again))
                }
            }
        }
    }
}
