# Media Master

Media Master is an open-source Android media and file manager built with Kotlin and Jetpack Compose. It combines photo/video browsing, audio playback, document scanning, storage tools, app/APK management, editing, backup, and a desktop-friendly file browser in one app.

Media Master は、Kotlin と Jetpack Compose で開発されたオープンソースのAndroid向けメディア・ファイル管理アプリです。写真・動画、音楽、書類、ストレージ、アプリ／APK、編集、バックアップを1つのアプリで扱えます。

## v0.1.0

- Responsive layouts for phones, tablets, foldables, landscape windows, and desktop-class windows
- Finder-inspired DeX/Android desk-mode layout with tabs and a persistent sidebar
- Default shortcuts for Pictures, Downloads, APK, internal/external storage, and network storage
- Folder pinning by drag and drop; long-click/right-click actions for opening a new tab or removing a pin
- List separators, centered icon containers, adaptive grids, and improved touch targets
- Complete UI resources for English, Japanese, Simplified Chinese, Arabic (RTL), and Dutch
- Media library, audio playlists, document scanner/PDF handling, file browser, cleanup, APK manager, editors, backup and restore

## Requirements

- Android Studio with Android SDK 36
- JDK 21 (the source compatibility target is Java 11)
- Android 7.0 (API 24) or newer

## Build

Open the project directory in Android Studio, or run:

```sh
./gradlew :app:assembleDebug
```

The generated debug APK is placed under `app/build/outputs/apk/debug/`.

## Release signing

Signing secrets are intentionally not committed. Create a keystore and provide its values as environment variables:

```sh
export KEYSTORE_PATH=/absolute/path/to/my-upload-key.jks
export KEY_ALIAS=upload
export STORE_PASSWORD='your-password'
export KEY_PASSWORD='your-password'
./gradlew :app:assembleRelease
```

The generated release APK is placed under `app/build/outputs/apk/release/`. Never commit a keystore, `.env`, or signing password.

## DeX and desktop mode

The desktop shell is enabled when Android reports desk mode. A width-based fallback also enables it for desktop windowing and sufficiently wide tablet/foldable windows. Folder drag and drop uses Android's standard drag-and-drop framework; long-click provides the same menu on touch devices and maps to contextual mouse interaction in desktop environments.

## License

Copyright © 2026 Yuki_Orita. Released under the [MIT License](LICENSE).
