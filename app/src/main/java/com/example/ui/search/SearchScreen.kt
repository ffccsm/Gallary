package com.example.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.MediaItem
import com.example.domain.model.SearchMediaFilter
import com.example.ui.GalleryUiState
import com.example.ui.components.MediaThumbnailGridItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    uiState: GalleryUiState,
    preferences: UserPreferencesState,
    onQueryChanged: (String) -> Unit,
    onCommitSearch: (String) -> Unit,
    onFilterSelected: (SearchMediaFilter) -> Unit,
    onMediaItemClicked: (List<MediaItem>, MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header + Search Input
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChanged,
                placeholder = {
                    Text(
                        text = "Photos, Videos, Albums, Dates, Places…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear Search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onCommitSearch(uiState.searchQuery)
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Filter Chips (All, Photos, Videos, Favorites, Screenshots, Camera)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SearchMediaFilter.entries.forEach { filter ->
                    val selected = uiState.searchFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        shape = CircleShape,
                        modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        val isFiltering = uiState.searchQuery.isNotBlank() || uiState.searchFilter != SearchMediaFilter.ALL

        LazyVerticalGrid(
            columns = GridCells.Fixed(preferences.gridDensity.columns),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("search_results_grid")
        ) {
            if (!isFiltering) {
                // Discovery suggestions when search query is blank
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        if (uiState.recentSearches.isNotEmpty()) {
                            SuggestionGroupTitle("Recent & Suggested")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                uiState.recentSearches.forEach { term ->
                                    SuggestionChipPill(
                                        icon = Icons.Filled.History,
                                        text = term,
                                        onClick = {
                                            onQueryChanged(term)
                                            onCommitSearch(term)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (uiState.availableYears.isNotEmpty()) {
                            SuggestionGroupTitle("Years")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                uiState.availableYears.forEach { year ->
                                    SuggestionChipPill(
                                        icon = Icons.Filled.CalendarMonth,
                                        text = year,
                                        onClick = {
                                            onQueryChanged(year)
                                            onCommitSearch(year)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (uiState.availableFolders.isNotEmpty()) {
                            SuggestionGroupTitle("Folders & Albums")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                uiState.availableFolders.forEach { folder ->
                                    SuggestionChipPill(
                                        icon = Icons.Filled.Folder,
                                        text = folder,
                                        onClick = {
                                            onQueryChanged(folder)
                                            onCommitSearch(folder)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        val locations = uiState.allMedia.mapNotNull { it.locationLabel }.distinct()
                        if (locations.isNotEmpty()) {
                            SuggestionGroupTitle("Places")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                locations.forEach { loc ->
                                    SuggestionChipPill(
                                        icon = Icons.Filled.LocationOn,
                                        text = loc,
                                        onClick = {
                                            onQueryChanged(loc)
                                            onCommitSearch(loc)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }

            // Results Count Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isFiltering) "Matching Media" else "All Indexed Media",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${uiState.searchResults.size} ${if (uiState.searchResults.size == 1) "Result" else "Results"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.searchResults.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Matching Photos or Videos",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try searching by filename, folder, year, month, or selecting a different media filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = uiState.searchResults,
                    key = { it.id },
                    contentType = { "media_tile" }
                ) { item ->
                    MediaThumbnailGridItem(
                        item = item,
                        isSelectionMode = false,
                        isSelected = false,
                        roundedCorners = preferences.roundedThumbnails,
                        showVideoDuration = preferences.showVideoDuration,
                        thumbnailPx = preferences.gridDensity.thumbnailPx,
                        onClick = { onMediaItemClicked(uiState.searchResults, item) },
                        onLongClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SuggestionChipPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
