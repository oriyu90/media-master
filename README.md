# Media Master

Media Master is an open-source Android media and file manager built with Kotlin and Jetpack Compose. It combines photo/video browsing, audio playback, document scanning, storage tools, app/APK management, editing, backup, and a desktop-friendly file browser in one app.

Media Master は、Kotlin と Jetpack Compose で開発されたオープンソースのAndroid向けメディア・ファイル管理アプリです。写真・動画、音楽、書類、ストレージ、アプリ／APK、編集、バックアップを1つのアプリで扱えます。

## v1.2.0

- **Universal document viewer:** Media Master can now open text/log, CSV/TSV, JSON, Markdown, PDF, and `.docx`/`.pptx` files inside the app — no more automatic hand-off to another app for these types. A hex/ASCII fallback view means any other file can still be inspected in-app rather than being refused.
  - Text/CSV/JSON use a lightweight built-in charset sniffer (BOM, UTF-8 validity, Shift_JIS heuristic) so Japanese text files decode correctly instead of showing mojibake; large files are capped with a "load more" control instead of loading unbounded data into memory.
  - CSV/TSV render as a fixed-header, horizontally-scrollable table.
  - Markdown renders with headings/bold/italic/lists/quotes/code blocks (via `commonmark`), with a source/rendered toggle.
  - PDF pages render one at a time via the platform `PdfRenderer` (bitmaps are recycled per page, so large PDFs don't balloon memory).
  - `.docx`/`.pptx` are read with a dependency-free zip+XML parser (paragraph text, bold/italic, headings, inline images) rather than a full office library.
  - Legacy binary `.doc`/`.ppt` (pre-2007) are **not** rendered in-app: Apache POI was evaluated and rejected because it fails to `dex` below `minSdk 26`, which would have dropped Android 7.0/7.1 support. They keep the previous "open in another app" behaviour.
- **Default-app candidate:** new `ACTION_VIEW` intent-filters (text/plain, text/csv, text/markdown, application/json, application/pdf, docx, pptx) let Android offer Media Master in the "Open with"/default-app chooser for these types; Settings → Default apps links to the system screen where a default choice can be reviewed/cleared.
- **ja/en complete, zh/ar/nl kept in sync:** all new viewer strings ship in the same 5 locales as the rest of the app.
- Version `1.2.0` (`versionCode 6`), signed with the same upload key as v0.1.0–v1.1.0 (APK Signature Scheme v2+v3 verified). SHA-256: `a9be2530fc51397582a820b9e9c7404dad3d1374d685838e0170de495e591c33`.

## v1.1.0

- **Release:** version `1.1.0` (`versionCode 5`), signed with the same upload key as v0.1.0–v1.0.0 (APK Signature Scheme v2+v3 verified). SHA-256: `fe75d958b3c811a48582058903d95947b1f97ad108b8c23fd5338ec6ae8eb9a3`.
- **DeX + normal mode:** the desktop shell now requires desk `uiMode` *and* ≥600 dp window width (small DeX windows keep the touch UI); `density|layoutDirection|uiMode` added to `configChanges` so docking no longer recreates the activity and wipes navigation; MiniPlayer is now overlaid in DeX too; the DeX sidebar, Manage dashboard and folder picker all resolve volumes through the single `MediaRepository.storageRoots()` (internal/SD/USB incl. `SECONDARY_STORAGE`), so both shells always see identical files.
- **File engine:** `FileViewModel` split into MVI layers (`files/MediaRepository`, `files/DuplicateFinder`, `MediaFile.kt` models); MediaStore queries run in bounded 2000-row LIMIT/OFFSET pages with stable `_ID` order (no giant CursorWindow); excluded folders migrated from raw `SharedPreferences` to DataStore with a one-time union migration.
- **Player:** the viewer shares one `ExoPlayer` per session (`setMediaItem` on page change; neighbours show placeholders) instead of up to three per-page instances. Audio playback keeps using the single shared `PlaybackManager` controller.
- **Leaner build:** unused `retrofit`/`moshi`/`firebase-ai`/`firebase-appcheck`/`room` dependencies and the `ksp`/`secrets`/`google-services` plugins removed (`kotlinx-coroutines-play-services` is now a direct dependency for ML Kit `await()`); `.env.example` no longer advertises a dead `GEMINI_API_KEY`.
- **ja/en complete:** backup time labels (`hour_abbrev`/`minute_abbrev`: h/m, 時/分) and trim slider TalkBack labels are now resources in en/ja/zh/ar/nl; dates/sizes stay locale-aware; no hardcoded UI text remains.
- Paging3 full-UI adoption (one `mediaState` feeds six screens) stays a v1.2.0 candidate by design: the repository paging groundwork above is the safe intermediate step.

## v1.0.0

- **Formal release:** version `1.0.0` (`versionCode 4`), signed with the same upload key as v0.1.0–v0.3.0 (APK Signature Scheme v2+v3 verified). SHA-256: `255d8ed2b60e1f7a3dd51d1f933b08ae39cc7fa9a8398149202cb20d038b0082`.
- **Stability first:** every delete/uninstall/restore now asks for confirmation inside the app; bulk uninstall fires a single system prompt; `FileUriExposedException` crash paths removed; `FLAG_GRANT_READ_URI_PERMISSION` on all shares; SAF backup folders persist across reboots; all stream/`PdfRenderer` leaks closed with `use{}`.
- **State races removed:** all `MutableStateFlow` writes go through `update{}`; parallel reloads are job-guarded so a late error can no longer clobber fresh data; delete failures surface as toasts instead of `printStackTrace`.
- **Viewer fixed:** load errors show a retryable error state (no more infinite spinner), missing files show an empty state (no more blank `?: return` screens), OCR runs a single Japanese recognizer off the main thread with proper `close()`, OCR text is exposed to TalkBack.
- **File engine:** deprecated storage APIs replaced, `Downloads` matching is case-insensitive, MIME table extended (`heic/heif/avif/bmp/svg/mov/webm/m4a/aac/opus/csv/json/md/html/epub/zip`, real office MIME types), duplicate detection uses head+tail sampled hashes and never groups unreadable files, `FileProvider` covers cache/files paths.
- **Hallmark UI pass:** shared `Hallmark` spacing/radius/control tokens, `ConfirmDeleteDialog` + `HallmarkDivider`, stable `key={path}` on every media list (index-based rows removed), `collectAsStateWithLifecycle` everywhere including `MainActivity`, default Scaffold insets restored (no more `WindowInsets(0)` lists hidden behind navigation bars), `parent_folder`/`retry` strings localized in ja/zh/ar/nl.
- Toolchain unchanged from v0.3.0 (Compose BOM 2025.05, Navigation 2.9, JDK 17, R8 + resource shrink).

## v0.3.0

- **External access:** other apps can open a specific feature via the dedicated `mediamaster://` scheme (Library, Audio, Documents, Manage, Apps, Clean, Settings, `browse?path=`, `edit/image?uri=`, `edit/video?uri=`), and Media Master now appears in the system "Open with" / "Edit with" chooser for images, video, audio and folders. Malformed external intents open the app normally and never perform destructive actions.
- **Network storage:** add SMB shares and WebDAV servers under Settings → Server connection; passwords are stored with `EncryptedSharedPreferences`. Browse remote folders and open files through the system chooser.
- **UI remade against `compose-kotlin-agent-skills`:** a full Material 3 colour system verified for WCAG AA contrast in light and dark, an explicit type scale, shared empty/error/loading components, and a strict-MVI-leaning state model. Every top-level feature is reachable directly from Home. The image/video editors and the excluded-folders screen are now wired into the app.
- **Accessibility:** 48 dp minimum touch targets, `selected`/`Role` semantics on selectable items, merged descendants on cards, assertive live regions on error text, localised date formatting, and `<plurals>` for counts (six categories for Arabic).
- **Safety:** zip-slip guard on restore, no more main-thread file I/O, no `!!` on nullable UI state, no state writes during composition, listener-driven playback position instead of polling loops, atomic DataStore updates.
- Toolchain moved to Compose BOM 2025.05, Navigation 2.9, JDK 17 source level; release builds now run R8 with resource shrinking.

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
- JDK 21 (the source compatibility target is Java 17)
- Android 7.0 (API 24) or newer

See the "External access" and "Network storage" sections below for the new integration points.

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
- `ACTION_VIEW` with `text/plain`, `text/csv`, `text/markdown`, `application/json`, `application/pdf`, `.docx`, or `.pptx` → opens the universal document viewer directly on the incoming URI (v1.2.0+). Legacy `application/msword`/`application/vnd.ms-powerpoint` are intentionally not registered — see the v1.2.0 changelog.

Unknown or malformed requests simply open the app normally. Destructive
operations (delete, uninstall, restore-from-backup) are never performed from an
external intent — they always require an explicit in-app confirmation.

### 外部連携（他アプリから機能を直接開く）

他のアプリから Media Master の特定機能を直接開けます。`mediamaster://` スキームは
`BROWSABLE` を付けていないため、Web ページからは起動できず、端末上のアプリが明示的に
Intent を組み立てた場合のみ動作します。対応する URI は上表のとおりです。標準の
`ACTION_EDIT`（`image/*`・`video/*`）、`ACTION_VIEW`（メディア／フォルダ）にも登録されます。
v1.2.0 からは `ACTION_VIEW` の `text/plain`・`text/csv`・`text/markdown`・
`application/json`・`application/pdf`・`.docx`・`.pptx` も汎用ドキュメントビューアーへ
直接ルーティングされます（旧形式の `.doc`/`.ppt` は意図的に対象外）。
未知・不正なリクエストは通常どおりアプリを開くだけで、削除・アンインストール・バックアップ復元
などの破壊的操作が外部 Intent から実行されることはありません。

## License

Copyright © 2026 Yuki_Orita. Released under the [MIT License](LICENSE).
