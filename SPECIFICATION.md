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
4b. **ZIP Archive Viewer** (v1.10.0+): ZIP files open inside the app from both the file browser and external `ACTION_VIEW` (`application/zip`, `application/x-zip-compressed`, `application/x-zip`, plus `.zip` extension fallback when the sender omits the MIME type). Contents list with sizes; **Extract all** to a user-picked folder with zip-slip + decompression-bomb guards (2,000 entries / 100MB per file / 500MB total, streaming 32KB). Corrupt archives show an empty/error state, never crash.
4c. **Desktop list default + Explorer Details** (v1.10.0+): desktop/DeX file browser and category screens default to LIST (phone keeps GRID for browser and photo/video galleries; explicit user toggles persist). List rows show Size / Date modified / Date created / Type with per-column show/hide (Win11 Details parity; Name always shown; Created hidden by default, Date added from MediaStore DATE_ADDED, filesystem fallback reuses modified on minSdk 24). Desktop lists add a tappable header row that sorts.
4d. **Accurate folder names** (v1.10.0+): every surface (browser title, breadcrumb root, desktop sidebar, exclude picker, pins, new-tab titles, category tabs) shows the real volume/folder name (`ABCD-1234`, …) with full-path disambiguation for same-named folders (previously only Manage did; others showed a generic label and merged same-named folders).
4e. **Universal Document Viewer** (v1.2.0+): Text/log, CSV/TSV, JSON, Markdown, PDF, `.docx`/`.pptx`, and `.tex` source (v1.3.0+) files open inside the app (no automatic hand-off to another app); any other file falls back to a paged hex/ASCII view rather than being refused. A lightweight built-in charset sniffer (BOM/UTF-8/Shift_JIS) avoids mojibake on non-UTF-8 text files. Legacy binary `.doc`/`.ppt` remain external-only — see "Known limitations" below. Media Master registers `ACTION_VIEW` intent-filters for the supported types so it can be chosen as a default app. The document viewer's share and delete actions work for any file it can display; delete is available whenever Media Master itself resolved the file's on-device path (never for an arbitrary external URI handed in by another app).
4c. **LaTeX Math Rendering** (v1.3.0+): `$...$`/`$$...$$` math in Markdown, and the native LaTeX math delimiters (`\(...\)`, `\[...\]`, `equation`/`align`/`gather`/`eqnarray`/`multline` environments) in `.tex` source files, are typeset with a bundled, fully offline copy of KaTeX (MIT license) rendered in a sandboxed WebView — no network access, no `trust`-gated LaTeX commands (`\href`, `\includegraphics`, etc. stay disabled per KaTeX's default). `.tex` files are not compiled; non-math source text is shown verbatim.
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
- **Target SDK**: 36
- **Form Factors**: Adaptive layouts tailored for Phones, Foldables, and Tablets.
- **Desktop / PC modes (v1.7.0+)**: Samsung DeX (classic + One UI 8 native), Android Desktop Windowing, Motorola Smart Connect / Ready For, Huawei EMUI Desktop Mode, HONOR Desktop Mode, Xiaomi HyperOS Desktop / Workstation Mode, OPPO ColorOS PC Mode, Lenovo Tab PC Mode. Unified dependency-free detection with ≥600dp width gate plus Settings → Desktop mode manual override (Auto / Always desktop / Always touch).

## 7. Known limitations
- Legacy binary Office formats (`.doc`, `.ppt` — pre-2007) are not rendered in-app. Apache POI was evaluated as the renderer but rejected: its core module uses `java.lang.invoke.MethodHandle` in a way D8 cannot dex below `minSdk 26`, which would drop Android 7.0/7.1 support. These files keep the "open in another app" behavior instead.
- `.docx`/`.pptx` rendering favors correct, mojibake-free text and inline images over pixel-perfect page layout (no full office-document layout engine is used).
- `.docx`/`.pptx` list numbering (`w:numPr`) isn't resolved; numbered/bulleted content renders as plain paragraphs.
- Markdown tables (GFM extension) aren't supported; table rows render as plain text.
- `.tex` files are not compiled — only recognised math regions are typeset; macros, packages, and custom commands outside of math mode have no effect on the rendered output.
- A document with a very large number of distinct inline math (`$...$`) expressions can be slow to render, since each one is backed by its own small WebView.
- ZIP extraction caps at 2,000 entries / 100MB per file / 500MB total; larger or hostile archives extract partially (counted, never crash). Unsafe entries (`..`, absolute paths, drive letters) are skipped.
- File creation time uses MediaStore DATE_ADDED when indexed; non-indexed files reuse last-modified (true creation time needs API 26+ NIO, incompatible with minSdk 24). Created is hidden when identical to Modified.
