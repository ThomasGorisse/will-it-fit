package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Elevator
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.data.PremiumFeature
import com.gorisse.thomas.willitfit.data.PremiumPlanUi
import com.gorisse.thomas.willitfit.data.localizedDate
import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Hall
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.checkBoot
import com.gorisse.thomas.willitfit.fit.checkHall
import com.gorisse.thomas.willitfit.fit.checkLift
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.scene.BootScene
import com.gorisse.thomas.willitfit.scene.FurnitureTeal
import com.gorisse.thomas.willitfit.scene.HallLabels
import com.gorisse.thomas.willitfit.scene.HallScene
import com.gorisse.thomas.willitfit.scene.LiftScene
import com.gorisse.thomas.willitfit.scene.RoomPalette
import com.gorisse.thomas.willitfit.scene.RoomScene
import java.time.ZonedDateTime

/** The same small lock is spoken by accessibility services at every premium entry point. */
@Composable
fun PremiumLock() {
    Icon(Icons.Outlined.Lock, stringResource(R.string.premium_locked), Modifier.padding(start = 4.dp).size(14.dp))
}

/** The feature the person asked for is the title, rather than a generic upgrade message. */
fun PremiumFeature.headline(): Int = when (this) {
    PremiumFeature.HALL -> R.string.premium_headline_hall
    PremiumFeature.BOOT -> R.string.premium_headline_boot
    PremiumFeature.LIFT -> R.string.premium_headline_lift
    PremiumFeature.TAPE -> R.string.premium_headline_tape
    PremiumFeature.MY_HOME -> R.string.premium_headline_home
    else -> R.string.premium_headline
}

/**
 * The scene introduces the requested feature; the sheet uses the same paper and type as Home.
 * Close and purchase stay outside the scroll so long translations never hide either action.
 */
