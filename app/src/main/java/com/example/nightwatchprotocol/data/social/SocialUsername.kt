package com.example.nightwatchprotocol.data.social

private val USERNAME_RULE = Regex("^[A-Za-z0-9_]{3,20}$")

/** Shortest and longest username (decision O6). */
const val USERNAME_MIN_LENGTH = 3
const val USERNAME_MAX_LENGTH = 20

/** The same rule as the `check` of `social_profile` (3 to 20 letters, digits or `_`); the server checks it again. */
fun isValidUsername(username: String): Boolean = USERNAME_RULE.matches(username)
