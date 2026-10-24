package com.example.util

import android.content.Context
import android.graphics.Bitmap
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import com.example.domain.model.MediaItem
import java.io.File

/**
 * Singleton Coil ImageLoader and request builder optimized for high-density photo & video grids.
 *
 * Performance optimizations:
 * 1. Single shared VideoFrameDecoder.Factory() registered on the ImageLoader component registry
 *    rather than allocating a new decoder factory per grid cell.
 * 2. 25% heap MemoryCache and 150MB DiskCache so scrolled thumbnails are served synchronously
 *    from RAM on revisit without re-decoding.
 * 3. RGB_565 bitmap config enabled for opaque grid thumbnails (halves memory footprint per bitmap
 *    from 4 bytes/pixel to 2 bytes/pixel, doubling the number of thumbnails held in MemoryCache).
 * 4. Precision.INEXACT + Scale.FILL so Coil uses fast power-of-two BitmapFactory inSampleSize
 *    subsampling without extra secondary bitmap scaling passes.
 * 5. Crossfade disabled for grid thumbnails to avoid dual-layer alpha blending overdraw during flings.
 */
object GalleryImageLoader {

    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { loader ->
                instance = loader
                Coil.setImageLoader(loader)
            }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "gallery_thumb_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .allowRgb565(true)
            .allowHardware(true)
            .bitmapFactoryMaxParallelism(4)
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    fun buildGridThumbnailRequest(
        context: Context,
        item: MediaItem,
        thumbnailPx: Int = 220
    ): ImageRequest {
        val cacheKey = "thumb_${item.id}_${item.dateModifiedMillis}_$thumbnailPx"
        return ImageRequest.Builder(context)
            .data(item.uri)
            .size(Size(thumbnailPx, thumbnailPx))
            .precision(Precision.INEXACT)
            .scale(Scale.FILL)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .crossfade(false)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
