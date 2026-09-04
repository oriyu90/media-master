# Media Master v0.2.0 実装・保守メモ

最終更新: 2026-08-27

## リリース情報

| 項目 | 内容 |
| --- | --- |
| バージョン | `0.2.0` (`versionCode 2`) |
| アプリケーションID | `com.yukiorita.mediamaster` |
| 最小 SDK / target SDK | 24 / 36 |
| ライセンス | MIT |
| 著作者 | Yuki_Orita |
| release APK | `app/build/outputs/apk/release/app-release.apk` |
| GitHub Release | `v0.2.0` (GitHub Releases) |

release APK は RSA 4096 ビット鍵で APK Signature Scheme v2 署名する想定です。公開前には毎回 `apksigner verify --verbose` で署名を確認してください。

## v0.2.0 の実装内容

### DeX 判定

- Finder風デスクトップUIは `Configuration.UI_MODE_TYPE_DESK` が返る場合だけ有効化するよう変更しました。
- 画面幅 `840dp` 以上という推測条件を撤廃したため、通常のタブレット、折りたたみ端末、横画面、分割画面ではDeX UIに誤遷移しません。

### ストレージ・ドキュメント

- Android 11以降は「すべてのファイルへのアクセス」を明示的に要求し、メディアだけでなくフォルダや一般ファイルを適切に列挙します。
- ファイル一覧は `File.listFiles()` を主として実体を表示し、MediaStoreはMIMEタイプ・共有URIの補完に使用します。MediaStore未登録のフォルダや文書が消える問題を解消します。
- 内部・外部ストレージのルートを検出し、PDF、Word、OpenDocument、RTF、テキスト、表計算、プレゼンテーションをドキュメント一覧へ表示します。
- スキャンPDFは共有Documents領域の `Documents/Media Master/` に保存します。複数ページを個別JPGとして `Pictures/Media Master Scans/` へ書き出せます。

## 今回の実装内容

### レスポンシブ UI

- 固定の正方形カードを最小高ベースのカードへ変更し、縦長・横長・分割画面でも内容が切れにくいレイアウトへ修正。
- 画像・動画・編集画面は `ContentScale.Fit` と画面高に応じた操作パネルを使用。
- カテゴリ一覧は狭い画面で1列、通常幅以上では2列に適応。
- リスト項目には `outlineVariant` の1dp区切り線を追加。
- アイコンを含むボタン・カードは `Box(contentAlignment = Alignment.Center)` を用い、アイコン位置を中央揃えに統一。
- 破損していた密度別 WebP ランチャーアイコンをベクターのレイヤーリストへ置換。元のWebPはワークスペース直下の `media-master.zip` から復元可能です。

### DeX / デスクトップ UI

`ui/DesktopNavigation.kt` に Finder 風のデスクトップシェルを実装しています。

- Android が desk mode を返す場合にのみ有効化。
- 初期画面は **ホーム**。ライブラリ、オーディオ、ドキュメントへの入口を表示。
- 左サイドバーからホーム、ライブラリ、オーディオ、ドキュメント、管理、アプリ、設定へ移動可能。
- 端末欄には内部ストレージ、外部ストレージ、ネットワークストレージを表示。
- 標準ピン留めは `Pictures`、`Download`、`apk`。
- ファイル一覧のフォルダはドラッグ＆ドロップでサイドバーへ追加可能。
- 長押しまたはマウス右クリックから「新しいタブで開く」「サイドバーにピン留め」、ピン留め済み項目では「サイドバーから削除」を実行可能。
- ピン留め一覧は DataStore の `pinned_folders` に保存。

ネットワークストレージは、設定済みサーバーURLを表示するための入口です。ネットワークプロトコル（SMB / WebDAVなど）のファイル列挙そのものは未実装のため、今後追加する場合は専用クライアントと認証情報の安全な保管が必要です。

### 多言語対応

次の5言語に対し、全175文字列キーを同数で揃えています。

- 英語: `values/`
- 日本語: `values-ja/`
- 簡体字中国語: `values-zh/`
- アラビア語（RTL）: `values-ar/`
- オランダ語: `values-nl/`

アプリ内設定に加え、Android 13以降のアプリ言語設定でも認識できるよう `res/xml/locales_config.xml` を追加済みです。

## 外部連携 API（2026-09 追加 / Phase 1）

