package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.albums.AlbumRepository
import com.example.data.database.GalleryDatabase
import com.example.domain.model.EditorAdjustments
import com.example.domain.model.MediaItem
import com.example.util.PhotoEditorEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: GalleryDatabase
    private lateinit var albumRepository: AlbumRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, GalleryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        albumRepository = AlbumRepository(database.albumDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app_name string resource is Gallery`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Gallery", appName)
    }

    @Test
    fun `mainActivity launches and renders without crashing`() {
        var uncaughtError: Throwable? = null
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            uncaughtError = throwable
        }
        try {
            val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
            val activity = controller.get()
            org.junit.Assert.assertNotNull(activity)
            org.junit.Assert.assertNull("Expected no uncaught exception on startup, but got: $uncaughtError", uncaughtError)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `user album lifecycle creates, renames, adds media, removes media, and deletes album without losing media`() = runBlocking {
        val sampleMedia = listOf(
            MediaItem(
                id = 101L,
                uri = Uri.parse("content://media/external/images/media/101"),
                displayName = "IMG_Alps.jpg",
                dateTakenMillis = 1_700_000_000_000L,
                dateModifiedMillis = 1_700_000_000_000L,
                mimeType = "image/jpeg",
                isVideo = false
            ),
            MediaItem(
                id = 102L,
                uri = Uri.parse("content://media/external/images/media/102"),
                displayName = "IMG_Tokyo.jpg",
                dateTakenMillis = 1_700_000_100_000L,
                dateModifiedMillis = 1_700_000_100_000L,
                mimeType = "image/jpeg",
                isVideo = false
            )
        )

        // 1. Create album with initial item
        val albumId = albumRepository.createAlbum("Summer Trip", listOf(sampleMedia[0]))
        var entities = albumRepository.userAlbumsFlow.first()
        assertEquals(1, entities.size)
        assertEquals("Summer Trip", entities.first().title)

        // 2. Add second item
        albumRepository.addMediaToAlbum(albumId, listOf(sampleMedia[1]))
        var crossRefs = albumRepository.crossRefsFlow.first()
        var itemsInAlbum = albumRepository.getMediaForAlbum("user_$albumId", sampleMedia, crossRefs)
        assertEquals(2, itemsInAlbum.size)

        // 3. Rename album
        albumRepository.renameAlbum(albumId, "Swiss & Japan")
        entities = albumRepository.userAlbumsFlow.first()
        assertEquals("Swiss & Japan", entities.first().title)

        // 4. Remove one item from album
        albumRepository.removeMediaFromAlbum(albumId, 101L)
        crossRefs = albumRepository.crossRefsFlow.first()
        itemsInAlbum = albumRepository.getMediaForAlbum("user_$albumId", sampleMedia, crossRefs)
        assertEquals(1, itemsInAlbum.size)
        assertEquals(102L, itemsInAlbum.first().id)

        // 5. Delete album without deleting original media
        albumRepository.deleteAlbumWithoutDeletingMedia(albumId)
        entities = albumRepository.userAlbumsFlow.first()
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `photoEditorEngine renders crop, rotation, and color adjustments to output bitmap`() = runBlocking {
        val src = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(120, 150, 200))
        }

        val adjustments = EditorAdjustments(
            brightness = 0.15f,
            contrast = 1.2f,
            saturation = 1.1f,
            rotationDegrees = 90,
            cropLeft = 0.1f,
            cropTop = 0.1f,
            cropRight = 0.9f,
            cropBottom = 0.9f
        )

        val rendered = PhotoEditorEngine.renderEditedBitmap(src, adjustments)
        // After 90 deg rotation, 200x100 becomes 100x200; 80% crop yields 80x160
        assertEquals(80, rendered.width)
        assertEquals(160, rendered.height)
        assertFalse(rendered.isRecycled)
    }

    @Test
    fun `galleryImageLoader builds memory-cached RGB_565 thumbnail request`() {
        val item = MediaItem(
            id = 555L,
            uri = Uri.parse("content://media/external/images/media/555"),
            displayName = "IMG_555.jpg",
            dateTakenMillis = 1_700_000_000_000L,
            dateModifiedMillis = 1_700_000_000_000L,
            mimeType = "image/jpeg",
            isVideo = false
        )
        val request = com.example.util.GalleryImageLoader.buildGridThumbnailRequest(
            context = context,
            item = item,
            thumbnailPx = 220
        )
        assertEquals(Bitmap.Config.RGB_565, request.bitmapConfig)
        assertEquals(coil.size.Precision.INEXACT, request.precision)
        assertEquals("thumb_555_1700000000000_220", request.memoryCacheKey?.key)
        assertEquals("thumb_555_1700000000000_220", request.diskCacheKey)
    }
}
