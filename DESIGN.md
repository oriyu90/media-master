# 設計書兼仕様書 (Media Master)

## バージョン情報
- **Version:** 1.7.0

## v1.7.0 の設計変更（要約）
- **8ベンダーPCモード統合検出**: 新規 `desktop/DesktopMode.kt`（`Signals`+`resolve()`+`vendorForManufacturer()`、すべてpureで単体テスト可能）。AUTO時は従来信号（`UI_MODE_TYPE_DESK`・キャプションバー・`isInMultiWindowMode`）に加え、フリーフォーム（`Activity.getWindowingMode==5`、API 28+、reflection＋`runCatching`）とSamsung `SemDesktopModeManager.isDesktopMode` reflectionをOR。キーボード/マウス・外部ディスプレイは単独トリガーにしない（キーボード付きタブレットの誤判定防止）。`MainNavigation`の幅ゲート（≥600dp）は維持。
- **手動オーバーライド**: `SettingsRepository.DESKTOP_MODE_OVERRIDE`（0=自動/1=常にデスクトップ/2=常にタッチ、DataStore永続化）＋設定画面のラジオダイアログ（日英中阿蘭5言語完全対応、`LocaleStringParityTest`で検証）。
- **Manifest堅牢化**: `resizeableActivity="true"`明示、`windowSoftInputMode="adjustResize"`、`configChanges`に`navigation|colorMode`追加（ドック/キーボード着脱/ナイト切替でActivity再生成なし）。新規権限・新規Gradle依存なし。
- **安全性**: 全リフレクション・システムサービス取得を`runCatching`で保護、Activity取得はContextWrapper遡及（最大8段）でクラッシュなし、ステートレスオブジェクトでメモリリークなし。既存の破壊的操作ガード・`update{}`・`use{}`方針は変更なし。

## v1.6.0 の設計変更（要約）
- **ビューアーのスワイプナビゲーション修正**: `HorizontalPager`自体はv1.0.0から存在していたが、
  画像のピンチズームジェスチャー（`detectTransformGestures`）と動画`PlayerView`（ネイティブView）
  が等倍/コントローラー表示中でも1本指の横ドラッグを無条件に消費し、ページャーへスワイプが
  届いていなかった。画像側は「2本指またはズーム中のみ消費」に変更、動画側はPlayerViewより
  手前でInitialパスの軽量スワイプ検出を追加し、ページ送りを直接駆動するよう修正。
- **動画の10秒シークボタン**: `ExoPlayer.Builder.setSeekBackIncrementMs`/`setSeekForwardIncrementMs`
  を10秒に設定し、`PlayerView.setShowRewindButton`/`setShowFastForwardButton`を有効化。
  タップでコントローラーを表示した際、再生/一時停止ボタンの両脇に表示される
  （YouTube等と同様の配置。ExoPlayer/media3標準機能のみで実現、独自UIは追加していない）。

## v1.5.0 の設計変更（要約）
- **管理カテゴリのデフォルト表示をカテゴリ種別で出し分け**: `CategoryScreen`が
  `MediaCategory.IMAGES`/`VIDEOS`のときだけ`ViewMode.GRID`をデフォルトにする
  （`LaunchedEffect(categoryName)`で入場時に設定、ユーザーの手動切替はセッション中は尊重）。
- **サムネイル表示を`MediaThumbnail`として共通化**: `FilesScreen`/`AudioScreen`/
  `ManageDashboardScreen`が個別に持っていた「常に汎用アイコン」実装を、
  `CategoryScreen`/`LibraryScreen`で確立済みの実サムネイルパターンに統一。
  音声は`MediaMetadataRetriever`で埋め込みアートワークを抽出（`produceState`+IOディスパッチャ）。
- **DeX判定に`Activity.isInMultiWindowMode()`を追加**: LenovoのPCモード等、
  公開検出APIを持たないOEM独自デスクトップシェルはOSのフリーフォーム/
  マルチウィンドウとしてアプリを起動するため、既存の`UI_MODE_TYPE_DESK`/
  キャプションバー検出に次ぐ3つ目の信号として追加（幅ゲートは維持）。
