package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Keep forms readable in wide windows without changing any compact-window constraints. */
@Composable
internal fun Modifier.formWidth(): Modifier =
    if (LocalConfiguration.current.screenWidthDp >= 600) widthIn(max = 640.dp) else this
