# Media Master v1.2.0 実装・保守メモ

最終更新: 2026-09-11

## リリース情報

| 項目 | 内容 |
| --- | --- |
| バージョン | `1.2.0` (`versionCode 6`) |
| アプリケーションID | `com.yukiorita.mediamaster` |
| 最小 SDK / target SDK | 24 / 36 |
| ライセンス | MIT |
| 著作者 | Yuki_Orita |
| release APK | `app/build/outputs/apk/release/app-release-signed.apk`（R8 + resource shrink 有効） |
| APK SHA-256 | `a9be2530fc51397582a820b9e9c7404dad3d1374d685838e0170de495e591c33` |
| 署名証明書 SHA-256 | `33:2C:E3:86:FB:F2:92:54:F1:79:78:B0:44:B8:BD:22:D6:A7:41:89:54:BB:50:59:38:72:17:12:E3:4E:EB:A6`（v0.1.0〜v1.1.0 と同一鍵） |
| GitHub Release | `v1.2.0` (GitHub Releases) |

release APK は RSA 4096 ビット鍵・APK Signature Scheme v2+v3 署名（`apksigner verify` で確認済み）。署名鍵は `common-rules-document/keystores/media-master-upload-key.jks`（alias `upload`）。公開前には毎回 `apksigner verify --verbose` で署名を確認してください。

v1.2.0 は JDK 21 + R8 有効ビルドで `:app:assembleDebug` / `:app:assembleRelease` が成功。単体テスト43件全て通過（`DeepLinksTest` を含む — v1.1.0時点でJDK17起因のRobolectric失敗が記録されていたが、JDK21で解消を確認）。`lintDebug` はエラー0件。実機・エミュレータのスモークテストは未実施のため、配布前に新規追加した各ドキュメント形式（CSV/JSON/TXT/バイナリ/MD/PDF/DOCX/PPTX/DOC/PPT）を開く確認、既定アプリ選択、DeX・RTL(ar)・TalkBack・分割画面・実SMB/WebDAV疎通を推奨（下記チェックリスト参照）。旧リリースの APK SHA-256: v1.1.0 `fe75d958b3c811a48582058903d95947b1f97ad108b8c23fd5338ec6ae8eb9a3`、v1.0.0 `255d8ed2b60e1f7a3dd51d1f933b08ae39cc7fa9a8398149202cb20d038b0082`、v0.3.0 `5f896b1bd15a65b4a947c428490ca63cc0ea0cac81332d89477029b5fe3d4bab`。

## v1.2.0 の実装内容（汎用ドキュメントビューアー・既定アプリ化）

### 新設ビューアー

- `ui/viewer/DocumentViewerScreen`（ルート `docViewer/{uri}`）: 既存 `ViewerScreen`（画像/動画/音声）とは
  別画面。URI文字列を直接受け取り、`viewer/ViewerKindClassifier` が拡張子/MIMEから種別判定して
  子Composableへ委譲。トップバーは戻る/外部アプリで開く/共有/（自アプリ管理下ファイルのみ）削除で統一。
- 対応形式と実装:
  - **テキスト**: `viewer/TextCharsetReader`（BOM検出→UTF-8妥当性検証→Shift_JISヒューリスティック、
    新規依存なし）。既定5MBまで読み込み、超過時は「さらに読み込む」で段階拡張。
  - **CSV/TSV**: `viewer/CsvParser`（区切り文字自動判定、引用符/エスケープ対応の簡易RFC4180）。
    固定ヘッダー + 横スクロール表（`LazyColumn`+共有`ScrollState`）。1万行超は切り詰め表示。
  - **JSON**: 既存の `kotlinx.serialization.json` で整形表示。パース失敗時は生テキストへ自動フォールバック
    （クラッシュ・空表示なし）。
  - **Markdown**: `viewer/MarkdownParser`（`commonmark` 依存、新規追加）でパースし、独自モデル
    (`MdBlock`/`MdInline`) をCompose側で描画。見出し/太字/斜体/リスト/引用/コードブロック/リンク対応。
    ソース/レンダリング切替可。
  - **PDF**: `viewer/PdfPageRenderer`（既存で実績のある `android.graphics.pdf.PdfRenderer` を流用）。
    ページ単位で `Bitmap` を生成し表示後 `recycle()`、複数ページを同時にメモリへ持たない。
  - **`.docx`/`.pptx`**: `office/OoxmlDocumentReader`・`office/OoxmlSlideReader`。追加ライブラリなしで
    `java.util.zip` + 標準 `XmlPullParser` により zip+XML を直接パースし、段落テキスト・太字/斜体・
    見出しレベル・インライン画像（`.docx`）/スライド単位テキスト・画像（`.pptx`）を抽出。全エントリに
    サイズ上限（zip 1エントリ10MB、画像枚数上限）を設け decompression-bomb 的な入力でもメモリを保護。
  - **バイナリ全般**: `viewer/HexPageReader`（ページ単位ランダムアクセス、既定4KB/ページ）+
    `HexFormatter` によるオフセット+16進+ASCIIダンプ。`LazyColumn`でページ遅延読込。
