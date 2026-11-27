package com.gorisse.thomas.willitfit.ui

import com.gorisse.thomas.willitfit.data.News
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.data.Prefs
import com.gorisse.thomas.willitfit.fit.TapeSet
import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.LiftLimit
import com.gorisse.thomas.willitfit.fit.checkLift
import com.gorisse.thomas.willitfit.scene.LiftScene
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.horizontalScroll
import com.gorisse.thomas.willitfit.fit.Carry
import com.gorisse.thomas.willitfit.fit.BootLimit
import com.gorisse.thomas.willitfit.fit.DoorVerdict
import com.gorisse.thomas.willitfit.fit.checkBoot
import com.gorisse.thomas.willitfit.fit.HallLimit
import com.gorisse.thomas.willitfit.fit.checkHall
import com.gorisse.thomas.willitfit.scene.HallLabels
import com.gorisse.thomas.willitfit.scene.HallScene
import com.gorisse.thomas.willitfit.scene.hallPicture
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.rememberTextMeasurer
import com.gorisse.thomas.willitfit.scene.BootScene
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.capture.PhotoFailure
import com.gorisse.thomas.willitfit.capture.PhotoResult
import android.graphics.Rect
import com.gorisse.thomas.willitfit.capture.CARD_SCENE_HEIGHT
import com.gorisse.thomas.willitfit.capture.CARD_WIDTH
import com.gorisse.thomas.willitfit.capture.CardVerdict
import com.gorisse.thomas.willitfit.capture.captureScene
import com.gorisse.thomas.willitfit.capture.savePhoto
import com.gorisse.thomas.willitfit.capture.sharePhoto
import com.gorisse.thomas.willitfit.capture.takePhoto
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.SharedCheck
import com.gorisse.thomas.willitfit.fit.Space
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Shape
import com.gorisse.thomas.willitfit.fit.checkDoor
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.formatMargin
import com.gorisse.thomas.willitfit.fit.formatNumber
import com.gorisse.thomas.willitfit.fit.shape
import com.gorisse.thomas.willitfit.scene.DoorScene
import com.gorisse.thomas.willitfit.data.PremiumFeature
import com.gorisse.thomas.willitfit.data.PremiumState
import com.gorisse.thomas.willitfit.data.canUse
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
    onMeasure: (TapeSet) -> Unit = {},
    premiumState: PremiumState = PremiumState.FREE,
    openedReadOnly: Boolean = false,
    onRequirePremium: (PremiumFeature) -> Unit = {},
    onSegmentChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    // Keep the persisted space indices: door 0, boot 1, hallway 2, lift 3.
    val savedSpace = { if (prefs.liftSelected) 3 else if (prefs.hallway) 2 else if (prefs.carBoot) 1 else 0 }
    var space by remember { mutableIntStateOf(if (!canUse(PremiumFeature.HALL, premiumState, openedReadOnly)) 0 else savedSpace()) }
    // Play confirms a subscription a moment after the screen opens: until the person picks a
    // segment, the space they were on comes back as soon as access is known.
    var spacePicked by remember { mutableStateOf(false) }
    LaunchedEffect(premiumState) {
        if (!spacePicked && space == 0 && canUse(PremiumFeature.HALL, premiumState, openedReadOnly)) space = savedSpace()
    }
    val readOnly = (openedReadOnly || space != 0) && !canUse(PremiumFeature.MY_HOME, premiumState)
    val elevator = space == 3
    var lift by remember { mutableStateOf(prefs.lift) }
    var liftTyped by remember { mutableStateOf(prefs.liftTyped) }
    val liftVerdict = remember(dims, lift) { checkLift(dims, lift) }
    val carBoot = space == 1
    val hallway = space == 2
    var hall by remember { mutableStateOf(prefs.hall) }
    var hallValid by remember(space) { mutableStateOf(true) }
    var boot by remember { mutableStateOf(prefs.boot) }
    var bootTyped by remember { mutableStateOf(prefs.bootTyped) }
    var bootFieldsRevision by remember { mutableIntStateOf(0) }
    var openingValid by remember(space) { mutableStateOf(true) }
    var insideValid by remember(space) { mutableStateOf(true) }
    val bootVerdict = checkBoot(dims, boot)
    val hallVerdict = checkHall(dims, hall)
    val verdict = when {
        elevator -> DoorVerdict(liftVerdict.fits, liftVerdict.carry, liftVerdict.marginCm)
        carBoot -> DoorVerdict(bootVerdict.fits, bootVerdict.carry, bootVerdict.marginCm)
        hallway -> DoorVerdict(hallVerdict.fits, hallVerdict.carry, hallVerdict.marginCm)
        else -> checkDoor(dims, door)
    }
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    var sheetPx by remember { mutableIntStateOf(0) }
    var doorValid by remember(space) { mutableStateOf(true) }
    val fieldsValid = when {
        elevator || carBoot -> openingValid && insideValid
        hallway -> hallValid
        else -> doorValid
    }
    // A verdict on screen for a second counts as seen: an answer, for Play's rating sheet.
    LaunchedEffect(fieldsValid, space) {
        if (fieldsValid) { delay(1_000); onVerdictShown() }
    }
    // News and tips: offered once, after a first "yes", where Android asks for notifications (13+).
    // Before 13 they are on from the start, and each note carries its Turn off.
    var newsOffer by remember { mutableStateOf(false) }
    LaunchedEffect(fieldsValid, space, verdict.fits) {
        if (fieldsValid && verdict.fits && Build.VERSION.SDK_INT >= 33 && !prefs.newsOffered && !prefs.newsOn) {
            delay(1_200)
            prefs.newsOffered = true
            newsOffer = true
        }
    }
    val askNews = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A refusal is final: the card never comes back, and Android is not asked again.
        if (granted) News.turnOn(context)
        newsOffer = false
    }
    var holding by remember { mutableStateOf(false) }
    // The last share whose picture did not come out, and why: a card with Try again.
    var photoFailure by remember { mutableStateOf<PhotoFailure?>(null) }
    var shot by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val bootBackground = (if (dark) RoomPalette.Night else RoomPalette.Day).wall.toArgb()

    val answer = stringResource(answerFor(space, verdict.fits, verdict.isTight))
    val marginCore = if (elevator) {
        if (verdict.fits) stringResource(R.string.hall_margin_spare, formatMargin(verdict.marginCm, unit), stringResource(liftVerdict.limit.label()))
        else stringResource(R.string.lift_margin_over, formatMargin(verdict.marginCm, unit), stringResource(liftVerdict.limit.label()))
    } else if (hallway) {
        if (verdict.fits) stringResource(
            R.string.hall_margin_spare, formatMargin(verdict.marginCm, unit), stringResource(hallVerdict.limit.label()),
        ) else stringResource(hallVerdict.limit.overLabel(), formatMargin(verdict.marginCm, unit))
    } else if (carBoot) {
        if (verdict.fits) stringResource(
            R.string.boot_margin_spare, formatMargin(verdict.marginCm, unit), stringResource(bootVerdict.limit.label()),
        ) else stringResource(bootVerdict.limit.overLabel(), formatMargin(verdict.marginCm, unit))
    } else stringResource(
        if (verdict.fits) R.string.door_margin_spare else R.string.door_margin_over,
        formatMargin(verdict.marginCm, unit),
    )
    val margin = marginCore + " · " + stringResource(
        // In a boot the object faces the opening, in a hallway the way you walk, not a door.
        when {
            verdict.carry != Carry.UPRIGHT || space == 0 || elevator -> verdict.carry.label()
            carBoot -> R.string.carry_upright_boot
            else -> R.string.carry_upright_hall
        },
    )
    val liftMargin = if (elevator && liftVerdict.diagonal) " · " + stringResource(R.string.lift_diagonally) else ""
    val objectLine = stringResource(R.string.door_object, objectName(dims), formatDimensions(dims, unit))
    val hallLabels = HallLabels(
        width = "${formatNumber(hall.widthCm, unit)} ${unit.symbol}",
        turnWidth = "${formatNumber(hall.turnWidthCm, unit)} ${unit.symbol}",
    )
    val doorLine = if (elevator) stringResource(
        R.string.photo_lift,
        "${formatNumber(lift.doorWidthCm, unit)} × ${formatNumber(lift.doorHeightCm, unit)}",
        "${formatNumber(lift.cabinWidthCm, unit)} × ${formatNumber(lift.cabinDepthCm, unit)} × ${formatNumber(lift.cabinHeightCm, unit)} ${unit.symbol}",
    ) else if (hallway) stringResource(
        R.string.photo_hall,
        "${formatNumber(hall.widthCm, unit)} → ${formatNumber(hall.turnWidthCm, unit)} ${unit.symbol}",
        "${formatNumber(hall.ceilingCm, unit)} ${unit.symbol}",
    ) else if (carBoot) stringResource(
        R.string.photo_boot,
        // One unit for the line, at its end: the photo's caption has room for one line only.
        "${formatNumber(boot.openingWidthCm, unit)} × ${formatNumber(boot.openingHeightCm, unit)}",
        "${formatNumber(boot.lengthCm, unit)} × ${formatNumber(boot.widthCm, unit)} × ${formatNumber(boot.heightCm, unit)} ${unit.symbol}",
    ) else stringResource(
        R.string.photo_door, "${formatNumber(door.widthCm, unit)} × ${formatNumber(door.heightCm, unit)} ${unit.symbol}",
    )
    val signature = stringResource(R.string.photo_signature)
    // The link opens the same check for whoever receives it, in the app or on its page.
    val checkLink = {
        when (space) {
            1 -> SharedCheck(dims, Space.BOOT, boot = boot, unit = unit)
            3 -> SharedCheck(dims, Space.LIFT, lift = lift, unit = unit)
            2 -> SharedCheck(dims, Space.HALL, hall = hall, unit = unit)
            else -> SharedCheck(dims, Space.DOOR, door = door, unit = unit)
        }.link()
    }
    val chooser = stringResource(R.string.share_chooser)

    Box(Modifier.fillMaxSize().imePadding()) {
        val scenePadding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp,
            bottom = (with(density) { sheetPx.toDp() } - 28.dp).coerceAtLeast(0.dp),
        )
        if (elevator) LiftScene(
            dims = dims, lift = lift, verdict = liftVerdict,
            accent = FurnitureTeal, palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            modifier = Modifier.fillMaxSize(), contentPadding = scenePadding,
        ) else if (hallway) HallScene(
            dims = dims, hall = hall, verdict = hallVerdict, labels = hallLabels,
            accent = FurnitureTeal, palette = if (dark) RoomPalette.Night else RoomPalette.Day,
            modifier = Modifier.fillMaxSize(), contentPadding = scenePadding,
        ) else if (carBoot) BootScene(
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
                        if (hallway) PhotoResult.Success(
                            // Drawn, not read back from the screen: the plan is not a 3D scene.
                            hallPicture(
                                dims, hall, hallVerdict, hallLabels, FurnitureTeal,
                                if (dark) RoomPalette.Night else RoomPalette.Day, measurer, density,
                                // Drawn to the card's picture frame, so nothing of the plan is cut.
                                width = CARD_WIDTH, height = CARD_SCENE_HEIGHT,
                            ).asAndroidBitmap(),
                        ) else {
                            // Hold the object on the answer and let the scene draw it before reading it back.
                            delay(250)
                            captureScene(activity)
                        }
                    },
                    save = { bitmap ->
                        val color = when {
                            !verdict.fits -> 0xFFFFB4AB // the dark scheme's error
                            verdict.isTight -> 0xFFF2C46D
                            else -> 0xFF8DD99A // FitColors.success, dark
                        }.toInt()
                        // The card: the answer, then the margin in large (with how it is carried
                        // through a door; the picture shows how it lies elsewhere), then the sizes.
                        savePhoto(
                            context, bitmap, answer, if (elevator) margin + liftMargin else if (space != 0) marginCore else margin, listOf(objectLine, doorLine),
                            signature, color,
                            verdict = when {
                                !verdict.fits -> CardVerdict.NO
                                verdict.isTight -> CardVerdict.TIGHT
                                else -> CardVerdict.FITS
                            },
                            // The boot floats on the screen's wall colour; the door scene is opaque.
                            background = if (carBoot) bootBackground else null,
                            // The scene shows above the sheet: the rest is behind it. The hallway
                            // plan is drawn for the card, whole.
                            crop = if (hallway) null else Rect(0, 0, bitmap.width, (bitmap.height - sheetPx + with(density) { 28.dp.roundToPx() }).coerceIn(bitmap.height / 3, bitmap.height)),
                        )
                    },
                )
                when (result) {
                    is PhotoResult.Success -> sharePhoto(
                        context, result.value, chooser, "$answer · $margin$liftMargin\n$objectLine · $doorLine\n${checkLink()}",
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
                // Allow large accessibility text to scroll; at 360 dp the compact labels fit together.
                val windowWidth = LocalWindowInfo.current.containerSize.width
                val compact = windowWidth <= with(density) { 400.dp.roundToPx() } || density.fontScale > 1f
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp).padding(bottom = 12.dp)) {
                    listOf(R.string.space_door, if (compact) R.string.space_boot_short else R.string.space_boot, if (compact) R.string.space_hall_short else R.string.space_hall, R.string.space_lift).forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = space == i,
                            onClick = {
                                if (i != 0 && !canUse(spaceFeature(i), premiumState)) {
                                    onRequirePremium(spaceFeature(i))
                                    return@SegmentedButton
                                }
                                onSegmentChanged()
                                space = i; spacePicked = true
                                prefs.liftSelected = i == 3
                                prefs.carBoot = i == 1
                                prefs.hallway = i == 2
                                photoFailure = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 4),
                            icon = { if (i != 0 && !canUse(spaceFeature(i), premiumState)) PremiumLock() },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            label = { Text(stringResource(label), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                        )
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
                        Text(
                            stringResource(when (space) { 3 -> R.string.lift_enter; 1 -> R.string.boot_enter; 2 -> R.string.hall_enter; else -> R.string.door_enter }),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    } else AnimatedContent(Triple(space, verdict.fits, verdict.isTight), label = "verdict") { (shown, fits, tight) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (fits) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                                contentDescription = null,
                                tint = if (fits) FitColors.success() else scheme.error,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(answerFor(shown, fits, tight)),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (fieldsValid) Text(
                        text = margin + liftMargin,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (space == 0 && fieldsValid && !verdict.fits && dims.shape == Shape.SOFA) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.door_sofa_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(newsOffer) {
                        NewsOfferCard(
                            onYes = { askNews.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            onNo = { newsOffer = false },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (elevator) key(space) {
                    val updateLift: (Lift) -> Unit = { lift = it; prefs.lift = it; liftTyped = true }
                    Text(stringResource(R.string.lift_opening), modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleSmall)
                    LengthFields(
                        readOnly = readOnly,
                        onReadOnlyClick = { onRequirePremium(if (space == 0) PremiumFeature.MY_HOME else spaceFeature(space)) },
                        labels = listOf(stringResource(R.string.door_width), stringResource(R.string.door_height)),
                        values = listOf(lift.doorWidthCm, lift.doorHeightCm), unit = unit,
                        onChange = { i, cm -> updateLift(if (i == 0) lift.copy(doorWidthCm = cm) else lift.copy(doorHeightCm = cm)) },
                        modifier = Modifier.padding(horizontal = 16.dp), onValidChange = { openingValid = it },
                    )
                    Text(stringResource(R.string.lift_inside), modifier = Modifier.padding(start = 24.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
                    LengthFields(
                        readOnly = readOnly,
                        onReadOnlyClick = { onRequirePremium(if (space == 0) PremiumFeature.MY_HOME else spaceFeature(space)) },
                        labels = listOf(stringResource(R.string.boot_width), stringResource(R.string.lift_depth), stringResource(R.string.boot_height)),
                        values = listOf(lift.cabinWidthCm, lift.cabinDepthCm, lift.cabinHeightCm), unit = unit,
                        onChange = { i, cm -> updateLift(when (i) {
                            0 -> lift.copy(cabinWidthCm = cm)
                            1 -> lift.copy(cabinDepthCm = cm)
                            else -> lift.copy(cabinHeightCm = cm)
                        }) },
                        modifier = Modifier.padding(horizontal = 16.dp), onValidChange = { insideValid = it },
                    )
                    Text(
                        stringResource(R.string.lift_help) + if (!liftTyped) " " + stringResource(R.string.lift_example_help) else "",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                } else if (hallway) key(space) {
                    LengthFields(
                        readOnly = readOnly,
                        onReadOnlyClick = { onRequirePremium(if (space == 0) PremiumFeature.MY_HOME else spaceFeature(space)) },
                        labels = listOf(
                            stringResource(R.string.hall_width), stringResource(R.string.hall_turn), stringResource(R.string.hall_ceiling),
                        ),
                        values = listOf(hall.widthCm, hall.turnWidthCm, hall.ceilingCm),
                        unit = unit,
                        onChange = { i, cm ->
                            hall = when (i) {
                                0 -> hall.copy(widthCm = cm)
                                1 -> hall.copy(turnWidthCm = cm)
                                else -> hall.copy(ceilingCm = cm)
                            }
                            prefs.hall = hall
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onValidChange = { hallValid = it },
                    )
                    Text(
                        stringResource(R.string.hall_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                } else if (carBoot) key(space, bootFieldsRevision) {
                    val updateBoot: (Boot) -> Unit = { boot = it; prefs.boot = it; bootTyped = true }
                    Text(stringResource(R.string.boot_opening), modifier = Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleSmall)
                    LengthFields(
                        readOnly = readOnly,
                        onReadOnlyClick = { onRequirePremium(if (space == 0) PremiumFeature.MY_HOME else spaceFeature(space)) },
                        labels = listOf(stringResource(R.string.boot_width), stringResource(R.string.boot_height)),
                        values = listOf(boot.openingWidthCm, boot.openingHeightCm), unit = unit,
                        onChange = { i, cm -> updateBoot(if (i == 0) boot.copy(openingWidthCm = cm) else boot.copy(openingHeightCm = cm)) },
                        modifier = Modifier.padding(horizontal = 16.dp), onValidChange = { openingValid = it },
                    )
                    Text(stringResource(R.string.boot_inside), modifier = Modifier.padding(start = 24.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
                    LengthFields(
                        readOnly = readOnly,
                        onReadOnlyClick = { onRequirePremium(if (space == 0) PremiumFeature.MY_HOME else spaceFeature(space)) },
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
                        if (readOnly) { onRequirePremium(PremiumFeature.BOOT); return@TextButton }
                        prefs.useExampleBoot()
                        boot = prefs.boot
                        bootTyped = false
                        bootFieldsRevision++
                        openingValid = true
                        insideValid = true
                    }, modifier = Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.boot_example))
                    }
                } else key(space) {
                    LengthFields(
                        readOnly = readOnly,
                        onReadOnlyClick = { onRequirePremium(if (space == 0) PremiumFeature.MY_HOME else spaceFeature(space)) },
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
                }
                if (arAvailable) TextButton(
                    onClick = {
                        onMeasure(when (space) {
                            1 -> TapeSet.BOOT
                            2 -> TapeSet.HALL
                            3 -> TapeSet.LIFT
                            else -> TapeSet.DOOR
                        })
                    },
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                ) {
                    if (!canUse(PremiumFeature.TAPE, premiumState)) PremiumLock()
                    Icon(Icons.Outlined.Straighten, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(when (space) {
                        1 -> R.string.boot_measure
                        2 -> R.string.hall_measure
                        3 -> R.string.lift_measure
                        else -> R.string.door_measure
                    }))
                }
            }
        }
    }
}

private fun answerFor(space: Int, fits: Boolean, tight: Boolean): Int = when (space) {
    1 -> if (!fits) R.string.boot_no else if (tight) R.string.boot_tight else R.string.boot_yes
    3 -> if (!fits) R.string.lift_no else if (tight) R.string.lift_tight else R.string.lift_yes
    2 -> if (!fits) R.string.hall_no else if (tight) R.string.hall_tight else R.string.hall_yes
    else -> if (!fits) R.string.door_no else if (tight) R.string.door_tight else R.string.door_yes
}

private fun LiftLimit.label(): Int = when (this) {
    LiftLimit.DOOR_WIDTH -> R.string.lift_limit_door_width
    LiftLimit.DOOR_HEIGHT -> R.string.lift_limit_door_height
    LiftLimit.CABIN_FLOOR -> R.string.lift_limit_floor
    LiftLimit.CABIN_HEIGHT -> R.string.lift_limit_height
}

private fun HallLimit.label(): Int = when (this) {
    HallLimit.WIDTH -> R.string.hall_limit_width
    HallLimit.TURN -> R.string.hall_limit_turn
    HallLimit.CEILING -> R.string.hall_limit_ceiling
}

private fun HallLimit.overLabel(): Int = when (this) {
    HallLimit.WIDTH -> R.string.hall_over_width
    HallLimit.TURN -> R.string.hall_over_turn
    HallLimit.CEILING -> R.string.hall_over_ceiling
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

/** "Get a note when new checks arrive?": shown once, under a first "yes". */
@Composable
private fun NewsOfferCard(onYes: () -> Unit, onNo: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.news_offer), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                stringResource(R.string.news_offer_body),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onNo) { Text(stringResource(R.string.news_no)) }
                TextButton(onClick = onYes) { Text(stringResource(R.string.news_yes)) }
            }
        }
    }
}

/** Keep the old space indices in preferences while access is named by feature. */
private fun spaceFeature(index: Int): PremiumFeature = when (index) {
    1 -> PremiumFeature.BOOT
    2 -> PremiumFeature.HALL
    3 -> PremiumFeature.LIFT
    else -> PremiumFeature.DOOR
}
