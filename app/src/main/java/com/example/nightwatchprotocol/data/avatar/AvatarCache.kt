package com.example.nightwatchprotocol.data.avatar

import java.io.File

/** Local copy of the avatar so it shows instantly and offline. Blocking: call it off the main thread. */
interface AvatarCache {
    fun read(uid: String): ByteArray?
    fun write(uid: String, jpegBytes: ByteArray)
    fun delete(uid: String)
}

/** One file per user (`avatar_<uid>.jpg`), so another account on the same phone never sees this photo. */
class FileAvatarCache(private val directory: File) : AvatarCache {

    override fun read(uid: String): ByteArray? = file(uid).takeIf { it.isFile }?.readBytes()

    override fun write(uid: String, jpegBytes: ByteArray) {
        directory.mkdirs()
        file(uid).writeBytes(jpegBytes)
    }

    override fun delete(uid: String) {
        file(uid).delete()
    }

    private fun file(uid: String) = File(directory, "avatar_$uid.jpg")
}
