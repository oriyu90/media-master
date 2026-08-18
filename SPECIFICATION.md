# Media Master Specification

## 1. Introduction
Media Master is a comprehensive Android application designed to manage, browse, and organize media files (photos, videos, audio, and documents) natively on the device. It focuses on a clean, modern user experience following Material Design 3 guidelines (Hallmark design).

## 2. Architecture & Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Concurrency**: Kotlin Coroutines and Flows
- **Local Persistence**: SharedPreferences via DataStore / Preferences
- **Media Playback**: AndroidX Media3 (ExoPlayer)
- **Background Tasks**: WorkManager for scheduled backups
- **Permissions**: Accompanist Permissions (supporting API 33/34 granular media permissions)
- **Image Loading**: Coil

## 3. Core Features
1. **Media Library**: View local images and videos sorted by dates or albums. Supports grid layouts and selection modes. Includes an integrated viewer for full-screen media playback/viewing.
2. **Audio Player**: Browse tracks and playlists, with an embedded Media3 playback service supporting background audio, shuffle, and skip capabilities.
3. **File Manager**: Explore internal and external storage directories with standard file operations (copy, move, delete).
4. **Document Scanner**: Built-in integration with GMS Document Scanner to digitize physical documents to PDF or JPEG formats.
5. **Video Editor**: Trim and mute videos using ExoPlayer + MediaMuxer/MediaExtractor.
6. **Backup System**: WorkManager-based scheduled backup engine. Archives target directories into ZIP files based on custom constraints (charging, Wi-Fi).
7. **Storage Cleaning**: Analyzes storage and identifies duplicate files across the device using MD5 hashing.
8. **Settings & Customization**: Dynamic theme colors, language selection (multi-lingual support), folder exclusions, and backup configurations.

## 4. UI/UX Design Guidelines (Hallmark)
- **Edge-to-Edge**: The app utilizes `enableEdgeToEdge()` and handles `WindowInsets` properly in Compose Scaffolds.
- **Dynamic Color**: Material You dynamic color scheme is enabled by default for Android 12+ devices, gracefully degrading to a bright custom theme on older OS versions.
- **Typography**: Uses Material 3 canonical typographies (`bodyLarge`, `headlineSmall`, etc.) with semi-bold emphases where applicable.
- **Components**: Adheres to modern guidelines using `LargeTopAppBar`, `LazyVerticalGrid`, and elegantly padded `Card` surfaces. Touch targets conform to a minimum of 48dp.
- **Dark Mode**: Fully supports automatic system dark mode switching.

## 5. Security & Privacy
- Relies on system-provided `MediaStore` and `Storage Access Framework (SAF)` APIs where necessary.
- Requests only the required subset of permissions (e.g., granular `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED` on Android 14+).

## 6. Target Devices
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Form Factors**: Adaptive layouts tailored for Phones, Foldables, and Tablets.
