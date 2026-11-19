package com.gorisse.thomas.willitfit.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.capture.PhotoFailure
import com.gorisse.thomas.willitfit.capture.PhotoResult
import com.gorisse.thomas.willitfit.capture.STORE_LINK
import com.gorisse.thomas.willitfit.capture.captureScene
import com.gorisse.thomas.willitfit.capture.savePhoto
import com.gorisse.thomas.willitfit.capture.sharePhoto
import com.gorisse.thomas.willitfit.capture.takePhoto
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Shape
import com.gorisse.thomas.willitfit.fit.checkDoor
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.fit.formatNumber
import com.gorisse.thomas.willitfit.fit.shape
import com.gorisse.thomas.willitfit.scene.DoorScene
import com.gorisse.thomas.willitfit.scene.RoomPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Screen 4, "Through the door?". No camera: the door's opening, and the answer — yes or no, by
 * how much, and which way to carry it — acted out in 3D above. Share sends that answer as a
 * picture: the scene held on the doorway, with the answer written on it.
 */
@Composable
fun DoorScreen(
    dims: Dimensions,
    door: Door,
    unit: LengthUnit,
    onDoor: (Door) -> Unit,
    onBack: () -> Unit,
    onVerdictShown: () -> Unit = {},
) {
    val verdict = checkDoor(dims, door)
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var sheetPx by remember { mutableIntStateOf(0) }
    var fieldsValid by remember { mutableStateOf(true) }
    // A verdict on screen for a second counts as seen: an answer, for Play's rating sheet.
    LaunchedEffect(fieldsValid) {
        if (fieldsValid) { delay(1_000); onVerdictShown() }
    }
    var holding by remember { mutableStateOf(false) }
    // The last share whose picture did not come out, and why: a card with Try again.
    var photoFailure by remember { mutableStateOf<PhotoFailure?>(null) }
    var shot by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val answer = stringResource(
        when {
            !verdict.fits -> R.string.door_no
            verdict.isTight -> R.string.door_tight
            else -> R.string.door_yes
        },
    )
    val margin = stringResource(
        if (verdict.fits) R.string.door_margin_spare else R.string.door_margin_over,
        formatLength(abs(verdict.marginCm), unit),
    ) + " · " + stringResource(verdict.carry.label())
    val objectLine = stringResource(R.string.door_object, objectName(dims), formatDimensions(dims, unit))
    val doorLine = stringResource(
        R.string.photo_door, "${formatNumber(door.widthCm, unit)} × ${formatNumber(door.heightCm, unit)} ${unit.symbol}",
    )
    val signature = stringResource(R.string.photo_signature)
    val chooser = stringResource(R.string.share_chooser)

    Box(Modifier.fillMaxSize().imePadding()) {
        DoorScene(
            dims = dims, door = door, verdict = verdict,
            accent = FurnitureTeal,
            palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = (with(density) { sheetPx.toDp() } - 28.dp).coerceAtLeast(0.dp)),
            hold = holding,
        )
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp),
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
        val share: () -> Unit = share@{
            val activity = context as? Activity ?: return@share
            if (shot?.isActive == true) return@share // one picture at a time, saving included
            photoFailure = null
            shot = scope.launch {
                val result = takePhoto(
                    setBusy = { holding = it },
                    capture = {
                        // Hold the object on the answer and let the scene draw it before reading it back.
                        delay(250)
                        captureScene(activity)
                    },
                    save = { bitmap ->
                        val color = when {
                            !verdict.fits -> 0xFFFFB4AB // the dark scheme's error
                            verdict.isTight -> 0xFFF2C46D
                            else -> 0xFF8DD99A // FitColors.success, dark
                        }.toInt()
                        savePhoto(context, bitmap, answer, listOf(objectLine, doorLine, margin), signature, color)
                    },
                )
                when (result) {
                    is PhotoResult.Success -> sharePhoto(
                        context, result.value, chooser, "$answer · $margin\n$objectLine · $doorLine\n$STORE_LINK",
                    )
                    is PhotoResult.Failure -> photoFailure = result.reason
                }
            }
        }
        if (fieldsValid) FilledTonalIconButton(
            onClick = share,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(48.dp),
        ) { Icon(Icons.Outlined.Share, stringResource(R.string.door_share)) }

        var shownFailure by remember { mutableStateOf(PhotoFailure.COPY_FAILED) }
        photoFailure?.let { shownFailure = it }
        AnimatedVisibility(
            photoFailure != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
            // Just above the sheet, over the scene.
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { sheetPx.toDp() }),
        ) {
            PhotoFailedCard(
                reason = shownFailure,
                onRetry = share,
                onDismiss = { photoFailure = null },
                retryEnabled = fieldsValid,
            )
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { sheetPx = it.height },
            color = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 20.dp, bottom = 16.dp)) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.door_object, objectName(dims), formatDimensions(dims, unit)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!fieldsValid) {
                        Text(stringResource(R.string.door_enter), style = MaterialTheme.typography.headlineSmall)
                    } else AnimatedContent(verdict.fits to verdict.isTight, label = "verdict") { (fits, tight) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (fits) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                contentDescription = null,
                                tint = if (fits) FitColors.success() else scheme.error,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(
                                    when {
                                        !fits -> R.string.door_no
                                        tight -> R.string.door_tight
                                        else -> R.string.door_yes
                                    },
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (fieldsValid) Text(
                        text = stringResource(
                            if (verdict.fits) R.string.door_margin_spare else R.string.door_margin_over,
                            formatLength(abs(verdict.marginCm), unit),
                        ) + " · " + stringResource(verdict.carry.label()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (fieldsValid && !verdict.fits && dims.shape == Shape.SOFA) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.door_sofa_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                LengthFields(
                    labels = listOf(stringResource(R.string.door_width), stringResource(R.string.door_height)),
                    values = listOf(door.widthCm, door.heightCm),
                    unit = unit,
                    onChange = { i, cm -> onDoor(if (i == 0) door.copy(widthCm = cm) else door.copy(heightCm = cm)) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onValidChange = { fieldsValid = it },
                )
                Text(
                    stringResource(R.string.door_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                )
            }
        }
    }
}
