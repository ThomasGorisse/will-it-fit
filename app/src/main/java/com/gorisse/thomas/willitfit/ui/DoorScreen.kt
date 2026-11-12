package com.gorisse.thomas.willitfit.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Shape
import com.gorisse.thomas.willitfit.fit.checkDoor
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.fit.shape
import com.gorisse.thomas.willitfit.scene.DoorScene
import com.gorisse.thomas.willitfit.scene.RoomPalette
import kotlin.math.abs

/**
 * Screen 4, "Through the door?". No camera: the door's opening, and the answer — yes or no, by
 * how much, and which way to carry it — acted out in 3D above.
 */
@Composable
fun DoorScreen(
    dims: Dimensions,
    door: Door,
    unit: LengthUnit,
    onDoor: (Door) -> Unit,
    onBack: () -> Unit,
) {
    val verdict = checkDoor(dims, door)
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var sheetPx by remember { mutableIntStateOf(0) }
    var fieldsValid by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().imePadding()) {
        DoorScene(
            dims = dims, door = door, verdict = verdict,
            accent = FurnitureTeal,
            palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = with(density) { sheetPx.toDp() } - 28.dp),
        )
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp),
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }

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
