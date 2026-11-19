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
import androidx.compose.ui.platform.LocalUriHandler
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
                supportingContent = {
                    val uriHandler = LocalUriHandler.current
                    Column {
                        Text(stringResource(R.string.settings_privacy))
                        Text(
                            linkText(stringResource(R.string.settings_privacy_policy), PRIVACY_POLICY_URL, MaterialTheme.colorScheme.primary) { url ->
                                runCatching { uriHandler.openUri(url) }
                            },
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                },
            )
            // Required by Google next to the privacy line: the AR camera runs on ARCore.
            ListItem(
                leadingContent = { Icon(Icons.Outlined.ViewInAr, null) },
                headlineContent = { Text(stringResource(R.string.settings_arcore_title)) },
                supportingContent = {
                    val uriHandler = LocalUriHandler.current
                    // A phone without a browser: the tap does nothing rather than crash the app.
                    Text(arCoreNoticeText(MaterialTheme.colorScheme.primary) { url -> runCatching { uriHandler.openUri(url) } })
                },
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

/** The app's privacy policy, the one the Play listing points to. */
const val PRIVACY_POLICY_URL = "https://thomasgorisse.github.io/willitfit/privacy/"

private fun linkStyles(color: Color) = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))

/** [text] as one link to [url]; a tap calls [open]. */
private fun linkText(text: String, url: String, linkColor: Color, open: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(text)
        addLink(LinkAnnotation.Url(url, linkStyles(linkColor)) { open(url) }, 0, text.length)
    }

/** [ArCoreNotice.TEXT] with its links, underlined in the theme's primary colour; a tap calls [open]. */
private fun arCoreNoticeText(linkColor: Color, open: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    append(ArCoreNotice.TEXT)
    val styles = linkStyles(linkColor)
    ArCoreNotice.links.forEach { link ->
        addLink(LinkAnnotation.Url(link.url, styles) { open(link.url) }, link.start, link.end)
    }
}
