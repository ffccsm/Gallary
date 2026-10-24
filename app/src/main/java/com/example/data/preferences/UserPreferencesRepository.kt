package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.runtime.Immutable
import com.example.domain.model.GridDensity
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gallery_preferences")

@Immutable
data class UserPreferencesState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val gridDensity: GridDensity = GridDensity.STANDARD,
    val roundedThumbnails: Boolean = true,
    val showVideoDuration: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val ROUNDED_THUMBNAILS = booleanPreferencesKey("rounded_thumbnails")
        val SHOW_VIDEO_DURATION = booleanPreferencesKey("show_video_duration")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_PREF] ?: ThemePreference.SYSTEM.name
        val theme = runCatching { ThemePreference.valueOf(themeStr) }.getOrDefault(ThemePreference.SYSTEM)
        val columns = prefs[Keys.GRID_COLUMNS] ?: GridDensity.STANDARD.columns
        val rounded = prefs[Keys.ROUNDED_THUMBNAILS] ?: true
        val showDuration = prefs[Keys.SHOW_VIDEO_DURATION] ?: true

        UserPreferencesState(
            themePreference = theme,
            gridDensity = GridDensity.fromColumns(columns),
            roundedThumbnails = rounded,
            showVideoDuration = showDuration
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }

    suspend fun setGridDensity(density: GridDensity) {
        context.dataStore.edit { it[Keys.GRID_COLUMNS] = density.columns }
    }

    suspend fun setRoundedThumbnails(rounded: Boolean) {
        context.dataStore.edit { it[Keys.ROUNDED_THUMBNAILS] = rounded }
    }

    suspend fun setShowVideoDuration(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_VIDEO_DURATION] = show }
    }
}
