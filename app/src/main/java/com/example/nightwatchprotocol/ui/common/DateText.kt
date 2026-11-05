package com.example.nightwatchprotocol.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import com.example.nightwatchprotocol.data.language.AppLanguage
import com.example.nightwatchprotocol.data.profile.BirthDate

/**
 * The birth date in the language the screen is in. The Activity's configuration already follows the language chosen in the app
 * (AppCompat), so it is read here and the format changes with the screen, also for a ViewModel that outlives the language change.
 */
@Composable
fun BirthDate.displayText(): String = display(AppLanguage.fromTag(LocalConfiguration.current.locales[0].language))
