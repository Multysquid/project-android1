package de.jw.einkaufsliste.feature.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** A photo copied out of the camera buffer, so the [ImageProxy] can be closed right away. */
sealed interface RawPhoto {
    val rotationDegrees: Int

    class Jpeg(val bytes: ByteArray, override val rotationDegrees: Int) : RawPhoto

    /** Fallback for cameras that deliver another format than JPEG. */
    class Decoded(val bitmap: Bitmap, override val rotationDegrees: Int) : RawPhoto
}

/** What is sent to Gemini ([jpeg]) and shown meanwhile ([preview], upright, same pixels). */
class PreparedPhoto(val jpeg: ByteArray, val preview: Bitmap)

object PhotoEncoding {
    /** Longest side sent to Gemini; plenty to read a label, small enough for a quick upload. */
    const val MAX_SIDE = 1024
    const val JPEG_QUALITY = 80

    /** Copies the image data; call before closing [image]. Cheap enough for the main thread for JPEG. */
    fun copy(image: ImageProxy): RawPhoto {
        val rotation = image.imageInfo.rotationDegrees
        if (image.format == ImageFormat.JPEG) {
            val buffer = image.planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return RawPhoto.Jpeg(bytes, rotation)
        }
        return RawPhoto.Decoded(image.toBitmap(), rotation)
    }

    /** Decodes, turns upright, scales the long side down to [MAX_SIDE] and compresses. Not on the main thread. */
    fun prepare(raw: RawPhoto): PreparedPhoto {
        val source = when (raw) {
            is RawPhoto.Jpeg -> decodeSampled(raw.bytes, MAX_SIDE)
            is RawPhoto.Decoded -> raw.bitmap
        }
        val scale = min(1f, MAX_SIDE.toFloat() / max(source.width, source.height))
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        if (raw.rotationDegrees % 360 != 0) matrix.postRotate(raw.rotationDegrees.toFloat())
        val upright = if (matrix.isIdentity) {
            source
        } else {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
        if (upright !== source) source.recycle()
        val out = ByteArrayOutputStream()
        if (!upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
            throw IOException("JPEG compression failed")
        }
        return PreparedPhoto(out.toByteArray(), upright)
    }

    /** Decodes with the largest power-of-two subsampling that keeps the long side at least [minSide]. */
    private fun decodeSampled(bytes: ByteArray, minSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longSide / (sampleSize * 2) >= minSide) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Camera image could not be decoded")
    }
}
