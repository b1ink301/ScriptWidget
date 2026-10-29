package kr.b1ink.scriptwidget.data.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kr.b1ink.scriptwidget.data.engine.CachedImage
import kr.b1ink.scriptwidget.data.engine.ImageStore
import java.io.File
import java.io.IOException

/**
 * Decodes downloaded images, downsamples them to [maxDimension] and stores them as JPEG (opaque)
 * or PNG (with transparency) files.
 * Widgets only receive file paths, which keeps RemoteViews well below the binder size limit.
 */
class BitmapImageStore(
    private val root: File,
    private val maxDimension: Int = DEFAULT_MAX_DIMENSION,
) : ImageStore {

    override fun store(runKey: String, index: Int, bytes: ByteArray): CachedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image data")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Failed to decode image")
        val bitmap = decoded.scaledDownTo(maxDimension)

        val dir = File(root, runKey).apply { mkdirs() }
        // Photos are much smaller as JPEG; images with transparency (icons) need PNG.
        val transparent = bitmap.hasAlpha() && bitmap.hasTransparentPixels()
        val file = File(dir, if (transparent) "img_$index.png" else "img_$index.jpg")
        file.outputStream().use {
            if (transparent) bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            else bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        }
        val result = CachedImage(file.absolutePath, bitmap.width, bitmap.height)
        if (bitmap !== decoded) decoded.recycle()
        bitmap.recycle()
        return result
    }

    override fun retainOnly(cacheKey: String, runKey: String) {
        val keep = File(root, runKey)
        File(root, cacheKey).listFiles()?.filter { it != keep }?.forEach { it.deleteRecursively() }
    }

    override fun clear(key: String) {
        File(root, key).deleteRecursively()
    }

    override fun bytes(src: String): ByteArray? {
        val file = File(src).canonicalFile
        val inside = file.path.startsWith(root.canonicalPath + File.separator)
        return if (inside && file.isFile) file.readBytes() else null
    }

    companion object {
        const val DEFAULT_MAX_DIMENSION = 512
        private const val JPEG_QUALITY = 90

        /** Bitmaps from PNGs often have an alpha channel without using it; sample to find out. */
        private fun Bitmap.hasTransparentPixels(): Boolean {
            val stepX = maxOf(1, width / 64)
            val stepY = maxOf(1, height / 64)
            for (y in 0 until height step stepY) for (x in 0 until width step stepX) {
                if (android.graphics.Color.alpha(getPixel(x, y)) < 255) return true
            }
            return false
        }

        /** Largest power-of-two sample size that keeps both sides at least [maxDimension]. */
        fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxDimension || height / (sample * 2) >= maxDimension) {
                sample *= 2
            }
            return sample
        }

        fun Bitmap.scaledDownTo(maxDimension: Int): Bitmap {
            val longest = maxOf(width, height)
            if (longest <= maxDimension) return this
            val ratio = maxDimension.toFloat() / longest
            return Bitmap.createScaledBitmap(
                this,
                (width * ratio).toInt().coerceAtLeast(1),
                (height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        }
    }
}
