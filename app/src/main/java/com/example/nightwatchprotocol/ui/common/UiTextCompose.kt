package com.example.nightwatchprotocol.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.common.asString

/** The text of this [UiText] in the language of the screen right now (it follows a change of configuration). */
@Composable
fun UiText.asString(): String {
    // Reading the configuration makes the text resolve again when the language changes.
    LocalConfiguration.current
    return asString(LocalContext.current)
}
