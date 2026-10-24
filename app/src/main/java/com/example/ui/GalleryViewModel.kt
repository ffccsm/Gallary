package com.example.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.albums.AlbumRepository
import com.example.data.database.AlbumMediaCrossRef
import com.example.data.database.GalleryDatabase
import com.example.data.database.UserAlbumEntity
import com.example.data.media.MediaStoreRepository
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.AlbumSummary
import com.example.domain.model.EditorAdjustments
import com.example.domain.model.GridDensity
import com.example.domain.model.MediaItem
import com.example.domain.model.SearchMediaFilter
import com.example.domain.model.ThemePreference
import com.example.domain.model.TimelineGroupingMode
import com.example.domain.model.TimelineSection
import com.example.permissions.MediaAccessState
import com.example.permissions.MediaPermissionManager
import com.example.util.DateGroupingUtil
import com.example.util.GalleryImageLoader
import com.example.util.PhotoEditorEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GalleryUiState(
    val accessState: MediaAccessState = MediaAccessState.DENIED,
    val isLoading: Boolean = true,
    val isSeedingSampleMedia: Boolean = false,
    val errorMessage: String? = null,
    val statusToastMessage: String? = null,
    val allMedia: List<MediaItem> = emptyList(),
    val timelineGroupingMode: TimelineGroupingMode = TimelineGroupingMode.ALL,
    val timelineSections: List<TimelineSection> = emptyList(),
    val isSelectionMode: Boolean = false,
    val selectedMediaIds: Set<Long> = emptySet(),
    val smartAlbums: List<AlbumSummary> = emptyList(),
    val userAlbums: List<AlbumSummary> = emptyList(),
    val activeAlbum: AlbumSummary? = null,
    val activeAlbumItems: List<MediaItem> = emptyList(),
    val searchQuery: String = "",
    val searchFilter: SearchMediaFilter = SearchMediaFilter.ALL,
    val searchResults: List<MediaItem> = emptyList(),
    val recentSearches: List<String> = listOf("Camera", "Screenshots", "2026", "Alps", "Tokyo"),
    val availableYears: List<String> = emptyList(),
    val availableFolders: List<String> = emptyList(),
    val viewerItems: List<MediaItem> = emptyList(),
    val viewerIndex: Int = 0,
    val isViewerOpen: Boolean = false,
    val lastViewedMediaId: Long? = null,
    val viewerExifDetails: Map<String, String> = emptyMap(),
    val editingItem: MediaItem? = null,
    val editorBitmap: Bitmap? = null,
    val isEditorLoadingBitmap: Boolean = false,
    val editorAdjustments: EditorAdjustments = EditorAdjustments(),
    val canUndoEdit: Boolean = false,
    val canRedoEdit: Boolean = false,
    val isSavingEdit: Boolean = false,
    val pendingLegacyDeleteItems: List<MediaItem> = emptyList()
)