- **旧形式 `.doc`/`.ppt` は内蔵表示の対象外（`ViewerKind.EXTERNAL_ONLY`）**: Apache POI (`org.apache.poi:poi`
  core) を実装・ビルド検証したところ、`org.apache.poi.poifs.nio.CleanerUtil` が
  `java.lang.invoke.MethodHandle.invoke` を使用しており、D8 が `minSdk 26` 未満では
  dex 化できずビルド自体が失敗することが判明（`mergeExtDexDebug`/`mergeDebugGlobalSynthetics` エラー）。
  `minSdk 24`（Android 7.0/7.1）の互換性維持を優先し、POI 依存を撤去。これら2形式は従来通り
  `Intent.ACTION_VIEW` + `Intent.createChooser` で外部アプリへ委譲する（`FilesScreen.openMediaFile`・
  `DeepLinks`・Manifest intent-filter のいずれからも対象外）。

### 既定アプリ化

- `AndroidManifest.xml` に `ACTION_VIEW`(DEFAULT+BROWSABLE) intent-filter を追加：`text/plain`・
  `text/csv`・`text/markdown`・`application/json`・`application/pdf`・
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document`・
  `application/vnd.openxmlformats-officedocument.presentationml.presentation`。
- `DeepLinks.resolve()` が上記MIMEの `ACTION_VIEW` を `docViewer/{受信URI}` へ直接ルーティング
  （`MediaFile`一覧に無い外部URIも表示可能。旧コードの「後続フェーズ」コメントを解消）。
- Android は「アプリ側から既定を強制する」手段を提供しないため（初回起動時の「常時」選択、または
  設定→アプリ情報からの解除のみ）、`SettingsScreen` に「既定のアプリ」行を追加し
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` へ誘導。誇大表現を避け、実際の挙動をja/en文言で説明。

### MIME判定の細部修正

- `MediaRepository.isDocument()` に `text/csv`・`text/markdown`・`application/json`・`text/html`・
  `application/vnd.ms-powerpoint` を明示追加（従来は拡張子一致のみに依存していた抜け穴）。
- `MediaCategory.DOCUMENTS.matches()` の拡張子集合に `json`・`html`・`htm` を追加し `isDocumentName()` と一致させた。
- `getMimeType()` に `tsv`→`text/csv`、`md`/`markdown`→`text/markdown`（従来`txt`と同一の`text/plain`だった点を分離）、
  `log`/`ini`/`conf`/`cfg`/`yaml`/`yml`/`properties`→`text/plain` を追加。

### 新規依存

- `org.commonmark:commonmark`（Apache-2.0、純Java、Markdown解析のみに使用）。
- それ以外はゼロ（CSV/JSON/文字コード判定/`.docx`/`.pptx`/HEX/PDFはすべて標準APIまたは自前実装）。
- 検証済みで**不採用**: `org.apache.poi:poi`/`poi-scratchpad`（上記「旧形式」節参照）。

### 多言語対応

- 新規 `strings_viewer.xml` を5ロケール（en/ja/zh/ar/nl）に同数（12キー）で追加。既存の
  `strings_network.xml` と同じ1行1`<string>`形式。全ロケール合計 **220キー**（v1.1.0時点208キー+12）。
  正確な値は `LocaleStringParityTest` が継続的に検証する。
