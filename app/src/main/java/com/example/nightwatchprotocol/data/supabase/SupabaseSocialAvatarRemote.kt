package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.avatar.AVATAR_STORAGE_TIMEOUT_MILLIS
import com.example.nightwatchprotocol.data.avatar.AvatarFormat
import com.example.nightwatchprotocol.data.social.AvatarMark
import com.example.nightwatchprotocol.data.social.SOCIAL_SET_AVATAR_RPC
import com.example.nightwatchprotocol.data.social.SocialAvatarRemote
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.SocialRpcClient
import com.example.nightwatchprotocol.data.social.avatarParams
import com.example.nightwatchprotocol.data.social.flatMap
import com.example.nightwatchprotocol.data.social.parseAvatarMark
import io.github.jan.supabase.storage.BucketApi
import io.ktor.http.ContentType

/** The private bucket of the photos of Social (created by `0010_fotos_social.sql`); the profile photo lives in another one. */
const val SOCIAL_AVATAR_BUCKET = "social-avatars"

/**
 * The files of the photos of Social in the bucket [SOCIAL_AVATAR_BUCKET] and the function that tells the server about them.
 * The file is always `<key>.jpg` and the server only lets a person write the one named by their own key. A failure is a classified
 * [com.example.nightwatchprotocol.data.social.SocialError]; the library's exception (whose message may carry the token) is not kept.
 *
 * Not tested against a real server from here (CLAUDE.md, F-03): the logic around it is tested with a fake, and the real calls are
 * checked by hand once `0010` is applied.
 */
class SupabaseSocialAvatarRemote(
    private val bucket: BucketApi,
    private val rpc: SocialRpcClient,
) : SocialAvatarRemote {

    override suspend fun download(key: String): SocialResult<ByteArray> =
        socialCall(AVATAR_STORAGE_TIMEOUT_MILLIS) { bucket.downloadAuthenticated(path(key)) }

    override suspend fun upload(key: String, bytes: ByteArray): SocialResult<Unit> = socialCall(AVATAR_STORAGE_TIMEOUT_MILLIS) {
        bucket.upload(path(key), bytes) {
            upsert = true
            contentType = ContentType.parse(AvatarFormat.Jpeg.mimeType)
        }
        Unit
    }

    override suspend fun delete(key: String): SocialResult<Unit> = socialCall(AVATAR_STORAGE_TIMEOUT_MILLIS) {
        bucket.delete(path(key))
        Unit
    }

    override suspend fun mark(hasPhoto: Boolean): SocialResult<AvatarMark> =
        rpc.call(SOCIAL_SET_AVATAR_RPC, avatarParams(hasPhoto)).flatMap(::parseAvatarMark)

    private fun path(key: String) = "$key.${AvatarFormat.Jpeg.extension}"
}