他アプリから Media Master の各機能を「アクセス先を指定して」開けるようにした。設計上の要点:

- **専用スキーム `mediamaster://`**: `com.example.deeplink.DeepLinks` が受信 Intent を内部ナビ
  ルート文字列へ変換する単一の allowlist。`AndroidManifest.xml` の `mediamaster` スキームの
  `intent-filter` には **`android.intent.category.BROWSABLE` を付けない**（Web ページから任意に
  起動されないため）。
- 対応ホスト（引数なし）: `home` `library` `audio` `documents` `manage` `apps` `clean` `settings`。
  引数あり: `browse?path=<絶対パス>`（`file_browser?path=` へ）、`edit/image?uri=<uri>` /
  `edit/video?uri=<uri>`（既存の `imageEditor`/`videoEditor` ルートはもともと URI 文字列引数を取る）。
- **標準 Intent**: `ACTION_EDIT`(`image/*`,`video/*`) → 各エディタ。`ACTION_VIEW`(`image/*`,
  `video/*`,`audio/*`) → 現状は `library` を開く（外部 URI 単体表示の `ViewerScreen` 対応は
  後続フェーズ）。`ACTION_VIEW`(`vnd.android.document/directory`) → `file_browser`。
- `MainActivity`: `android:launchMode="singleTask"`。`onCreate` と `onNewIntent` で
  `DeepLinks.resolve(intent)` を `MutableStateFlow` に載せ、`MainNavigation` が
  ストレージ権限付与後に一度だけ `navController.navigate(...)`（`runCatching` で
  未知ルートを握りつぶし、**不正 Intent でクラッシュしない**）。
- **破壊的操作（削除・アンインストール・バックアップ復元）は外部 Intent から到達不可**。必ず
  アプリ内 UI で明示確認する。
- `FileProvider`（authority `${applicationId}.fileprovider`, `res/xml/file_paths.xml`）を
  Manifest に宣言。従来 `FilesScreen`/`AppManagerScreen`/`CategoryScreen` が同 authority を
  参照していたが Manifest 未宣言で共有・APKインストールが失敗していた潜在バグも解消。
- 検証: `DeepLinksTest`（Robolectric）でパーサを網羅。手動疎通は
  `adb shell am start -a android.intent.action.VIEW -d "mediamaster://<host>" com.yukiorita.mediamaster`。

## デザインシステム（2026-09 / Phase 2）

- `ui/theme/Color.kt`: Material 3 の全ロールを light/dark 両方で明示定義。アンバーのブランド色から
  導出し、**すべての前景/背景の文字ペアが WCAG 2.1 AA（本文 4.5:1・大文字 3:1）以上**になるよう
  手調整（`ColorUtils.calculateContrast` 相当で検証）。従来 `onPrimary=白` on 明るいアンバー地で
  AA 未達だったのを、light は深いアンバーブラウンの `primary`＋白文字、明るいアンバーは
  `primaryContainer`/`inversePrimary` へ移動。ビューア用のダーク固定トークン（`ViewerSurface` 等）も
  追加し、`ViewerScreen` などの生 `Color.Black/White` を段階的に置換する。
- `ui/theme/Type.kt`: M3 タイプスケールを全ロール明示。CJK・アラビア語で行が詰まらないよう
  `lineHeight` と `LineHeightStyle` を全ロールに設定。
- `ui/theme/Theme.kt`: `MediaMasterTheme`（`MyApplicationTheme` は `@Deprecated` エイリアスで後方互換）。
  **動的カラー（Material You）は既定 OFF**（端末生成配色はコントラストを保証できないため）。
  edge-to-edge のシステムバーアイコン明暗を `WindowCompat` で制御。
- `res/values/themes.xml`: `Theme.MediaMaster`（`Theme.AppCompat.DayNight.NoActionBar` 継承。
  AppCompat 継承は per-app locale のために必須）。旧 `Theme.MyApplication` は別名で残す。
  `window_background` を light/dark（`values-night/`）で定義。
- `ui/components/CommonUi.kt`: `SectionHeader` / `EmptyState` / `ErrorState`（`liveRegion`）/
  `LoadingButton` / `StatusPill`。全画面リメイクで共通利用する。
- ツールチェーン: Compose BOM `2025.05.00`、lifecycle `2.9.0` へ引き上げ（Navigation の
  `2.9.x` 化は Phase 3）。BOM 更新に伴い `FilesScreen` の `dragAndDropSource` を
  新 API（`transferData` ラムダ形式、長押し検出は API 内蔵）へ移行。