- **ライブラリをGoogleフォト型（日付グルーピング+ピンチ密度切替）に再設計**:
  `LibraryScreen`内でローカルな`LibraryDensityMode`（`BY_DATE`/`COMPACT_ALL`）を保持し、
  2本指ズームジェスチャー（`awaitEachGesture`+`calculateZoom`、1本指スクロールとは
  非干渉）で切替。`BY_DATE`は日付キーでグルーピングした`LazyVerticalGrid`+
  初回のみ最下部へ自動スクロール、`COMPACT_ALL`は共通化した`sortMediaFiles`
  （`FileViewModel`から抽出）で並び替え可能なフラットグリッド。
- **ライブラリの複数選択アクションを`CategoryScreen`/`FilesScreen`水準に拡張**:
  全選択・削除・移動/コピー（既存の`FolderPickerDialog`+`moveFile`/`copyFile`を再利用）。
  `FolderPickerDialog`自体に新規フォルダ作成機能を追加。

## v1.4.0 の設計変更（要約）
- **DeX判定をウィンドウサイズ+キャプションバー検出の二段構えに変更**: `UI_MODE_TYPE_DESK`
  単独判定は One UI 8 以降の新デスクトップウィンドウイングで機能しないため、システム
  キャプションバーの表示有無を補助信号として追加。
- **ピンチズームを`ZoomableBox`として共通化**し、動画/PDF/Office埋め込み画像へ適用。
  既存のOCR画像ズーム（座標計算と密結合）はリファクタ対象から除外。
- **音楽プレイヤーをフル画面UI+システムイコライザーへ拡張**: `EqualizerController`が
  `android.media.audiofx.Equalizer`をExoPlayerの`audioSessionId`にアタッチ。
- **管理画面をFiles by Google/マイファイル型のカテゴリ+ストレージバー+パンくず+検索
  構成へ刷新**。ファイル操作（リネーム/移動/コピー）は`FileViewModel`の既存の
  スコープドストレージ処理（`RecoverableSecurityException`ハンドリング）を再利用。

## v1.3.0 の設計変更（要約）
- **Markdown数式(LaTeX)対応**: `$...$`/`$$...$$` をCommonMark解析前に抽出する
  `viewer/MathExtractor`（制御文字プレースホルダ方式）を新設。抽出結果は
  `MdBlock.MathBlock`/`MdInline.Math` として木構造に統合され、太字/リンクなど
  他のインライン書式と共存できる。
- **KaTeXのオフライン同梱描画**: 新規 `ui/viewer/LatexView`（WebView +
  `androidx.webkit.WebViewAssetLoader`）が `assets/katex/`（KaTeX本体・
  `auto-render`拡張・フォントwoff2一式、MITライセンス、ネットワーク接続なし）を
  仮想オリジン `https://appassets.androidplatform.net/assets/katex/` 経由で読み込み、
  1数式=1WebViewとしてレンダリングする。ブロック数式は自己サイズ調整
  （JS→`@JavascriptInterface`でscrollWidth/Heightを報告）、インライン数式は
  Composeの`InlineTextContent`/`Placeholder`機構でテキスト内に埋め込み、
  初回は文字数ヒューリスティックでサイズ推定→実測値で1回だけ補正する設計。
  `trust:false`（既定）を維持し`\href`等の危険なコマンドは無効のまま。
  R8向けに`@android.webkit.JavascriptInterface`メソッドのkeepルールを追加
  （JSブリッジ名がobfuscateされるとビルドは通るが実行時に静かに壊れるため）。
- **`.tex`ソースビューアー**: 完全なLaTeXコンパイルは端末上では非現実的なため、
  数式部分（`$...$`/`$$...$$`/`\(...\)`/`\[...\]`/`equation`等の環境）のみ
  KaTeXで組版し、それ以外は原文をそのまま等幅テキストで表示する簡易ビューアー
  （`viewer/LatexSourceParser`+`ui/viewer/DocumentViewerScreen`の
  `LatexSourceBody`）。`ViewerKind.LATEX_SOURCE`として独立分類。
