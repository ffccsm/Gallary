package com.example.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

enum class GalleryTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    LIBRARY(
        route = "tab_library",
        label = "Library",
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
        testTag = "nav_tab_library"
    ),
    ALBUMS(
        route = "tab_albums",
        label = "Albums",
        selectedIcon = Icons.Filled.Collections,
        unselectedIcon = Icons.Outlined.Collections,
        testTag = "nav_tab_albums"
    ),
    SEARCH(
        route = "tab_search",
        label = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_tab_search"
    )
}