## ホーム & ナビ（2026-09 / Phase 3）

- Navigation `2.9.0` へ引き上げ。**ルート文字列方式は維持**（`@Serializable` 型安全ルートへの全面移行は
  各画面フェーズ 4–6 で段階的に行い、回帰リスクをフェーズ内に閉じる）。
- `HomeScreen`: 全トップレベル機能（Library / Audio / Documents / Manage / Apps / Clean）を
  データ駆動のカードグリッドで**ホームから直接到達可能**に（従来 Apps/Clean は Manage 配下のみ、
  Settings は右上のみだった）。`LargeTopAppBar` + `exitUntilCollapsedScrollBehavior`、
  `contentWindowInsets = WindowInsets(0)`。カードは `semantics(mergeDescendants)`、最小高 148dp。
- `DesktopHomeScreen`: カードに Manage / Apps / Settings を追加。
- `DesktopNavigation` アクセシビリティ: タブ閉じるボタンとサイドバー項目を 48dp 以上へ、
  タブ列の高さも 48dp、サイドバー項目に `semantics { selected }`、`PinnedFolderItem` に
  右クリック/長押し以外の代替として末尾「その他」IconButton を追加。
- `PermissionScreen`: `WindowInsets.safeDrawing` パディング、ボタンを全幅・48dp、
  文字色を `onSurface`/`onSurfaceVariant` で明示。
- 新規文字列 `home_desc_apps` を en/ja/zh/ar/nl の5ロケールに追加（同数維持）。

## ライブラリ・ビューア・エディタ（2026-09 / Phase 4）

- **i18n バグ修正**: `AdjustmentType` のラベルが Kotlin 内ハードコード日本語だったのを `@StringRes`
  化し、`res/values*/strings_editor.xml` に9キー×5ロケールを追加（en/ja/zh/ar/nl 同数）。
- **`<plurals>` 導入**: `res/values*/plurals.xml` に `items_selected` / `duplicate_groups_found`。
  ar は zero/one/two/few/many/other の6分類。選択数タイトルの文字列連結
  （`"${n} ${selected}"`）を `pluralStringResource` へ置換（Library、以降のフェーズで他画面も）。
- **共通部品適用**: `LibraryScreen` / `AlbumScreen` の空表示に `EmptyState`、エラー表示に
  `ErrorState`（assertive live region）。`AlbumScreen` は `when (val vs = viewState)` で
  危険キャストを排除。アルバム名ラベルのスクリムを縦グラデーションに。
- **`ViewerScreen`**: OCR ドラッグ選択の `!!`（`selectionStart!!`/`selectionCurrent!!`）を
  ローカル束縛へ、重複していた `DisposableEffect(pageUri){exoPlayer.release()}` を削除
  （二重 release 回避）。トップバー／背景の生 `Color.Black/White` を `ui/theme` の
  `Viewer*` トークンへ。トップバーに **「編集」アクション**を追加し画像/動画エディタへ遷移
  （孤立していた `imageEditor`/`videoEditor` ルートを内部から到達可能に）。
- **`ImageEditorScreen`**: 合成中に `isPerspectiveMode` を書き込んでいた anti-pattern を、
  `pagerState.currentPage == 2` からの派生値へ変更（`pagerState` を状態宣言部へホイスト）。
  `mutableStateOf(0f)` → `mutableFloatStateOf`。`DraggableCorner` は毎フレームの
  `resources.displayMetrics.density` 読みを `LocalDensity` へ、`contentDescription` を付与。
- **`VideoEditorScreen`**: `mutableStateOf(0L/0)` → `mutableLongStateOf`/`mutableIntStateOf`。
- **`AdjustmentControls`**: `AdjustmentType.values()` → `entries`、チップを 48dp・
  `Role.Tab` + `selected` セマンティクスへ。
- 補足: `ViewerScreen` の OCR 座標計算と `?: return` を含む本格的なビューア再設計は、
  回帰リスク管理のため専用フォローアップに切り出し（本フェーズは安全な部分改修に限定）。

## オーディオ・再生（2026-09 / Phase 5）

