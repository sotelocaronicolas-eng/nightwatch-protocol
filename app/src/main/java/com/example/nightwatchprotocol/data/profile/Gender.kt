package com.example.nightwatchprotocol.data.profile

/** The only values the Firestore rules accept for `gender` are the [key]s below. */
enum class Gender(val key: String, val label: String) {
    MALE("male", "Masculino"),
    FEMALE("female", "Femenino"),
    OTHER("other", "Otro"),
    PREFER_NOT_TO_SAY("prefer_not_to_say", "Prefiero no decir"),
    ;

    companion object {
        /** Null for a missing or unknown key, so a bad stored value never breaks the screen. */
        fun fromKey(key: String?): Gender? = entries.firstOrNull { it.key == key }
    }
}
