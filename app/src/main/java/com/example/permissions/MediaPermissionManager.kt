package com.example.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccessState {
    FULL_ACCESS,
    PARTIAL_ACCESS,
    DENIED
}

object MediaPermissionManager {

    fun requiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun getAccessState(context: Context): MediaAccessState {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val hasImages = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                val hasSelected = isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    hasImages || hasVideos -> MediaAccessState.FULL_ACCESS
                    hasSelected -> MediaAccessState.PARTIAL_ACCESS
                    else -> MediaAccessState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasImages = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                if (hasImages || hasVideos) MediaAccessState.FULL_ACCESS else MediaAccessState.DENIED
            }
            else -> {
                val hasStorage = isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                if (hasStorage) MediaAccessState.FULL_ACCESS else MediaAccessState.DENIED
            }
        }
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