- **`PlaybackManager`**: 宣言だけで未更新だった `currentPosition` を実装。内部 `CoroutineScope`
  で再生中のみ 0.5 秒間隔で更新するティッカーを `onIsPlayingChanged` から起動/停止し、
  `onMediaItemTransition` / `onPositionDiscontinuity` でも即時反映。`release()` でティッカー停止＋
  状態リセット。
- **`MiniPlayer`**: `collectAsState()` → `collectAsStateWithLifecycle()`。合成内の
  `while(true){ delay(1000) }` ポーリングを撤廃し `PlaybackManager.currentPosition` を購読。
  `WindowInsets.navigationBars` を自前でパディングし、`MainNavigation` 側で
  `Modifier.align(Alignment.BottomCenter)` を付与（従来は Box 内で左上に描画され得た）。
  背景を `surfaceContainerHigh`＋`onSurfaceVariant` の副題色でコントラスト確保。
- **`AudioScreen`**: 選択数タイトルを `pluralStringResource` へ。`(viewState as ViewState.Success)`
  の危険キャストを `?.let` へ。空表示 `EmptyState`／エラー `ErrorState`。プレイリスト作成の
  `file.copyTo` を `coroutineScope.launch { withContext(Dispatchers.IO) { … } }` へ（主スレッド I/O 解消）。
  プレイリストカード副題の `onSecondaryContainer.copy(alpha = 0.7f)` を不透明へ。
- **`PlaylistScreen`**: `when (val vs = viewState)` で危険キャスト排除、`EmptyState`/`ErrorState`、
  区切り線・省略表示を追加。

## 管理系・設定（2026-09 / Phase 6）

- **`MediaCategory` enum 新設**（`ui/MediaCategory.kt`）: `key`（ナビ用の非ローカライズ ID）/
  `titleRes` / `icon` / `matches(file)` を一元化。従来 `ManageDashboardScreen` と `CategoryScreen`
  の3箇所に英語 literal で重複していた分類ロジックの単一情報源。
- **`SettingsScreen`**: 空 onClick だった「メディアフォルダ」行 → `exclude_folders` へ遷移
  （孤立画面の配線完了）。バックアップ時間帯ダイアログの入力欄に数値キーボード・`label`・
  桁数フィルタ・`coerceIn(0..23 / 0..59)` を追加。テーマ/言語のラジオ行を `selectableGroup` +
  `Modifier.selectable(role = Role.RadioButton)` + 48dp へ。
- **`CleanScreen`**: `LoadingButton` 共通部品、重複ゼロ時に `EmptyState`、行パスの省略表示。
- **`ManageDashboardScreen`**: `CategoryCard` に `semantics(mergeDescendants)`。
- **`FilesScreen`**: 選択数タイトルを `pluralStringResource` へ。ディレクトリ削除の
  `deleteRecursively()`/`delete()` を `rememberCoroutineScope` + `Dispatchers.IO` へ（主スレッド I/O 解消、
  `runCatching` で保護）。`formatDate` をロケール依存 `DateFormat.getDateInstance(MEDIUM)` へ。
- **`AppManagerScreen`**: ランチャーアイコンの `toBitmap().asImageBitmap()` を
  `remember(packageName)` でキャッシュ（毎再合成の再生成を解消）。選択数タイトルを plural へ。
  行テキストを省略表示。
- **`DocumentsScreen`**: `appendPdfUri!!` / `scannedPdfUri!!` をローカル束縛へ。日付整形を
  ロケール依存へ。選択数タイトルを plural へ。
- **`ExcludeFoldersScreen`**: 合成中の `File.listFiles()` を `LaunchedEffect(currentPath)` +
  `Dispatchers.IO` へ。

## ネットワークストレージ SMB / WebDAV（2026-09 / Phase 7）

`SERVER_URL` 設定の未実装ネットワーク層を実機能化。

- 依存追加: `com.hierynomus:smbj:0.13.0`（SMB）、`androidx.security:security-crypto:1.1.0-alpha06`
  （EncryptedSharedPreferences）。WebDAV は既存 OkHttp を使用。`proguard-rules.pro` に
  smbj/BouncyCastle/slf4j/Tink の keep を追加。
