package com.example.nightwatchprotocol.ui.social

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.example.nightwatchprotocol.data.social.SocialAvatarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * How the screens of Social get the photo of a person. A screen only knows the key and the version that the server gave with that
 * person; the photo itself comes through [LocalSocialPhotoSource], which the activity provides once for the whole app. Without a
 * source (previews, tests) nobody has a photo and the avatar shows its emoji.
 */

/** Gives the decoded photo for a key and version, or null (no photo, still unreachable, not allowed): the emoji stays. */
fun interface SocialPhotoSource {
    suspend fun photo(key: String?, version: Int?): ImageBitmap?
}

val LocalSocialPhotoSource = staticCompositionLocalOf { SocialPhotoSource { _, _ -> null } }

/** The source of the app: the bytes come from the [SocialAvatarRepository] (with its caches) and are decoded off the main thread. */
class RepositorySocialPhotoSource(private val repository: SocialAvatarRepository) : SocialPhotoSource {
    override suspend fun photo(key: String?, version: Int?): ImageBitmap? {
        val bytes = repository.photo(key, version) ?: return null
        return withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
}

/** The photo of a person, null while it loads or when there is none. It loads again only when the key or the version changes. */
@Composable
fun rememberSocialPhoto(avatarKey: String?, avatarVersion: Int?): ImageBitmap? {
    val source = LocalSocialPhotoSource.current
    return produceState<ImageBitmap?>(initialValue = null, avatarKey, avatarVersion, source) {
        value = source.photo(avatarKey, avatarVersion)
    }.value
}

/** [SocialAvatar] with the photo of the person loaded by [rememberSocialPhoto]; the rest is as in [SocialAvatar]. */
@Composable
fun SocialPhotoAvatar(
    avatarKey: String?,
    avatarVersion: Int?,
    emoji: String,
    size: Dp,
    withWizardGear: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    SocialAvatar(
        photo = rememberSocialPhoto(avatarKey, avatarVersion),
        emoji = emoji,
        size = size,
        withWizardGear = withWizardGear,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
