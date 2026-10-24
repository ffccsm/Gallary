package com.example.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.AlbumSummary
import com.example.domain.model.GridDensity
import com.example.domain.model.ThemePreference
import com.example.permissions.MediaAccessState
import com.example.ui.albums.AlbumNameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    preferences: UserPreferencesState,
    accessState: MediaAccessState,
    mediaCount: Int,
    onDismiss: () -> Unit,
    onThemeChanged: (ThemePreference) -> Unit,
    onGridDensityChanged: (GridDensity) -> Unit,
    onRoundedThumbnailsChanged: (Boolean) -> Unit,
    onShowVideoDurationChanged: (Boolean) -> Unit,
    onSeedSamplePhotos: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Gallery Settings",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Appearance Theme
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThemePreference.entries.forEach { theme ->
                    FilterChip(
                        selected = preferences.themePreference == theme,
                        onClick = { onThemeChanged(theme) },
                        label = { Text(theme.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid Columns
            Text(
                text = "Default Thumbnail Grid Size",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GridDensity.entries.forEach { density ->
                    FilterChip(
                        selected = preferences.gridDensity == density,
                        onClick = { onGridDensityChanged(density) },
                        label = { Text("${density.columns} Col") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rounded Photo Thumbnails", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Apply subtle iOS-inspired corner rounding in grids",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.roundedThumbnails,
                            onCheckedChange = onRoundedThumbnailsChanged
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Video Duration", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Display timestamp badges on video thumbnails",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.showVideoDuration,
                            onCheckedChange = onShowVideoDurationChanged
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // MediaStore Status & Sample Seeder
            Text(
                text = "Device MediaStore Library ($mediaCount items • ${accessState.name.replace('_', ' ')})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onSeedSamplePhotos()
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_seed_media_button")
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add 8 Sample High-Res Photos to MediaStore")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumBottomSheet(
    userAlbums: List<AlbumSummary>,
    itemCount: Int,
    onDismiss: () -> Unit,
    onSelectAlbum: (Long) -> Unit,
    onCreateNewAlbumWithItems: (String) -> Unit
) {
    var showNewAlbumDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Add $itemCount ${if (itemCount == 1) "Item" else "Items"} to Album",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { showNewAlbumDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Album", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (userAlbums.isEmpty()) {
                Text(
                    text = "You haven't created any custom albums yet. Tap \"New Album\" above to create one and add these items.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        userAlbums.forEachIndexed { index, album ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        album.userAlbumId?.let { id ->
                                            onSelectAlbum(id)
                                            onDismiss()
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Collections,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${album.count}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < userAlbums.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 50.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewAlbumDialog) {
        AlbumNameDialog(
            title = "New Album",
            initialValue = "",
            confirmLabel = "Create & Add",
            onDismiss = { showNewAlbumDialog = false },
            onConfirm = { title ->
                showNewAlbumDialog = false
                onCreateNewAlbumWithItems(title)
                onDismiss()
            }
        )
    }
}
