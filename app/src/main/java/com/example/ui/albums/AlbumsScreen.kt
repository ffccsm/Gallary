package com.example.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.AlbumSummary
import com.example.domain.model.MediaItem
import com.example.domain.model.SmartAlbumType
import com.example.ui.GalleryUiState
import com.example.ui.components.MediaThumbnailGridItem
import com.example.ui.theme.IosHeartRed

@Composable
fun AlbumsScreen(
    uiState: GalleryUiState,
    preferences: UserPreferencesState,
    onOpenAlbum: (String) -> Unit,
    onCloseActiveAlbum: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (Long, String) -> Unit,
    onDeleteAlbum: (Long) -> Unit,
    onAddItemsToAlbum: (Long, List<MediaItem>) -> Unit,
    onRemoveItemFromActiveAlbum: (MediaItem) -> Unit,
    onMediaItemClicked: (List<MediaItem>, MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AlbumSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumSummary?>(null) }

    val activeAlbum = uiState.activeAlbum
    if (activeAlbum != null) {
        BackHandler { onCloseActiveAlbum() }
        AlbumDetailView(
            album = activeAlbum,
            items = uiState.activeAlbumItems,
            allMedia = uiState.allMedia,
            preferences = preferences,
            onBack = onCloseActiveAlbum,
            onRename = { renameTarget = activeAlbum },
            onDelete = { deleteTarget = activeAlbum },
            onAddMedia = { selected ->
                activeAlbum.userAlbumId?.let { id ->
                    onAddItemsToAlbum(id, selected)
                }
            },
            onRemoveMedia = onRemoveItemFromActiveAlbum,
            onItemClicked = { item ->
                onMediaItemClicked(uiState.activeAlbumItems, item)
            },
            modifier = modifier
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showCreateDialog = true }
                        .testTag("create_album_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Album",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Album",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 110.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("albums_list")
            ) {
                // Featured Smart Albums Grid (All Photos, Recents, Favorites, Camera)
                item {
                    SectionTitleRow(title = "Collections")
                    val featured = uiState.smartAlbums.take(4)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        featured.chunked(2).forEach { rowAlbums ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowAlbums.forEach { album ->
                                    AlbumGridCard(
                                        album = album,
                                        onClick = { onOpenAlbum(album.id) },
                                        onRename = null,
                                        onDelete = null,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowAlbums.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // User-Created Albums Section
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "My Albums",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${uiState.userAlbums.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.userAlbums.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clickable { showCreateDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Create Your First Album",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Organize photos into custom collections stored in Room.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            uiState.userAlbums.chunked(2).forEach { rowAlbums ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowAlbums.forEach { album ->
                                        AlbumGridCard(
                                            album = album,
                                            onClick = { onOpenAlbum(album.id) },
                                            onRename = { renameTarget = album },
                                            onDelete = { deleteTarget = album },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowAlbums.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Media Types iOS-style List
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitleRow(title = "Media Types & Categories")
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column {
                            uiState.smartAlbums.forEachIndexed { index, smartAlbum ->
                                SmartCategoryRow(
                                    album = smartAlbum,
                                    onClick = { onOpenAlbum(smartAlbum.id) }
                                )
                                if (index < uiState.smartAlbums.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 52.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Album Dialog
    if (showCreateDialog) {
        AlbumNameDialog(
            title = "New Album",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateAlbum(name)
            }
        )
    }

    // Rename Album Dialog
    renameTarget?.let { target ->
        AlbumNameDialog(
            title = "Rename Album",
            initialValue = target.title,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                val userAlbumId = target.userAlbumId
                renameTarget = null
                if (userAlbumId != null) {
                    onRenameAlbum(userAlbumId, newName)
                }
            }
        )
    }

    // Delete Album Confirmation Dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.title}\"?") },
            text = {
                Text("This removes the album collection. The original photos and videos in your library will not be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.userAlbumId
                        deleteTarget = null
                        if (id != null) onDeleteAlbum(id)
                    }
                ) {
                    Text("Delete Album", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionTitleRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AlbumGridCard(
    album: AlbumSummary,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("album_card_${album.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (album.coverUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(album.coverUri)
                        .size(Size(420, 420))
                        .crossfade(true)
                        .build(),
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
            }

            if (album.smartType == SmartAlbumType.FAVORITES) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = IosHeartRed,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(20.dp)
                )
            }

            if (album.isUserCreated && (onRename != null || onDelete != null)) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Album Options",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        onRename?.let {
                            DropdownMenuItem(
                                text = { Text("Rename Album") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    it()
                                }
                            )
                        }
                        onDelete?.let {
                            DropdownMenuItem(
                                text = { Text("Delete Album") },
                                leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    it()
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${album.count}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmartCategoryRow(
    album: AlbumSummary,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (album.smartType) {
        SmartAlbumType.ALL_PHOTOS -> Icons.Filled.PhotoLibrary
        SmartAlbumType.RECENTS -> Icons.Filled.History
        SmartAlbumType.FAVORITES -> Icons.Filled.Favorite
        SmartAlbumType.VIDEOS -> Icons.Filled.Videocam
        SmartAlbumType.SCREENSHOTS -> Icons.Filled.PhoneAndroid
        SmartAlbumType.CAMERA -> Icons.Filled.CameraAlt
        SmartAlbumType.DOWNLOADS -> Icons.Filled.Download
        null -> Icons.Filled.Collections
    }

    val iconTint = if (album.smartType == SmartAlbumType.FAVORITES) {
        IosHeartRed
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("smart_album_row_${album.id}")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${album.count}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailView(
    album: AlbumSummary,
    items: List<MediaItem>,
    allMedia: List<MediaItem>,
    preferences: UserPreferencesState,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddMedia: (List<MediaItem>) -> Unit,
    onRemoveMedia: (MediaItem) -> Unit,
    onItemClicked: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMediaPicker by remember { mutableStateOf(false) }
    var isRemoveMode by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("album_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Albums"
                    )
                }
                Column {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${items.size} ${if (items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (album.isUserCreated) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showAddMediaPicker = true },
                        modifier = Modifier.testTag("album_add_photos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Photos to Album",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { isRemoveMode = !isRemoveMode }) {
                            Text(if (isRemoveMode) "Done" else "Edit")
                        }
                    }
                    IconButton(onClick = onRename) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Rename Album"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete Album",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Photos in \"${album.title}\"",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (album.isUserCreated) {
                        Button(onClick = { showAddMediaPicker = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Photos from Library")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(preferences.gridDensity.columns),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = items,
                    key = { it.id },
                    contentType = { "media_tile" }
                ) { item ->
                    Box {
                        MediaThumbnailGridItem(
                            item = item,
                            isSelectionMode = false,
                            isSelected = false,
                            roundedCorners = preferences.roundedThumbnails,
                            showVideoDuration = preferences.showVideoDuration,
                            thumbnailPx = preferences.gridDensity.thumbnailPx,
                            onClick = {
                                if (isRemoveMode && album.isUserCreated) {
                                    onRemoveMedia(item)
                                } else {
                                    onItemClicked(item)
                                }
                            },
                            onLongClick = {
                                if (album.isUserCreated) {
                                    isRemoveMode = !isRemoveMode
                                }
                            }
                        )
                        if (isRemoveMode && album.isUserCreated) {
                            IconButton(
                                onClick = { onRemoveMedia(item) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(28.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RemoveCircleOutline,
                                    contentDescription = "Remove from Album",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMediaPicker) {
        val existingIds = remember(items) { items.map { it.id }.toSet() }
        val candidates = remember(allMedia, existingIds) {
            allMedia.filterNot { existingIds.contains(it.id) }
        }
        val selectedToAdd = remember { mutableStateListOf<MediaItem>() }

        ModalBottomSheet(
            onDismissRequest = { showAddMediaPicker = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { showAddMediaPicker = false }) {
                        Text("Cancel")
                    }
                    Text(
                        text = "Add to ${album.title}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        enabled = selectedToAdd.isNotEmpty(),
                        onClick = {
                            onAddMedia(selectedToAdd.toList())
                            showAddMediaPicker = false
                        }
                    ) {
                        Text("Add (${selectedToAdd.size})", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (candidates.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("All library items are already in this album.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(candidates, key = { it.id }) { candidate ->
                            val isChosen = selectedToAdd.any { it.id == candidate.id }
                            MediaThumbnailGridItem(
                                item = candidate,
                                isSelectionMode = true,
                                isSelected = isChosen,
                                roundedCorners = true,
                                showVideoDuration = true,
                                onClick = {
                                    if (isChosen) {
                                        selectedToAdd.removeAll { it.id == candidate.id }
                                    } else {
                                        selectedToAdd.add(candidate)
                                    }
                                },
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Album Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("album_name_input")
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.trim().isNotEmpty(),
                onClick = { onConfirm(text.trim()) },
                modifier = Modifier.testTag("album_dialog_confirm_button")
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
