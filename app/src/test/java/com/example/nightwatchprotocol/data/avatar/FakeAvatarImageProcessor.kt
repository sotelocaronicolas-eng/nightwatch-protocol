package com.example.nightwatchprotocol.data.avatar

class FakeAvatarImageProcessor : AvatarImageProcessor {
    var result: Result<ByteArray> = Result.success(byteArrayOf(7, 7, 7))
    var processedUris = mutableListOf<String>()

    override suspend fun process(sourceUri: String): ByteArray {
        processedUris += sourceUri
        return result.getOrThrow()
    }
}