- `com.example.network`:
  - `NetworkModels`: `NetworkProtocol`(SMB/WEBDAV)、`@Serializable NetworkLocation`
    （**パスワードを含まない**）、`NetworkEntry`、`sealed interface BrowseUiState`。
  - `NetworkCredentialStore`: `EncryptedSharedPreferences`（AES-256、鍵は Keystore）。
    キーセット破損時は例外を投げず「パスワード無し」に劣化。
  - `NetworkLocationRepository`: 専用 DataStore(`network_locations`)へ JSON 永続化。
    `upsert`/`delete` は `edit{}` 内で read-modify-write。
  - `NetworkStorageClient`: `list` / `download` とも `Dispatchers.IO`・タイムアウト・
    リソース `use{}`・`Result` 返却で**クラッシュしない**。WebDAV は PROPFIND(Depth 1)+
    DOM パース（名前空間非依存の局所名マッチ）。SMB は smbj `DiskShare`。
  - `NetworkViewModel`: `locations` StateFlow、`browse` StateFlow（Idle/Loading/Ready/Error）。
    ファイルタップ → キャッシュへ DL → `FileProvider` + `ACTION_VIEW`。
- UI: `ui/NetworkScreen.kt`（保存済み一覧＋追加/編集ダイアログ／ブラウズ）。ルート `network` を
  `MainNavigation` に追加。設定「サーバー接続」行と DeX サイドバー「ネットワークストレージ」から遷移。
  旧「サーバー URL」ダイアログは削除。
- 新規文字列 `strings_network.xml` 14キー×5ロケール（en/ja/zh/ar/nl 同数、合計202キー）。
- 実機テストは今サイクル未実施。到達不可・認証失敗系のパスは `Result.failure` で UI にエラー表示。

## 設計評価（2026-09 / Phase 8）

### コントラスト（`ui/theme/Color.kt`、WCAG AA 目標 本文 4.5:1）

`ColorUtils.calculateContrast` 相当で検証。代表ペア（すべて AA 以上）:

| ペア | Light | Dark |
| --- | --- | --- |
| onPrimary / primary | 6.44 | 7.69 |
| onPrimaryContainer / primaryContainer | 7.25 | 7.25 |
| onSurface / surface | 16.4 | 14.3 |
| onSurfaceVariant / surfaceVariant | 7.23 | 5.48 |
| onSurfaceVariant / surfaceContainer | 7.99 | 9.63 |
| onSecondaryContainer / secondaryContainer | 13.3 | 7.25 |
| onError / error | 6.46 | 7.72 |
| outline / surface（非テキスト、3:1 目標） | 4.23 | 5.81 |

ビューアの `Viewer*` トークンは意図的にダーク固定（写真/動画は暗地が最適）。OCR の
`Color.Blue`/`Color.Yellow` は任意画像上の高視認マーカーとして意図的に残置。

### banned-antipattern 対応状況（`compose-kotlin-agent-skills` 表）

| # | 項目 | 状態 |
| --- | --- | --- |
| 3 | ハードコード文字列 | 解消（`AdjustmentType` 日本語 literal → `@StringRes`。新規は全 `stringResource`） |
| 4 | `collectAsState()` on Android | 解消（`MiniPlayer` を `collectAsStateWithLifecycle` へ。全新規コードも） |
| 5 | `_state.value = x` | ネットワーク層は `update`/置換のみ。既存 `FileViewModel` は `ViewState` 据え置き（段階移行） |
| 7 | `items(list)` no key | 主要リストに `key` 付与（Library/Album/Audio/Playlist/Clean/AppManager/Network 等） |
| 15 | LazyColumn 内でソート | 一覧のソートは呼び出し前 / repository 側 |
| 17 | 合成中の状態書き込み | 解消（`ImageEditorScreen.isPerspectiveMode` を派生値へ） |
| 18 | nullable への `!!` | UI コードから除去（Viewer/Documents/FileViewModel） |
| 21 | `SharedPreferences.edit().apply()` | 新規は DataStore。除外フォルダの生 SP は既存機能維持のため据え置き（別課題） |
| 24 | `contentDescription = null` on icons | 機能アイコンにラベル、装飾は明示 `null`。`DraggableCorner` に付与 |
| 25 | `kapt` | 不使用（KSP のみ） |

### 残課題（フォローアップ）

- `FileViewModel`（532行）の完全 MVI 分割は未実施（`ViewState` は安全消費へ移行済み。回帰リスク管理のため据え置き）。
- `ViewerScreen` の OCR 座標計算・合成中 `?: return` の本格再設計。
- `DocumentsScreen.ScanView` の `?: return`（合成中）。
- 除外フォルダの生 `SharedPreferences("media_master_prefs")` を DataStore へ統一。
- ネットワークストレージの実機（SMB/WebDAV サーバー）疎通テスト。
- Firebase AI/AppCheck・Room の未使用依存の撤去（本改修では非対象）。

