package com.example.data.albums

import android.net.Uri
import com.example.data.database.AlbumDao
import com.example.data.database.AlbumMediaCrossRef
import com.example.data.database.FavoriteMediaEntity
import com.example.data.database.UserAlbumEntity
import com.example.domain.model.AlbumSummary
import com.example.domain.model.MediaItem
import com.example.domain.model.SmartAlbumType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlbumRepository(private val albumDao: AlbumDao) {

    val userAlbumsFlow: Flow<List<UserAlbumEntity>> = albumDao.observeUserAlbums()
    val crossRefsFlow: Flow<List<AlbumMediaCrossRef>> = albumDao.observeAllAlbumCrossRefs()
    val favoriteIdsFlow: Flow<Set<Long>> = albumDao.observeFavorites().map { list ->
        list.map { it.mediaId }.toSet()
    }

    fun buildSmartAlbums(allMedia: List<MediaItem>): List<AlbumSummary> {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 3600_000L

        val recents = allMedia.filter { it.effectiveDateMillis >= thirtyDaysAgo }.ifEmpty { allMedia.take(50) }
        val favorites = allMedia.filter { it.isFavorite }
        val videos = allMedia.filter { it.isVideo }
        val screenshots = allMedia.filter { it.isScreenshot }
        val camera = allMedia.filter { it.isCamera }
        val downloads = allMedia.filter { it.isDownload }

        return listOf(
            AlbumSummary(
                id = SmartAlbumType.ALL_PHOTOS.id,
                title = SmartAlbumType.ALL_PHOTOS.title,
                count = allMedia.size,
                coverUri = allMedia.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.ALL_PHOTOS
            ),
            AlbumSummary(
                id = SmartAlbumType.RECENTS.id,
                title = SmartAlbumType.RECENTS.title,
                count = recents.size,
                coverUri = recents.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.RECENTS
            ),
            AlbumSummary(
                id = SmartAlbumType.FAVORITES.id,
                title = SmartAlbumType.FAVORITES.title,
                count = favorites.size,
                coverUri = favorites.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.FAVORITES
            ),
            AlbumSummary(
                id = SmartAlbumType.VIDEOS.id,
                title = SmartAlbumType.VIDEOS.title,
                count = videos.size,
                coverUri = videos.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.VIDEOS
            ),
            AlbumSummary(
                id = SmartAlbumType.SCREENSHOTS.id,
                title = SmartAlbumType.SCREENSHOTS.title,
                count = screenshots.size,
                coverUri = screenshots.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.SCREENSHOTS
            ),
            AlbumSummary(
                id = SmartAlbumType.CAMERA.id,
                title = SmartAlbumType.CAMERA.title,
                count = camera.size,
                coverUri = camera.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.CAMERA
            ),
            AlbumSummary(
                id = SmartAlbumType.DOWNLOADS.id,
                title = SmartAlbumType.DOWNLOADS.title,
                count = downloads.size,
                coverUri = downloads.firstOrNull()?.uri,
                isUserCreated = false,
                smartType = SmartAlbumType.DOWNLOADS
            )
        )
    }

    fun buildUserAlbums(
        entities: List<UserAlbumEntity>,
        crossRefs: List<AlbumMediaCrossRef>,
        allMedia: List<MediaItem>
    ): List<AlbumSummary> {
        val mediaById = allMedia.associateBy { it.id }
        val refsByAlbum = crossRefs.groupBy { it.albumId }

        return entities.map { entity ->
            val albumRefs = refsByAlbum[entity.id].orEmpty()
            val validMediaItems = albumRefs.mapNotNull { mediaById[it.mediaId] }
            val coverUri = validMediaItems.firstOrNull()?.uri
                ?: entity.customCoverUri?.let { Uri.parse(it) }

            AlbumSummary(
                id = "user_${entity.id}",
                title = entity.title,
                count = validMediaItems.size,
                coverUri = coverUri,
                isUserCreated = true,
                userAlbumId = entity.id,
                smartType = null
            )
        }
    }

    fun getMediaForAlbum(
        albumId: String,
        allMedia: List<MediaItem>,
        crossRefs: List<AlbumMediaCrossRef>
    ): List<MediaItem> {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 3600_000L

        return when (albumId) {
            SmartAlbumType.ALL_PHOTOS.id -> allMedia
            SmartAlbumType.RECENTS.id -> allMedia.filter { it.effectiveDateMillis >= thirtyDaysAgo }.ifEmpty { allMedia.take(50) }
            SmartAlbumType.FAVORITES.id -> allMedia.filter { it.isFavorite }
            SmartAlbumType.VIDEOS.id -> allMedia.filter { it.isVideo }
            SmartAlbumType.SCREENSHOTS.id -> allMedia.filter { it.isScreenshot }
            SmartAlbumType.CAMERA.id -> allMedia.filter { it.isCamera }
            SmartAlbumType.DOWNLOADS.id -> allMedia.filter { it.isDownload }
            else -> {
                val userAlbumId = albumId.removePrefix("user_").toLongOrNull() ?: return emptyList()
                val mediaById = allMedia.associateBy { it.id }
                crossRefs
                    .filter { it.albumId == userAlbumId }
                    .mapNotNull { mediaById[it.mediaId] }
            }
        }
    }

    suspend fun createAlbum(title: String, initialItems: List<MediaItem> = emptyList()): Long {
        val trimmed = title.trim().ifEmpty { "Untitled Album" }
        val albumId = albumDao.insertAlbum(
            UserAlbumEntity(
                title = trimmed,
                customCoverUri = initialItems.firstOrNull()?.uri?.toString()
            )
        )
        if (initialItems.isNotEmpty()) {
            addMediaToAlbum(albumId, initialItems)
        }
        return albumId
    }

    suspend fun renameAlbum(albumId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            albumDao.renameAlbum(albumId, trimmed)
        }
    }

    suspend fun deleteAlbumWithoutDeletingMedia(albumId: Long) {
        albumDao.deleteAllCrossRefsForAlbum(albumId)
        albumDao.deleteAlbumById(albumId)
    }

    suspend fun addMediaToAlbum(albumId: Long, items: List<MediaItem>) {
        val now = System.currentTimeMillis()
        val refs = items.mapIndexed { index, item ->
            AlbumMediaCrossRef(
                albumId = albumId,
                mediaId = item.id,
                mediaUriString = item.uri.toString(),
                addedAtMillis = now + index
            )
        }
        albumDao.insertAlbumMediaCrossRefs(refs)
    }

    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long) {
        albumDao.removeMediaFromAlbum(albumId, mediaId)
    }

    suspend fun toggleFavorite(item: MediaItem): Boolean {
        val currentlyFav = albumDao.isFavorite(item.id) || item.isFavorite
        return if (currentlyFav) {
            albumDao.removeFavorite(item.id)
            false
        } else {
            albumDao.insertFavorite(
                FavoriteMediaEntity(
                    mediaId = item.id,
                    mediaUriString = item.uri.toString()
                )
            )
            true
        }
    }

    suspend fun setFavoriteForItems(items: List<MediaItem>, favorite: Boolean) {
        for (item in items) {
            if (favorite) {
                albumDao.insertFavorite(
                    FavoriteMediaEntity(
                        mediaId = item.id,
                        mediaUriString = item.uri.toString()
                    )
                )
            } else {
                albumDao.removeFavorite(item.id)
            }
        }
    }
}
