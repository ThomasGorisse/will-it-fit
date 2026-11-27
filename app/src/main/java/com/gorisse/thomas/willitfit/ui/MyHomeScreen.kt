package com.gorisse.thomas.willitfit.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Elevator
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.data.MyThings
import com.gorisse.thomas.willitfit.data.Thing
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Home
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.Pass
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.formatLength
import com.gorisse.thomas.willitfit.fit.formatMargin
import com.gorisse.thomas.willitfit.fit.formatNumber
import com.gorisse.thomas.willitfit.fit.movingDay
import com.gorisse.thomas.willitfit.fit.orderMovingDay

/** Which of the home's spaces the person measured; the others are the typical ones. */
data class HomeTyped(val door: Boolean, val hall: Boolean, val boot: Boolean, val lift: Boolean = false)

/**
 * "My home": the front door, the hallway and the car, typed once in the checks, and the person's
 * own things by name. Moving day answers every thing for every space at once.
 */
@Composable
fun MyHomeScreen(
    home: Home,
    typed: HomeTyped,
    things: List<Thing>,
    current: Dimensions,
    unit: LengthUnit,
    onAdd: (Thing) -> Unit,
    onRemove: (Thing) -> Unit,
    onRename: (Thing, String) -> Unit,
    onPick: (Dimensions) -> Unit,
    onSpace: (Int) -> Unit,
    onBack: () -> Unit,
    premium: Boolean = false,
    onRequirePremium: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val rows = orderMovingDay(things.map { it to movingDay(it.dims, home) }, bootTyped = typed.boot)
    val shareLabel = stringResource(R.string.home_my_share)
    val checklist = movingDayChecklist(
        rows, unit,
        stringResource(R.string.home_my_share_door),
        stringResource(R.string.home_my_share_turn),
        stringResource(R.string.home_my_share_car),
        bootTyped = typed.boot, lift = stringResource(R.string.space_lift),
    )
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = things.firstOrNull { it.name == editingName }
    if (editing != null) {
        key(editing.name) {
            EditThingDialog(
                editing, things,
                onDismiss = { editingName = null },
                onRename = { onRename(editing, it); editingName = null },
                onRemove = { onRemove(editing); editingName = null },
            )
        }
    }
    AppScaffold(
        title = stringResource(R.string.home_my_title), onBack = onBack,
        actions = {
            if (things.isNotEmpty()) IconButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, checklist)
                }
                context.startActivity(Intent.createChooser(intent, shareLabel))
            }) {
                Icon(Icons.Outlined.Share, shareLabel)
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.formWidth().fillMaxSize().verticalScroll(rememberScrollState())) {
                SectionTitle(stringResource(R.string.home_my_spaces))
                SpaceItem(
                    Icons.Outlined.DoorFront, stringResource(R.string.space_door),
                    "${formatNumber(home.door.widthCm, unit)} × ${formatLength(home.door.heightCm, unit)}",
                    typed.door, onClick = { onSpace(0) },
                )
                SpaceItem(
                    Icons.Outlined.TurnRight, stringResource(R.string.space_hall),
                    stringResource(
                        R.string.home_my_hall_sizes,
                        formatLength(home.hall.widthCm, unit), formatLength(home.hall.turnWidthCm, unit),
                    ),
                    typed.hall, onClick = { onSpace(2) },
                )
                SpaceItem(
                    Icons.Outlined.DirectionsCar, stringResource(R.string.space_boot),
                    stringResource(
                        R.string.home_my_boot_sizes,
                        "${formatNumber(home.boot.openingWidthCm, unit)} × ${formatLength(home.boot.openingHeightCm, unit)}",
                    ),
                    typed.boot, onClick = { onSpace(1) },
                )

                SpaceItem(
                    Icons.Outlined.Elevator, stringResource(R.string.space_lift),
                    (home.lift ?: Lift.typicalFor(unit)).let {
                        "${formatNumber(it.cabinWidthCm, unit)} × ${formatNumber(it.cabinDepthCm, unit)} × ${formatLength(it.cabinHeightCm, unit)}"
                    },
                    typed.lift, onClick = { onSpace(3) },
                )
                Spacer(Modifier.height(8.dp))
                SectionTitle(stringResource(R.string.home_my_moving_day))
                if (things.isNotEmpty()) {
                    val inCount = rows.count { it.second.getsIn }
                    Text(
                        stringResource(R.string.home_my_summary, inCount, things.size),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.home_my_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rows.forEach { (thing, row) ->
                        ThingCard(thing, unit, listOfNotNull(
                            Triple(Icons.Outlined.DoorFront, stringResource(R.string.space_door), row.door),
                            Triple(Icons.Outlined.TurnRight, stringResource(R.string.space_hall), row.hall),
                            row.lift?.let { Triple(Icons.Outlined.Elevator, stringResource(R.string.space_lift), it) },
                            if (typed.boot) Triple(Icons.Outlined.DirectionsCar, stringResource(R.string.space_boot), row.boot) else null,
                        ), onClick = { onPick(thing.dims) }, onRemove = { onRemove(thing) },
                            onLongClick = { editingName = thing.name }, editable = premium)
                    }
                }
                AddThing(current, unit, things, onAdd = onAdd, premium = premium, onRequirePremium = onRequirePremium)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SpaceItem(icon: ImageVector, name: String, sizes: String, typed: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp),
        headlineContent = { Text(name) },
        supportingContent = {
            Text(if (typed) sizes else stringResource(R.string.home_my_not_measured, sizes))
        },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Text(
                stringResource(if (typed) R.string.home_my_edit else R.string.home_my_measure),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun ThingCard(
    thing: Thing,
    unit: LengthUnit,
    passes: List<Triple<ImageVector, String, Pass>>,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    editable: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick.takeIf { editable },
            onLongClickLabel = if (editable) stringResource(R.string.home_my_manage) else null,
        ),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            val preset = Preset.byId(thing.dims.presetId)
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (preset != null) Image(
                    painterResource(preset.thumbnail()), null,
                    contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp),
                ) else Icon(Icons.Outlined.Inventory2, null, tint = scheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    thing.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatDimensions(thing.dims, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    passes.forEach { (icon, name, pass) -> PassChip(icon, name, pass, unit) }
                }
            }
            if (editable) IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, stringResource(R.string.home_my_remove, thing.name), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PassChip(icon: ImageVector, name: String, pass: Pass, unit: LengthUnit) {
    val scheme = MaterialTheme.colorScheme
    val margin = formatMargin(pass.marginCm, unit)
    val said = stringResource(
        if (!pass.fits) R.string.home_my_pass_no else if (pass.isTight) R.string.home_my_pass_tight else R.string.home_my_pass_yes,
        name, margin,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { contentDescription = said }) {
        Icon(icon, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(
            if (pass.fits) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel, null, Modifier.size(18.dp),
            tint = if (!pass.fits) scheme.error else if (pass.isTight) scheme.tertiary else FitColors.success(),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            (if (pass.fits) "+" else "−") + margin,
            style = MaterialTheme.typography.labelLarge,
            color = if (pass.fits) scheme.onSurface else scheme.error,
        )
    }
}

@Composable
private fun AddThing(current: Dimensions, unit: LengthUnit, things: List<Thing>, onAdd: (Thing) -> Unit, premium: Boolean, onRequirePremium: () -> Unit) {
    val suggested = objectName(current)
    var name by rememberSaveable(current.widthCm, current.depthCm, current.heightCm) { mutableStateOf("") }
    val full = things.size >= MyThings.MAX
    val cleaned = MyThings.cleanName(name).ifEmpty { suggested }
    val unique = MyThings.canName(things, cleaned)
    val canAdd = !full && current.isValid && unique
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // Revisit the whole row as the keyboard opens, so the name and Keep button stay together.
    LaunchedEffect(imeBottom, focused, things.size, unique) {
        if (focused) bringIntoView.bringIntoView()
    }
    val add = {
        if (!premium) onRequirePremium() else if (canAdd) { onAdd(Thing(cleaned, current)); name = "" }
    }
    Text(
        stringResource(R.string.home_my_add_title, formatDimensions(current, unit)),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
    )
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().bringIntoViewRequester(bringIntoView),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MyThings.MAX_NAME) },
            placeholder = { Text(suggested, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            label = { Text(stringResource(R.string.home_my_name)) },
            singleLine = true,
            enabled = !full,
            isError = !unique,
            supportingText = if (!unique) ({ Text(stringResource(R.string.home_my_name_taken)) }) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = add, enabled = canAdd) {
            if (!premium) PremiumLock()
            Text(stringResource(R.string.home_my_add))
        }
    }
}

@Composable
private fun EditThingDialog(
    thing: Thing,
    things: List<Thing>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(thing.name) }
    val cleaned = MyThings.cleanName(name)
    val valid = MyThings.canName(things, cleaned, exceptName = thing.name)
    val duplicate = cleaned.isNotEmpty() && !valid
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_my_manage)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MyThings.MAX_NAME) },
                label = { Text(stringResource(R.string.home_my_name)) },
                singleLine = true,
                isError = duplicate,
                supportingText = if (duplicate) ({ Text(stringResource(R.string.home_my_name_taken)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onRename(cleaned) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(cleaned) }, enabled = valid) {
                Text(stringResource(R.string.home_my_rename))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.home_my_delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_my_cancel)) }
            }
        },
    )
}
