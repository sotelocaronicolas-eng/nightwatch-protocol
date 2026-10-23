package com.example.nightwatchprotocol.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.components.NightWatchGold
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import kotlin.math.cos
import kotlin.math.sin

/*
 * The avatar of a person in Social: a circle with their photo or, when they have none (or it is loading, or it failed), the emoji.
 * It has no state of its own: whoever shows it loads the photo (see SocialPhotos.kt). In the arena the avatar can also wear the
 * wizard's hat and wand, drawn here with paths (no image, no dependency). The hat sticks out above the circle and the wand crosses
 * its lower edge, so the avatar reserves that room in its own size and nothing is cut by a scrolling parent.
 */

/** How much the hat rises above the circle and how much the wand drops below it, as a part of the circle's size. */
private const val HAT_HEADROOM = 0.30f
private const val WAND_FOOTROOM = 0.18f

private val HatBody = SocialOnLight
private val GearOutline = NightWatchOnBackground
private val WandStick = NightWatchSurfaceButton

/**
 * [photo] is null while it loads, when it failed or when the person has none: the [emoji] shows instead. [contentDescription] is what
 * a screen reader says; null makes the avatar decoration (use it when the name is right next to it). The hat and the wand
 * ([withWizardGear], only in the arena) are decoration and never spoken.
 */
@Composable
fun SocialAvatar(
    photo: ImageBitmap?,
    emoji: String,
    size: Dp,
    withWizardGear: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val headroom = if (withWizardGear) size * HAT_HEADROOM else 0.dp
    val footroom = if (withWizardGear) size * WAND_FOOTROOM else 0.dp
    Box(
        modifier = modifier
            .size(width = size, height = size + headroom + footroom)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.offset(y = headroom).size(size).clip(CircleShape).background(SocialElevated),
        ) {
            if (photo != null) {
                Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(text = emoji, fontSize = (size.value * 0.46f).sp, color = Color.Black, modifier = Modifier.clearAndSetSemantics {})
            }
        }
        if (withWizardGear) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawWizardHat(headroom.toPx())
                drawWizardWand(headroom.toPx())
            }
        }
    }
}

/** A tilted pointed hat: dark body with a light outline, a gold band and a gold sparkle. [headroom] is where the circle starts. */
private fun DrawScope.drawWizardHat(headroom: Float) {
    val s = size.width
    val cx = s * 0.5f
    val brimY = headroom + s * 0.16f
    val baseY = brimY - s * 0.02f
    val outline = Stroke(width = s * 0.03f)
    rotate(degrees = -12f, pivot = Offset(cx, brimY)) {
        val brimTopLeft = Offset(cx - s * 0.42f, brimY - s * 0.09f)
        val brimSize = Size(s * 0.84f, s * 0.18f)
        drawOval(HatBody, topLeft = brimTopLeft, size = brimSize)
        drawOval(GearOutline, topLeft = brimTopLeft, size = brimSize, style = outline)

        val cone = Path().apply {
            moveTo(cx - s * 0.30f, baseY)
            quadraticTo(cx - s * 0.12f, baseY - s * 0.26f, cx + s * 0.04f, brimY - s * 0.46f)
            quadraticTo(cx + s * 0.16f, baseY - s * 0.24f, cx + s * 0.30f, baseY)
            close()
        }
        drawPath(cone, HatBody)
        drawPath(cone, GearOutline, style = outline)

        val band = Path().apply {
            moveTo(cx - s * 0.30f, baseY)
            lineTo(cx + s * 0.30f, baseY)
            lineTo(cx + s * 0.254f, baseY - s * 0.08f)
            lineTo(cx - s * 0.244f, baseY - s * 0.08f)
            close()
        }
        drawPath(band, NightWatchGold)
        drawPath(sparkle(Offset(cx + s * 0.01f, baseY - s * 0.25f), s * 0.07f), NightWatchGold)
    }
}

/** A wand that goes diagonally across the lower edge of the circle, with a gold sparkle at its tip. */
private fun DrawScope.drawWizardWand(headroom: Float) {
    val s = size.width
    val start = Offset(s * 0.20f, headroom + s * 1.12f)
    val tip = Offset(s * 0.76f, headroom + s * 0.66f)
    drawLine(GearOutline, start, tip, strokeWidth = s * 0.11f, cap = StrokeCap.Round)
    drawLine(WandStick, start, tip, strokeWidth = s * 0.06f, cap = StrokeCap.Round)
    drawPath(sparkle(tip, s * 0.13f), GearOutline, style = Stroke(width = s * 0.04f))
    drawPath(sparkle(tip, s * 0.13f), NightWatchGold)
}

/** A four-pointed star: eight points around [center], alternating the full radius and a short one. */
private fun sparkle(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 8) {
        val r = if (i % 2 == 0) radius else radius * 0.38f
        val angle = Math.toRadians(-90.0 + i * 45.0)
        val x = center.x + r * cos(angle).toFloat()
        val y = center.y + r * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A flat brown square standing in for a photo, only for the preview. */
private fun previewPhoto(): ImageBitmap = ImageBitmap(64, 64).also { bitmap ->
    GraphicsCanvas(bitmap).drawRect(Rect(0f, 0f, 64f, 64f), Paint().apply { color = Color(0xFFB07A4F) })
}

@Preview(showBackground = true, backgroundColor = 0xFF2C595D)
@Composable
private fun SocialAvatarPreview() {
    NightWatchProtocolTheme {
        val photo = previewPhoto()
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SocialAvatar(photo = photo, emoji = "🧑", size = 56.dp, withWizardGear = true, contentDescription = "Con foto y equipo")
                SocialAvatar(photo = null, emoji = "🧑", size = 56.dp, withWizardGear = true, contentDescription = "Sin foto y con equipo")
                SocialAvatar(photo = photo, emoji = "🧑", size = 40.dp, withWizardGear = false, contentDescription = null)
                SocialAvatar(photo = null, emoji = "🧑", size = 40.dp, withWizardGear = false, contentDescription = null)
            }
        }
    }
}
