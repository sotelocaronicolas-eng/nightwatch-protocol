package com.example.nightwatchprotocol.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The user's photo, or their initial on a colored circle. The photo is decoded off the main thread. */
@Composable
fun UserAvatar(
    display: AvatarDisplay,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val photo: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, display) {
        value = (display as? AvatarDisplay.Photo)?.let { photo ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(photo.jpegBytes, 0, photo.jpegBytes.size)?.asImageBitmap()
            }
        }
    }

    val bitmap = photo
    if (display is AvatarDisplay.Photo && bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        // Also covers the moment a photo is still decoding, and a photo that cannot be decoded.
        val letter = (display as? AvatarDisplay.Initial)?.letter ?: "?"
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.size(size).clip(CircleShape).background(NightWatchBorderAccent),
        ) {
            Text(
                text = letter,
                color = NightWatchOnBackground,
                fontFamily = NightWatchBodyFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}
