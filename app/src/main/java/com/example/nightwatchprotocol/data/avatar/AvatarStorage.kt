package com.example.nightwatchprotocol.data.avatar

import io.github.jan.supabase.storage.BucketApi
import io.ktor.http.ContentType

/** Private Supabase Storage bucket for the photos (created by `0004_storage_avatares.sql`). */
const val AVATAR_BUCKET = "avatars"

/** Same limit as the bucket's `file_size_limit`: 256 KB. The processor's own target is ~100 KB. */
const val AVATAR_STORAGE_MAX_BYTES = 262_144

/** Bigger than the 5 s of the small Postgres calls: a photo upload or download takes longer. */
const val AVATAR_STORAGE_TIMEOUT_MILLIS = 15_000L

/** The formats the bucket accepts. The app only ever produces [Jpeg] (see BitmapAvatarImageProcessor). */
enum class AvatarFormat(val extension: String, val mimeType: String) {
    Jpeg("jpg", "image/jpeg"),
}

/** Looks at the first bytes, not at a name: JPEG always starts with FF D8 FF. Null for anything else. */
fun detectAvatarFormat(bytes: ByteArray): AvatarFormat? =
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        AvatarFormat.Jpeg
    } else {
        null
    }

/** Object name inside the bucket: `<user_id>/avatar.<ext>`. The first folder must be the uid (the RLS policies). */
fun avatarObjectPath(userId: String, format: AvatarFormat): String = "$userId/avatar.${format.extension}"

/**
 * `profile.avatar_path` is a column the user can write, so a path is only followed when it is inside that user's
 * own folder and has no way to climb out of it.
 */
fun isOwnAvatarPath(userId: String, path: String?): Boolean {
    if (path.isNullOrBlank() || userId.isBlank()) return false
    if (!path.startsWith("$userId/")) return false
    val name = path.removePrefix("$userId/")
    return name.isNotEmpty() && '/' !in name && ".." !in name
}

/** Whether these processed bytes can go to the bucket at all. */
fun canUploadAvatar(bytes: ByteArray): Boolean =
    detectAvatarFormat(bytes) != null && bytes.size <= AVATAR_STORAGE_MAX_BYTES

/** Where the photo files live. Every call throws on failure; no call does any error classification. */
interface AvatarStorage {
    /** Creates or replaces [path]. */
    suspend fun upload(path: String, bytes: ByteArray, format: AvatarFormat)

    /** Downloads with the user's session (the bucket is private; there are no public URLs). */
    suspend fun download(path: String): ByteArray

    /** Deletes [path]; a file that does not exist is not an error. */
    suspend fun delete(path: String)
}

class SupabaseAvatarStorage(private val bucket: BucketApi) : AvatarStorage {

    override suspend fun upload(path: String, bytes: ByteArray, format: AvatarFormat) {
        bucket.upload(path, bytes) {
            upsert = true
            contentType = ContentType.parse(format.mimeType)
        }
    }

    override suspend fun download(path: String): ByteArray = bucket.downloadAuthenticated(path)

    override suspend fun delete(path: String) {
        bucket.delete(path)
    }
}