- **新規テスト `LocaleStringParityTest`**（`app/src/test/java/com/example/LocaleStringParityTest.kt`）：
  5ロケールの `strings*.xml` キー集合とキー数が完全一致することを検証。従来手作業で維持していた
  「言語間で文字列数を揃える」不変条件を初めて自動テスト化した（IMPLEMENTATION_AND_MAINTENANCE.md
  の残課題として指摘されていたもの）。

### 新規テスト

- `CsvParserTest`（7件）：区切り文字判定、引用符/エスケープ、改行入り引用フィールド、行数切り詰め。
- `TextCharsetReaderTest`（5件）：BOM検出、UTF-8/Shift_JIS判定と往復一致（文字化けしないことを保証）。
- `ViewerKindClassifierTest`（4件）：拡張子/MIME判定、旧形式のEXTERNAL_ONLY化、未知バイナリのHEXフォールバック。
- `MarkdownParserTest`（7件）：見出し/強調/コードブロック/リスト/不正入力での非クラッシュ。
- `DeepLinksTest` に新形式のACTION_VIEWルーティングとレガシー形式除外の2ケースを追加。
- `LocaleStringParityTest`（2件、上記）。

### 残課題（v1.3.0以降の候補）

- `.docx`/`.pptx` のリスト番号付け（`w:numPr`）は未解決で、箇条書き/番号付きリストは通常段落として表示される。
- `.pptx` のスライド順序はファイル名の数値サフィックス依存（大幅な並べ替え後のファイルでは順序がずれる可能性）。
- Markdown表（GFMテーブル拡張）は未対応（テーブル行はプレーンテキストとして表示される）。
- 旧形式 `.doc`/`.ppt` の内蔵表示は、D8制約が解消されない限り対象外の方針を維持。
- 実機・エミュレータでの新形式スモークテスト、DeX・RTL・TalkBack確認は未実施。

## v1.1.0 の実装内容（残課題の解消 + DeX/通常モード堅牢化）

### DeX / 通常モード

- デスクトップ判定を `UI_MODE_TYPE_DESK && windowWidth >= 600dp` に変更（`MainNavigation` の `BoxWithConstraints`）。小さな DeX ウィンドウでは通常タッチ UI を維持し、誤ったデスクトップ表示を防止。
- Manifest の `configChanges` に `density|layoutDirection|uiMode` を追加。DeX ドック/解除・密度変更で Activity が再生成されず、ナビゲーション状態を保持。
- DeX シェルにも `MiniPlayer` をオーバーレイ（従来は通常モードのみで、DeX では再生操作が不可だった）。
- `DesktopNavigation` の独自 root 解決（非推奨 `getExternalStorageDirectory`）を撤去し、`FileViewModel.storageRoots()` に一元化。Manage ダッシュボード・除外フォルダピッカーも同一実装に統一（DeX/通常で同一ボリューム集合を表示）。

### ファイル認識

- `storageRoots()` を `MediaRepository` へ移動し公開化。`getExternalFilesDirs()` + `SECONDARY_STORAGE`（SD/USB-OTG のベンダー差分吸収）+ レガシーフォールバックの順で解決し、存在確認で絞る。`isStorageRoot()` も同候補集合に統一。
- Manage/除外ピッカーの root 解決は `Dispatchers.IO` 経由（合成中のディスク I/O を排除）。

### MVI 分離（`FileViewModel` 632行 → オーケストレーター化）

- 新設 `com.example.MediaFile.kt`（`MediaFile`/`SortOption`/`ViewMode`/`ViewState`、同一パッケージのため既存画面の import 変更なし）。
- 新設 `com.example.files.MediaRepository`（storage roots/MediaStore クエリ/ドキュメント走査/MIME 判定）。
- 新設 `com.example.files.DuplicateFinder`（head/tail サンプルハッシュ、null=読取不可で誤合流防止）。
- `FileViewModel` は StateFlow + intent + 削除フロー（`MediaStore.createDeleteRequest` の権限委譲含む）のみ保持。外部 Intent から破壊的操作に到達できない設計は維持。