class GalleryViewModel(
    application: Application,
    private val mediaStoreRepository: MediaStoreRepository,
    private val albumRepository: AlbumRepository,
    private val preferencesRepository: UserPreferencesRepository
) : AndroidViewModel(application) {

    private val _rawMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _accessState = MutableStateFlow(MediaPermissionManager.getAccessState(application))
    private val _isLoading = MutableStateFlow(true)
    private val _isSeeding = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val _groupingMode = MutableStateFlow(TimelineGroupingMode.ALL)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _activeAlbumId = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    private val _searchFilter = MutableStateFlow(SearchMediaFilter.ALL)
    private val _recentSearches = MutableStateFlow(listOf("Camera", "Screenshots", "Alps", "Tokyo"))

    // Viewer state
    private val _viewerSourceList = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _viewerIndex = MutableStateFlow(0)
    private val _isViewerOpen = MutableStateFlow(false)
    private val _lastViewedMediaId = MutableStateFlow<Long?>(null)
    private val _viewerExif = MutableStateFlow<Map<String, String>>(emptyMap())

    // Editor state
    private val _editingItem = MutableStateFlow<MediaItem?>(null)
    private val _editorBitmap = MutableStateFlow<Bitmap?>(null)
    private val _isEditorLoading = MutableStateFlow(false)
    private val _editorAdjustments = MutableStateFlow(EditorAdjustments())
    private val _undoStack = MutableStateFlow<List<EditorAdjustments>>(emptyList())
    private val _redoStack = MutableStateFlow<List<EditorAdjustments>>(emptyList())
    private val _isSavingEdit = MutableStateFlow(false)

    // Pending legacy delete confirmation (API <= 29 or fallback)
    private val _pendingLegacyDelete = MutableStateFlow<List<MediaItem>>(emptyList())

    private data class EnrichedDataSnapshot(
        val allMedia: List<MediaItem>,
        val userAlbumEntities: List<UserAlbumEntity>,
        val crossRefs: List<AlbumMediaCrossRef>,
        val groupingMode: TimelineGroupingMode
    )

    private data class SearchAndSelectionSnapshot(
        val query: String,
        val filter: SearchMediaFilter,
        val isSelectionMode: Boolean,
        val selectedIds: Set<Long>,
        val activeAlbumId: String?
    )

    @Volatile
    private var latestSnapshot: EnrichedDataSnapshot = EnrichedDataSnapshot(
        allMedia = emptyList(),
        userAlbumEntities = emptyList(),
        crossRefs = emptyList(),
        groupingMode = TimelineGroupingMode.ALL
    )

    val preferencesState: StateFlow<UserPreferencesState> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferencesState()
        )

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        GalleryImageLoader.get(application)

        // Observe Room favorites, user albums, cross-refs, and media items off the main thread
        viewModelScope.launch {
            combine(
                _rawMedia,
                albumRepository.favoriteIdsFlow,
                albumRepository.userAlbumsFlow,
                albumRepository.crossRefsFlow,
                _groupingMode
            ) { rawItems, favIds, userAlbumEntities, crossRefs, groupingMode ->
                val enrichedMedia = if (favIds.isEmpty()) {
                    rawItems
                } else {
                    rawItems.map { item ->
                        val fav = item.isFavorite || favIds.contains(item.id)
                        if (fav == item.isFavorite) item else item.copy(isFavorite = fav)
                    }
                }
                EnrichedDataSnapshot(
                    allMedia = enrichedMedia,
                    userAlbumEntities = userAlbumEntities,
                    crossRefs = crossRefs,
                    groupingMode = groupingMode
                )
            }
                .flowOn(Dispatchers.Default)
                .collect { snapshot ->
                    recomputeDerivedUiState(snapshot)
                }
        }

        // Observe search & selection changes
        viewModelScope.launch {
            combine(
                _searchQuery,
                _searchFilter,
                _isSelectionMode,
                _selectedIds,
                _activeAlbumId
            ) { query, filter, selMode, selIds, activeAlbumId ->
                SearchAndSelectionSnapshot(query, filter, selMode, selIds, activeAlbumId)
            }.collect {
                recomputeSearchAndAlbums()
            }
        }

        // Observe MediaStore external changes automatically
        viewModelScope.launch {
            mediaStoreRepository.observeMediaStoreChanges().collect {
                if (_accessState.value != MediaAccessState.DENIED) {
                    refreshLibrary(silent = true)
                }
            }
        }

        // Initial permission check & load
        checkPermissionsAndLoad()
    }

    private suspend fun recomputeDerivedUiState(snapshot: EnrichedDataSnapshot) = withContext(Dispatchers.Default) {
        latestSnapshot = snapshot
        val media = snapshot.allMedia
        val sections = DateGroupingUtil.groupMedia(media, snapshot.groupingMode)
        val smartAlbums = albumRepository.buildSmartAlbums(media)
        val userAlbums = albumRepository.buildUserAlbums(
            snapshot.userAlbumEntities,
            snapshot.crossRefs,
            media
        )

        val yearFmt = SimpleDateFormat("yyyy", Locale.US)
        val years = media.map { yearFmt.format(Date(it.effectiveDateMillis)) }
            .distinct()
            .sortedDescending()
        val folders = media.map { it.bucketDisplayName }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)

        // Keep viewer items synced with favorite/delete updates
        val currentViewerItems = if (_isViewerOpen.value) {
            val byId = media.associateBy { it.id }
            val updatedList = _viewerSourceList.value.mapNotNull { byId[it.id] }
            _viewerSourceList.value = updatedList
            updatedList
        } else {
            emptyList()
        }
        val clampedViewerIndex = if (currentViewerItems.isNotEmpty()) {
            _viewerIndex.value.coerceIn(0, currentViewerItems.lastIndex)
        } else 0
        _viewerIndex.value = clampedViewerIndex

        val activeAlbumId = _activeAlbumId.value
        val allAlbums = smartAlbums + userAlbums
        val activeAlbum = allAlbums.firstOrNull { it.id == activeAlbumId }
        val activeAlbumItems = if (activeAlbumId != null) {
            albumRepository.getMediaForAlbum(activeAlbumId, media, snapshot.crossRefs)
        } else {
            emptyList()
        }

        val searchResults = filterMediaForSearch(
            allMedia = media,
            userAlbums = userAlbums,
            crossRefs = snapshot.crossRefs,
            query = _searchQuery.value,
            filter = _searchFilter.value
        )

        _uiState.update { state ->
            state.copy(
                accessState = _accessState.value,
                isLoading = _isLoading.value,
                isSeedingSampleMedia = _isSeeding.value,
                errorMessage = _errorMessage.value,
                allMedia = media,
                timelineGroupingMode = snapshot.groupingMode,
                timelineSections = sections,
                smartAlbums = smartAlbums,
                userAlbums = userAlbums,
                activeAlbum = activeAlbum,
                activeAlbumItems = activeAlbumItems,
                searchResults = searchResults,
                availableYears = years,
                availableFolders = folders,
                viewerItems = currentViewerItems,
                viewerIndex = clampedViewerIndex,
                isViewerOpen = _isViewerOpen.value && currentViewerItems.isNotEmpty(),
                lastViewedMediaId = _lastViewedMediaId.value
            )
        }
    }

    private suspend fun recomputeSearchAndAlbums() = withContext(Dispatchers.Default) {
        val snapshot = latestSnapshot
        val media = snapshot.allMedia
        val smartAlbums = albumRepository.buildSmartAlbums(media)
        val userAlbums = albumRepository.buildUserAlbums(
            snapshot.userAlbumEntities,
            snapshot.crossRefs,
            media
        )
        val activeAlbumId = _activeAlbumId.value
        val activeAlbum = (smartAlbums + userAlbums).firstOrNull { it.id == activeAlbumId }
        val activeAlbumItems = if (activeAlbumId != null) {
            albumRepository.getMediaForAlbum(activeAlbumId, media, snapshot.crossRefs)
        } else {
            emptyList()
        }
        val searchResults = filterMediaForSearch(
            allMedia = media,
            userAlbums = userAlbums,
            crossRefs = snapshot.crossRefs,
            query = _searchQuery.value,
            filter = _searchFilter.value
        )

        _uiState.update { state ->
            state.copy(
                searchQuery = _searchQuery.value,
                searchFilter = _searchFilter.value,
                searchResults = searchResults,
                isSelectionMode = _isSelectionMode.value,
                selectedMediaIds = _selectedIds.value,
                activeAlbum = activeAlbum,
                activeAlbumItems = activeAlbumItems
            )
        }
    }

    private fun filterMediaForSearch(
        allMedia: List<MediaItem>,
        userAlbums: List<AlbumSummary>,
        crossRefs: List<AlbumMediaCrossRef>,
        query: String,
        filter: SearchMediaFilter
    ): List<MediaItem> {
        val filteredByCategory = when (filter) {
            SearchMediaFilter.ALL -> allMedia
            SearchMediaFilter.PHOTOS -> allMedia.filter { !it.isVideo }
            SearchMediaFilter.VIDEOS -> allMedia.filter { it.isVideo }
            SearchMediaFilter.FAVORITES -> allMedia.filter { it.isFavorite }
            SearchMediaFilter.SCREENSHOTS -> allMedia.filter { it.isScreenshot }
            SearchMediaFilter.CAMERA -> allMedia.filter { it.isCamera }
        }

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return filteredByCategory

        val tokens = trimmed.lowercase(Locale.getDefault()).split(" ").filter { it.isNotBlank() }
        val dateTextFormatter = SimpleDateFormat("MMMM yyyy EEE d", Locale.getDefault())

        // Map mediaId -> user album titles it belongs to
        val albumTitleById = userAlbums.mapNotNull { summary ->
            summary.userAlbumId?.let { it to summary.title.lowercase(Locale.getDefault()) }
        }.toMap()
        val mediaAlbumTitles = HashMap<Long, MutableList<String>>()
        for (ref in crossRefs) {
            val title = albumTitleById[ref.albumId] ?: continue
            mediaAlbumTitles.getOrPut(ref.mediaId) { mutableListOf() }.add(title)
        }

        return filteredByCategory.filter { item ->
            val dateStr = dateTextFormatter.format(Date(item.effectiveDateMillis)).lowercase(Locale.getDefault())
            val typeStr = if (item.isVideo) "video mp4 clip movie" else "photo image picture jpg"
            val albumNames = mediaAlbumTitles[item.id]?.joinToString(" ").orEmpty()
            val searchableBlob = buildString {
                append(item.displayName.lowercase(Locale.getDefault())).append(' ')
                append(item.bucketDisplayName.lowercase(Locale.getDefault())).append(' ')
                append(item.relativePath.lowercase(Locale.getDefault())).append(' ')
                append(dateStr).append(' ')
                append(typeStr).append(' ')
                append(albumNames).append(' ')
                if (item.isScreenshot) append("screenshot ")
                if (item.isFavorite) append("favorite heart starred ")
                item.locationLabel?.let { append(it.lowercase(Locale.getDefault())) }
            }
            tokens.all { token -> searchableBlob.contains(token) }
        }
    }

    fun checkPermissionsAndLoad() {
        val access = MediaPermissionManager.getAccessState(getApplication())
        _accessState.value = access
        _uiState.update { it.copy(accessState = access) }
        if (access != MediaAccessState.DENIED) {
            refreshLibrary(silent = false)
        } else {
            _isLoading.value = false
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshLibrary(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _isLoading.value = true
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            runCatching {
                val favs = _uiState.value.allMedia.filter { it.isFavorite }.map { it.id }.toSet()
                mediaStoreRepository.loadAllMedia(favs)
            }.onSuccess { mediaList ->
                _rawMedia.value = mediaList
                _isLoading.value = false
                _errorMessage.value = null
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            }.onFailure { err ->
                _isLoading.value = false
                _errorMessage.value = err.localizedMessage ?: "Failed to read device MediaStore."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = _errorMessage.value
                    )
                }
            }
        }
    }

    fun seedSampleMediaIntoMediaStore() {
        if (_isSeeding.value) return
        viewModelScope.launch {
            _isSeeding.value = true
            _uiState.update { it.copy(isSeedingSampleMedia = true) }
            val count = mediaStoreRepository.seedSamplePhotosIntoMediaStore()
            _isSeeding.value = false
            _uiState.update {
                it.copy(
                    isSeedingSampleMedia = false,
                    statusToastMessage = if (count > 0) {
                        "Added $count sample photos to device MediaStore"
                    } else {
                        "Could not write sample photos to MediaStore"
                    }
                )
            }
            refreshLibrary(silent = false)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
        _uiState.update { it.copy(statusToastMessage = null) }
    }

    // Timeline Grouping & Grid Density
    fun setTimelineGroupingMode(mode: TimelineGroupingMode) {
        _groupingMode.value = mode
    }

    fun setGridDensity(density: GridDensity) {
        viewModelScope.launch {
            preferencesRepository.setGridDensity(density)
        }
    }

    fun cycleGridDensity() {
        val next = preferencesState.value.gridDensity.next()
        setGridDensity(next)
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            preferencesRepository.setThemePreference(theme)
        }
    }

    fun setRoundedThumbnails(rounded: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRoundedThumbnails(rounded)
        }
    }

    fun setShowVideoDuration(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowVideoDuration(show)
        }
    }

    // Selection Mode
    fun toggleSelectionMode() {
        val next = !_isSelectionMode.value
        _isSelectionMode.value = next
        if (!next) {
            _selectedIds.value = emptySet()
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleItemSelection(mediaId: Long) {
        if (!_isSelectionMode.value) {
            _isSelectionMode.value = true
        }
        _selectedIds.update { current ->
            if (current.contains(mediaId)) current - mediaId else current + mediaId
        }
    }

    fun selectAllInCurrentView(items: List<MediaItem>) {
        _isSelectionMode.value = true
        _selectedIds.value = items.map { it.id }.toSet()
    }

    fun getSelectedItems(): List<MediaItem> {
        val ids = _selectedIds.value
        return _uiState.value.allMedia.filter { ids.contains(it.id) }
    }

    // Favorites
    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            val isNowFav = albumRepository.toggleFavorite(item)
            _uiState.update {
                it.copy(
                    statusToastMessage = if (isNowFav) "Added to Favorites" else "Removed from Favorites"
                )
            }
        }
    }

    fun favoriteSelectedItems() {
        val items = getSelectedItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            albumRepository.setFavoriteForItems(items, true)
            exitSelectionMode()
            _uiState.update {
                it.copy(statusToastMessage = "Added ${items.size} items to Favorites")
            }
        }
    }

    // Albums management
    fun openAlbum(albumId: String) {
        _activeAlbumId.value = albumId
    }

    fun closeActiveAlbum() {
        _activeAlbumId.value = null
    }

    fun createUserAlbum(title: String, initialItems: List<MediaItem> = emptyList()) {
        viewModelScope.launch {
            albumRepository.createAlbum(title, initialItems)
            if (_isSelectionMode.value) exitSelectionMode()
            _uiState.update {
                it.copy(statusToastMessage = "Created album \"$title\"")
            }
        }
    }

    fun renameUserAlbum(userAlbumId: Long, newTitle: String) {
        viewModelScope.launch {
            albumRepository.renameAlbum(userAlbumId, newTitle)
            _uiState.update {
                it.copy(statusToastMessage = "Renamed album to \"$newTitle\"")
            }
        }
    }

    fun deleteUserAlbum(userAlbumId: Long) {
        viewModelScope.launch {
            if (_activeAlbumId.value == "user_$userAlbumId") {
                _activeAlbumId.value = null
            }
            albumRepository.deleteAlbumWithoutDeletingMedia(userAlbumId)
            _uiState.update {
                it.copy(statusToastMessage = "Deleted album (original photos kept)")
            }
        }
    }

    fun addItemsToUserAlbum(userAlbumId: Long, items: List<MediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            albumRepository.addMediaToAlbum(userAlbumId, items)
            if (_isSelectionMode.value) exitSelectionMode()
            _uiState.update {
                it.copy(statusToastMessage = "Added ${items.size} ${if (items.size == 1) "item" else "items"} to album")
            }
        }
    }

    fun removeItemFromActiveUserAlbum(item: MediaItem) {
        val userAlbumId = _uiState.value.activeAlbum?.userAlbumId ?: return
        viewModelScope.launch {
            albumRepository.removeMediaFromAlbum(userAlbumId, item.id)
            _uiState.update {
                it.copy(statusToastMessage = "Removed from album (device file kept)")
            }
        }
    }

    // Search
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun commitRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        _recentSearches.update { current ->
            (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(8)
        }
        _uiState.update { it.copy(recentSearches = _recentSearches.value) }
    }

    fun setSearchFilter(filter: SearchMediaFilter) {
        _searchFilter.value = filter
    }

    // Full-screen Viewer
    fun openViewer(sourceList: List<MediaItem>, clickedItem: MediaItem) {
        val list = sourceList.ifEmpty { listOf(clickedItem) }
        val index = list.indexOfFirst { it.id == clickedItem.id }.coerceAtLeast(0)
        val activeId = list[index].id
        _viewerSourceList.value = list
        _viewerIndex.value = index
        _isViewerOpen.value = true
        _lastViewedMediaId.value = activeId
        _uiState.update {
            it.copy(
                viewerItems = list,
                viewerIndex = index,
                isViewerOpen = true,
                lastViewedMediaId = activeId
            )
        }
        loadExifForCurrentViewerItem(list[index])
    }

    fun onViewerPageChanged(newIndex: Int) {
        val list = _uiState.value.viewerItems
        if (newIndex in list.indices) {
            val activeItem = list[newIndex]
            _viewerIndex.value = newIndex
            _lastViewedMediaId.value = activeItem.id
            _uiState.update {
                it.copy(
                    viewerIndex = newIndex,
                    lastViewedMediaId = activeItem.id
                )
            }
            loadExifForCurrentViewerItem(activeItem)
        }
    }

    private fun loadExifForCurrentViewerItem(item: MediaItem) {
        viewModelScope.launch {
            val details = mediaStoreRepository.readDetailedExifMetadata(item)
            _viewerExif.value = details
            _uiState.update { it.copy(viewerExifDetails = details) }
        }
    }

    fun closeViewer() {
        _isViewerOpen.value = false
        _uiState.update { it.copy(isViewerOpen = false) }
    }

    // Share Helper
    fun buildShareIntent(items: List<MediaItem>): Intent? {
        if (items.isEmpty()) return null
        return if (items.size == 1) {
            val item = items.first()
            Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            val uris = ArrayList<Uri>(items.map { it.uri })
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    // Delete Media (with Android 11+ MediaStore.createDeleteRequest or confirmation dialog)
    fun requestDeleteItems(
        items: List<MediaItem>,
        onLaunchSystemDialog: (PendingIntent) -> Unit
    ) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val systemIntent = mediaStoreRepository.createSystemDeleteIntent(uris)
        if (systemIntent != null) {
            onLaunchSystemDialog(systemIntent)
        } else {
            _pendingLegacyDelete.value = items
            _uiState.update { it.copy(pendingLegacyDeleteItems = items) }
        }
    }

    fun confirmLegacyDelete() {
        val items = _pendingLegacyDelete.value
        _pendingLegacyDelete.value = emptyList()
        _uiState.update { it.copy(pendingLegacyDeleteItems = emptyList()) }
        if (items.isEmpty()) return

        viewModelScope.launch {
            val count = mediaStoreRepository.deleteMediaDirect(items.map { it.uri })
            exitSelectionMode()
            refreshLibrary(silent = true)
            _uiState.update {
                it.copy(statusToastMessage = "Deleted $count ${if (count == 1) "item" else "items"}")
            }
        }
    }

    fun cancelLegacyDelete() {
        _pendingLegacyDelete.value = emptyList()
        _uiState.update { it.copy(pendingLegacyDeleteItems = emptyList()) }
    }

    fun onSystemDeleteCompleted(approved: Boolean) {
        if (approved) {
            exitSelectionMode()
            refreshLibrary(silent = true)
            _uiState.update {
                it.copy(statusToastMessage = "Deleted from device")
            }
        }
    }

    // Photo Editor
    fun openPhotoEditor(item: MediaItem) {
        if (item.isVideo) return
        _editingItem.value = item
        _editorAdjustments.value = EditorAdjustments()
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _isEditorLoading.value = true
        _uiState.update {
            it.copy(
                editingItem = item,
                editorBitmap = null,
                isEditorLoadingBitmap = true,
                editorAdjustments = EditorAdjustments(),
                canUndoEdit = false,
                canRedoEdit = false
            )
        }

        viewModelScope.launch {
            val bitmap = PhotoEditorEngine.decodeSampledBitmap(
                context = getApplication(),
                uri = item.uri,
                maxDimension = 2048
            )
            _editorBitmap.value = bitmap
            _isEditorLoading.value = false
            _uiState.update {
                it.copy(
                    editorBitmap = bitmap,
                    isEditorLoadingBitmap = false,
                    statusToastMessage = if (bitmap == null) "Unable to decode image for editing" else null
                )
            }
        }
    }

    fun updateEditorAdjustments(
        newAdjustments: EditorAdjustments,
        recordHistory: Boolean = true
    ) {
        val current = _editorAdjustments.value
        if (current == newAdjustments) return
        if (recordHistory) {
            _undoStack.update { (it + current).takeLast(25) }
            _redoStack.value = emptyList()
        }
        _editorAdjustments.value = newAdjustments
        syncEditorHistoryUi()
    }

    fun undoEditorChange() {
        val stack = _undoStack.value
        if (stack.isEmpty()) return
        val previous = stack.last()
        val current = _editorAdjustments.value
        _undoStack.value = stack.dropLast(1)
        _redoStack.update { it + current }
        _editorAdjustments.value = previous
        syncEditorHistoryUi()
    }

    fun redoEditorChange() {
        val stack = _redoStack.value
        if (stack.isEmpty()) return
        val next = stack.last()
        val current = _editorAdjustments.value
        _redoStack.value = stack.dropLast(1)
        _undoStack.update { it + current }
        _editorAdjustments.value = next
        syncEditorHistoryUi()
    }

    fun resetAllEditorAdjustments() {
        updateEditorAdjustments(EditorAdjustments(), recordHistory = true)
    }

    private fun syncEditorHistoryUi() {
        _uiState.update {
            it.copy(
                editorAdjustments = _editorAdjustments.value,
                canUndoEdit = _undoStack.value.isNotEmpty(),
                canRedoEdit = _redoStack.value.isNotEmpty()
            )
        }
    }

    fun closePhotoEditor() {
        _editingItem.value = null
        _editorBitmap.value = null
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _uiState.update {
            it.copy(
                editingItem = null,
                editorBitmap = null,
                isEditorLoadingBitmap = false,
                isSavingEdit = false
            )
        }
    }

    fun saveEditedPhotoCopy() {
        val item = _editingItem.value ?: return
        val sourceBitmap = _editorBitmap.value ?: return
        val adjustments = _editorAdjustments.value
        if (_isSavingEdit.value) return

        viewModelScope.launch {
            _isSavingEdit.value = true
            _uiState.update { it.copy(isSavingEdit = true) }

            val savedUri = runCatching {
                val rendered = PhotoEditorEngine.renderEditedBitmap(sourceBitmap, adjustments)
                val uri = PhotoEditorEngine.saveEditedBitmapToMediaStore(
                    context = getApplication(),
                    originalDisplayName = item.displayName,
                    renderedBitmap = rendered
                )
                if (rendered !== sourceBitmap) {
                    rendered.recycle()
                }
                uri
            }.getOrNull()

            _isSavingEdit.value = false
            if (savedUri != null) {
                closePhotoEditor()
                refreshLibrary(silent = false)
                _uiState.update {
                    it.copy(statusToastMessage = "Saved edited copy to Pictures/Gallery")
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSavingEdit = false,
                        statusToastMessage = "Failed to save edited photo"
                    )
                }
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = GalleryDatabase.getInstance(application)
            val mediaRepo = MediaStoreRepository(application)
            val albumRepo = AlbumRepository(db.albumDao())
            val prefsRepo = UserPreferencesRepository(application)
            return GalleryViewModel(application, mediaRepo, albumRepo, prefsRepo) as T
        }
    }
}
