# 設計書兼仕様書 (Media Master)

## バージョン情報
- **Version:** 2.0.0

## v2.0.0 の設計変更（要約）
- **外部メディアの入口**: 新ルート `externalViewer/{uriString}?mime={mime}`。`DeepLinks` の `ACTION_VIEW` image/video をここへ送り、MIME がワイルドカード/未指定/octet-stream の場合は拡張子（jpg/png/heic/mp4/mkv…）から推定する。audio は従来どおり `library`、文書/ZIP/フォルダは不変。`DesktopViewerPolicy` に `externalViewer/` を追加し、デスクトップではシェルを畳んで全画面表示する。
- **外部 URI の解決**: 新規 `external/ExternalMediaInfo` と `resolveExternalMediaInfo()`。`OpenableColumns`（表示名・サイズ）と MediaStore（`_ID`/`DATA`/`DATE_MODIFIED`）をベストエフォートで読み、全クエリを `runCatching` で保護する。解決前は URI 最終セグメントとインテント MIME のフォールバックで即時表示する。
- **ライブラリ照合**: pure `matchLibraryFile()` が content URI 文字列 → 解決パス → MediaStore `_ID` の順で照合。表示名・サイズのみの照合はしない（誤ったページングより単体表示が安全）。`libraryMediaOrder()` は写真タブと同じく画像/動画のみ・除外フォルダ非表示・更新日時昇順。`externalMediaList()` は一致時のみライブラリ順、不一致時は外部ファイル1件を返す。
- **ビューアの外部モード**: `ViewerScreen(external=…)` は `externalViewer` を合成の `ViewState.Success` として扱い、ライブラリ読み込み中でも画像を即時表示。`mediaState` 未取得なら `loadAllMedia()` を一度だけ実行し、コールドスタートでも一致時にページングできる。全画面開始、`closeViewer()` は外部時のみ `library` へ `popUpTo(home)` で遷移（戻る/ESC/戻る矢印/下スワイプ/削除後）。外部のみの項目は `canModify=false` で削除/名前変更を非表示。
- **安全設計**: URI 権限切れ・プロバイダ非対応は既存の画像エラーUIに落ち、閉じればライブラリへ。`_ID` はボリューム跨ぎの衝突可能性があるため content URI/パスの照合を優先。外部ファイルの破壊的操作なし。既存の `ViewerOrder`・全画面・スワイプ・OCR・dismiss・通常ディープリンクは不変。
- テスト: `ExternalMediaTest`（一致/不一致/順序/1ページ/ID解析）、`DeepLinksTest`（externalViewer・音声・MIMEフォールバック）、`DesktopViewerPolicyTest`。実機でコールドスタート一致→ページング、不一致→単体＋下スワイプでライブラリ、デスクトップのシェル折り畳みを確認。