- **削除ボタンの修正**: `DocumentViewerScreen`の削除操作が
  `FileViewModel.mediaState`（画像/動画/音声のみを含む）内の検索に依存しており、
  CSV/JSON/PDF/Officeなど文書系ファイルでは常に非表示だった潜在バグを修正。
  ナビゲーションルートに実ファイルパスを追加で渡す設計（`docViewer/{uri}?path=`）
  に変更し、呼び出し元がパスを渡した場合のみ削除を許可（外部Intent由来の
  未知URIには従来通り削除を許可しない設計を維持）。共有は元々無条件で動作していた。

## v1.2.0 の設計変更（要約）
- **汎用ドキュメントビューアー**: 既存の `ViewerScreen`（画像/動画/音声、OCR、単一Player設計）は変更せず、
  新規 `ui/viewer/DocumentViewerScreen`（ルート `docViewer/{uri}`）を追加。テキスト/CSV/JSON/Markdown/
  PDF/`.docx`/`.pptx` をアプリ内で表示し、それ以外の全ファイルは16進+ASCIIダンプ（`viewer/HexPageReader`、
  ページ単位遅延読込）へフォールバックするため「開けないファイル」が存在しない設計。
  URIを直接受け取る設計（`MediaFile`経由の path 参照に依存しない）とし、外部アプリからの
  `ACTION_VIEW` 単体URIも同一画面で表示できる（`DeepLinks` 経由）。
- **文字コード判定**: 新規依存を追加せず、BOM検出→UTF-8妥当性検証→Shift_JISヒューリスティックの
  自前実装（`viewer/TextCharsetReader`）。大きすぎるテキスト/CSVは既定上限（5〜10MB）まで読み込み、
  「さらに読み込む」で段階拡張しメモリを保護。
- **Office文書**: `.docx`/`.pptx` は Apache POI 等を使わず、`java.util.zip` + 標準 `XmlPullParser` による
  自前パーサ（`office/OoxmlDocumentReader`・`office/OoxmlSlideReader`）で段落/書式/画像を抽出。
  **旧形式 `.doc`/`.ppt` は内蔵表示の対象外**とした: Apache POI (core) の導入を検証したところ
  `java.lang.invoke.MethodHandle` 使用箇所が D8 の dex 化を `minSdk 26` 未満で失敗させることが判明し
  （実行時クラッシュではなくビルド不能）、`minSdk 24` 互換性を優先して不採用。`ViewerKind.EXTERNAL_ONLY`
  として従来通り外部アプリへ委譲する。
- **Markdown**: `commonmark`（Apache-2.0, 純Java）1件のみを新規依存として追加し、パース結果を
  Compose用の独自モデル（`viewer/MarkdownParser`の`MdBlock`/`MdInline`）へ変換して描画。ソース/
  レンダリング切替つき。
- **既定アプリ化**: `AndroidManifest.xml` に `text/plain`・`text/csv`・`text/markdown`・
  `application/json`・`application/pdf`・docx・pptx の `ACTION_VIEW` intent-filter を追加。
  Android の仕様上アプリ側から既定を強制することはできないため、設定画面に
  `ACTION_APPLICATION_DETAILS_SETTINGS` へ誘導する「既定のアプリ」導線を追加するに留める。
- **安全性**: PDFはページ単位でBitmapを生成・`recycle()`、HEXビューアはページ遅延読込、
  Office/Markdown/JSONパーサはすべて例外を握りつぶし失敗時は空表示かフォールバックに留める
  （既存の `runCatching`/`when` 網羅方針を踏襲）。

## v1.0.0 の設計変更（要約）
- **破壊的操作の確認**: 削除・アンインストール・復元はすべてアプリ内 `ConfirmDeleteDialog` を経由。
  一括アンインストールは先頭1件のみシステム確認へ（N件Intent爆発を解消）。
- **状態競合の排除**: 全 `MutableStateFlow` を `update{}` 化し、並列リロードを単一 `reloadJob` で
  ガード。削除失敗は `deleteError` フローでUI通知。
