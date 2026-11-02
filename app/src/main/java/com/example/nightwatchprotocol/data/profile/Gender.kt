package com.example.nightwatchprotocol.data.profile

import androidx.annotation.StringRes
import com.example.nightwatchprotocol.R

/** The only values the `gender` column accepts (a CHECK constraint) are the [key]s below. */
enum class Gender(val key: String, @StringRes val labelRes: Int) {
    MALE("male", R.string.profile_gender_male),
    FEMALE("female", R.string.profile_gender_female),
    OTHER("other", R.string.profile_gender_other),
    PREFER_NOT_TO_SAY("prefer_not_to_say", R.string.profile_gender_prefer_not_to_say),
    ;

    companion object {
        /** Null for a missing or unknown key, so a bad stored value never breaks the screen. */
        fun fromKey(key: String?): Gender? = entries.firstOrNull { it.key == key }
    }
}
