package com.example.nightwatchprotocol.data.avatar

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BitmapAvatarImageProcessor(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AvatarImageProcessor {

    override suspend fun process(sourceUri: String): ByteArray = withContext(ioDispatcher) {
        try {
            val uri = Uri.parse(sourceUri)
            val decoded = decodeSampled(uri)
            val oriented = applyExifOrientation(decoded, uri)
            val square = centerCropAndScale(oriented)
            compressWithinLimit(encode = { quality -> square.toJpeg(quality) })
        } catch (e: AvatarImageException) {
            throw e
        } catch (e: Exception) {
            throw AvatarImageException("No se pudo procesar la imagen", e)
        } catch (e: OutOfMemoryError) {
            throw AvatarImageException("Imagen demasiado grande", e)
        }
    }

    private fun decodeSampled(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw AvatarImageException("Imagen ilegible")

        // Decode at no more than ~2x the target so a 12 MP photo is never fully loaded.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= AVATAR_MAX_SIDE_PX * 2 &&
            bounds.outHeight / (sample * 2) >= AVATAR_MAX_SIDE_PX * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return openStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw AvatarImageException("Imagen ilegible")
    }

    // BitmapFactory ignores EXIF, so camera photos would otherwise come out rotated.
    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = runCatching {
            openStream(uri).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun centerCropAndScale(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, side, side)
        return if (side > AVATAR_MAX_SIDE_PX) {
            Bitmap.createScaledBitmap(square, AVATAR_MAX_SIDE_PX, AVATAR_MAX_SIDE_PX, true)
        } else {
            square
        }
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private fun openStream(uri: Uri) =
        contentResolver.openInputStream(uri) ?: throw AvatarImageException("No se pudo abrir la imagen")
}
