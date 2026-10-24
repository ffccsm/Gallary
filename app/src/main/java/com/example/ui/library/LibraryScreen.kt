package com.example.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.GridDensity
import com.example.domain.model.MediaItem
import com.example.domain.model.TimelineGroupingMode
import com.example.permissions.MediaAccessState
import com.example.ui.GalleryUiState
import com.example.ui.components.MediaThumbnailGridItem

@Composable
fun LibraryScreen(
    uiState: GalleryUiState,
    preferences: UserPreferencesState,
    gridState: LazyGridState,
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSeedSampleMedia: () -> Unit,
    onRefresh: () -> Unit,
    onGroupingModeSelected: (TimelineGroupingMode) -> Unit,
    onCycleGridDensity: () -> Unit,
    onGridDensityChanged: (GridDensity) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleItemSelected: (Long) -> Unit,
    onSelectAll: (List<MediaItem>) -> Unit,
    onItemClicked: (List<MediaItem>, MediaItem) -> Unit,
    onShareSelected: () -> Unit,
    onFavoriteSelected: () -> Unit,
    onAddSelectedToAlbum: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentDensity by rememberUpdatedState(preferences.gridDensity)
    val currentOnDensityChanged by rememberUpdatedState(onGridDensityChanged)

    // When returning from the viewer, preserve the exact scroll position if the viewed item is
    // already on screen; only scroll if the user swiped far enough in the viewer that the item
    // moved outside the current visible viewport.
    LaunchedEffect(uiState.isViewerOpen, uiState.lastViewedMediaId) {
        val targetId = uiState.lastViewedMediaId
        if (!uiState.isViewerOpen && targetId != null) {
            val isAlreadyVisible = gridState.layoutInfo.visibleItemsInfo.any { it.key == targetId }
            if (!isAlreadyVisible) {
                var flatIndex = 0
                var foundIndex = -1
                for (section in uiState.timelineSections) {
                    flatIndex++ // section header slot
                    val idxInSection = section.items.indexOfFirst { it.id == targetId }
                    if (idxInSection >= 0) {
                        foundIndex = flatIndex + idxInSection
                        break
                    }
                    flatIndex += section.items.size
                }
                if (foundIndex >= 0) {
                    gridState.scrollToItem(foundIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                // Multi-touch-only pinch detector: NEVER consumes 1-finger vertical scroll events!
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var cumulativeScale = 1f
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                cumulativeScale *= zoomChange
                                if (cumulativeScale > 1.35f) {
                                    currentOnDensityChanged(currentDensity.zoomIn())
                                    cumulativeScale = 1f
                                } else if (cumulativeScale < 0.72f) {
                                    currentOnDensityChanged(currentDensity.zoomOut())
                                    cumulativeScale = 1f
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            cumulativeScale = 1f
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top iOS-inspired Library Header
            LibraryTopBar(
                totalCount = uiState.allMedia.size,
                isSelectionMode = uiState.isSelectionMode,
                selectedCount = uiState.selectedMediaIds.size,
                gridDensity = preferences.gridDensity,
                onCycleGridDensity = onCycleGridDensity,
                onToggleSelectionMode = onToggleSelectionMode,
                onSelectAll = { onSelectAll(uiState.allMedia) },
                onOpenSettings = onOpenSettings
            )

            // Android 14+ Partial Photos Access Banner
            if (uiState.accessState == MediaAccessState.PARTIAL_ACCESS) {
                PartialAccessBanner(
                    onManageSelectedPhotos = onRequestPermissions
                )
            }

            // Main Content Area
            when {
                uiState.accessState == MediaAccessState.DENIED -> {
                    PermissionRequiredView(
                        onGrantPermission = onRequestPermissions,
                        onOpenSettings = onOpenAppSettings
                    )
                }
                uiState.isLoading || uiState.isSeedingSampleMedia -> {
                    LoadingLibraryView(
                        isSeeding = uiState.isSeedingSampleMedia
                    )
                }
                uiState.errorMessage != null -> {
                    ErrorLibraryView(
                        message = uiState.errorMessage,
                        onRetry = onRefresh
                    )
                }
                uiState.allMedia.isEmpty() -> {
                    EmptyLibraryView(
                        onSeedSampleMedia = onSeedSampleMedia,
                        onRefresh = onRefresh
                    )
                }
                else -> {
                    val columns = preferences.gridDensity.columns
                    val thumbnailPx = preferences.gridDensity.thumbnailPx
                    val spacing = if (preferences.roundedThumbnails) 3.dp else 1.5.dp
                    val isSelectionMode = uiState.isSelectionMode
                    val selectedIds = uiState.selectedMediaIds
                    val roundedCorners = preferences.roundedThumbnails
                    val showVideoDuration = preferences.showVideoDuration
                    val allMediaSnapshot = uiState.allMedia

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 4.dp,
                            bottom = 148.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_media_grid")
                    ) {
                        uiState.timelineSections.forEach { section ->
                            item(
                                key = "header_${section.key}",
                                contentType = "section_header",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                SectionHeaderRow(
                                    title = section.title,
                                    subtitle = section.subtitle,
                                    count = section.items.size
                                )
                            }

                            items(
                                items = section.items,
                                key = { media -> media.id },
                                contentType = { "media_tile" }
                            ) { item ->
                                val isSelected = isSelectionMode && selectedIds.contains(item.id)
                                MediaThumbnailGridItem(
                                    item = item,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    roundedCorners = roundedCorners,
                                    showVideoDuration = showVideoDuration,
                                    thumbnailPx = thumbnailPx,
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleItemSelected(item.id)
                                        } else {
                                            onItemClicked(allMediaSnapshot, item)
                                        }
                                    },
                                    onLongClick = {
                                        onToggleItemSelected(item.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating Segmented Control (Years / Months / Days / All Photos)
        AnimatedVisibility(
            visible = uiState.accessState != MediaAccessState.DENIED &&
                uiState.allMedia.isNotEmpty() &&
                !uiState.isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            TimelineSegmentedPill(
                selectedMode = uiState.timelineGroupingMode,
                onModeSelected = onGroupingModeSelected
            )
        }

        // Selection Multi-Action Bar when in Selection Mode
        AnimatedVisibility(
            visible = uiState.isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SelectionActionToolbar(
                selectedCount = uiState.selectedMediaIds.size,
                onShare = onShareSelected,
                onFavorite = onFavoriteSelected,
                onAddToAlbum = onAddSelectedToAlbum,
                onDelete = onDeleteSelected
            )
        }
    }
}

@Composable
private fun LibraryTopBar(
    totalCount: Int,
    isSelectionMode: Boolean,
    selectedCount: Int,
    gridDensity: GridDensity,
    onCycleGridDensity: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column {
            Text(
                text = if (isSelectionMode) {
                    if (selectedCount == 0) "Select Items" else "$selectedCount Selected"
                } else {
                    "Library"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (totalCount > 0 && !isSelectionMode) {
                Text(
                    text = "$totalCount ${if (totalCount == 1) "Item" else "Items"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSelectionMode && totalCount > 0) {
                TextButton(
                    onClick = onSelectAll,
                    modifier = Modifier.testTag("select_all_button")
                ) {
                    Text("Select All", fontWeight = FontWeight.SemiBold)
                }
            } else if (totalCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onCycleGridDensity)
                        .testTag("grid_density_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GridView,
                            contentDescription = "Change Grid Size",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${gridDensity.columns}×",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (totalCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelectionMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onToggleSelectionMode)
                        .testTag("toggle_selection_button")
                ) {
                    Text(
                        text = if (isSelectionMode) "Cancel" else "Select",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelectionMode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtitle: String?,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimelineSegmentedPill(
    selectedMode: TimelineGroupingMode,
    onModeSelected: (TimelineGroupingMode) -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.testTag("timeline_segmented_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            TimelineGroupingMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("segment_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActionToolbar(
    selectedCount: Int,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDelete: () -> Unit
) {
    val enabled = selectedCount > 0
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 8.dp)
        ) {
            IconButton(
                onClick = onShare,
                enabled = enabled,
                modifier = Modifier.testTag("selection_share_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share Selected",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(
                onClick = onFavorite,
                enabled = enabled,
                modifier = Modifier.testTag("selection_favorite_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite Selected",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(
                onClick = onAddToAlbum,
                enabled = enabled,
                modifier = Modifier.testTag("selection_add_to_album_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CreateNewFolder,
                    contentDescription = "Add Selected to Album",
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.testTag("selection_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete Selected",
                    tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun PartialAccessBanner(
    onManageSelectedPhotos: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Showing selected photos only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onManageSelectedPhotos) {
                Text("Manage", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PermissionRequiredView(
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Access Your Photo Library",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Gallery reads your local photos and videos directly from Android MediaStore so you can browse by date, organize albums, search metadata, and edit photos offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onGrantPermission,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("grant_permission_button")
        ) {
            Text("Allow Access to Photos & Videos", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("open_system_settings_button")
        ) {
            Text("Open System App Settings")
        }
    }
}

@Composable
private fun LoadingLibraryView(isSeeding: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isSeeding) "Writing high-res sample photos to MediaStore…" else "Scanning device MediaStore…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorLibraryView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = "Unable to Load Library",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyLibraryView(
    onSeedSampleMedia: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No Photos or Videos Found",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Android MediaStore on this device currently has no indexed photos or videos. You can take photos with the device camera or populate real sample JPEGs into MediaStore to test the timeline, albums, search, and photo editor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSeedSampleMedia,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("seed_sample_media_button")
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Populate Device MediaStore (8 Photos)", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onRefresh,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("refresh_empty_library_button")
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rescan MediaStore")
        }
    }
}