### 除外フォルダの DataStore 統一

- `SettingsRepository.EXCLUDED_FOLDERS`（string-set）+ `updateExcludedFolders()`（`edit{}` 内 read-modify-write で単一トランザクション）+ `mergeExcludedFolders()`（移行用 union）。
- `FileViewModel` 起動時に旧 `media_master_prefs` の値を union 移行してキーを削除。以降の読み書きは DataStore のみ。

### 未使用依存の撤去

- 撤去: `retrofit`/`converter-moshi`/`moshi`（+ksp codegen）/`firebase-bom`・`firebase-ai`・`firebase-appcheck-recaptcha`/`room-ktx`・`room-runtime`（+ksp compiler）/`logging-interceptor`、プラグイン `ksp`・`secrets`・`google-services`。catalog の対応 version/library/plugin エントリも削除。`.env.example` の死んだ `GEMINI_API_KEY` を整理。
- 直接依存化: `kotlinx-coroutines-play-services`（ML Kit `Task.await()` 用。従来は firebase 経由の推移依存だったため、撤去後に明示化）。
- 維持: `okhttp`（WebDAV）、`smbj`、`coil-compose`+`coil-video`（`VideoFrameDecoder` 使用）、`media3` 全種（session/transformer/effect は Playback/VideoEditor で使用）、`datastore`、`security-crypto`。
- proguard の moshi/retrofit keep を除去（okhttp dontwarn 維持）。

### Viewer 単一 Player 化

- ページ毎 `ExoPlayer.Builder` を廃止し、Viewer セッションで 1 インスタンスを `remember`。`LaunchedEffect(currentFile)` で `setMediaItem`+`prepare`+再生、`DisposableEffect` で `release()`。
- 同一 Player を複数 `PlayerView` に束縛すると映像出力が裏ページへ移動するため、settled ページのみ `AndroidView`、隣接ページはプレースホルダー表示。

### MediaStore の LIMIT/OFFSET 化

- `MediaRepository.queryMediaVolume()` がボリューム毎に 2000 行ページング（`_ID ASC LIMIT … OFFSET …`）で取得し、巨大ライブラリでも単一 CursorWindow を肥大化させない。`mediaState` を消費する 6 画面の API は不変（インメモリのソート/フィルタ維持）。
- Paging3 の全画面移行は `mediaState` 共有設計と両立しないため v1.2.0 候補に据え置き（本改修が Repository 側の土台）。

### 日英完全対応

- バックアップ時間ダイアログの `Text("h")`/`Text("m")` → `hour_abbrev`/`minute_abbrev`（en h/m、ja 時/分、+zh/ar/nl）。
- トリムスライダーのサム読み上げ → `trim_start_position`/`trim_end_position`（`%1$s` 付き 5 言語）。
- 日付/サイズは locale 対応済み（`DateFormat.MEDIUM`、`Formatter`/`String.format(Locale)`）、件数は plurals。コード内の UI 直書きテキストはゼロ。

### 残課題（v1.2.0 以降の候補）

- Paging3 の全画面移行（`mediaState` を `Flow<PagingData>` 化し、Library/Audio/Category/Album/Playlist/Viewer を `collectAsLazyPagingItems` 化）。
- `ViewerScreen` の OCR 座標計算・`DocumentsScreen.ScanView` の合成中 `?: return` の本格再設計。
- ネットワークストレージの実機（SMB/WebDAV サーバー）疎通テスト。
- 実機スモーク（削除確認・共有・SAF永続・バックアップ/復元）、DeX・RTL・TalkBack・分割画面。

## v1.0.0 の実装内容（正式リリース改修）

### 危険設計の修正（Critical/High）