## v1.14.0 の設計変更（要約）
- **デスクトップ没入の分離**: 「ビューア内の全画面フラグ」と「デスクトップシェルを畳むか」を分けた。`ui/DesktopViewerPolicy.kt`（pure）が `viewer/`・`imageEditor/`・`videoEditor/` のルートだけをメディア提示として判定し、`ViewerScreen` は `onDesktopImmersiveChange(isFullScreen && hasTarget)` を通知するだけ。`MainNavigation` が `desktopImmersive` を保持し、`DesktopNavigation` はサイドバー・タブ帯・フッターを **兄弟要素として条件付きコンポーズ**する（NavHost の呼び出し位置は固定なので、ページャ・ズーム・OCR・バックスタックは破棄されない）。ライブラリへ戻るルート変更で必ず false に戻すため、クロームのないウィンドウが残らない。
- **システムバーの所有権**: デスクトップの hide/show は `DesktopNavigation` の `DisposableEffect(hideShell)` が所有する。ViewerScreen の `onDispose` で show すると、ビューア→編集の遷移（ビューアのコンポジション破棄）でバーが復活して編集画面が全画面でなくなるため。タッチは従来どおり ViewerScreen が所有。
- **ツールバー上端**: `TopAppBar` の外側に付けていた `windowInsetsPadding(safeDrawing.top)` を削除し、`windowInsets` 引数へ `safeDrawing.only(Top + Horizontal)` を渡す。Material3 は Surface の内側でインセットを消費するため、背景はステータスバー/カットアウト領域まで届き、内容だけが安全領域に収まる（隙間なし・没入時はインセット0で完全一致）。
- **エディタの幾何パイプライン**: 新規 pure `ui/editor/GeometryMath.kt` が傾き（X/Y）・反転・Z回転後の四隅を返し、`ImageGeometry.kt` が `Matrix.setPolyToPoly` でその四隅へ写像する。従来の `android.graphics.Camera` はカメラ距離がピクセル固定で、1024px プレビューと原寸保存の歪みが一致しなかった。新実装は画像長辺に比例した距離の純射影なので解像度非依存（`GeometryMathTest` で固定）。90°回転も外接矩形で計算するため見切れない。
- **プレビュー=保存**: プレビューは長辺1024pxの縮小コピーに同じ `applyGeometry` を適用し、保存は作業ビットマップ（長辺4096pxにサンプリング）へ同じ関数を適用する。色行列も同一。作業ビットマップは編集開始時に4096pxへ間引き、中間 Bitmap は即時 `recycle()`、失敗は `Result` で返して Toast（無言で閉じない）。
- **切り抜き**: pure `ui/editor/CropMath.kt` の `CropRect`（0..1、幾何適用後基準）と `AspectOption`（自由/元/1:1/4:3/3:4/16:9/9:16/3:2/2:3）。比率固定時は反対角アンカーで画素比率を維持し、境界・最小サイズでクランプ。表示は `ContentScale.Fit` の実矩形（`fitImageRect`）に合わせ、`CropOverlay` が角リサイズ/内側移動を画素円形のタップ判定で処理する。
- **四隅合わせ**: pure `ui/editor/PerspectiveMath.kt` の `PerspectiveQuad`（凸性・面積・出力サイズ）。変形タブの「四隅を合わせる」でモードに入り、OK 時に現在の幾何を原寸で焼き込んだうえで `setPolyToPoly(quad→矩形)` を実行し、その場で作業ビットマップを置換する（要望どおり「OKで切り抜き＆変形」）。キャンセル/リセット、交差時の OK 無効化、失敗時の Toast を備える。
- **リセットUI**: 共有 `ui/components/EditorSlider.kt`（ラベル＋スライダー＋右端リセット）を傾き3軸とカラー全項目（画像/動画の Adjust）に適用。既定値は `getDefaultAdjustmentValue` と一致し、既に既定ならボタン無効。
- 互換/安全: 新規依存・権限・DB変更なし、minSdk24維持。`startFullscreen` 既定（ライブラリ/アルバム=true、エクスプローラー=false）は不変。5ロケール（en/ja/zh/ar/nl）に新キーのみ追加し parity テストで強制。画像編集の出力先は従来どおり `Pictures/Edited`（元ファイル不変）。

## v1.13.0 の設計変更（要約）
- **表示順ページャー**: ナビゲーション引数に任意長のパス一覧を載せるとtransaction size/encodingが危険なため、`FileViewModel.ViewerLaunch(path, order, startFullscreen)`を一方向ハンドオフとして導入。呼び出し元（Library日付/コンパクト、Album、Category、Files、Manage最近）が「いま表示している順序」を`prepareViewer()`で渡し、`ViewerScreen`は`launch.path == route path`のときだけ採用。pure `ViewerOrder.resolve()`が対象のメディア種別（画像/動画・音声・単一）で順序を保持しつつ不足パスを除外し、launchが無い/古い場合はv1.12.2のグローバル一覧フォールバックを再現（deep link・プロセス復元互換）。`clearViewerLaunch(path)`は同一パスのみ破棄し、新旧launchの競合を防止。
- **表示中ページの保持**: launchはコンポジション破棄では消さず、NavHost destinationの`Lifecycle.Event.ON_DESTROY`（pop時）だけ`clearViewerLaunch`。画像/動画エディタへ移動して戻る場合やメディア再スキャンでも、`lastShownPath`（`rememberSaveable`）を新しい一覧内へ再アンカーして、復元ページ番号が別ファイルを指す事故を防ぐ。
- **全画面モデルの分離**: `ViewerScreen`の`isFullScreen`（没入）と`chromeVisible`（半透明オーバーレイ）を分離。タッチのライブラリ/アルバム/カテゴリ/ファイルは`isFullScreen=true, chromeVisible=false`で開始、タップはchromeのみトグル。オーバーレイ先頭に全画面/全画面終了ボタン（既存`fullscreen`/`exit_fullscreen`文言）を追加し、没入中は`WindowInsetsControllerCompat.hide(systemBars())`＋transient swipe、終了/`onDispose`で`show()`。`BackHandler(enabled=isFullScreen)`で解除優先→次でpop。動画は`PlayerView`のコントローラ可視性が両状態を同期（従来挙動維持）。`DocumentViewerScreen`は既存のfullscreen契約を変えず、touchの`!desktop`時のみシステムバーを隠す。
- **Explorerのメディアタブ**: 既存の資料タブ機構を一般化し、`DesktopNavigation.openViewerInNewTab(path,title)`（route `viewer/{path}`、タブ名=ファイル名）を追加。`MediaNavHost`第4コールバック→Files/Category/Manageへ配線し、desktopのみメディアを新規タブで開く（`startFullscreen=false`でツールバー表示、オーバーレイの全画面ボタンでタブ内没入）。touchは既定no-opで直接遷移のまま、資料の`openDocInNewTab`/ESC/戻るは不変。デスクトップの「全画面」はタブ内のアプリクロームのみを隠す（DeXキャプション/OSシステムバーはタブ外枠のため対象外、資料タブと同じ契約）。
- 安全性：新規依存・権限・DB変更なし。launchはパス照合＋destinationのON_DESTROY時クリアで陳腐化を防止。`DesktopMode.activityOrNull`でActivity参照を解決しnull安全、システムバーは必ず復元。既存のスワイプ/ピンチ/OCR/dismissのpointerInput分岐は無変更。新規文言なし（既存5言語キーを再利用、parity維持）。

