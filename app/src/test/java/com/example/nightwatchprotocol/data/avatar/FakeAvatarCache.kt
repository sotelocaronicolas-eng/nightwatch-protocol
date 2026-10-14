package com.example.nightwatchprotocol.data.avatar

class FakeAvatarCache : AvatarCache {
    val files = mutableMapOf<String, ByteArray>()

    override fun read(uid: String): ByteArray? = files[uid]

    override fun write(uid: String, jpegBytes: ByteArray) {
        files[uid] = jpegBytes
    }

    override fun delete(uid: String) {
        files.remove(uid)
    }
}
