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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.key
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
import com.gorisse.thomas.willitfit.data.Prefs
import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.Carry
import com.gorisse.thomas.willitfit.fit.BootLimit
import com.gorisse.thomas.willitfit.fit.DoorVerdict
import com.gorisse.thomas.willitfit.fit.checkBoot
import com.gorisse.thomas.willitfit.scene.BootScene
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
import com.gorisse.thomas.willitfit.fit.formatMargin
import com.gorisse.thomas.willitfit.fit.formatNumber
import com.gorisse.thomas.willitfit.fit.shape
import com.gorisse.thomas.willitfit.scene.DoorScene
import com.gorisse.thomas.willitfit.scene.RoomPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Door and boot checks, with the chosen orientation shown above the measurement sheet. */
@Composable
fun DoorScreen(
    dims: Dimensions,
    door: Door,
    unit: LengthUnit,
    onDoor: (Door) -> Unit,
    onBack: () -> Unit,
    onVerdictShown: () -> Unit = {},
    arAvailable: Boolean = false,
    onMeasure: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    var carBoot by remember { mutableStateOf(prefs.carBoot) }
    var boot by remember { mutableStateOf(prefs.boot) }
    var bootTyped by remember { mutableStateOf(prefs.bootTyped) }
    var bootFieldsRevision by remember { mutableIntStateOf(0) }
    var openingValid by remember(carBoot) { mutableStateOf(true) }
    var insideValid by remember(carBoot) { mutableStateOf(true) }
    val bootVerdict = checkBoot(dims, boot)
    val verdict = if (carBoot) DoorVerdict(bootVerdict.fits, bootVerdict.carry, bootVerdict.marginCm)
        else checkDoor(dims, door)
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var sheetPx by remember { mutableIntStateOf(0) }
    var doorValid by remember(carBoot) { mutableStateOf(true) }
    val fieldsValid = if (carBoot) openingValid && insideValid else doorValid
    // A verdict on screen for a second counts as seen: an answer, for Play's rating sheet.
    LaunchedEffect(fieldsValid, carBoot) {
        if (fieldsValid) { delay(1_000); onVerdictShown() }
    }
    var holding by remember { mutableStateOf(false) }
    // The last share whose picture did not come out, and why: a card with Try again.
    var photoFailure by remember { mutableStateOf<PhotoFailure?>(null) }
    var shot by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val answer = stringResource(
        when {
            !verdict.fits -> if (carBoot) R.string.boot_no else R.string.door_no
            verdict.isTight -> if (carBoot) R.string.boot_tight else R.string.door_tight
            else -> if (carBoot) R.string.boot_yes else R.string.door_yes
        },
    )
    val margin = (if (carBoot) {
        if (verdict.fits) stringResource(
            R.string.boot_margin_spare, formatMargin(verdict.marginCm, unit), stringResource(bootVerdict.limit.label()),
        ) else stringResource(bootVerdict.limit.overLabel(), formatMargin(verdict.marginCm, unit))
    } else stringResource(
        if (verdict.fits) R.string.door_margin_spare else R.string.door_margin_over,
        formatMargin(verdict.marginCm, unit),
    )) + " · " + stringResource(
        // In a boot the object faces the opening, not a door.
        if (carBoot && verdict.carry == Carry.UPRIGHT) R.string.carry_upright_boot else verdict.carry.label(),
    )
    val objectLine = stringResource(R.string.door_object, objectName(dims), formatDimensions(dims, unit))
    val doorLine = if (carBoot) stringResource(
        R.string.photo_boot,
        "${formatNumber(boot.openingWidthCm, unit)} × ${formatNumber(boot.openingHeightCm, unit)} ${unit.symbol}",
        "${formatNumber(boot.lengthCm, unit)} × ${formatNumber(boot.widthCm, unit)} × ${formatNumber(boot.heightCm, unit)} ${unit.symbol}",
    ) else stringResource(
        R.string.photo_door, "${formatNumber(door.widthCm, unit)} × ${formatNumber(door.heightCm, unit)} ${unit.symbol}",
    )
    val signature = stringResource(R.string.photo_signature)
    val chooser = stringResource(R.string.share_chooser)

    Box(Modifier.fillMaxSize().imePadding()) {
        val scenePadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp,
            bottom = (with(density) { sheetPx.toDp() } - 28.dp).coerceAtLeast(0.dp),
        )
        if (carBoot) BootScene(
            dims = dims, boot = boot, verdict = bootVerdict,
            accent = FurnitureTeal, palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            modifier = Modifier.fillMaxSize(), contentPadding = scenePadding,
        ) else DoorScene(
            dims = dims, door = door, verdict = verdict,
            accent = FurnitureTeal,
            palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            modifier = Modifier.fillMaxSize(),
            contentPadding = scenePadding,
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
            // Never over the back and share buttons: with very large text the sheet stops below
            // them and scrolls.
            modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding().padding(top = 72.dp)
                .formWidth().fillMaxWidth().onSizeChanged { sheetPx = it.height },
            color = scheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 20.dp, bottom = 16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                    listOf(R.string.space_door, R.string.space_boot).forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = carBoot == (i == 1),
                            onClick = { carBoot = i == 1; prefs.carBoot = carBoot; photoFailure = null },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(stringResource(label)) }
                    }
                }
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(R.string.door_object, objectName(dims), formatDimensions(dims, unit)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!fieldsValid) {
                        Text(stringResource(if (carBoot) R.string.boot_enter else R.string.door_enter), style = MaterialTheme.typography.headlineSmall)
                    } else AnimatedContent(Triple(carBoot, verdict.fits, verdict.isTight), label = "verdict") { (bootMode, fits, tight) ->
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
                                        !fits -> if (bootMode) R.string.boot_no else R.string.door_no
                                        tight -> if (bootMode) R.string.boot_tight else R.string.door_tight
                                        else -> if (bootMode) R.string.boot_yes else R.string.door_yes
                                    },
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (fieldsValid) Text(
                        text = margin,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!carBoot && fieldsValid && !verdict.fits && dims.shape == Shape.SOFA) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.door_sofa_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (carBoot) key(carBoot, bootFieldsRevision) {
                    val updateBoot: (Boot) -> Unit = { boot = it; prefs.boot = it; bootTyped = true }
                    Text(stringResource(R.string.boot_opening), modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleSmall)
                    LengthFields(
                        labels = listOf(stringResource(R.string.boot_width), stringResource(R.string.boot_height)),
                        values = listOf(boot.openingWidthCm, boot.openingHeightCm), unit = unit,
                        onChange = { i, cm -> updateBoot(if (i == 0) boot.copy(openingWidthCm = cm) else boot.copy(openingHeightCm = cm)) },
                        modifier = Modifier.padding(horizontal = 16.dp), onValidChange = { openingValid = it },
                    )
                    Text(stringResource(R.string.boot_inside), modifier = Modifier.padding(start = 24.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
                    LengthFields(
                        labels = listOf(stringResource(R.string.boot_length), stringResource(R.string.boot_width), stringResource(R.string.boot_height)),
                        values = listOf(boot.lengthCm, boot.widthCm, boot.heightCm), unit = unit,
                        onChange = { i, cm -> updateBoot(when (i) {
                            0 -> boot.copy(lengthCm = cm)
                            1 -> boot.copy(widthCm = cm)
                            else -> boot.copy(heightCm = cm)
                        }) },
                        modifier = Modifier.padding(horizontal = 16.dp), onValidChange = { insideValid = it },
                    )
                    Text(
                        stringResource(R.string.boot_help) + if (!bootTyped) " " + stringResource(R.string.boot_example_help) else "",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                    TextButton(onClick = {
                        prefs.useExampleBoot()
                        boot = prefs.boot
                        bootTyped = false
                        bootFieldsRevision++
                        openingValid = true
                        insideValid = true
                    }, modifier = Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.boot_example))
                    }
                } else key(carBoot) {
                    LengthFields(
                        labels = listOf(stringResource(R.string.door_width), stringResource(R.string.door_height)),
                        values = listOf(door.widthCm, door.heightCm),
                        unit = unit,
                        onChange = { i, cm -> onDoor(if (i == 0) door.copy(widthCm = cm) else door.copy(heightCm = cm)) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onValidChange = { doorValid = it },
                    )
                    Text(
                        stringResource(R.string.door_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                    if (arAvailable) TextButton(
                        onClick = onMeasure,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                    ) {
                        Icon(Icons.Outlined.Straighten, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.door_measure))
                    }
                }
            }
        }
    }
}

private fun BootLimit.label(): Int = when (this) {
    BootLimit.OPENING_WIDTH -> R.string.boot_limit_opening_width
    BootLimit.OPENING_HEIGHT -> R.string.boot_limit_opening_height
    BootLimit.LENGTH -> R.string.boot_limit_length
    BootLimit.WIDTH -> R.string.boot_limit_width
    BootLimit.HEIGHT -> R.string.boot_limit_height
}

private fun BootLimit.overLabel(): Int = when (this) {
    BootLimit.OPENING_WIDTH -> R.string.boot_over_opening_width
    BootLimit.OPENING_HEIGHT -> R.string.boot_over_opening_height
    BootLimit.LENGTH -> R.string.boot_over_length
    BootLimit.WIDTH -> R.string.boot_over_width
    BootLimit.HEIGHT -> R.string.boot_over_height
}
