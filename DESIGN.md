# 設計書兼仕様書 (Media Master)

## バージョン情報
- **Version:** 0.3.0

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
- **ドキュメント表示:** AndroidView を介した PDF レンダリング（必要に応じて拡張）
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
