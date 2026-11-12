package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.formatNumber
import com.gorisse.thomas.willitfit.fit.parseLength

/** One length field per side. [values] in centimetres; [onChange] gets a parsed value or nothing. */
@Composable
fun LengthFields(
    labels: List<String>,
    values: List<Float>,
    unit: LengthUnit,
    onChange: (index: Int, cm: Float) -> Unit,
    modifier: Modifier = Modifier,
    onValidChange: (Boolean) -> Unit = {},
) {
    val valid = remember { mutableStateMapOf<Int, Boolean>() }
    val allValid = labels.indices.all { valid[it] != false }
    LaunchedEffect(allValid) { onValidChange(allValid) }
    val focus = LocalFocusManager.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val external = values[i]
            var text by remember { mutableStateOf(formatNumber(external, unit)) }
            // A preset or a unit change rewrites the field, unless it already says the same.
            LaunchedEffect(external, unit) {
                if (parseLength(text, unit)?.let { formatNumber(it, unit) } != formatNumber(external, unit)) {
                    text = formatNumber(external, unit)
                }
            }
            val error = parseLength(text, unit) == null
            LaunchedEffect(error) { valid[i] = !error }
            OutlinedTextField(
                value = text,
                onValueChange = { t ->
                    text = t
                    parseLength(t, unit)?.let { onChange(i, it) }
                },
                label = { Text(label, maxLines = 1) },
                suffix = { Text(unit.symbol) },
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = if (i == labels.lastIndex) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