### 検証手順（リリース前・要実施）

1. `./gradlew :app:assembleRelease`（R8 有効）でビルドが通ること。
2. 署名済み APK を実機へ導入し**全機能スモーク**（ライブラリ/ビューア/編集/オーディオ/
   ドキュメントスキャン/管理/重複クリーン/APK管理/バックアップ/復元/ネットワーク/
   全ディープリンク `am start`）。
3. 回転・分割・DeX・RTL(ar)・ダーク・TalkBack を確認。
4. `apksigner verify --verbose --print-certs` と SHA-256 記録。

## 主要ファイル

| パス | 役割 |
| --- | --- |
| `app/src/main/java/com/example/ui/DesktopNavigation.kt` | DeX / 大画面のサイドバー、タブ、ホーム |
| `app/src/main/java/com/example/ui/MainNavigation.kt` | 通常UI・デスクトップUIの振り分け、ナビゲーション定義 |
| `app/src/main/java/com/example/FilesScreen.kt` | ファイル一覧、区切り線、フォルダのDnDとコンテキスト操作 |
| `app/src/main/java/com/example/SettingsRepository.kt` | DataStore設定（ピン留めを含む） |
| `app/src/main/java/com/example/SettingsViewModel.kt` | 設定操作のViewModel |
| `app/src/main/res/values*/strings*.xml` | UI翻訳リソース |
| `app/build.gradle.kts` | アプリID、v0.2.0、署名設定、依存関係 |
| `README.md` | 利用・ビルド・公開の概要 |
| `LICENSE` | MITライセンス |
| `index.html` / `tokens.css` / `assets/site.css` | 紹介サイト。Cloudflare Pages([https://studio-rizi.pages.dev/projects/media-master/](https://studio-rizi.pages.dev/projects/media-master/))へデプロイ済み |
| `robots.txt` / `sitemap.xml` | 紹介サイトのSEO設定 |

## 開発・検証

前提条件は Android SDK 36 と JDK 21 です。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

主な確認ポイント:

1. phone / tablet / 横画面 / 分割画面で表示が切れないこと。
2. DeX時だけサイドバーとホームが表示され、通常の大画面タブレットでは標準UIのままであること。
3. 内部・外部ストレージで、MediaStore未登録のフォルダと文書が一覧に現れること。
4. PDFとOffice/OpenDocumentファイルがドキュメント一覧に現れ、外部アプリで開けること。
5. スキャン後にPDF保存と複数JPG書き出しができ、それぞれDocuments/Picturesに現れること。
3. フォルダをサイドバーにドラッグしてピン留めできること。
4. 右クリック・長押しでフォルダのコンテキストメニューが開くこと。
5. 各アプリ言語で文字列が英語へフォールバックしていないこと。
6. `app/build/reports/lint-results-debug.html` にエラーがないこと。

## Release ビルドと署名

キーストア、パスワード、`.env` はコミットしません。`.gitignore` に `*.jks` と `*.keystore` を追加済みです。

```sh
export KEYSTORE_PATH=/absolute/path/to/my-upload-key.jks
export KEY_ALIAS=upload
export STORE_PASSWORD='set-in-your-shell'
export KEY_PASSWORD='set-in-your-shell'
./gradlew :app:assembleRelease
```

署名確認:

```sh
$ANDROID_HOME/build-tools/36.1.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

v0.2.0 release APK の SHA-256:

```text
252625e14469d9d9282e90d38512943c23ced02a30698a00be6a1a70fd11d58f
```

```text
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

## 依存関係と保守上の注意

- OCRは ML Kit Text Recognition 16.0.1、Document Scanner は 16.0.0 を利用。16KBページサイズと関連するLint警告を解消済み。
- `QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES` はストア配布時に審査対象となるため、アプリ／APK管理機能の必要性をストア申請で説明してください。
- Lintはエラー0件。依存関係の更新提案、未使用リソース、既存APIの非推奨警告などの非ブロッキング警告は残り得ます。機能変更時は `lintDebug` を再実行してください。
- release APKは現時点でv2署名を使用しています。Play App Signingを利用する場合は、作成済みのアップロード鍵を安全なバックアップ先へ保管してください。