- **Viewer再設計**: `Loading/Error/Success` の網羅 `when`（無限スピナー・白画面の除去）、
  ページリストの `remember` 安定化、OCR単一recognizer＋`close()`＋TalkBack公開。
- **Hallmarkトークン**: `CommonUi.Hallmark` に間隔・角丸・操作サイズを集約し、
  全リストに安定キー・既定インセット・選択セマンティクスを適用。

## v0.3.0 の設計変更（要約）
- **外部連携**: `com.example.deeplink.DeepLinks` を単一 allowlist とし、`mediamaster://` スキームと
  標準 `ACTION_VIEW`/`ACTION_EDIT` を内部ナビルートへ写像。破壊的操作は外部から到達不可。
- **デザインシステム**: `ui/theme` に WCAG AA 検証済み M3 フル配色（light/dark）・明示タイプスケール・
  `MediaMasterTheme`（動的カラー既定 OFF）。`ui/components` に `EmptyState`/`ErrorState`/`LoadingButton` 等。
- **状態管理**: 画面は `when (val s = state)` で `ViewState` を安全に消費（危険キャスト・`!!`・
  合成中副作用を排除）。`PlaybackManager` はリスナー駆動で位置更新。
- **ネットワークストレージ**: `com.example.network`（SMB=smbj / WebDAV=OkHttp、認証情報は
  `EncryptedSharedPreferences`）。設定→サーバー接続、DeX サイドバーから到達。
- **アクセシビリティ**: 48dp タップ領域、`selected`/`Role` セマンティクス、`liveRegion`、
  `<plurals>`（ar 6分類）、ロケール依存の日付整形。
- **ツールチェーン**: Compose BOM 2025.05 / Navigation 2.9 / JDK 17 ソース。release は R8 + resource shrink。

## 概要
本アプリケーションは、Android デバイス内のメディアファイル（写真、動画、音声ファイルなど）やストレージを効率よく管理・閲覧できるメディア管理・ファイルマネージャーアプリです。Google Photo や Files by Google のような操作感を意識し、より直感的に操作できるワークフローと、Material Design 3 に準拠した最新の UI を提供します。

## 機能要件一覧

### 1. ホーム画面 (Home)
- アプリのメインポータル。
- 以下の主要モジュールへのナビゲーションを提供。
  - **Library:** 写真や動画の管理
  - **Audio:** 音楽や音声ファイルの管理
  - **Manage:** ファイルブラウザ・カテゴリ別検索・クリーンアップ機能
  - **Settings:** アプリの動作設定

### 2. ライブラリ機能 (Library)
- **Photos ビュー:** デバイス内の全画像・動画ファイルをグリッド形式で表示。タップでビューアーへ。
- **Albums ビュー:** ファイルが保存されているフォルダ（アルバム）ごとにメディアをグループ化して横方向・グリッド表示。アルバム詳細画面 (`AlbumScreen`) に遷移可能。

### 3. オーディオ機能 (Audio)
- **Tracks ビュー:** デバイス内の全音声ファイルの一覧表示。
- **Playlists ビュー:** フォルダベースで音声ファイルをグループ化。プレイリスト詳細画面 (`PlaylistScreen`) に遷移可能。

### 4. ストレージ管理機能 (Manage Dashboard)
- **Categories:** Downloads, Images, Videos, Audio, Documents, Apps といったカテゴリ別にファイルを自動フィルタリング。タップで該当するファイル一覧 (`CategoryScreen`) を表示。
- **Internal Storage:** 内部ストレージをフォルダ階層に従って直接ブラウジング (`FilesScreen`)。
- **Document Discovery:** 全ファイルアクセス時は実ファイルシステムとMediaStoreを併用し、PDF・Office・OpenDocumentなどのローカル文書を検出。
- **Clean Duplicates:** 同一ファイル（ハッシュや名前、サイズが一致するもの）をスキャンして重複ファイルを検出し、不要なファイルを削除できるクリーンアップ機能 (`CleanScreen`)。