- 全削除フローにアプリ内確認（`CommonUi.ConfirmDeleteDialog`）を追加：Files（複数）/ Category / Audio / Viewer / APK一括 / Clean単体 / 復元（`SettingsScreen`）。
- `AppManagerScreen` の `Uri.fromFile(sourceDir)`（`/data/app` 共有で N+ クラッシュ）を除去し、APK共有は `FileProvider` + `FLAG_GRANT_READ_URI_PERMISSION` に統一。Library/Audio/Category/Documents の共有 Intent にも付与漏れを修正。
- `SettingsScreen` の SAF フォルダ選択に `takePersistableUriPermission` を追加（再起動後の無効化を解消）。
- `ImageEditor` / `VideoEditor` エクスポート / `DocumentsScreen` の `PdfRenderer`・`openFileDescriptor` をすべて `use{}` 化（例外時リーク解消）。`appendImagesToPdf` はページ単位 `use` + `finally close`。
- `FileViewModel` の `_value` 直代入 20 箇所 + `NetworkViewModel` 9 箇所を `update{}` 化。`reload()` を単一 `reloadJob` でガードし、遅勝ちエラーによる成功上書きを防止。削除失敗は `deleteError` でトースト通知。
- `ViewerScreen` の Error 握り潰し（無限スピナー）と裸 `?: return`（白画面）を `when` + `EmptyState`/`ErrorState` に置換。OCR は日英2並列→Japanese単独＋`close()`＋IO/Main 分離。OCRテキストを TalkBack に公開。
- `VideoEditorScreen` の 100ms ホットポーリングを 250ms・`ensureActive()` 付きに緩和。
- `FileProvider`（`file_paths.xml`）に `cache-path`/`files-path`/`external-cache-path`/`external-files-path` を追加（ネットワーク cache 共有の `IllegalArgumentException` を解消）。

### ファイル管理の完成度向上

- `Environment.getExternalStorageDirectory()` の直接利用を `getExternalFilesDirs()` 派生ルートに置換（`storageRoots()` の `exists()` は IO スレッドに隔離）。UI のハードコード `/storage/emulated/0` 比較を `isStorageRoot()` に統一。
- MIME 表を拡張（`heic/heif/avif/bmp/tif/svg/mov/webm/3gp/flv/m4a/aac/opus/csv/json/md/html/epub/zip`、表計算/プレゼンの実 MIME）。`MediaCategory` の `Downloads` 判定を case-insensitive 化し、`DOCUMENTS`/`APPS` の定義を `FileViewModel` と一致。
- 重複検出を先頭256KB＋末尾256KB＋サイズ混入のサンプルハッシュに修正（旧「フルハッシュ」は先頭1MBのみの名称詐称）。読取不可は `null` 返却で誤グルーピングを防止。
- `getAllDocumentFiles` の打ち切り上限は維持（50,000件）するが、Paging3 本格導入・`MediaStore` 高速化は v1.1.0 以降の候補。

### Hallmark 準拠 UI リメイク

- `CommonUi.Hallmark` トークン（`ContentEdge 16dp`・`ItemGap 8dp`・`RowMinHeight 56dp`・`ControlMinHeight 40dp`・`IconSize 18dp`・半径 8/12/16）を新設し、Files/Clean に適用。`ConfirmDeleteDialog`・`HallmarkDivider` を共通化。
- 全メディアリストに安定キー（`key={path}`、Audio の index ベースを除去、Category/Library/Documents/Apks/除外フォルダ）。
- `MainActivity` と `MainNavigation` の `collectAsState()` を `collectAsStateWithLifecycle()` に統一。
- `Album`/`Playlist`/`Settings`/`Network`/`Clean` の `contentWindowInsets = WindowInsets(0)` を除去し、既定インセット＋末尾パディングに戻した（3ボタンナビでの隠れを解消）。
- `FileItemRow/Grid` に `modifier` 引数＋`selected` セマンティクス＋`Role.Checkbox` を追加。`VideoEditor` の RangeSlider サムに読み上げラベル、`DocumentsScreen` の日付区切りは既存の locale 対応を維持。
- 新規文字列 `retry`・`parent_folder` を en/ja/zh/ar/nl に追加（`".."` 直書きを除去）。

### 残課題（v1.1.0 で解消済み → 上記参照）

