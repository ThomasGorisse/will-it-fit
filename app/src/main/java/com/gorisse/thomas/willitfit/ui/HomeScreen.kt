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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.data.RecentSizes
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.fit.DimensionSource
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.dimensionsIn
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
    onMeasure: () -> Unit = {},
    onMyHome: () -> Unit = {},
    recentSizes: List<Dimensions> = emptyList(),
    premium: Boolean = false,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val sidePane = configuration.screenWidthDp >= 840
    var sheetPx by remember(sidePane) { mutableIntStateOf(0) }
    var fieldsValid by remember { mutableStateOf(true) }
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    // Short phones (5", 16:9, or a big font) keep at least half the screen for the room.
    val compact = configuration.screenHeightDp < 760
    val buttonHeight = if (compact) 48.dp else 52.dp
    Box(Modifier.fillMaxSize().imePadding()) {
        RoomScene(
            dims = dims,
            unit = unit,
            accent = FurnitureTeal,
            palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            labelText = Color(0xFF1C1B1F),
            labelBackground = Color(0xF2FFFFFF),
            modifier = Modifier.fillMaxSize(),
            // SceneView resolves end padding with the layout direction and centres the camera
            // in the space beside the pane. Bottom sheets keep the phone's existing framing.
            contentPadding = if (sidePane) PaddingValues(end = 400.dp) else PaddingValues(
                bottom = (with(density) { sheetPx.toDp() } - 28.dp).coerceAtLeast(0.dp),
            ),
        )
        Surface(
            // The pane fills a wide window's end edge; smaller windows keep the bottom sheet.
            // With very large text the content still scrolls clear of the system bars.
            modifier = if (sidePane) {
                Modifier.align(Alignment.CenterEnd).width(400.dp).fillMaxHeight()
            } else {
                Modifier.align(Alignment.BottomCenter).statusBarsPadding().formWidth()
                    .fillMaxWidth().onSizeChanged { sheetPx = it.height }
            },
            color = scheme.surfaceContainerLow,
            shape = if (sidePane) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.then(if (sidePane) Modifier.statusBarsPadding() else Modifier)
                    .verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(top = if (compact) 12.dp else 16.dp, bottom = 12.dp),
            ) {
                Row(
                    Modifier.padding(start = 24.dp, end = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = when (dims.source) {
                                DimensionSource.PRESET -> stringResource(R.string.home_subtitle_preset, objectName(dims))
                                // "Your size · your size" for a size typed from scratch: the name says it.
                                DimensionSource.TYPED -> if (dims.presetId == null) objectName(dims)
                                else stringResource(R.string.home_subtitle_typed, objectName(dims))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            // A long name with "check yours" runs past one line on a phone in most
                            // languages: two lines keep the advice, the ellipsis stays for extremes.
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // The AR tape: measure the object (or the space) and its sides fill in.
                    if (arAvailable) IconButton(onClick = onMeasure) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Straighten, contentDescription = stringResource(R.string.action_measure))
                            if (!premium) PremiumLock()
                        }
                    }
                    // My home: the person's door, hallway and car, and their things for moving day.
                    IconButton(onClick = onMyHome) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.home_my_title))
                            if (!premium) PremiumLock()
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
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
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                PresetRow(dims, unit, compact, recentSizes, onPick = { onDims(it) })
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
                // The two answers, stacked at the bottom where the thumb rests.
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAr,
                        enabled = arAvailable && dims.isValid && fieldsValid,
                        // A minimum, not a height: a label on two lines (large text) still fits.
                        modifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight),
                    ) {
                        Icon(Icons.Outlined.ViewInAr, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.action_ar), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalButton(
                        onClick = onDoor,
                        enabled = dims.isValid && fieldsValid,
                        modifier = Modifier.fillMaxWidth().heightIn(min = buttonHeight),
                        // In the evening a solid deep teal, as in the target, not a grey-teal tone.
                        colors = if (dark) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
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
private fun PresetRow(
    dims: Dimensions,
    unit: LengthUnit,
    compact: Boolean,
    recentSizes: List<Dimensions>,
    onPick: (Dimensions) -> Unit,
) {
    val thumb = if (compact) 48.dp else 60.dp
    val state = rememberLazyListState()
    // A size of the person's own that matches wins the selection: an edited sofa is theirs now.
    val recentIndex = recentSizes.indexOfFirst { RecentSizes.matches(dims, it) }
    val selected = if (recentIndex >= 0) null else Preset.byId(dims.presetId)
    // Again when the history changes: back home after an answer, the size just checked joins
    // the row after its first frame, and the row would otherwise keep it out of view.
    LaunchedEffect(recentSizes) {
        // The selected card with its left neighbour in full view, never a card cut in half.
        // The person's own recent sizes lead the row, so a preset sits that many cards further.
        val i = if (recentIndex >= 0) recentIndex
        else Preset.entries.indexOf(selected).let { if (it < 0) it else it + recentSizes.size }
        if (i >= 0) state.scrollToItem((i - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Sizes the person checked before come first: they are why the row is opened again.
        items(recentSizes, key = { "recent_${it.widthCm}_${it.depthCm}_${it.heightCm}" }) { entry ->
            val formatted = formatDimensions(entry, unit)
            PresetCard(
                compact = compact,
                selected = RecentSizes.matches(dims, entry),
                label = formatted.removeSuffix(" ${unit.symbol}"),
                description = stringResource(R.string.recent_size, formatted),
                onClick = { onPick(entry.copy(source = DimensionSource.TYPED)) },
            ) {
                // The object it started from when there is one, a clock otherwise: the label's
                // numbers already say it is the person's own size.
                val preset = Preset.byId(entry.presetId)
                if (preset != null) {
                    Image(
                        painter = painterResource(preset.thumbnail()),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(thumb),
                    )
                } else {
                    Box(Modifier.size(thumb), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        items(Preset.entries, key = { it.id }) { preset ->
            PresetCard(
                compact = compact,
                selected = preset == selected,
                label = stringResource(preset.label()),
                onClick = { onPick(preset.dimensionsIn(unit)) },
            ) {
                Image(
                    painter = painterResource(preset.thumbnail()),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(thumb),
                )
            }
        }
        item(key = "custom") {
            PresetCard(
                compact = compact,
                selected = selected == null && recentIndex < 0,
                label = stringResource(R.string.preset_custom),
                onClick = {
                    onPick(Dimensions(dims.widthCm, dims.depthCm, dims.heightCm))
                },
            ) {
                Box(Modifier.size(thumb), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Shared with welcome so the same thumbnail always stands for the same object. */
@Composable
internal fun PresetCard(
    compact: Boolean,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    wrapLabel: Boolean = false,
    image: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        // As wide as its name needs ("Canapé 3 places", "Waschmaschine"), never cut with an ellipsis.
        modifier = modifier.widthIn(min = if (compact) 80.dp else 88.dp).semantics {
            this.selected = selected
            role = Role.RadioButton
            if (description != null) contentDescription = description
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        ),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            image()
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = if (wrapLabel) Modifier.heightIn(min = 32.dp) else Modifier,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = if (wrapLabel) Int.MAX_VALUE else 1, softWrap = wrapLabel,
            )
        }
    }
}
