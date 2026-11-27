package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.data.PremiumFeature
import com.gorisse.thomas.willitfit.data.PremiumPlan
import com.gorisse.thomas.willitfit.data.localizedDate
import com.gorisse.thomas.willitfit.data.yearlyPricePerWeek
import com.gorisse.thomas.willitfit.fit.Preset
import java.text.NumberFormat
import java.time.ZonedDateTime
import java.util.Currency

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

/** A soft offer: close stays visible even when Play is missing or the content needs to scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(feature: PremiumFeature, plans: List<PremiumPlan>, available: Boolean,
    onClose: () -> Unit, onBuy: (PremiumPlan) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("yearly") }
    val plan = plans.firstOrNull { it.id == selected } ?: plans.firstOrNull()
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.premium_title)) }, navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.premium_close)) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).formWidth().fillMaxSize().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(feature.headline()), style = MaterialTheme.typography.headlineMedium)
            Benefit(Icons.Outlined.TurnRight, R.string.premium_benefit_spaces)
            Benefit(Icons.Outlined.Straighten, R.string.premium_benefit_tape)
            Benefit(Icons.Outlined.Home, R.string.premium_benefit_home)
            if (!available || plan == null) {
                Text(stringResource(R.string.premium_unavailable), style = MaterialTheme.typography.bodyLarge)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { item ->
                        val chosen = item == plan
                        Card(colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.fillMaxWidth().selectable(selected = chosen, role = Role.RadioButton,
                                onClick = { selected = item.id }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosen, onClick = null)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(stringResource(item.periodLabel()), style = MaterialTheme.typography.titleMedium)
                                    Text(item.price.formattedPrice, style = MaterialTheme.typography.bodyLarge)
                                    if (item.id == "yearly") {
                                        val currency = Currency.getInstance(item.price.priceCurrencyCode)
                                        val formatter = NumberFormat.getCurrencyInstance().apply { this.currency = currency
                                            maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
                                            minimumFractionDigits = maximumFractionDigits }
                                        Text(stringResource(R.string.premium_per_week,
                                            formatter.format(yearlyPricePerWeek(item.price.priceAmountMicros))),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (item.trial) {
                                        Text(stringResource(R.string.premium_trial_badge), color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = { onBuy(plan) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (plan.trial) stringResource(R.string.premium_start_trial, plan.price.formattedPrice)
                        else stringResource(R.string.premium_continue_price, plan.price.formattedPrice, stringResource(plan.periodSuffix())))
                }
                if (plan.trial) Text(stringResource(R.string.premium_first_charge,
                    localizedDate(ZonedDateTime.now().plusDays(3).toInstant().toEpochMilli())), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.premium_returns), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One short reason to subscribe, with a decorative icon. */
@Composable
private fun Benefit(icon: ImageVector, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Plan names and billing units have different forms in several languages. */
private fun PremiumPlan.periodLabel(): Int = when (id) {
    "yearly" -> R.string.premium_yearly
    "monthly" -> R.string.premium_monthly
    else -> R.string.premium_weekly
}

/** The suffix belongs to the recurring price, never the trial phase. */
private fun PremiumPlan.periodSuffix(): Int = when (id) {
    "yearly" -> R.string.premium_year
    "monthly" -> R.string.premium_month
    else -> R.string.premium_week
}

/** Two short choices use the existing presets and never touch camera or account permissions. */
@Composable
fun OnboardingScreen(step: Int, selectedPreset: String?, spaces: Set<String>, onPreset: (Preset?) -> Unit,
    onSpaces: (Set<String>) -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    AppScaffold(title = "", onBack = onSkip, actions = {
        TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
    }) { padding ->
        Column(Modifier.padding(padding).formWidth().fillMaxSize().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(if (step == 1) R.string.onboarding_object else R.string.onboarding_spaces),
                style = MaterialTheme.typography.headlineMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == 1) {
                    Preset.entries.forEach { preset ->
                        FilterChip(selected = selectedPreset == preset.id, onClick = { onPreset(preset) },
                            label = { Text(stringResource(preset.label())) })
                    }
                    FilterChip(selected = selectedPreset == null, onClick = { onPreset(null) },
                        label = { Text(stringResource(R.string.onboarding_other)) })
                } else {
                    listOf("DOOR" to R.string.space_door, "HALL" to R.string.onboarding_hall,
                        "BOOT" to R.string.space_boot, "LIFT" to R.string.space_lift).forEach { (key, label) ->
                        FilterChip(selected = key in spaces, onClick = {
                            onSpaces(if (key in spaces) spaces - key else spaces + key)
                        }, label = { Text(stringResource(label)) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_next)) }
        }
    }
}
