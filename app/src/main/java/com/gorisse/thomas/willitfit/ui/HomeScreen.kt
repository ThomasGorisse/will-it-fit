package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.scene.RoomPalette
import com.gorisse.thomas.willitfit.scene.RoomScene

/**
 * Screen 1, "Size". The room and the object fill the screen from the first frame; the sheet
 * holds everything the thumb needs: the three sides, the typical objects, and the two ways to
 * answer the question (in my room, through a door).
 */
@Composable
fun HomeScreen(
    dims: Dimensions,
    unit: LengthUnit,
    arAvailable: Boolean,
    onDims: (Dimensions) -> Unit,
    onAr: () -> Unit,
    onDoor: () -> Unit,
    onSettings: () -> Unit,
) {
    val density = LocalDensity.current
    var sheetPx by remember { mutableIntStateOf(0) }
    var fieldsValid by remember { mutableStateOf(true) }
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().imePadding()) {
        RoomScene(
            dims = dims,
            unit = unit,
            accent = FurnitureTeal,
            palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            labelText = Color(0xFF1C1B1F),
            labelBackground = Color(0xF2FFFFFF),
            modifier = Modifier.fillMaxSize(),
            // Keep the object centred in what the sheet leaves free, minus its rounded top.
            contentPadding = PaddingValues(bottom = (with(density) { sheetPx.toDp() } - 28.dp).coerceAtLeast(0.dp)),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { sheetPx = it.height },
            color = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 20.dp, bottom = 16.dp)) {
                Row(
                    Modifier.padding(start = 24.dp, end = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = when (dims.source) {
                                DimensionSource.PRESET -> stringResource(R.string.home_subtitle_preset, objectName(dims))
                                DimensionSource.TYPED -> stringResource(R.string.home_subtitle_typed, objectName(dims))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
                Spacer(Modifier.height(16.dp))
                LengthFields(
                    labels = listOf(
                        stringResource(R.string.field_width),
                        stringResource(R.string.field_depth),
                        stringResource(R.string.field_height),
                    ),
                    values = listOf(dims.widthCm, dims.depthCm, dims.heightCm),
                    unit = unit,
                    onChange = { i, cm ->
                        onDims(
                            when (i) {
                                0 -> dims.withWidth(cm)
                                1 -> dims.withDepth(cm)
                                else -> dims.withHeight(cm)
                            },
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onValidChange = { fieldsValid = it },
                )
                Spacer(Modifier.height(12.dp))
                PresetRow(dims, onPick = { onDims(it) })
                Spacer(Modifier.height(16.dp))
                // The two answers, stacked at the bottom where the thumb rests.
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAr,
                        enabled = arAvailable && dims.isValid && fieldsValid,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.ViewInAr, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_ar), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalButton(
                        onClick = onDoor,
                        enabled = dims.isValid && fieldsValid,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Outlined.DoorFront, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_door), style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (!arAvailable) {
                    Text(
                        stringResource(R.string.ar_unsupported),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetRow(dims: Dimensions, onPick: (Dimensions) -> Unit) {
    val state = rememberLazyListState()
    val selected = Preset.byId(dims.presetId)
    LaunchedEffect(Unit) {
        val i = Preset.entries.indexOf(selected)
        if (i > 0) state.scrollToItem(i)
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Preset.entries, key = { it.id }) { preset ->
            PresetCard(
                selected = preset == selected,
                label = stringResource(preset.label()),
                onClick = { onPick(preset.dimensions) },
            ) {
                Image(
                    painter = painterResource(preset.thumbnail()),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp),
                )
            }
        }
        item(key = "custom") {
            PresetCard(
                selected = selected == null,
                label = stringResource(R.string.preset_custom),
                onClick = {
                    onPick(Dimensions(dims.widthCm, dims.depthCm, dims.heightCm))
                },
            ) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun PresetCard(selected: Boolean, label: String, onClick: () -> Unit, image: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.width(96.dp).semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        ),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp, start = 6.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            image()
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
