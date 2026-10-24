package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.EditorAdjustments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object PhotoEditorEngine {

    /**
     * Loads a memory-safe, EXIF-rotated Bitmap suitable for interactive editing and high-res export.
     */
    suspend fun decodeSampledBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val srcWidth = boundsOptions.outWidth
        val srcHeight = boundsOptions.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return@withContext null

        var inSampleSize = 1
        while ((srcWidth / inSampleSize) > maxDimension || (srcHeight / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return@withContext null

        val exifOrientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        applyExifOrientation(rawBitmap, exifOrientation)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    /**
     * Builds an Android Graphics ColorMatrix from brightness, contrast, and saturation adjustments.
     */
    fun buildAndroidColorMatrix(adjustments: EditorAdjustments): ColorMatrix {
        val combined = ColorMatrix()

        // 1. Saturation
        val satMatrix = ColorMatrix().apply {
            setSaturation(adjustments.saturation.coerceIn(0f, 2f))
        }
        combined.postConcat(satMatrix)

        // 2. Contrast & Brightness
        val contrast = adjustments.contrast.coerceIn(0.5f, 1.8f)
        val brightnessOffset = adjustments.brightness.coerceIn(-1f, 1f) * 180f
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightnessOffset

        val bcMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        combined.postConcat(bcMatrix)
        return combined
    }

    /**
     * Builds a Jetpack Compose ColorMatrix for real-time hardware-accelerated preview in the UI.
     */
    fun buildComposeColorMatrix(adjustments: EditorAdjustments): androidx.compose.ui.graphics.ColorMatrix {
        val androidMatrix = buildAndroidColorMatrix(adjustments)
        return androidx.compose.ui.graphics.ColorMatrix(androidMatrix.array)
    }

    /**
     * Renders the final edited Bitmap off the main thread, applying:
     * 1. Rotation (90° increments) & Horizontal/Vertical Flip
     * 2. Normalized Crop rectangle
     * 3. Brightness, Contrast, and Saturation ColorMatrix
     */
    suspend fun renderEditedBitmap(
        source: Bitmap,
        adjustments: EditorAdjustments
    ): Bitmap = withContext(Dispatchers.Default) {
        // Step 1: Rotate & Flip
        val transformMatrix = Matrix()
        val sx = if (adjustments.flipHorizontal) -1f else 1f
        val sy = if (adjustments.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            transformMatrix.postScale(sx, sy, source.width / 2f, source.height / 2f)
        }
        val normalizedRotation = ((adjustments.rotationDegrees % 360) + 360) % 360
        if (normalizedRotation != 0) {
            transformMatrix.postRotate(normalizedRotation.toFloat())
        }

        val transformedBitmap = if (!transformMatrix.isIdentity) {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                transformMatrix,
                true
            )
        } else {
            source
        }

        // Step 2: Crop
        val leftFrac = adjustments.cropLeft.coerceIn(0f, 0.9f)
        val topFrac = adjustments.cropTop.coerceIn(0f, 0.9f)
        val rightFrac = adjustments.cropRight.coerceIn(leftFrac + 0.1f, 1f)
        val bottomFrac = adjustments.cropBottom.coerceIn(topFrac + 0.1f, 1f)

        val cropX = (transformedBitmap.width * leftFrac).roundToInt().coerceIn(0, max(0, transformedBitmap.width - 1))
        val cropY = (transformedBitmap.height * topFrac).roundToInt().coerceIn(0, max(0, transformedBitmap.height - 1))
        val cropW = ((transformedBitmap.width * (rightFrac - leftFrac)).roundToInt())
            .coerceIn(1, transformedBitmap.width - cropX)
        val cropH = ((transformedBitmap.height * (bottomFrac - topFrac)).roundToInt())
            .coerceIn(1, transformedBitmap.height - cropY)

        // Step 3: Render into output Bitmap with ColorMatrixFilter
        val outputBitmap = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(buildAndroidColorMatrix(adjustments))
        }

        val srcRect = android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH)
        val dstRect = android.graphics.Rect(0, 0, cropW, cropH)
        canvas.drawBitmap(transformedBitmap, srcRect, dstRect, paint)

        if (transformedBitmap !== source && transformedBitmap !== outputBitmap) {
            transformedBitmap.recycle()
        }
        outputBitmap
    }

    /**
     * Saves the rendered Bitmap as a new JPEG copy in Pictures/Gallery via MediaStore,
     * preserving the original photo intact.
     */
    suspend fun saveEditedBitmapToMediaStore(
        context: Context,
        originalDisplayName: String,
        renderedBitmap: Bitmap
    ): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = originalDisplayName.substringBeforeLast('.').ifBlank { "IMG" }
        val newFileName = "${baseName}_Edited_$timestamp.jpg"
        val nowMillis = System.currentTimeMillis()

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, newFileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, nowMillis)
            put(MediaStore.Images.Media.DATE_ADDED, nowMillis / 1000L)
            put(MediaStore.Images.Media.DATE_MODIFIED, nowMillis / 1000L)
            put(MediaStore.Images.Media.WIDTH, renderedBitmap.width)
            put(MediaStore.Images.Media.HEIGHT, renderedBitmap.height)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val insertedUri = resolver.insert(collection, values) ?: return@withContext null

        val success = runCatching {
            resolver.openOutputStream(insertedUri)?.use { outStream ->
                renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
            } ?: false
        }.getOrDefault(false)

        if (!success) {
            runCatching { resolver.delete(insertedUri, null, null) }
            return@withContext null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(insertedUri, publishValues, null, null)
        }

        insertedUri
    }
}
