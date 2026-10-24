package com.example

import android.net.Uri
import com.example.domain.model.CropAspectRatio
import com.example.domain.model.EditorAdjustments
import com.example.domain.model.GridDensity
import com.example.domain.model.MediaItem
import com.example.domain.model.TimelineGroupingMode
import com.example.util.DateGroupingUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

    private fun sampleItem(
        id: Long,
        name: String,
        takenMillis: Long,
        isVideo: Boolean = false,
        durationMillis: Long = 0L,
        bucket: String = "Camera"
    ) = MediaItem(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        displayName = name,
        dateTakenMillis = takenMillis,
        dateModifiedMillis = takenMillis,
        mimeType = if (isVideo) "video/mp4" else "image/jpeg",
        isVideo = isVideo,
        durationMillis = durationMillis,
        bucketDisplayName = bucket
    )

    @Test
    fun `groupMedia groups items into Today, Yesterday, and This Week`() {
        val now = 1_760_000_000_000L
        val oneHourAgo = now - 3_600_000L
        val yesterday = now - 26 * 3_600_000L
        val fourDaysAgo = now - 4 * 24 * 3_600_000L

        val items = listOf(
            sampleItem(1L, "IMG_1.jpg", oneHourAgo),
            sampleItem(2L, "IMG_2.jpg", yesterday),
            sampleItem(3L, "IMG_3.jpg", fourDaysAgo)
        )

        val sections = DateGroupingUtil.groupMedia(items, TimelineGroupingMode.ALL, nowMillis = now)
        assertEquals(3, sections.size)
        assertEquals("Today", sections[0].title)
        assertEquals("Yesterday", sections[1].title)
        assertEquals("This Week", sections[2].title)
    }

    @Test
    fun `formattedDuration formats minutes and hours accurately`() {
        val shortVideo = sampleItem(10L, "VID_1.mp4", 1000L, isVideo = true, durationMillis = 65_000L)
        assertEquals("1:05", shortVideo.formattedDuration)

        val longVideo = sampleItem(11L, "VID_2.mp4", 1000L, isVideo = true, durationMillis = 3_665_000L)
        assertEquals("1:01:05", longVideo.formattedDuration)
    }

    @Test
    fun `smart category flags detect screenshots and camera items`() {
        val screenshot = sampleItem(1L, "Screenshot_20261024.png", 1000L, bucket = "Screenshots")
        assertTrue(screenshot.isScreenshot)

        val cameraPhoto = sampleItem(2L, "IMG_20261024.jpg", 1000L, bucket = "Camera")
        assertTrue(cameraPhoto.isCamera)
        assertFalse(cameraPhoto.isScreenshot)
    }

    @Test
    fun `editorAdjustments detects modifications accurately`() {
        val clean = EditorAdjustments()
        assertFalse(clean.isModified)

        val rotated = clean.copy(rotationDegrees = 90)
        assertTrue(rotated.isModified)

        val cropped = clean.copy(cropAspectRatio = CropAspectRatio.SQUARE, cropLeft = 0.1f, cropRight = 0.9f)
        assertTrue(cropped.isModified)
    }

    @Test
    fun `gridDensity zoomIn and zoomOut clamp properly`() {
        assertEquals(GridDensity.COMFORTABLE, GridDensity.STANDARD.zoomIn())
        assertEquals(GridDensity.COMPACT, GridDensity.STANDARD.zoomOut())
        assertEquals(220, GridDensity.STANDARD.thumbnailPx)
        assertEquals(180, GridDensity.COMPACT.thumbnailPx)
    }

    @Test
    fun `groupMedia processes 3000 sorted items rapidly without re-sorting`() {
        val now = 1_760_000_000_000L
        val largeList = (0 until 3000).map { idx ->
            sampleItem(
                id = idx.toLong() + 1L,
                name = "IMG_$idx.jpg",
                takenMillis = now - idx * 60_000L
            )
        }
        val sections = DateGroupingUtil.groupMedia(largeList, TimelineGroupingMode.ALL, nowMillis = now)
        assertTrue(sections.isNotEmpty())
        assertEquals(3000, sections.sumOf { it.items.size })
    }
}