## v1.12.2 の設計変更（要約）
- **アイコン縮小**: 前景フォルダを`<group scaleX/Y=0.72 pivot=54>`で中央基準縮小（昼夜共通）。背景・マスク定義は不変。

## v1.12.1 の設計変更（要約）
- **実機監査の回帰修正**: Library日付ヘッダの`span = { GridItemSpan(maxLineSpan) }`を復活（v1.11.0のkey/contentType化で落としていた）。key＋contentTypeは維持しスクロール安定性を損なわない。実機（Lenovo TB710FU / Android 16 / PC Mode）での精査では他に要修正なし（%表示は実ファイル名、IMEかな変換は端末仕様）。監査で触れた端末設定（テーマ/言語/音量）は全て復元、テストファイルは削除済み。

## v1.12.0 の設計変更（要約）
- **資料のタブ＋全画面(#A)**: `DesktopNavigation.openDocInNewTab(uri,path,title)`（pure `docTabRoute`＋単体テスト）で資料をファイル名タブへ。`MediaNavHost`第3コールバック→Files/Documents/Categoryへ配線（touchは既定=直接遷移で不変）。`DocumentViewerScreen(desktop)`にワンクリック全画面ボタン（TopAppBar＋全画面中のフローティング終了ボタン、`AnimatedVisibility` fade）。touchは初回ON・desktopはOFF。`BackHandler`（全画面→解除優先）＋ESC（`onPreviewKeyEvent`＋desktop全画面時のみ`FocusRequester`、touchはBackHandlerのみ依存）。Category文書行の空白バグを`openMediaFile`委譲で修正（メディア→viewer、APK→installer、文書→docViewer、旧Office→外部）。
- **標準ファイルマネージャー化(#B)**: Manifestにdirectory VIEWへBROWSABLE＋`resource/folder`、GET_CONTENT/OPEN_DOCUMENT（OPENABLE＋ワイルドカード）を追加。`DeepLinks`は`resource/folder`対応＋directory pathガード（fileスキームか`/storage/`・`/sdcard`始まりのみ採用、content系はManageへ）。pure `PickRequest.parse`（単体テスト）。新規`FilePickerScreen`（既存FileViewModelのfileTreeState再利用、MIME絞り込みpure `mimeMatches`＋単体テスト、単一選択のみ・Cancel/BACKでCANCELED、削除等の副作用なし）。`MainActivity`がpickerをdeep linkより優先し`setResult+finish`（読取grant付与、書込は要求時のみ反映）。
- 安全性：minSdk24維持、新規権限/依存なし、外部起動はtoast化、`!!`なし、日英中阿蘭4キー追加（parity検証）。KDoc内の`*/`・`/*`列注意（Kotlinはブロックコメントがネストするため拾い物MIME表記を避ける）。
- 既知の制約：`EXTRA_ALLOW_MULTIPLE`は単一返却（文書化）、OPEN_DOCUMENTの永続権限はベストエフォート（`DocumentsProvider`非実装のため。システムピッカー利用を推奨）。

## v1.11.0 の設計変更（要約）
- **アイコン(#1)**: 添付フォルダマークへ置換。`drawable/ic_launcher_background.xml`を白単色化、夜間用`drawable-night/`に黒背景＋白フォルダを追加（`drawable-night/ic_launcher_foreground.xml`）。形状はタブ付き単一シルエット（マスクで欠けないよう細い渡しは持たせない）。monochromeはforegroundと同ペア。
- **スクロール安定(#2)**: `android/skills`の`jetpack-compose-performance`（lazy-layouts: key＋contentType必須）に従い全Lazyへ付与（9箇所の`items(size)`を含む）。`DocumentListRow`は`PdfPageCountCache`（プロセス内ConcurrentHashMap）でスクロール中の`PdfRenderer`再オープンを排除し支持行を1行固定。`CategoryScreen`はタブ再構成時にページをクランプ/先頭化。`LibraryScreen`の日付ヘッダにkey付与。
- **他のアプリで開く(#3)**: 新規`openWithExternalApp()`（FileProvider優先・MIME推定・`ActivityNotFound`時は`no_app_to_open`トーストで非クラッシュ）＋共有`OpenWithMenu.kt`（`OpenWithDropdownItem`/`SelectDropdownItem`/`rightClickMenu`）。Files行/グリッド・Category行/グリッド・Documents行・Libraryグリッドに右クリック/長押しメニューを追加。タップ既定（内部ビューア）は不変、メニュー内**選択**で一括操作を維持。
- **ズーム制限(#4)**: 新規pure `ZoomBounds`（`clampZoomScale`/`clampFillOffset`/`clampFitOffset`/`fitDrawSize`/`maxPanOffset`）を`ZoomableBox`（`onPlaced`計測）と`ImageWithOcrOverlay`（Fit描画 rect 基準）の両パンに適用。下限フィット等倍・移動は画像端で停止（-0.0正規化で`Offset.Zero`安定）。OCR選択・pager/ dismiss arbitration は不変。
- **UI精査(#5)**: `jetpack-compose-performance`＋`jetpack-compose-adaptive`を`.agent/skills/`へ同梱（Apache-2.0・改変なし、`AGENTS.md`更新）。Hallmark/MD3の軽微修正（行・タイトルの1行ellipsis、5言語5キー追加＋parity検証）。
- 安全性：互換（minSdk24・新規権限/依存なし）・クラッシュ安全（全外部起動を`runCatching`/トースト化、`!!`なし）・メモリ安全（PDFキャッシュはIntのみ、サムネイル/プレイヤー経路不変）・日英中阿蘭完全対応。

## v1.10.0 の設計変更（要約）
- **ZIP外部API対応(#1)**: `ViewerKind.ZIP`新設＋分類器（拡張子/mimeの3種）＋`DeepLinks`（明示mime＋拡張子フォールバック）＋Manifest（3 mime）で外部`ACTION_VIEW`をdocViewerへ。表示は`ZipViewerBody`（一覧＋件数＋安全マーク、破損時は空表示で非クラッシュ）、解凍は`FileViewModel.extractZip`（`BackupPaths.safeResolve`＋2000件/100MB毎/500MB総量＋32KB streaming、失敗は件数化）。新規依存・権限なし、minSdk24維持。
- **フォルダ名正確化(#2)**: 新規pure `FolderNames`（volume/folder/storageRootTitle/重複親の完全パス展開）に統一。Manage/ブラウザ标题/パンくず/DeX sidebar/除外ピッカー/ピン留め/新規タブが同一名。Categoryタブは親フルパスでグルーピング（同名結合の解消）＋重複時のみ完全パス表示。
- **デスクトップはリスト既定(#3)**: 新規pure `DesktopViewModes`（browser/category既定）＋ViewModelのuserSetガード（手動切替を再合成で潰さない）。`MainNavigation`がdesktop旗をFiles/Categoryへ 전달。電話既定は不変（browser GRID、画像/動画 GRID）。
- **Win11風Details(#4)**: `MediaFile.dateAdded`（MediaStore DATE_ADDED、非索引はmodified proxy。NIO不使用でminSdk24維持）＋DataStore 4列（Size/Modified既定表示、Type既定表示、Created既定非表示）＋`FileDetails` pure（typeLabel/detailParts/join）＋行2行化（Files/Category）＋DeXのみヘッダ行（タップでSortOption、非表示列は非表示）＋`SortViewMenu`にColumns区画。5言語17キー追加（parity検証）。
- 安全性：全IOはDispatchers.IO、`runCatching`＋空フォールバック、`!!`なし、新規`rememberLoadable`流用、破壊的操作は外部から到達不可を維持。

## v1.9.2 の設計変更（要約）
- **ドラッグ dismiss**: 画像は`ImageWithOcrOverlay`内・動画は既存Initialパス検出器に下方向分岐を追加。単指・非ズーム・非OCR・下優勢（1.2倍）・28dp係留で係合、係合後は全 pointer を consume（ページ遷移抑止）、複指で abort→復帰。指は plain state直書き（制限スコープでsnapTo不可のため）、解放のみアニメ化（140dp超で画面外＋pop、未満は復帰）。音声ページ対象外。
- **継ぎ目なしフェード**: slide+fade→fade単独、`fillMaxWidth`明示、バー下96dpグラデーションスクリムを同一Visibilityで連動。dismiss中はクローム非表示。
- **Manage実名**: `volumeDisplayName()`（pure、単体テスト）で末尾セグメントを見出し化、フルパスを補足化。内部は既存文言維持。
- 新規文言なし（5言語パリティ維持）。

## v1.9.1 の設計変更（要約）
- **デスクトップ区切り線**: 3箇所の構造線（サイドバー/本文境界・タブストリップ下辺・タブ間）を`outlineVariant`→`outline`へ。ダークで約1.9:1→5.4〜5.8:1、ライトで4.0:1。M3の重要境界用途に合致。`ThemeContrastTest`に非テキスト3:1＋デスクトップ文字4.5:1の回帰を追加。

## v1.9.0 の設計変更（要約）
- **スキル同梱**: `hamen/material-3-skill`（MIT）と`android/skills/testing-setup`（Apache-2.0）を`.agent/skills/`へ同梱（LICENSE同梱・出典明記）。監査はスキル手順の10分類で実施（総合約68/100）。
- **テーマ完結**: `surfaceDim/Bright/Tint`をwarm-neutralランプで追加配線（既定の紫ベース残存を解消）。`Shapes.kt`新設（4/8/12/16/28dp）し`MediaMasterTheme(shapes=)`へ明示（既定値と同値のため見た目不変）。動的カラー既定OFF・書体既定・タイトルウェイトは意図通り維持。
- **電話NavigationBar**: 上位5ルートのみ表示する永続バー（`TOP_DESTINATIONS`＋`BottomNavBar`分離でテスト可能）。`popUpTo(saveState)+launchSingleTop+restoreState`遷移。MiniPlayerはbottomBar内上段に積み、content paddingで下敷きを解消（デスクトップは84dp予約）。DeX検出・幅ゲート・viewer/player・データ層は不変。
- **正規化**: アルバム`Card(onClick)`＋72%帯スクリム、グリッドcheckboxセマンティクス、メニューはtrailing check、Breadcrumbsはselected＋スクロール競合修正、`SwitchRow`共有化（行全体toggle）、時刻は`TimePicker`＋Start/Endタブ、FolderPickerのCreateは`Button`＋Done IME、Network走査は`IconButton`化＋見出しlabel化＋imePadding、サムネイルはplaceholder/error、デスクトップタブにselected。
- **テスト**: `docs/testing.md`＋`AGENTS.md`、JaCoCo（`jacocoTestReport`）、pure seam（`discoveredToDraft`）、Robolectric行為3件、Breadcrumbs/Switch/BottomNav、Roborazzi部品6件（light/dark参照PNG同梱）。Hilt/MockK/AGP更新なし。

## v1.7.1 の設計変更（要約）
- **Lenovoヒューリスティック**: `DesktopMode`に`oemDesktopHeuristic`信号を追加し`hasCoreTrigger()`へOR。実体は`isLenovoDesktopHeuristic()`（Lenovo製＋キーボード/マウス接続、またはPCモード系設定キー`pc_mode`/`lenovo_pc_mode`/`zui_pc_mode`/`productivity_mode`/`desktop_mode`/`lenovo_desktop_mode`のいずれかが有効値。Global/Secure/Systemを横断プローブ、全`runCatching`保護・権限不要）。キーボード分岐はLenovo製にゲートし他社動作不変。`isDesktopLayout()`側で`remember(context, activity)`収集。
- **コントラスト修正**: `CategoryScreen`のファイル名スクリムを`Black 50%`→`72%`＋文字色を`ViewerOnSurface`へ（明背景での白文字が約3.9:1→約9.3:1に改善）。`DocumentsScreen.DocumentListRow`の選択行は`background`直貼りをやめ`ListItemDefaults.colors`でcontainer/headline/supporting/iconを`primaryContainer`/`onPrimaryContainer`へ明示。`LibraryScreen`のアルバム名を`Color.White`→`ViewerOnSurface`へ統一（同コントラスト）。新規`ThemeContrastTest`（JVMのみ、主要ペア＋スクリムの4.5:1を検証）。
- 新規権限・新規Gradle依存なし。既存の幅ゲート・手動オーバーライド・破壊的操作ガードは変更なし。

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
