package com.example.nightwatchprotocol.data.preferences

/**
 * How the whole app looks (Module 5, "Personalización"). [DARK] is the look the app has always had and the default;
 * [SYSTEM] follows the phone. The value is saved by [storageValue], never by the enum name, so renaming an entry cannot
 * lose the choice of someone who already picked one.
 */
enum class ThemeMode(val storageValue: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system"),
    ;

    companion object {
        val DEFAULT: ThemeMode = DARK

        /** An absent or unknown stored value (an older or newer app version wrote it) falls back to [DEFAULT]. */
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
