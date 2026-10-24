package com.example.ui.viewer

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer. pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.domain.model.MediaItem
import com.example.ui.theme.IosHeartRed
import com.example.util.DateGroupingUtil
import com.example.util.GalleryImageLoader
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    exifDetails: Map<String, String>,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit,
    onShare: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onEditPhoto: (MediaItem) -> Unit,
    onAddToAlbum: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onClose() }

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val safeInitialPage = initialIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { items.size }
    )

    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var currentPageZoomed by remember { mutableStateOf(false) }

    // Upward swipe-to-dismiss interactive animation state
    val scope = rememberCoroutineScope()
    val dismissOffsetY = remember { Animatable(0f) }
    val dismissOffsetX = remember { Animatable(0f) }
    val dismissScale = remember { Animatable(1f) }
    var isVerticalDismissDragging by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val dismissDistanceThresholdPx = remember(density) { with(density) { 105.dp.toPx() } }
    val maxDragReferencePx = remember(density) { with(density) { 380.dp.toPx() } }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentPageZoomed = false
            onPageChanged(page)
        }
    }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    // Progress of upward dismissal (0f = resting full-screen, 1f = fully dismissed toward thumbnail)
    val dismissProgress = remember(dismissOffsetY.value, maxDragReferencePx) {
        if (dismissOffsetY.value >= 0f) 0f
        else (abs(dismissOffsetY.value) / maxDragReferencePx).coerceIn(0f, 1f)
    }

    val backdropAlpha = (1f - dismissProgress * 0.88f).coerceIn(0f, 1f)
    val baseBackgroundColor = if (showControls) MaterialTheme.colorScheme.background else Color.Black

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBackgroundColor.copy(alpha = backdropAlpha))
            .testTag("media_viewer_screen")
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentPageZoomed && !isVerticalDismissDragging,
            beyondViewportPageCount = 1,
            key = { page -> items[page].id },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            val isActivePage = page == pagerState.currentPage

            val pageModifier = if (isActivePage) {
                Modifier.graphicsLayer {
                    translationX = dismissOffsetX.value
                    translationY = dismissOffsetY.value
                    scaleX = dismissScale.value
                    scaleY = dismissScale.value
                    shape = RoundedCornerShape((dismissProgress * 22f).dp)
                    clip = dismissProgress > 0.02f
                }
            } else {
                Modifier
            }

            if (item.isVideo) {
                NativeVideoPlayerPage(
                    uri = item.uri,
                    isActivePage = isActivePage,
                    showOverlayControls = showControls && !isVerticalDismissDragging,
                    onTapBackground = { showControls = !showControls },
                    modifier = pageModifier
                )
            } else {
                ZoomablePhotoPage(
                    item = item,
                    isActivePage = isActivePage,
                    onTap = { showControls = !showControls },
                    onZoomChanged = { isZoomed ->
                        if (isActivePage) {
                            currentPageZoomed = isZoomed
                        }
                    },
                    onUpwardDismissDragStart = {
                        isVerticalDismissDragging = true
                    },
                    onUpwardDismissDrag = { dx, dy ->
                        scope.launch {
                            val nextY = (dismissOffsetY.value + dy).coerceAtMost(0f)
                            val nextX = dismissOffsetX.value + dx * 0.35f
                            val prog = (abs(nextY) / maxDragReferencePx).coerceIn(0f, 1f)
                            val targetScale = (1f - prog * 0.38f).coerceIn(0.58f, 1f)
                            dismissOffsetY.snapTo(nextY)
                            dismissOffsetX.snapTo(nextX)
                            dismissScale.snapTo(targetScale)
                        }
                    },
                    onUpwardDismissDragEnd = { velocityY, wasCancelled ->
                        val currentY = dismissOffsetY.value
                        val passedDistance = currentY < -dismissDistanceThresholdPx
                        val passedVelocity = velocityY < -850f && currentY < -(dismissDistanceThresholdPx * 0.3f)

                        if (!wasCancelled && (passedDistance || passedVelocity)) {
                            // Animate photo upward and shrink toward thumbnail before closing viewer
                            scope.launch {
                                coroutineScope {
                                    launch {
                                        dismissOffsetY.animateTo(
                                            targetValue = -maxDragReferencePx * 1.15f,
                                            animationSpec = tween(durationMillis = 165)
                                        )
                                    }
                                    launch {
                                        dismissScale.animateTo(
                                            targetValue = 0.42f,
                                            animationSpec = tween(durationMillis = 165)
                                        )
                                    }
                                }
                                isVerticalDismissDragging = false
                                onClose()
                            }
                        } else {
                            // Gesture cancelled or below threshold -> natural spring return
                            scope.launch {
                                val springSpec = spring<Float>(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                                coroutineScope {
                                    launch { dismissOffsetY.animateTo(0f, springSpec) }
                                    launch { dismissOffsetX.animateTo(0f, springSpec) }
                                    launch { dismissScale.animateTo(1f, springSpec) }
                                }
                                isVerticalDismissDragging = false
                            }
                        }
                    },
                    modifier = pageModifier
                )
            }
        }

        // Top Bar (Back, Capture Date/Time, Location, Info button)
        AnimatedVisibility(
            visible = showControls && !isVerticalDismissDragging && dismissProgress < 0.05f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = DateGroupingUtil.formatFullDateTime(currentItem.effectiveDateMillis),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentItem.locationLabel
                                ?: currentItem.bucketDisplayName.ifBlank { currentItem.displayName },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { showInfoSheet = true },
                        modifier = Modifier.testTag("viewer_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Media Details"
                        )
                    }
                }
            }
        }

        // Bottom Action Toolbar (Share, Favorite, Edit, Add to Album, Delete)
        AnimatedVisibility(
            visible = showControls && !isVerticalDismissDragging && dismissProgress < 0.05f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    IconButton(
                        onClick = { onShare(currentItem) },
                        modifier = Modifier.testTag("viewer_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { onToggleFavorite(currentItem) },
                        modifier = Modifier.testTag("viewer_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (currentItem.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentItem.isFavorite) IosHeartRed else MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!currentItem.isVideo) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("viewer_edit_button")
                        ) {
                            IconButton(onClick = { onEditPhoto(currentItem) }) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Edit Photo",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { onAddToAlbum(currentItem) },
                        modifier = Modifier.testTag("viewer_add_to_album_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = "Add to Album",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { onDelete(currentItem) },
                        modifier = Modifier.testTag("viewer_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Metadata Info Sheet
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Info",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = DateGroupingUtil.formatFullDateTime(currentItem.effectiveDateMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MetadataRow("Size", DateGroupingUtil.formatFileSize(currentItem.sizeBytes))
                        exifDetails.entries.forEach { (key, value) ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                            MetadataRow(key, value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ZoomablePhotoPage(
    item: MediaItem,
    isActivePage: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onUpwardDismissDragStart: () -> Unit,
    onUpwardDismissDrag: (dx: Float, dy: Float) -> Unit,
    onUpwardDismissDragEnd: (velocityY: Float, wasCancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { GalleryImageLoader.get(context) }
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isActivePage) {
        if (!isActivePage) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    val imageRequest = remember(item.id, item.dateModifiedMillis) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(Size(1920, 1920))
            .crossfade(false)
            .build()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            val targetScale = 2.5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val dx = (centerX - tapOffset.x) * (targetScale - 1f) * 0.5f
                            val dy = (centerY - tapOffset.y) * (targetScale - 1f) * 0.5f
                            scale = targetScale
                            offset = Offset(dx, dy)
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(item.id, isActivePage) {
                // Handles:
                // 1. Multi-finger pinch zoom at any time
                // 2. Single-finger pan when zoomed in (scale > 1.05f)
                // 3. Single-finger upward swipe-to-exit when unzoomed (scale <= 1.05f),
                //    yielding horizontal swipes cleanly to HorizontalPager!
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var totalDx = 0f
                    var totalDy = 0f
                    var gestureMode = 0 // 0 = undecided, 1 = upward-dismiss, 2 = horizontal-pager-ignore

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }

                        if (pressedCount >= 2 || scale > 1.05f) {
                            // If we had started an upward dismiss and a second finger touched down, cancel dismiss
                            if (gestureMode == 1) {
                                gestureMode = 0
                                onUpwardDismissDragEnd(0f, true)
                            }
                            break
                        }

                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (gestureMode == 0) {
                                val distX = abs(totalDx)
                                val distY = abs(totalDy)
                                if (distX > touchSlop || distY > touchSlop) {
                                    if (totalDy < -touchSlop && distY > distX * 1.2f) {
                                        // Locked into upward swipe-to-exit gesture
                                        gestureMode = 1
                                        onUpwardDismissDragStart()
                                        onUpwardDismissDrag(delta.x, delta.y)
                                        change.consume()
                                    } else {
                                        // Horizontal swipe or downward motion -> yield to HorizontalPager
                                        gestureMode = 2
                                    }
                                }
                            } else if (gestureMode == 1) {
                                onUpwardDismissDrag(delta.x, delta.y)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (gestureMode == 1) {
                        val velocityY = velocityTracker.calculateVelocity().y
                        onUpwardDismissDragEnd(velocityY, false)
                    }
                }
            }
            .then(
                if (scale > 1.02f) {
                    Modifier.pointerInput(item.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                            val maxX = ((newScale - 1f) * containerSize.width) / 2f
                            val maxY = ((newScale - 1f) * containerSize.height) / 2f
                            val newOffset = if (newScale > 1.02f) {
                                Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                Offset.Zero
                            }
                            scale = newScale
                            offset = newOffset
                            onZoomChanged(newScale > 1.05f)
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun NativeVideoPlayerPage(
    uri: Uri,
    isActivePage: Boolean,
    showOverlayControls: Boolean,
    onTapBackground: () -> Unit,
    modifier: Modifier = Modifier
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(isActivePage, videoViewRef) {
        val view = videoViewRef ?: return@LaunchedEffect
        if (isActivePage) {
            view.start()
            isPlaying = true
        } else {
            if (view.isPlaying) view.pause()
            isPlaying = false
        }
    }

    LaunchedEffect(isPlaying, videoViewRef) {
        while (isPlaying) {
            val view = videoViewRef
            if (view != null && view.isPlaying) {
                positionMs = view.currentPosition
            }
            delay(200L)
        }
    }

    DisposableEffect(uri) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(onTap = { onTapBackground() })
            }
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        mp.isLooping = true
                        durationMs = mp.duration.coerceAtLeast(1)
                        val vol = if (isMuted) 0f else 1f
                        mp.setVolume(vol, vol)
                        if (isActivePage) {
                            start()
                            isPlaying = true
                        }
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = showOverlayControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = {
                            val view = videoViewRef ?: return@IconButton
                            if (view.isPlaying) {
                                view.pause()
                                isPlaying = false
                            } else {
                                view.start()
                                isPlaying = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    Slider(
                        value = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            val target = (fraction * durationMs).toInt()
                            positionMs = target
                            videoViewRef?.seekTo(target)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatVideoTime(positionMs) + " / " + formatVideoTime(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            val vol = if (isMuted) 0f else 1f
                            runCatching { mediaPlayerRef?.setVolume(vol, vol) }
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatVideoTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%d:%02d", min, sec)
}
