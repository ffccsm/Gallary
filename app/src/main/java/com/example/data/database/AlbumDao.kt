package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM user_albums ORDER BY updatedAtMillis DESC")
    fun observeUserAlbums(): Flow<List<UserAlbumEntity>>

    @Query("SELECT * FROM album_media_cross_ref ORDER BY addedAtMillis DESC")
    fun observeAllAlbumCrossRefs(): Flow<List<AlbumMediaCrossRef>>

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAtMillis DESC")
    fun observeCrossRefsForAlbum(albumId: Long): Flow<List<AlbumMediaCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: UserAlbumEntity): Long

    @Query("UPDATE user_albums SET title = :newTitle, updatedAtMillis = :updatedAt WHERE id = :albumId")
    suspend fun renameAlbum(albumId: Long, newTitle: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_albums WHERE id = :albumId")
    suspend fun deleteAlbumById(albumId: Long)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId")
    suspend fun deleteAllCrossRefsForAlbum(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumMediaCrossRefs(refs: List<AlbumMediaCrossRef>)

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long)

    @Query("DELETE FROM album_media_cross_ref WHERE mediaId IN (:mediaIds)")
    suspend fun removeMediaFromAllAlbums(mediaIds: List<Long>)

    @Query("SELECT * FROM favorite_media ORDER BY favoritedAtMillis DESC")
    fun observeFavorites(): Flow<List<FavoriteMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteMediaEntity)

    @Query("DELETE FROM favorite_media WHERE mediaId = :mediaId")
    suspend fun removeFavorite(mediaId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_media WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: Long): Boolean
}
