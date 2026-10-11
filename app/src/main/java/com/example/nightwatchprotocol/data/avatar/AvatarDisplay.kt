package com.example.nightwatchprotocol.data.avatar

sealed interface AvatarDisplay {
    data class Photo(val jpegBytes: ByteArray) : AvatarDisplay {
        override fun equals(other: Any?): Boolean =
            other is Photo && jpegBytes.contentEquals(other.jpegBytes)

        override fun hashCode(): Int = jpegBytes.contentHashCode()
    }

    data class Initial(val letter: String) : AvatarDisplay
}

/**
 * A photo wins when there is one; otherwise the first letter of the name, then of the email,
 * and "?" when neither has a usable character.
 */
fun avatarDisplay(photo: ByteArray?, displayName: String?, email: String?): AvatarDisplay {
    if (photo != null && photo.isNotEmpty()) return AvatarDisplay.Photo(photo)
    val letter = displayName.firstLetterOrNull() ?: email.firstLetterOrNull() ?: "?"
    return AvatarDisplay.Initial(letter)
}

private fun String?.firstLetterOrNull(): String? =
    this?.trim()?.firstOrNull { !it.isWhitespace() }?.uppercase()