### 5. メディアビューアー (Viewer)
- **動画・音声再生:** Media3 (ExoPlayer) を使用した再生。
- **画像表示:** Coil を利用した高速な画像ローディングとプレビュー。
- **簡易編集 (実装予定・プレースホルダー):** 
  - 画像向け: クロップ、回転などのエディタダイアログ。
  - 音声向け: トリム（切り出し）機能のプレースホルダーUI。
- 共有アクション。

### 5b. 汎用ドキュメントビューアー (v1.2.0〜)
- テキスト/ログ・CSV/TSV・JSON・Markdown・PDF・`.docx`・`.pptx`・`.tex`(v1.3.0〜) をアプリ内で表示。
- 上記以外の任意のファイルは16進+ASCIIダンプで表示（「開けないファイル」を作らない設計）。
- 旧形式 `.doc`/`.ppt` は互換性上の理由でアプリ内表示対象外（外部アプリで開く）。
- 各ビューアーに共通のトップバー: 戻る・外部アプリで開く・共有・（自アプリが実パスを把握しているファイルのみ）削除。
- `AndroidManifest.xml` の intent-filter により、対応形式で Media Master を既定アプリ候補として選択可能。
- Markdown・`.tex`ソースの数式（LaTeX）はKaTeX（オフライン同梱）で組版表示（v1.3.0〜）。

### 6. 設定機能 (Settings)
- **テーマ設定:** Light / Dark / System Default の切り替え。
- **言語設定:** 日本語 / 英語 / System Default の切り替え（Compose の `LocalContext` と `Configuration.setLocale` を利用した動的切り替え）。

## システムアーキテクチャ・技術スタック

### 基本構成
- **言語:** Kotlin
- **UI フレームワーク:** Jetpack Compose (Material Design 3)
- **アーキテクチャ:** MVVM (Model-View-ViewModel) + Clean Architecture ベース
- **非同期処理:** Kotlin Coroutines & Flow (`StateFlow` によるリアクティブな UI 更新)

### 主要ライブラリ
- **Navigation:** Jetpack Navigation Compose (型安全なルーティング構造を意識)
- **権限管理:** Accompanist Permissions (ランタイムパーミッション管理) + `MANAGE_EXTERNAL_STORAGE` (Android 11+)
- **メディア再生:** AndroidX Media3 (ExoPlayer)
- **画像読み込み:** Coil (非同期ロードとキャッシュ処理)
- **ドキュメント表示:** `android.graphics.pdf.PdfRenderer` によるPDFページレンダリング、`commonmark`
  によるMarkdown解析、`java.util.zip`+`XmlPullParser` による自前 `.docx`/`.pptx` パーサ
- **デスクトップUI:** `Configuration.UI_MODE_TYPE_DESK` のみでDeX/Finder風UIを有効化。画面幅では判定しない。

## ディレクトリ構造・設計方針

- `com.example.ui` 
  - 各画面 (Screen) の Composable 関数を配置。機能ごとにファイル分割。
- `com.example.FileViewModel`
  - ファイルスキャン、重複チェック、メディア分類などのビジネスロジックと状態を管理。IO Dispatcher で重いファイル I/O をバックグラウンド処理。
- `com.example.SettingsViewModel`
  - ユーザー設定（テーマ、言語）の永続化と提供。

## 最新技術と安定性の確保
- 全ての UI 状態を `StateFlow` と `collectAsStateWithLifecycle` によってライフサイクルに安全にバインド。
- ユーザーに重い処理（重複スキャンなど）の状況を伝えるため、ボタンのローディング状態（無効化とスピナー）を明確に UI に反映。
- Compose の非推奨 API (`Icons.Filled`) を `Icons.AutoMirrored.Filled` へ移行するなど、将来を見据えた API を使用。

## 今後の拡張予定 (課題)
- **カテゴリ分類の高度化:** Documents や Apps の正確な MIME タイプに基づく検出精度向上。
- **編集機能の実装:** 画像のクロップライブラリや音声処理ライブラリの統合。
- **パフォーマンス最適化:** ファイル数が数万件に及ぶ際の Paging3 の導入や、MediaStore API を活用した高速なクエリ取得。