- ~~`FileViewModel` の MVI 分割~~ → 解消（`MediaFile.kt` + `files/MediaRepository` + `files/DuplicateFinder`）。
- ~~`MediaStore` クエリの `LIMIT` 化~~ → 解消（2000行ページング）。Paging3 全画面移行は v1.2.0 候補。
- ~~除外フォルダの DataStore 統一~~ → 解消（union 移行付き）。
- ~~Firebase AI / AppCheck・Room 等の未使用依存撤去~~ → 解消。
- ~~`ViewerScreen` の単一 Player 化~~ → 解消。
- 実機スモーク（削除確認・共有・SAF永続・バックアップ/復元）、DeX・RTL・TalkBack・分割画面・実SMB/WebDAV疎通 → 未実施（v1.2.0 以降）。

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

- ~~`FileViewModel` の完全 MVI 分割~~ → v1.1.0 で解消（`MediaFile.kt` + `files/MediaRepository` + `files/DuplicateFinder`）。
- `ViewerScreen` の OCR 座標計算・合成中 `?: return` の本格再設計。
- `DocumentsScreen.ScanView` の `?: return`（合成中）。
- ~~除外フォルダの生 `SharedPreferences("media_master_prefs")` を DataStore へ統一~~ → v1.1.0 で解消。
- ネットワークストレージの実機（SMB/WebDAV サーバー）疎通テスト。
- ~~Firebase AI/AppCheck・Room の未使用依存の撤去~~ → v1.1.0 で解消（retrofit/moshi/logging-interceptor 同時撤去）。

### 検証手順（リリース前・要実施）

1. `./gradlew :app:assembleRelease`（R8 有効）でビルドが通ること。
2. 署名済み APK を実機へ導入し**全機能スモーク**（ライブラリ/ビューア/編集/オーディオ/
   ドキュメントスキャン/管理/重複クリーン/APK管理/バックアップ/復元/ネットワーク/
   全ディープリンク `am start`）。
3. **新規: 汎用ドキュメントビューアー**（v1.2.0〜）— CSV/JSON/TXT/バイナリ/MD/PDF/DOCX/PPTX/
   DOC/PPT をFiles/Documentsから開き、文字化け・レイアウト崩れ・クラッシュがないこと（巨大/破損
   ファイルを含む）。DOC/PPTは外部アプリへの委譲になることを確認。「既定のアプリ」設定から
   システム設定画面に遷移できること。外部アプリの共有シートからMedia Masterで対応形式を
   開けること。
4. 回転・分割・DeX・RTL(ar)・ダーク・TalkBack を確認。
5. `apksigner verify --verbose --print-certs` と SHA-256 記録。

## 主要ファイル

| パス | 役割 |
| --- | --- |
| `app/src/main/java/com/example/ui/DesktopNavigation.kt` | DeX / 大画面のサイドバー、タブ、ホーム |
| `app/src/main/java/com/example/ui/MainNavigation.kt` | 通常UI・デスクトップUIの振り分け、ナビゲーション定義 |
| `app/src/main/java/com/example/FilesScreen.kt` | ファイル一覧、区切り線、フォルダのDnDとコンテキスト操作 |
| `app/src/main/java/com/example/ui/viewer/DocumentViewerScreen.kt` | 汎用ドキュメントビューアー本体（v1.2.0〜） |
| `app/src/main/java/com/example/viewer/` | 文字コード判定・CSV/JSON/Markdown/HEX/PDFの各パーサ・レンダラ（v1.2.0〜） |
| `app/src/main/java/com/example/office/` | `.docx`/`.pptx` 自前zip+XMLパーサ（v1.2.0〜） |
| `app/src/main/java/com/example/SettingsRepository.kt` | DataStore設定（ピン留めを含む） |
| `app/src/main/java/com/example/SettingsViewModel.kt` | 設定操作のViewModel |
| `app/src/main/res/values*/strings*.xml` | UI翻訳リソース |
| `app/build.gradle.kts` | アプリID、バージョン、署名設定、依存関係 |
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
- `commonmark`（Markdownパーサ、v1.2.0で追加）以外に汎用ビューアー機能の新規依存はありません。**Apache POI（旧形式.doc/.ppt向け）は検証の上、意図的に不採用**（`java.lang.invoke.MethodHandle`がminSdk26未満でdex化できないため）。将来再検討する場合は、minSdk引き上げの可否をまず確認してください。
