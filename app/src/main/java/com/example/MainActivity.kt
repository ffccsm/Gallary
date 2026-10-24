package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.MediaItem
import com.example.domain.model.ThemePreference
import com.example.navigation.GalleryTab
import com.example.permissions.MediaPermissionManager
import com.example.ui.GalleryViewModel
import com.example.ui.albums.AlbumsScreen
import com.example.ui.editor.PhotoEditorScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.search.SearchScreen
import com.example.ui.settings.AddToAlbumBottomSheet
import com.example.ui.settings.SettingsBottomSheet
import com.example.ui.theme.GalleryTheme
import com.example.ui.viewer.MediaViewerScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: GalleryViewModel = viewModel(
                factory = GalleryViewModel.Factory(application)
            )
            GalleryAppRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun GalleryAppRoot(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()

    val isDarkTheme = when (preferences.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    // Runtime Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissionsAndLoad()
    }

    // System Delete Confirmation Launcher (Android 11+ MediaStore.createDeleteRequest)
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onSystemDeleteCompleted(result.resultCode == Activity.RESULT_OK)
    }

    // Automatically request MediaStore permissions on first launch if denied
    var hasPromptedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!hasPromptedOnce) {
            hasPromptedOnce = true
            if (MediaPermissionManager.getAccessState(context) == com.example.permissions.MediaAccessState.DENIED) {
                runCatching {
                    permissionLauncher.launch(MediaPermissionManager.requiredPermissions())
                }
            }
        }
    }

    // Auto-dismiss status toast
    LaunchedEffect(uiState.statusToastMessage) {
        if (uiState.statusToastMessage != null) {
            delay(2600L)
            viewModel.clearStatusMessage()
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(GalleryTab.LIBRARY) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var itemsPendingAddToAlbum by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Preserve Library scroll position across viewer/tab transitions
    val libraryGridState = rememberLazyGridState()

    val triggerShare = { items: List<MediaItem> ->
        viewModel.buildShareIntent(items)?.let { intent ->
            context.startActivity(Intent.createChooser(intent, "Share Media"))
        }
    }

    val triggerDelete = { items: List<MediaItem> ->
        viewModel.requestDeleteItems(items) { pendingIntent ->
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            deleteRequestLauncher.launch(request)
        }
    }

    GalleryTheme(darkTheme = isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    if (!uiState.isViewerOpen && uiState.editingItem == null) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 8.dp
                        ) {
                            GalleryTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        currentTab = tab
                                        if (tab != GalleryTab.ALBUMS) {
                                            viewModel.closeActiveAlbum()
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    when (currentTab) {
                        GalleryTab.LIBRARY -> {
                            LibraryScreen(
                                uiState = uiState,
                                preferences = preferences,
                                gridState = libraryGridState,
                                onRequestPermissions = {
                                    permissionLauncher.launch(MediaPermissionManager.requiredPermissions())
                                },
                                onOpenAppSettings = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                },
                                onSeedSampleMedia = { viewModel.seedSampleMediaIntoMediaStore() },
                                onRefresh = { viewModel.refreshLibrary(silent = false) },
                                onGroupingModeSelected = { viewModel.setTimelineGroupingMode(it) },
                                onCycleGridDensity = { viewModel.cycleGridDensity() },
                                onGridDensityChanged = { viewModel.setGridDensity(it) },
                                onToggleSelectionMode = { viewModel.toggleSelectionMode() },
                                onToggleItemSelected = { viewModel.toggleItemSelection(it) },
                                onSelectAll = { viewModel.selectAllInCurrentView(it) },
                                onItemClicked = { list, item -> viewModel.openViewer(list, item) },
                                onShareSelected = { triggerShare(viewModel.getSelectedItems()) },
                                onFavoriteSelected = { viewModel.favoriteSelectedItems() },
                                onAddSelectedToAlbum = {
                                    itemsPendingAddToAlbum = viewModel.getSelectedItems()
                                },
                                onDeleteSelected = { triggerDelete(viewModel.getSelectedItems()) },
                                onOpenSettings = { showSettingsSheet = true }
                            )
                        }
                        GalleryTab.ALBUMS -> {
                            AlbumsScreen(
                                uiState = uiState,
                                preferences = preferences,
                                onOpenAlbum = { viewModel.openAlbum(it) },
                                onCloseActiveAlbum = { viewModel.closeActiveAlbum() },
                                onCreateAlbum = { viewModel.createUserAlbum(it) },
                                onRenameAlbum = { id, title -> viewModel.renameUserAlbum(id, title) },
                                onDeleteAlbum = { id -> viewModel.deleteUserAlbum(id) },
                                onAddItemsToAlbum = { id, items -> viewModel.addItemsToUserAlbum(id, items) },
                                onRemoveItemFromActiveAlbum = { viewModel.removeItemFromActiveUserAlbum(it) },
                                onMediaItemClicked = { list, item -> viewModel.openViewer(list, item) }
                            )
                        }
                        GalleryTab.SEARCH -> {
                            SearchScreen(
                                uiState = uiState,
                                preferences = preferences,
                                onQueryChanged = { viewModel.updateSearchQuery(it) },
                                onCommitSearch = { viewModel.commitRecentSearch(it) },
                                onFilterSelected = { viewModel.setSearchFilter(it) },
                                onMediaItemClicked = { list, item -> viewModel.openViewer(list, item) }
                            )
                        }
                    }
                }
            }

            // Full-Screen Media Viewer Overlay
            AnimatedVisibility(
                visible = uiState.isViewerOpen && uiState.editingItem == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MediaViewerScreen(
                    items = uiState.viewerItems,
                    initialIndex = uiState.viewerIndex,
                    exifDetails = uiState.viewerExifDetails,
                    onPageChanged = { viewModel.onViewerPageChanged(it) },
                    onClose = { viewModel.closeViewer() },
                    onShare = { item -> triggerShare(listOf(item)) },
                    onToggleFavorite = { item -> viewModel.toggleFavorite(item) },
                    onEditPhoto = { item -> viewModel.openPhotoEditor(item) },
                    onAddToAlbum = { item -> itemsPendingAddToAlbum = listOf(item) },
                    onDelete = { item -> triggerDelete(listOf(item)) }
                )
            }

            // Built-in Non-Destructive Photo Editor Overlay
            AnimatedVisibility(
                visible = uiState.editingItem != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                PhotoEditorScreen(
                    bitmap = uiState.editorBitmap,
                    isLoadingBitmap = uiState.isEditorLoadingBitmap,
                    adjustments = uiState.editorAdjustments,
                    canUndo = uiState.canUndoEdit,
                    canRedo = uiState.canRedoEdit,
                    isSaving = uiState.isSavingEdit,
                    onUpdateAdjustments = { adj, record ->
                        viewModel.updateEditorAdjustments(adj, record)
                    },
                    onUndo = { viewModel.undoEditorChange() },
                    onRedo = { viewModel.redoEditorChange() },
                    onResetAll = { viewModel.resetAllEditorAdjustments() },
                    onCancel = { viewModel.closePhotoEditor() },
                    onSaveCopy = { viewModel.saveEditedPhotoCopy() }
                )
            }

            // Floating Status Toast Pill
            AnimatedVisibility(
                visible = uiState.statusToastMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 8.dp,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = uiState.statusToastMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Settings Bottom Sheet
        if (showSettingsSheet) {
            SettingsBottomSheet(
                preferences = preferences,
                accessState = uiState.accessState,
                mediaCount = uiState.allMedia.size,
                onDismiss = { showSettingsSheet = false },
                onThemeChanged = { viewModel.setThemePreference(it) },
                onGridDensityChanged = { viewModel.setGridDensity(it) },
                onRoundedThumbnailsChanged = { viewModel.setRoundedThumbnails(it) },
                onShowVideoDurationChanged = { viewModel.setShowVideoDuration(it) },
                onSeedSamplePhotos = { viewModel.seedSampleMediaIntoMediaStore() }
            )
        }

        // Add to Album Bottom Sheet
        if (itemsPendingAddToAlbum.isNotEmpty()) {
            AddToAlbumBottomSheet(
                userAlbums = uiState.userAlbums,
                itemCount = itemsPendingAddToAlbum.size,
                onDismiss = { itemsPendingAddToAlbum = emptyList() },
                onSelectAlbum = { albumId ->
                    viewModel.addItemsToUserAlbum(albumId, itemsPendingAddToAlbum)
                    itemsPendingAddToAlbum = emptyList()
                },
                onCreateNewAlbumWithItems = { title ->
                    viewModel.createUserAlbum(title, itemsPendingAddToAlbum)
                    itemsPendingAddToAlbum = emptyList()
                }
            )
        }

        // Legacy Delete Confirmation Dialog (API <= 29 fallback)
        if (uiState.pendingLegacyDeleteItems.isNotEmpty()) {
            val count = uiState.pendingLegacyDeleteItems.size
            AlertDialog(
                onDismissRequest = { viewModel.cancelLegacyDelete() },
                title = { Text("Delete $count ${if (count == 1) "Item" else "Items"}?") },
                text = {
                    Text("This item will be permanently deleted from your device storage.")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmLegacyDelete() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelLegacyDelete() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
