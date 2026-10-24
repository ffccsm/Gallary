# Gallery (`com.sohel.gallery`) — iOS-Inspired Native Android Photo & Video Library

A complete, native Android Gallery application built with **Kotlin** and **Jetpack Compose**, inspired by the clean minimalism, smooth gestures, and layout clarity of **Apple Photos**.

---

## 1. Architecture & Project Structure

- **`MainActivity.kt`**: Edge-to-edge host activity managing runtime media permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, and legacy `READ_EXTERNAL_STORAGE`), system media-deletion confirmation (`MediaStore.createDeleteRequest`), and top-level navigation.
- **`navigation/GalleryTab.kt`**: Bottom navigation tabs (`Library`, `Albums`, `Search`).
- **`domain/model/MediaModels.kt`**: Strongly typed models (`MediaItem`, `TimelineSection`, `TimelineGroupingMode`, `GridDensity`, `SmartAlbumType`, `AlbumSummary`, `SearchMediaFilter`, `CropAspectRatio`, `EditorAdjustments`).
- **`data/media/MediaStoreRepository.kt`**:
  - Queries `MediaStore.Files` for device photos and videos sorted by capture date (`DATE_TAKEN DESC, DATE_MODIFIED DESC`).
  - Registers a `ContentObserver` on `MediaStore.Images` and `MediaStore.Video` to automatically refresh when files change on device.
  - Reads EXIF metadata (camera make/model, aperture, ISO, focal length, GPS coordinates) via `androidx.exifinterface`.
  - Uses Android's `MediaStore.createDeleteRequest` on Android 11+ (API 30+) for user-confirmed deletion.
  - Includes a built-in `seedSamplePhotosIntoMediaStore()` helper that writes 8 high-resolution sample JPEGs with EXIF timestamps into `DCIM/Camera`, `Pictures/Screenshots`, `Pictures/Gallery`, and `Download` when testing on an empty emulator.
- **`data/database/` (`GalleryDatabase.kt`, `AlbumDao.kt`, `AlbumEntities.kt`)**:
  - Room SQLite persistence for user-created albums (`user_albums`), non-destructive album-media cross-references (`album_media_cross_ref`), and favorites (`favorite_media`).
- **`data/albums/AlbumRepository.kt`**:
  - Synthesizes smart collections (`All Photos`, `Recents`, `Favorites`, `Videos`, `Screenshots`, `Camera`, `Downloads`) alongside Room-backed user albums.
- **`data/preferences/UserPreferencesRepository.kt`**:
  - Jetpack DataStore Preferences for theme mode (`System`, `Light`, `Dark`), default grid density (`2`, `3`, `4`, or `5` columns), thumbnail corner style, and video duration badges.
- **`util/PhotoEditorEngine.kt`**:
  - Off-main-thread bitmap decoding with `inSampleSize` downsampling and EXIF orientation normalization.
  - Real pixel rendering pipeline for rotation (`90°` increments), horizontal/vertical flip, free and aspect-ratio crop (`Free`, `Original`, `1:1`, `4:3`, `3:4`, `16:9`, `9:16`), and `ColorMatrix` adjustments (`Brightness`, `Contrast`, `Saturation`).
  - Non-destructive export saving edited JPEGs to `Pictures/Gallery` via `MediaStore` (`IS_PENDING`).
- **`ui/library/LibraryScreen.kt`**, **`ui/viewer/MediaViewerScreen.kt`**, **`ui/albums/AlbumsScreen.kt`**, **`ui/search/SearchScreen.kt`**, **`ui/editor/PhotoEditorScreen.kt`**, **`ui/settings/SettingsSheet.kt`**.

---

## 2. Dependencies & Licensing

All third-party and Jetpack dependencies used in this project are open-source and permissive (**Apache License 2.0**):

| Library | Purpose | License |
|---|---|---|
| **AndroidX Jetpack Compose & Material 3** | Declarative UI, gestures, pager, animations | Apache-2.0 |
| **AndroidX Room (`androidx.room`)** | Persistent SQLite storage for custom albums & favorites | Apache-2.0 |
| **AndroidX DataStore (`datastore-preferences`)** | User preferences persistence | Apache-2.0 |
| **AndroidX ExifInterface (`exifinterface`)** | Reading/writing photo EXIF orientation & metadata | Apache-2.0 |
| **Coil (`io.coil-kt:coil-compose`, `coil-video`)** | Fast thumbnail & video frame loading with memory/disk caching | Apache-2.0 |
| **Android Graphics (`Bitmap`, `Canvas`, `Matrix`, `ColorMatrix`)** | Built-in native hardware-accelerated image editing | Apache-2.0 |

---

## 3. Opening, Building & Running in Android Studio

1. **Open the Project**:
   - Launch **Android Studio** (Ladybug or newer recommended).
   - Select **Open** and choose the root project directory.
2. **Sync Gradle**:
   - Android Studio will automatically sync Gradle and download dependencies from `google()` and `mavenCentral()`.
   - Ensure **JDK 17** or **JDK 21** is selected under *Settings → Build, Execution, Deployment → Build Tools → Gradle*.
3. **Build the Debug APK**:
   - On Windows:
     ```bat
     gradlew.bat assembleDebug
     ```
   - On macOS / Linux:
     ```bash
     ./gradlew assembleDebug
     ```
4. **Locate the Debug APK**:
   - After a successful build, the installable debug APK is generated at:
     ```
     app/build/outputs/apk/debug/app-debug.apk
     ```
5. **Install & Test on a Real Android Device**:
   - Enable **Developer Options** and **USB Debugging** on your Android phone (Android 10 / API 29+ recommended).
   - Connect via USB and run:
     ```bash
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```
6. **Create a Signed Release APK**:
   - In Android Studio, select **Build → Generate Signed App Bundle or APK… → APK**.
   - Choose or create your `.jks` keystore file, enter the key alias and passwords, and select the `release` build variant.
   - Or via command line with environment variables configured:
     ```bash
     ./gradlew assembleRelease
     ```
   - Output path: `app/build/outputs/apk/release/app-release.apk`.
