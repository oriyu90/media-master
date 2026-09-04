# Media Master

Media Master is an open-source Android media and file manager built with Kotlin and Jetpack Compose. It combines photo/video browsing, audio playback, document scanning, storage tools, app/APK management, editing, backup, and a desktop-friendly file browser in one app.

Media Master は、Kotlin と Jetpack Compose で開発されたオープンソースのAndroid向けメディア・ファイル管理アプリです。写真・動画、音楽、書類、ストレージ、アプリ／APK、編集、バックアップを1つのアプリで扱えます。

## v0.2.0

- DeX shell is enabled only when Android reports desk mode; wide tablets and foldables retain the standard UI
- File browser now lists actual accessible filesystem entries and available storage volumes, rather than only MediaStore-indexed media
- Document list detects local PDFs and office documents (`doc`, `docx`, `odt`, `rtf`, `txt`, spreadsheets, and presentations)
- Scans are saved to shared Documents storage; multi-page scans can also be exported as individual JPG files to Pictures
- Requests Android's all-files access when required for full file-manager and document-discovery functionality
- Published as a signed APK on GitHub Releases

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

The desktop shell is enabled only when Android reports desk mode (including Samsung DeX). Wide tablets, foldables, landscape orientation, and split-screen do not trigger it. Folder drag and drop uses Android's standard drag-and-drop framework; long-click provides the same menu on touch devices and maps to contextual mouse interaction in desktop environments.

## Storage access

On Android 11 and later, Media Master asks for Android's **all files access** before it opens the full file-manager interface. This is necessary to enumerate ordinary folders and local documents that are not MediaStore media, and to provide a complete internal/external-storage view.

## Network storage (SMB / WebDAV)

Add SMB shares and WebDAV servers under **Settings → Server connection**. For
each location you provide a display name, protocol, host, optional port, share or
path, optional sub-folder, and credentials. Passwords are stored with
`EncryptedSharedPreferences` (AES-256, key in the Android Keystore) — never in
plain text and never in the location's JSON.

Browsing a location lists its directories and files; opening a file downloads it
to the app cache and hands it to the system "Open with" chooser. All network
calls run off the main thread with connect/read timeouts, and a bad host, wrong
password, or timeout shows an error rather than crashing.

### ネットワークストレージ（SMB / WebDAV）

**設定 → サーバー接続** から SMB 共有・WebDAV サーバーを追加できます。表示名・
プロトコル・ホスト・ポート（任意）・共有名/パス・サブフォルダ（任意）・認証情報を
登録します。パスワードは `EncryptedSharedPreferences`（AES-256、鍵は Android
Keystore）で暗号化保存し、平文や場所の JSON には保存しません。ファイルを開くと
キャッシュへダウンロードしてシステムの「アプリで開く」に渡します。接続失敗・
認証エラー・タイムアウトはクラッシュせずエラー表示になります。

## External access (open a feature from another app)

Other apps can open a specific Media Master feature directly.

### `mediamaster://` scheme

A dedicated, documented entry point. It is **not** marked `BROWSABLE`, so a web
page cannot trigger it — only an app on the device that builds the intent
explicitly.

| URI | Opens |
| --- | --- |
| `mediamaster://home` | Home |
| `mediamaster://library` | Library (photos & videos) |
| `mediamaster://audio` | Audio |
| `mediamaster://documents` | Documents / scanner |
| `mediamaster://manage` | Manage (storage dashboard) |
| `mediamaster://apps` | App & APK manager |
| `mediamaster://clean` | Duplicate cleanup |
| `mediamaster://settings` | Settings |
| `mediamaster://browse?path=/storage/emulated/0/Download` | File browser at an absolute path |
| `mediamaster://edit/image?uri=<content-uri>` | Image editor for a URI the caller has granted read access to |
| `mediamaster://edit/video?uri=<content-uri>` | Video editor |

```sh
adb shell am start -a android.intent.action.VIEW \
  -d "mediamaster://library" com.yukiorita.mediamaster
```

### Standard intents

Media Master also registers for the system chooser:

- `ACTION_EDIT` with `image/*` or `video/*` → the corresponding editor
- `ACTION_VIEW` with `image/*`, `video/*`, or `audio/*` → opens the app at Library
- `ACTION_VIEW` with `vnd.android.document/directory` → file browser at that folder

Unknown or malformed requests simply open the app normally. Destructive
operations (delete, uninstall, restore-from-backup) are never performed from an
external intent — they always require an explicit in-app confirmation.

### 外部連携（他アプリから機能を直接開く）

他のアプリから Media Master の特定機能を直接開けます。`mediamaster://` スキームは
`BROWSABLE` を付けていないため、Web ページからは起動できず、端末上のアプリが明示的に
Intent を組み立てた場合のみ動作します。対応する URI は上表のとおりです。標準の
`ACTION_EDIT`（`image/*`・`video/*`）、`ACTION_VIEW`（メディア／フォルダ）にも登録されます。
未知・不正なリクエストは通常どおりアプリを開くだけで、削除・アンインストール・バックアップ復元
などの破壊的操作が外部 Intent から実行されることはありません。

## License

Copyright © 2026 Yuki_Orita. Released under the [MIT License](LICENSE).
