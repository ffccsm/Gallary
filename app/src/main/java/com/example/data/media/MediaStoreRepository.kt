package com.example.data.media

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MediaStoreRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    @OptIn(FlowPreview::class)
    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
        awaitClose {
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
    }.debounce(350L)

    suspend fun loadAllMedia(favoriteIds: Set<Long> = emptySet()): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val filesUri = MediaStore.Files.getContentUri("external")

        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.DATE_TAKEN)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.Video.VideoColumns.DURATION)
            add(MediaStore.Files.FileColumns.WIDTH)
            add(MediaStore.Files.FileColumns.HEIGHT)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Images.Media.BUCKET_ID)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Files.FileColumns.IS_FAVORITE)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        runCatching {
            contentResolver.query(
                filesUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
                val dateModCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                } else -1
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "Photo_$id"
                    val mediaType = if (mediaTypeCol >= 0) cursor.getInt(mediaTypeCol) else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                    val contentUri = if (isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    val rawTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val rawModifiedSec = if (dateModCol >= 0) cursor.getLong(dateModCol) else 0L
                    val rawAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L

                    val dateModifiedMillis = when {
                        rawModifiedSec > 1_000_000_000_000L -> rawModifiedSec
                        rawModifiedSec > 0L -> rawModifiedSec * 1000L
                        rawAddedSec > 0L -> rawAddedSec * 1000L
                        else -> System.currentTimeMillis()
                    }
                    val dateTakenMillis = if (rawTaken > 0L) rawTaken else dateModifiedMillis

                    val mimeType = (if (mimeCol >= 0) cursor.getString(mimeCol) else null)
                        ?: if (isVideo) "video/mp4" else "image/jpeg"
                    val durationMillis = if (isVideo && durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val bucketId = if (bucketIdCol >= 0) cursor.getString(bucketIdCol) ?: "" else ""
                    val bucketDisplayName = if (bucketNameCol >= 0) cursor.getString(bucketNameCol) ?: "Photos" else "Photos"
                    val relativePath = if (relPathCol >= 0) cursor.getString(relPathCol) ?: "" else ""
                    val sysFavorite = favCol >= 0 && cursor.getInt(favCol) == 1

                    val locationHint = inferLocationLabel(displayName, bucketDisplayName)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            displayName = displayName,
                            dateTakenMillis = dateTakenMillis,
                            dateModifiedMillis = dateModifiedMillis,
                            mimeType = mimeType,
                            isVideo = isVideo,
                            durationMillis = durationMillis,
                            width = width,
                            height = height,
                            sizeBytes = sizeBytes,
                            bucketId = bucketId,
                            bucketDisplayName = bucketDisplayName,
                            relativePath = relativePath,
                            isFavorite = sysFavorite || favoriteIds.contains(id),
                            locationLabel = locationHint
                        )
                    )
                }
            }
        }
        items.sortedByDescending { it.effectiveDateMillis }
    }

    private fun inferLocationLabel(displayName: String, bucketName: String): String? {
        val lower = "$displayName $bucketName".lowercase(Locale.US)
        return when {
            "alps" in lower || "zurich" in lower -> "Swiss Alps"
            "tokyo" in lower || "shibuya" in lower -> "Tokyo, Japan"
            "pacific" in lower || "big_sur" in lower -> "Big Sur, California"
            "kyoto" in lower || "botanical" in lower -> "Kyoto Gardens"
            "sahara" in lower || "dunes" in lower -> "Merzouga Dunes"
            "nordic" in lower || "oslo" in lower -> "Oslo, Norway"
            else -> null
        }
    }

    suspend fun readDetailedExifMetadata(item: MediaItem): Map<String, String> = withContext(Dispatchers.IO) {
        val details = LinkedHashMap<String, String>()
        details["File Name"] = item.displayName
        if (item.width > 0 && item.height > 0) {
            val mp = (item.width.toLong() * item.height.toLong()) / 1_000_000.0
            details["Dimensions"] = "${item.width} × ${item.height} (${String.format(Locale.US, "%.1f MP", mp)})"
        }
        details["Folder"] = item.relativePath.ifBlank { item.bucketDisplayName.ifBlank { "Internal Storage" } }
        details["MIME Type"] = item.mimeType

        if (!item.isVideo) {
            runCatching {
                contentResolver.openInputStream(item.uri)?.use { input ->
                    val exif = ExifInterface(input)
                    exif.getAttribute(ExifInterface.TAG_MAKE)?.takeIf { it.isNotBlank() }?.let { make ->
                        val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty()
                        details["Camera"] = "$make $model".trim()
                    }
                    exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.takeIf { it.isNotBlank() }?.let {
                        details["Focal Length"] = it
                    }
                    exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.takeIf { it.isNotBlank() }?.let {
                        details["Aperture"] = "f/$it"
                    }
                    exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.takeIf { it.isNotBlank() }?.let {
                        details["ISO"] = "ISO $it"
                    }
                    val latLong = FloatArray(2)
                    if (exif.getLatLong(latLong)) {
                        details["Coordinates"] = String.format(
                            Locale.US,
                            "%.4f°, %.4f°",
                            latLong[0],
                            latLong[1]
                        )
                    }
                }
            }
        }
        item.locationLabel?.let { details["Location"] = it }
        details
    }

    /**
     * Requests deletion of media items.
     * On Android 11+ (API 30+), creates a system confirmation PendingIntent via MediaStore.createDeleteRequest.
     * On Android 10 and earlier, deletes after in-app confirmation dialog.
     */
    fun createSystemDeleteIntent(uris: List<Uri>): PendingIntent? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createDeleteRequest(contentResolver, uris)
            }.getOrNull()
        } else {
            null
        }
    }

    suspend fun deleteMediaDirect(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (uri in uris) {
            runCatching {
                deleted += contentResolver.delete(uri, null, null)
            }
        }
        deleted
    }

    /**
     * Populates Android's real MediaStore with 8 high-resolution photographic sample images
     * across Today, Yesterday, This Week, and earlier months (including Camera, Screenshots,
     * and Gallery folders) so users on a fresh emulator or empty test device can immediately
     * experience the full MediaStore timeline, albums, search, and photo editor.
     */
    suspend fun seedSamplePhotosIntoMediaStore(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val day = 24 * hour

        data class SampleSpec(
            val fileName: String,
            val relativePath: String,
            val takenMillis: Long,
            val width: Int,
            val height: Int,
            val colorTop: Int,
            val colorMid: Int,
            val colorBottom: Int,
            val accentColor: Int,
            val title: String,
            val cameraModel: String,
            val lat: Double?,
            val lon: Double?
        )

        val specs = listOf(
            SampleSpec(
                fileName = "IMG_Alps_Golden_Hour.jpg",
                relativePath = "${Environment.DIRECTORY_DCIM}/Camera",
                takenMillis = now - (1 * hour),
                width = 1440,
                height = 1800,
                colorTop = Color.parseColor("#0F172A"),
                colorMid = Color.parseColor("#C2410C"),
                colorBottom = Color.parseColor("#FDE047"),
                accentColor = Color.parseColor("#FFFBEB"),
                title = "Swiss Alps Golden Hour",
                cameraModel = "Pixel 9 Pro",
                lat = 46.5584,
                lon = 7.9732
            ),
            SampleSpec(
                fileName = "IMG_Pacific_Big_Sur.jpg",
                relativePath = "${Environment.DIRECTORY_DCIM}/Camera",
                takenMillis = now - (3 * hour),
                width = 1600,
                height = 1200,
                colorTop = Color.parseColor("#0284C7"),
                colorMid = Color.parseColor("#0D9488"),
                colorBottom = Color.parseColor("#0F172A"),
                accentColor = Color.parseColor("#E0F2FE"),
                title = "Big Sur Coastline",
                cameraModel = "Pixel 9 Pro",
                lat = 36.2704,
                lon = -121.8081
            ),
            SampleSpec(
                fileName = "Screenshot_Home_Workspace.jpg",
                relativePath = "${Environment.DIRECTORY_PICTURES}/Screenshots",
                takenMillis = now - (1 * day + 2 * hour),
                width = 1080,
                height = 2340,
                colorTop = Color.parseColor("#18181B"),
                colorMid = Color.parseColor("#27272A"),
                colorBottom = Color.parseColor("#09090B"),
                accentColor = Color.parseColor("#38BDF8"),
                title = "System UI Screenshot",
                cameraModel = "Android Screenshot",
                lat = null,
                lon = null
            ),
            SampleSpec(
                fileName = "IMG_Tokyo_Shibuya_Nights.jpg",
                relativePath = "${Environment.DIRECTORY_DCIM}/Camera",
                takenMillis = now - (1 * day + 6 * hour),
                width = 1500,
                height = 1875,
                colorTop = Color.parseColor("#1E1B4B"),
                colorMid = Color.parseColor("#701A75"),
                colorBottom = Color.parseColor("#F43F5E"),
                accentColor = Color.parseColor("#22D3EE"),
                title = "Tokyo Neon Reflections",
                cameraModel = "Pixel 9 Pro",
                lat = 35.6595,
                lon = 139.7004
            ),
            SampleSpec(
                fileName = "IMG_Kyoto_Botanical_Leaf.jpg",
                relativePath = "${Environment.DIRECTORY_PICTURES}/Gallery",
                takenMillis = now - (4 * day),
                width = 1440,
                height = 1440,
                colorTop = Color.parseColor("#052E16"),
                colorMid = Color.parseColor("#15803D"),
                colorBottom = Color.parseColor("#86EFAC"),
                accentColor = Color.parseColor("#DCFCE7"),
                title = "Kyoto Bamboo & Fern",
                cameraModel = "Sony A7C II",
                lat = 35.0116,
                lon = 135.7681
            ),
            SampleSpec(
                fileName = "IMG_Oslo_Nordic_Architecture.jpg",
                relativePath = "${Environment.DIRECTORY_DCIM}/Camera",
                takenMillis = now - (19 * day),
                width = 1600,
                height = 1200,
                colorTop = Color.parseColor("#334155"),
                colorMid = Color.parseColor("#64748B"),
                colorBottom = Color.parseColor("#E2E8F0"),
                accentColor = Color.parseColor("#F8FAFC"),
                title = "Oslo Opera Minimalism",
                cameraModel = "Leica Q3",
                lat = 59.9075,
                lon = 10.7531
            ),
            SampleSpec(
                fileName = "Download_Sahara_Dunes_Wallpaper.jpg",
                relativePath = Environment.DIRECTORY_DOWNLOADS,
                takenMillis = now - (42 * day),
                width = 1600,
                height = 2000,
                colorTop = Color.parseColor("#451A03"),
                colorMid = Color.parseColor("#D97706"),
                colorBottom = Color.parseColor("#FDE68A"),
                accentColor = Color.parseColor("#FEF3C7"),
                title = "Merzouga Desert Dunes",
                cameraModel = "Canon EOS R5",
                lat = 31.0801,
                lon = -4.0134
            ),
            SampleSpec(
                fileName = "Screenshot_Flight_Boarding_Pass.jpg",
                relativePath = "${Environment.DIRECTORY_PICTURES}/Screenshots",
                takenMillis = now - (75 * day),
                width = 1080,
                height = 2340,
                colorTop = Color.parseColor("#0F172A"),
                colorMid = Color.parseColor("#1E3A8A"),
                colorBottom = Color.parseColor("#172554"),
                accentColor = Color.parseColor("#60A5FA"),
                title = "Boarding Pass Screenshot",
                cameraModel = "Android Screenshot",
                lat = null,
                lon = null
            )
        )

        var insertedCount = 0
        val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

        for ((index, spec) in specs.withIndex()) {
            runCatching {
                val bitmap = renderArtisticSamplePhoto(
                    width = spec.width,
                    height = spec.height,
                    colorTop = spec.colorTop,
                    colorMid = spec.colorMid,
                    colorBottom = spec.colorBottom,
                    accentColor = spec.accentColor,
                    seedIndex = index
                )

                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, spec.fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, spec.takenMillis)
                    put(MediaStore.Images.Media.DATE_ADDED, spec.takenMillis / 1000L)
                    put(MediaStore.Images.Media.DATE_MODIFIED, spec.takenMillis / 1000L)
                    put(MediaStore.Images.Media.WIDTH, spec.width)
                    put(MediaStore.Images.Media.HEIGHT, spec.height)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, spec.relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val uri = contentResolver.insert(collection, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    bitmap.recycle()

                    // Write EXIF metadata
                    runCatching {
                        contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                            val exif = ExifInterface(pfd.fileDescriptor)
                            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDateFormat.format(Date(spec.takenMillis)))
                            exif.setAttribute(ExifInterface.TAG_MAKE, "Gallery Studio")
                            exif.setAttribute(ExifInterface.TAG_MODEL, spec.cameraModel)
                            exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, spec.width.toString())
                            exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, spec.height.toString())
                            if (spec.lat != null && spec.lon != null) {
                                exif.setLatLong(spec.lat, spec.lon)
                            }
                            exif.saveAttributes()
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val publishValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                            put(MediaStore.Images.Media.DATE_TAKEN, spec.takenMillis)
                            put(MediaStore.Images.Media.DATE_MODIFIED, spec.takenMillis / 1000L)
                        }
                        contentResolver.update(uri, publishValues, null, null)
                    }
                    insertedCount++
                }
            }
        }
        insertedCount
    }

    private fun renderArtisticSamplePhoto(
        width: Int,
        height: Int,
        colorTop: Int,
        colorMid: Int,
        colorBottom: Int,
        accentColor: Int,
        seedIndex: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sky / atmosphere gradient
        paint.shader = LinearGradient(
            0f,
            0f,
            width * 0.25f,
            height.toFloat(),
            intArrayOf(colorTop, colorMid, colorBottom),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Sun / glowing orb
        val sunX = width * (0.28f + (seedIndex % 3) * 0.22f)
        val sunY = height * (0.26f + (seedIndex % 2) * 0.14f)
        val sunRadius = width.coerceAtMost(height) * 0.28f
        paint.shader = RadialGradient(
            sunX,
            sunY,
            sunRadius,
            intArrayOf(
                accentColor,
                Color.argb(110, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, sunRadius, paint)

        // Layered geometric mountain / horizon waves or UI cards for screenshots
        paint.shader = null
        if (seedIndex == 2 || seedIndex == 7) {
            // Screenshot style cards
            paint.color = Color.argb(45, 255, 255, 255)
            val margin = width * 0.08f
            canvas.drawRoundRect(
                RectF(margin, height * 0.14f, width - margin, height * 0.36f),
                48f,
                48f,
                paint
            )
            paint.color = Color.argb(35, 255, 255, 255)
            canvas.drawRoundRect(
                RectF(margin, height * 0.40f, width - margin, height * 0.68f),
                48f,
                48f,
                paint
            )
            paint.color = accentColor
            canvas.drawRoundRect(
                RectF(margin * 1.5f, height * 0.20f, width * 0.55f, height * 0.23f),
                20f,
                20f,
                paint
            )
        } else {
            // Layered photographic landscape silhouettes
            for (layer in 0..2) {
                val path = android.graphics.Path()
                val baseHeight = height * (0.56f + layer * 0.13f)
                path.moveTo(0f, height.toFloat())
                path.lineTo(0f, baseHeight)
                val segments = 5
                val stepX = width.toFloat() / segments
                for (s in 1..segments) {
                    val cx = (s - 0.5f) * stepX
                    val peakOffset = ((abs((seedIndex + 2) * (layer + 1) * s * 37) % 180) - 90) * (height / 950f)
                    val cy = baseHeight + peakOffset
                    val ex = s * stepX
                    val ey = baseHeight + (peakOffset * 0.35f)
                    path.quadTo(cx, cy, ex, ey)
                }
                path.lineTo(width.toFloat(), height.toFloat())
                path.close()

                val alpha = 115 + layer * 65
                paint.color = Color.argb(
                    alpha.coerceAtMost(245),
                    (Color.red(colorTop) * (3 - layer) / 3).coerceIn(0, 255),
                    (Color.green(colorTop) * (3 - layer) / 3).coerceIn(0, 255),
                    (Color.blue(colorTop) * (3 - layer) / 3).coerceIn(0, 255)
                )
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }
}