@Composable
fun PaywallScreen(feature: PremiumFeature, plans: List<PremiumPlanUi>, available: Boolean,
    dims: Dimensions, unit: LengthUnit, hall: Hall? = null, boot: Boot? = null, lift: Lift? = null,
    onClose: () -> Unit, onBuy: (PremiumPlanUi) -> Unit) {
    // The weekly plan leads (the smallest commitment to say yes to); the yearly one stays right
    // under it as the anchor, with its per-week price and the trial when Play offers it.
    var selected by rememberSaveable { mutableStateOf("weekly") }
    val plan = plans.firstOrNull { it.id == selected } ?: plans.firstOrNull()
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    // Dialogs own a separate Window: activity edge-to-edge flags do not colour its bar icons.
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val heroHeight = with(LocalDensity.current) { (windowHeight * 0.4f).toDp() }
    Scaffold(containerColor = scheme.surface, bottomBar = {
        if (available && plan != null) Surface(color = scheme.surface) {
            Column(Modifier.formWidth().fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onBuy(plan) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (plan.trial) stringResource(R.string.premium_start_trial, plan.formattedPrice)
                        else stringResource(R.string.premium_continue_price, plan.formattedPrice, stringResource(plan.periodSuffix())))
                }
                if (plan.trial) Text(stringResource(R.string.premium_first_charge,
                    localizedDate(ZonedDateTime.now().plusDays(3).toInstant().toEpochMilli())),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val sheetMinHeight = (maxHeight - heroHeight + 28.dp).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy((-28).dp)) {
                PaywallHero(feature, dims, unit, hall, boot, lift,
                    Modifier.fillMaxWidth().height(heroHeight).clipToBounds())
                // The sheet overlaps the scene, so its rounded corners read like Home's sheet.
                Surface(modifier = Modifier.fillMaxWidth().heightIn(min = sheetMinHeight), color = scheme.surface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.formWidth().fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(feature.headline()), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Benefit(Icons.Outlined.TurnRight, R.string.premium_benefit_spaces)
                            Benefit(Icons.Outlined.Straighten, R.string.premium_benefit_tape)
                            Benefit(Icons.Outlined.Home, R.string.premium_benefit_home)
                        }
                        if (!available || plan == null) {
                            Text(stringResource(R.string.premium_unavailable), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                plans.sortedBy { when (it.billingPeriod) { "P1W" -> 0; "P1Y" -> 1; else -> 2 } }.forEach { item ->
                                    PlanCard(item, item.id == plan.id) { selected = item.id }
                                }
                            }
                            Text(stringResource(R.string.premium_returns), style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
            FilledTonalIconButton(onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.premium_close))
            }
        }
    }
}

/** Reuse each check's inputs, verdict and theme; absent or invalid saved spaces use the room. */
@Composable
private fun PaywallHero(feature: PremiumFeature, dims: Dimensions, unit: LengthUnit,
    hall: Hall?, boot: Boot?, lift: Lift?, modifier: Modifier) {
    val palette = if (isSystemInDarkTheme()) RoomPalette.Night else RoomPalette.Day
    Box(modifier.clearAndSetSemantics { }) {
        val sceneModifier = Modifier.fillMaxSize()
        val scenePadding = PaddingValues(top = 48.dp, bottom = 12.dp)
        when {
            feature == PremiumFeature.HALL && hall?.isValid == true -> HallScene(
                dims = dims, hall = hall, verdict = checkHall(dims, hall),
                labels = HallLabels(formatLength(hall.widthCm, unit), formatLength(hall.turnWidthCm, unit)),
                accent = FurnitureTeal, palette = palette, modifier = sceneModifier, contentPadding = scenePadding,
            )
            feature == PremiumFeature.BOOT && boot?.isValid == true -> BootScene(
                dims = dims, boot = boot, verdict = checkBoot(dims, boot),
                accent = FurnitureTeal, palette = palette, modifier = sceneModifier, contentPadding = scenePadding,
            )
            feature == PremiumFeature.LIFT && lift?.isValid == true -> LiftScene(
                dims = dims, lift = lift, verdict = checkLift(dims, lift),
                accent = FurnitureTeal, palette = palette, modifier = sceneModifier, contentPadding = scenePadding,
            )
            else -> RoomScene(dims = dims, unit = unit, accent = FurnitureTeal, palette = palette,
                labelText = Color(0xFF1C1B1F), labelBackground = Color(0xF2FFFFFF),
                modifier = sceneModifier, contentPadding = scenePadding)
        }
        // Consume above the scene rather than changing the check screens' camera behaviour.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        })
    }
}

/** Selection is the whole card; price and trial remain legible without relying on colour. */
@Composable
private fun PlanCard(plan: PremiumPlanUi, chosen: Boolean, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) scheme.primary else scheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (chosen) scheme.secondaryContainer else scheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().selectable(selected = chosen, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = chosen, onClick = null)
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(plan.periodLabel()), style = MaterialTheme.typography.titleMedium)
                plan.perWeekPrice?.let {
                    Text(stringResource(R.string.premium_per_week, it), style = MaterialTheme.typography.bodySmall)
                }
                if (plan.trial) Surface(color = scheme.primary, contentColor = scheme.onPrimary,
                    shape = RoundedCornerShape(6.dp)) {
                    Text(stringResource(R.string.premium_trial_badge), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(plan.formattedPrice, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One short reason to subscribe, with the same icons as the app's feature entries. */
@Composable
private fun Benefit(icon: ImageVector, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Plan names and billing units have different forms in several languages. */
private fun PremiumPlanUi.periodLabel(): Int = when (billingPeriod) {
    "P1Y" -> R.string.premium_yearly
    "P1M" -> R.string.premium_monthly
    else -> R.string.premium_weekly
}

/** The suffix belongs to the recurring price, never the trial phase. */
private fun PremiumPlanUi.periodSuffix(): Int = when (billingPeriod) {
    "P1Y" -> R.string.premium_year
    "P1M" -> R.string.premium_month
    else -> R.string.premium_week
}

/** Home's thumbnail cards introduce the object; space cards use My home's familiar icons. */
@Composable
fun OnboardingScreen(step: Int, selectedPreset: String?, spaces: Set<String>, onPreset: (Preset?) -> Unit,
    onSpaces: (Set<String>) -> Unit, onNext: () -> Unit, onSkip: () -> Unit, onBack: () -> Unit) {
    AppScaffold(title = "", onBack = onBack, actions = {
        TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
    }) { padding ->
        Column(Modifier.padding(padding).formWidth().fillMaxSize().navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LinearProgressIndicator(progress = { step / 2f }, modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                Text(stringResource(if (step == 1) R.string.onboarding_object else R.string.onboarding_spaces),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (step == 1) {
                    (Preset.entries.toList() + null).chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                PresetCard(compact = true, selected = selectedPreset == preset?.id,
                                    label = stringResource(preset?.label() ?: R.string.onboarding_other),
                                    onClick = { onPreset(preset) }, modifier = Modifier.weight(1f), wrapLabel = true) {
                                    if (preset != null) Image(painterResource(preset.thumbnail()), null,
                                        contentScale = ContentScale.Fit, modifier = Modifier.size(64.dp))
                                    else Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Edit, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    listOf(
                        Triple("DOOR", R.string.space_door, Icons.Outlined.DoorFront),
                        Triple("HALL", R.string.onboarding_hall, Icons.Outlined.TurnRight),
                        Triple("BOOT", R.string.space_boot, Icons.Outlined.DirectionsCar),
                        Triple("LIFT", R.string.space_lift, Icons.Outlined.Elevator),
                    ).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (key, label, icon) ->
                                SpaceCard(stringResource(label), icon, key in spaces, Modifier.weight(1f)) {
                                    onSpaces(if (key in spaces) spaces - key else spaces + key)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                .heightIn(min = 52.dp)) { Text(stringResource(R.string.onboarding_next)) }
        }
    }
}

/** A large target and explicit check mark make these independent choices easy to recognise. */
@Composable
private fun SpaceCard(label: String, icon: ImageVector, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = modifier.semantics { selected = chosen; role = Role.Checkbox },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (chosen) scheme.secondaryContainer else scheme.surfaceContainerHigh),
        border = if (chosen) BorderStroke(2.dp, scheme.primary) else null) {
        Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, null, Modifier.size(32.dp), tint = scheme.primary)
                if (chosen) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = scheme.primary)
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
