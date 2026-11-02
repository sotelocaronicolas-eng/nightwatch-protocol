package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.ui.theme.LocalChosenThemeMode
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import com.example.nightwatchprotocol.ui.verifycode.CodePurpose
import kotlin.reflect.KClass

// TRANSITION (Module 5, Stage 4 -> 5): only Perfil y Ajustes (and the verification screen when it serves them) are migrated to
// the light theme. They follow the look the person chose in "Apariencia"; every other screen stays dark until Stage 5 moves
// it. When Stage 5 ends, delete this file, use `composable` again and give the root theme in MainActivity the chosen mode.

/** The routes of Perfil y Ajustes: they show the chosen look. */
private val ThemedRoutes: List<KClass<*>> = listOf(
    UserMenu::class,
    Profile::class,
    ProfileUpdated::class,
    EditProfile::class,
    ConfirmIdentity::class,
    PrivacySecurity::class,
    ChangePassword::class,
    PasswordUpdated::class,
    ChangeEmail::class,
    EmailUpdated::class,
    NotificationSettings::class,
    Appearance::class,
    Language::class,
    Achievements::class,
    Devices::class,
    DeviceSignedOut::class,
    Help::class,
    About::class,
    RecoveryEmail::class,
    RecoveryEmailSaved::class,
)

/**
 * True when the screen of [this] entry follows the chosen look. The verification screen follows it only for the profile
 * purposes (email change, recovery email); the sign-up one belongs to the login flow, which is still dark.
 */
fun NavBackStackEntry?.followsChosenTheme(): Boolean {
    val entry = this ?: return false
    val route = entry.destination.route ?: return false
    if (route.isRouteOf(VerifyCode::class)) return entry.toRoute<VerifyCode>().purpose.followsChosenTheme()
    return ThemedRoutes.any { route.isRouteOf(it) }
}

/** A typed route is its class name, followed by `/{arg}` or `?arg={arg}` when it has arguments. */
private fun String.isRouteOf(type: KClass<*>): Boolean {
    val name = type.qualifiedName ?: return false
    return this == name || startsWith("$name/") || startsWith("$name?")
}

/** Only the sign-up code screen belongs to the (still dark) login flow. */
internal fun CodePurpose.followsChosenTheme(): Boolean = this != CodePurpose.SIGN_UP

/** `composable` for a route of Perfil y Ajustes: its content gets the theme of the chosen look. */
internal inline fun <reified T : Any> NavGraphBuilder.themedComposable(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry -> ChosenTheme { content(entry) } }
}

@Composable
internal fun ChosenTheme(content: @Composable () -> Unit) {
    NightWatchProtocolTheme(themeMode = LocalChosenThemeMode.current, content = content)
}
