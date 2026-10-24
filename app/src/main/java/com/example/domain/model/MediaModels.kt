package com.example.domain.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTakenMillis: Long,
    val dateModifiedMillis: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMillis: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val bucketId: String = "",
    val bucketDisplayName: String = "",
    val relativePath: String = "",
    val isFavorite: Boolean = false,
    val locationLabel: String? = null
) {
    val effectiveDateMillis: Long =
        if (dateTakenMillis > 0L) dateTakenMillis else dateModifiedMillis

    val formattedDuration: String =
        if (!isVideo || durationMillis <= 0L) {
            ""
        } else {
            val totalSeconds = (durationMillis / 1000L).coerceAtLeast(1L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }

    val isScreenshot: Boolean =
        displayName.contains("screenshot", ignoreCase = true) ||
            bucketDisplayName.contains("screenshot", ignoreCase = true) ||
            relativePath.contains("screenshot", ignoreCase = true)

    val isCamera: Boolean =
        bucketDisplayName.equals("Camera", ignoreCase = true) ||
            relativePath.contains("DCIM/Camera", ignoreCase = true) ||
            displayName.startsWith("IMG_", ignoreCase = true) ||
            displayName.startsWith("VID_", ignoreCase = true)

    val isDownload: Boolean =
        bucketDisplayName.contains("Download", ignoreCase = true) ||
            relativePath.contains("Download", ignoreCase = true)
}

@Immutable
data class TimelineSection(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<MediaItem>
)

enum class TimelineGroupingMode(val label: String) {
    YEARS("Years"),
    MONTHS("Months"),
    DAYS("Days"),
    ALL("All Photos")
}

enum class GridDensity(val columns: Int, val label: String, val thumbnailPx: Int) {
    COZY(2, "2 Columns", 320),
    COMFORTABLE(3, "3 Columns", 260),
    STANDARD(4, "4 Columns", 220),
    COMPACT(5, "5 Columns", 180);

    fun next(): GridDensity = when (this) {
        COZY -> COMFORTABLE
        COMFORTABLE -> STANDARD
        STANDARD -> COMPACT
        COMPACT -> COZY
    }

    fun zoomIn(): GridDensity = when (this) {
        COMPACT -> STANDARD
        STANDARD -> COMFORTABLE
        COMFORTABLE -> COZY
        COZY -> COZY
    }

    fun zoomOut(): GridDensity = when (this) {
        COZY -> COMFORTABLE
        COMFORTABLE -> STANDARD
        STANDARD -> COMPACT
        COMPACT -> COMPACT
    }

    companion object {
        fun fromColumns(columns: Int): GridDensity =
            entries.firstOrNull { it.columns == columns } ?: STANDARD
    }
}

enum class SmartAlbumType(
    val id: String,
    val title: String
) {
    ALL_PHOTOS("smart_all", "All Photos"),
    RECENTS("smart_recents", "Recents"),
    FAVORITES("smart_favorites", "Favorites"),
    VIDEOS("smart_videos", "Videos"),
    SCREENSHOTS("smart_screenshots", "Screenshots"),
    CAMERA("smart_camera", "Camera"),
    DOWNLOADS("smart_downloads", "Downloads")
}

@Immutable
data class AlbumSummary(
    val id: String,
    val title: String,
    val count: Int,
    val coverUri: Uri?,
    val isUserCreated: Boolean,
    val userAlbumId: Long? = null,
    val smartType: SmartAlbumType? = null
)

enum class SearchMediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    FAVORITES("Favorites"),
    SCREENSHOTS("Screenshots"),
    CAMERA("Camera")
}

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", -1f),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    SIXTEEN_NINE("16:9", 16f / 9f),
    NINE_SIXTEEN("9:16", 9f / 16f)
}

@Immutable
data class EditorAdjustments(
    val brightness: Float = 0f,      // -1f to 1f (0f is neutral)
    val contrast: Float = 1f,        // 0.5f to 1.8f (1f is neutral)
    val saturation: Float = 1f,      // 0f to 2f (1f is neutral)
    val rotationDegrees: Int = 0,    // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropAspectRatio: CropAspectRatio = CropAspectRatio.FREE,
    val cropLeft: Float = 0f,        // 0f..1f
    val cropTop: Float = 0f,         // 0f..1f
    val cropRight: Float = 1f,       // 0f..1f
    val cropBottom: Float = 1f       // 0f..1f
) {
    val isModified: Boolean
        get() = brightness != 0f ||
            contrast != 1f ||
            saturation != 1f ||
            rotationDegrees != 0 ||
            flipHorizontal ||
            flipVertical ||
            cropLeft > 0.001f ||
            cropTop > 0.001f ||
            cropRight < 0.999f ||
            cropBottom < 0.999f
}

enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}
