package com.example.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_albums",
    indices = [Index(value = ["title"])]
)
data class UserAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val customCoverUri: String? = null
)

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaId"],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["mediaId"])
    ]
)
data class AlbumMediaCrossRef(
    val albumId: Long,
    val mediaId: Long,
    val mediaUriString: String,
    val addedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_media")
data class FavoriteMediaEntity(
    @PrimaryKey val mediaId: Long,
    val mediaUriString: String,
    val favoritedAtMillis: Long = System.currentTimeMillis()
)
