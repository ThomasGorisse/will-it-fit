package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.BuildConfig
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.fit.LengthUnit

/** Screen 5, "Settings": units, privacy, the ARCore notice, licences. */
@Composable
fun SettingsScreen(unit: LengthUnit, onUnit: (LengthUnit) -> Unit, onBack: () -> Unit) {
    AppScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding(),
        ) {
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Straighten, null) },
                headlineContent = { Text(stringResource(R.string.settings_units)) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        LengthUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = u == unit,
                                onClick = { onUnit(u) },
                                shape = SegmentedButtonDefaults.itemShape(i, LengthUnit.entries.size),
                            ) {
                                Text(stringResource(if (u == LengthUnit.CM) R.string.unit_cm else R.string.unit_in))
                            }
                        }
                    }
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Lock, null) },
                headlineContent = { Text(stringResource(R.string.settings_privacy_title)) },
                supportingContent = { Text(stringResource(R.string.settings_privacy)) },
            )
            // Required by Google next to the privacy line: the AR camera runs on ARCore.
            ListItem(
                leadingContent = { Icon(Icons.Outlined.ViewInAr, null) },
                headlineContent = { Text(stringResource(R.string.settings_arcore_title)) },
                supportingContent = { Text(arCoreNoticeText(MaterialTheme.colorScheme.primary)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Code, null) },
                headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                supportingContent = { Text(stringResource(R.string.settings_licenses_body)) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                },
            )
        }
    }
}

/** [ArCoreNotice.TEXT] with its links, underlined in the theme's primary colour. Tapping opens the browser. */
private fun arCoreNoticeText(linkColor: Color): AnnotatedString = buildAnnotatedString {
    append(ArCoreNotice.TEXT)
    val styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    ArCoreNotice.links.forEach { addLink(LinkAnnotation.Url(it.url, styles), it.start, it.end) }
}
