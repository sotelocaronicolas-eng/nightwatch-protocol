package com.example.nightwatchprotocol.data.avatar

class AvatarImageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Turns an image the user picked into a small square JPEG ready to store. */
interface AvatarImageProcessor {
    /** Throws [AvatarImageException] when the image cannot be read or decoded. */
    suspend fun process(sourceUri: String): ByteArray
}

const val AVATAR_MAX_SIDE_PX = 256
const val AVATAR_MAX_BYTES = 100_000
val AVATAR_JPEG_QUALITIES = listOf(80, 60, 40)

/**
 * Encodes with each quality in turn and returns the first result that fits in [maxBytes];
 * when none fits, the smallest one (the last quality) is returned.
 */
fun compressWithinLimit(
    encode: (quality: Int) -> ByteArray,
    maxBytes: Int = AVATAR_MAX_BYTES,
    qualities: List<Int> = AVATAR_JPEG_QUALITIES,
): ByteArray {
    var result = ByteArray(0)
    for (quality in qualities) {
        result = encode(quality)
        if (result.size <= maxBytes) return result
    }
    return result
}
