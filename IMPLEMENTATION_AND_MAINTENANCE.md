# Media Master v1.7.1 実装・保守メモ

最終更新: 2026-09-25

## リリース情報

| 項目 | 内容 |
| --- | --- |
| バージョン | `1.7.1` (`versionCode 12`) |
| アプリケーションID | `com.yukiorita.mediamaster` |
| 最小 SDK / target SDK | 24 / 36 |
| ライセンス | MIT |
| 著作者 | Yuki_Orita |
| release APK | `app/build/outputs/apk/release/app-release.apk`（R8 + resource shrink 有効） |
| APK SHA-256 | `4f79abcc32747a6889787c22d051ff831d55aca27330f81923760fcc63fcc1ee` |
| 署名証明書 SHA-256 | `33:2C:E3:86:FB:F2:92:54:F1:79:78:B0:44:B8:BD:22:D6:A7:41:89:54:BB:50:59:38:72:17:12:E3:4E:EB:A6`（v0.1.0〜v1.7.0 と同一鍵） |
| GitHub Release | `v1.7.1` (GitHub Releases) |

release APK は RSA 4096 ビット鍵・APK Signature Scheme v2 署名（`apksigner verify` で確認済み）。署名鍵は `common-rules-document/keystores/media-master-upload-key.jks`（alias `upload`）。

v1.7.1 はJDK21+R8有効ビルドで`:app:assembleRelease`/`:app:testDebugUnitTest`(77件)/`:app:lintDebug`(0エラー)を確認。**実機（エミュレータ）デバッグ実施済み**——本番署名済みAPKをクリーンインストールし起動・Home表示でクラッシュ0件（`logcat -b crash` FATAL 0件）。

旧リリースの APK SHA-256: v1.7.0 `08236934c0cbf522ce0432595933e03976368ec3b0ee000ad4ced962b2fa9fd5`、v1.6.0 `f922b2b2d797f69fe4b1c9a7aca366e9f1664f8b47c8cfbeeb9aaeb02235b615`。

## v1.7.1 実装内容（Lenovo PCモード修正＋ダークモード視認性）

ユーザー報告2件への対応（Lenovo TabでPCモードでもスマホUIのまま／ダークモードのコントラスト不足）。

### 1. Lenovo Tab PCモードでデスクトップUIへ切り替わらない

- 原因: ZUIのPCモードは公開検出APIがなく、desk uiMode・キャプションバー・multi-window・freeformのいずれも立てない場合がある。v1.7.0の汎用信号だけでは検出できずAUTOが偽のままになる。
- 修正: `DesktopMode`に`oemDesktopHeuristic`を追加。`isLenovoDesktopHeuristic()`は①Lenovo製＋キーボード/マウス接続（Lenovo公式仕様のキーボード着脱でPCモード自動切替に追随）、②PCモード系設定キー（`pc_mode`等6候補をGlobal/Secure/Systemで横断プローブ）のいずれかで真。①はLenovo製にゲートし他社不変、②は全`runCatching`・権限不要。新規テスト1件、全77件通過。
- 暫定回避（既にv1.7.0搭載）: **設定 → デスクトップモード → 常にデスクトップUI**で即時切替可能。Lenovo実機での最終確認を推奨。

### 2. ダークモード視認性（白文字化・コントラスト）

- `CategoryScreen`グリッドのファイル名スクリムを黒50%→72%へ（明背景での白文字が約3.9:1→約9.3:1に改善、AA適合）。文字色を`ViewerOnSurface`へ統一。
- `DocumentsScreen`選択行を`background`直貼りから`ListItemDefaults.colors`の正規ペアリング（`primaryContainer`＋`onPrimaryContainer`）へ修正。見出し・補足・アイコンすべて明示。
- `LibraryScreen`アルバム名を`Color.White`→`ViewerOnSurface`へ統一。
- テーマ本体の主要ペアはすべてAA適合を確認済み（例: dark onSurface/surface 14.3:1）。新規`ThemeContrastTest`で回帰を防止。
- 変更ファイル: `desktop/DesktopMode.kt`、`ui/DesktopNavigation.kt`、`ui/CategoryScreen.kt`、`ui/LibraryScreen.kt`、`ui/DocumentsScreen.kt`、新規`desktop/DesktopModeTest`追加分＋新規`ui/theme/ThemeContrastTest.kt`、`app/build.gradle.kts`（1.7.1/12）。

## リリース情報

| 項目 | 内容 |
| --- | --- |
| バージョン | `1.7.0` (`versionCode 11`) |
| アプリケーションID | `com.yukiorita.mediamaster` |
| 最小 SDK / target SDK | 24 / 36 |
| ライセンス | MIT |
| 著作者 | Yuki_Orita |
| release APK | `app/build/outputs/apk/release/app-release.apk`（R8 + resource shrink 有効） |
| APK SHA-256 | `08236934c0cbf522ce0432595933e03976368ec3b0ee000ad4ced962b2fa9fd5` |
| 署名証明書 SHA-256 | `33:2C:E3:86:FB:F2:92:54:F1:79:78:B0:44:B8:BD:22:D6:A7:41:89:54:BB:50:59:38:72:17:12:E3:4E:EB:A6`（v0.1.0〜v1.6.0 と同一鍵） |
| GitHub Release | `v1.7.0` (GitHub Releases) |

release APK は RSA 4096 ビット鍵・APK Signature Scheme v2 署名（`apksigner verify` で確認済み。v1無効、v3/v4はminSdk/AGP設定上有効化されていない——これまでの全リリースと同一の挙動）。署名鍵は `common-rules-document/keystores/media-master-upload-key.jks`（alias `upload`）。公開前には毎回 `apksigner verify --verbose` で署名を確認してください。

v1.7.0 はJDK21+R8有効ビルドで`:app:assembleDebug`/`:app:assembleRelease`/`:app:testDebugUnitTest`(73件)/`:app:lintDebug`(0エラー)を確認。**実機（エミュレータ）デバッグ実施済み**——本番署名済みAPKをクリーンインストールしてクラッシュがないことを確認する手順を、これまでのリリースの教訓どおり今回も実施した（下記「v1.7.0 実機デバッグ」参照）。

旧リリースの APK SHA-256: v1.6.0 `f922b2b2d797f69fe4b1c9a7aca366e9f1664f8b47c8cfbeeb9aaeb02235b615`、v1.5.0 `94647242f431bfec7919fbbe24700ef6e3d0f04aa871127056a7b59ef3a6dc01`。

## v1.7.0 実機デバッグ（2026-09、リリース前スモーク）

エミュレータ（API 34, arm64, Google APIs, `mm_test` AVD）へ本番署名済みAPK（v2署名・証明書はv0.1.0〜v1.6.0と同一 `332ce386…eba6`）をクリーンインストール：

- 起動・Home表示まで操作してクラッシュなし（PID確認、`adb logcat -b crash`でFATAL 0件）。
- `adb shell am start --windowingMode 5`（freeform）で起動してもクラッシュなし（singleTaskのため既存インスタンスへ配信される既定動作を確認）。
- 新規 `DesktopModeTest` 5件を含む全73件の単体テストが通過（従来68件＋新規5件）。
- `lintDebug`エラー0件（警告のみ、既存のdeprecation警告と同等）。
- 幅ゲート（≥600dp）を満たさない小型エミュレータではデスクトップUIへの切替は目視未検証（v1.5.0と同一の制約）。Lenovo Tab 11インチ実機・Samsung DeX実機での最終確認を推奨。検出できない端末向けの手動オーバーライド（設定→デスクトップモード）を用意済み。

## v1.7.0 の実装内容（8ベンダーPCモード対応）

2026年9月時点の仕様調査に基づく（Samsung DeXはOne UI 8でAndroid 16ネイティブデスクトップへ再構築、`UI_MODE_TYPE_DESK`単独では検出不可。Motorola Smart Connect（旧Ready For）のMobile Desktop、Huawei Easy Projection、HONOR MagicOS PCモード、Xiaomi HyperOS Workstation、Lenovo ZUI PCモードはいずれも公開検出APIなし・内部的にはfreeform/multi-window。OPPO ColorOSはオンデバイスのネイティブデスクトップなし・PC ConnectはPC側ミラーリングのためAOSP信号に依存）。

- 新規 `desktop/DesktopMode.kt`: `Signals`（desk uiMode/キャプションバー/multi-window/freeform/Samsung reflection＋参考情報のキーボード・外部ディスプレイ）＋pureな`resolve()`＋`vendorForManufacturer()`。OEM SDK依存なし、minSdk 24、全文`runCatching`保護、ステートレスでメモリ安全。
- `DesktopNavigation.isDesktopLayout(override)`へ拡張（既存の無引数呼び出しと互換のためデフォルト引数AUTO）。`MainNavigation`で`settingsViewModel.desktopModeOverride`を購読し、幅ゲート（≥600dp）とAND条件でデスクトップシェルを切替。
- 設定に「デスクトップモード」（自動/常にデスクトップ/常にタッチ）を追加。DataStore永続化、5言語（en/ja/zh/ar/nl）完全対応。
- Manifest: `resizeableActivity="true"`明示、`windowSoftInputMode="adjustResize"`、`configChanges`に`navigation|colorMode`追加。新規権限・新規依存なし。
- 新規テスト `DesktopModeTest` 5件（AUTO無信号・各トリガー・周辺機器単独では発火しない・オーバーライド優先・8ベンダー mapping）。`LocaleStringParityTest`で新6キーの5言語 parity を検証。
- 変更ファイル: `desktop/DesktopMode.kt`（新）、`ui/DesktopNavigation.kt`、`ui/MainNavigation.kt`、`SettingsRepository.kt`、`SettingsViewModel.kt`、`ui/SettingsScreen.kt`、`AndroidManifest.xml`、5言語`strings.xml`、`app/build.gradle.kts`（1.7.0/11）。

v1.4.0 は JDK21+R8有効ビルドで`:app:assembleDebug`/`:app:assembleRelease`/`:app:testDebugUnitTest`(68件)/`:app:lintDebug`(0エラー)を確認。実機（エミュレータ）デバッグ実施済み。旧リリースの APK SHA-256: v1.4.0 `2051ca8e092dd68a1bcdf0b2b65f40e8953ad80d66fa763cd5d1395e8cd4ad93`、v1.3.0（実機デバッグ後の最終版）`500ec32227858828e81370fc6b5d90f39495fc8b081a398a860610953ad06058`、v1.2.0 `a9be2530fc51397582a820b9e9c7404dad3d1374d685838e0170de495e591c33`、v1.1.0 `fe75d958b3c811a48582058903d95947b1f97ad108b8c23fd5338ec6ae8eb9a3`、v1.0.0 `255d8ed2b60e1f7a3dd51d1f933b08ae39cc7fa9a8398149202cb20d038b0082`、v0.3.0 `5f896b1bd15a65b4a947c428490ca63cc0ea0cac81332d89477029b5fe3d4bab`。

## v1.6.0 実機デバッグ（2026-09、ユーザー依頼によるリリース前スモーク）

エミュレータ（API 34, arm64, Google APIs, `mm_test` AVD）へ、日付をずらしたテスト画像2枚とテスト動画1本（ffmpeg生成、40秒）を配置し、デバッグビルドで新機能を実際に操作して確認した：

- ライブラリで写真/動画を開いた状態からのスワイプで次/前のメディアに移動できることを確認。**実装直後の1回目の検証で、画像ページ・動画ページのどちらでもスワイプがページャーまで届かない実際のバグを発見した**（詳細は下記「実機デバッグで発見・修正したバグ」）。修正後、画像→動画・動画→画像・画像→画像のいずれの組み合わせでもスワイプ遷移が機能することを実機で確認済み。
- 動画再生中に画面をタップすると、再生/一時停止ボタンと左右の「10秒戻す/進む」ボタンが一緒に表示されることを確認。「10秒進む」ボタンをタップし、シークバーの位置が実際に10秒分進むことを確認（例: 00:02→00:12、00:13→00:23）。
- 上記のスワイプ修正がOCRモードの画像ズーム・トグル操作を壊していないことを確認（OCRボタンのタップでモード切替アイコンが正しく変わることを確認）。
- 本番相当のデバッグビルドを一連の操作（動画再生・シーク・スワイプ・OCRトグル）の間、`adb logcat`でFATAL EXCEPTION・AndroidRuntimeエラーが0件であることを確認。
- 本番署名済みAPK（`apksigner`でv2署名・検証済み、証明書はv0.1.0〜v1.5.0と同一）をアンインストール→クリーンインストールし、起動・Library・動画再生まで操作してクラッシュがないことを確認。

### 実機デバッグで発見・修正したバグ（静的チェックでは検出不可能）

- **症状**: ライブラリで写真/動画を開いた直後、画面を1本指で横にスワイプしても次/前のメディアに移動しない（`HorizontalPager`自体は実装済みだったにもかかわらず反応しない）。
- **原因（画像ページ）**: OCRモード用のピンチズーム・パン検出（`ImageWithOcrOverlay`内の旧`detectTransformGestures`）が、ズームしていない等倍状態でも1本指の横ドラッグを無条件に「パン」として消費してしまい、親の`HorizontalPager`にドラッグイベントが一切渡っていなかった。
- **原因（動画ページ）**: 上記に加えて、ExoPlayerの`PlayerView`（`AndroidView`経由で埋め込んだネイティブView）が、コントローラー表示用の`GestureDetector`によって`ACTION_DOWN`の時点でジェスチャー全体を握ってしまい、Compose側の`HorizontalPager`が指の動きを一切観測できない状態になっていた（Composeの`AndroidView`相互運用における既知の制約——埋め込みNativeViewは、祖先のCompose側ジェスチャー検出がInitialパス（親→子の順で先に実行される段階）で明示的に消費しない限り、ACTION_DOWNの時点でジェスチャー全体を握ってしまう）。
- **修正**:
  - `ImageWithOcrOverlay`（[ViewerScreen.kt](app/src/main/java/com/example/ViewerScreen.kt)）と共有`ZoomableBox`（[ZoomableBox.kt](app/src/main/java/com/example/ui/components/ZoomableBox.kt)）のジェスチャー検出を、「2本指(ピンチ)のとき」または「すでに拡大中のとき」だけイベントを消費するように変更。等倍時の1本指スワイプは消費せずページャーに委ねる。
  - 動画ページには、`PlayerView`より手前（Compose階層で親側）に独自の軽量スワイプ検出を追加し、Initialパスで横方向のドラッグを検知した時点でイベントを消費して`pagerState.animateScrollToPage()`を直接呼び出すことで、ネイティブ`PlayerView`にジェスチャーを奪われる前にページ送りを行うようにした。
  - いずれもズーム中（`isZoomedIn`）は無効化し、ズーム操作とスワイプ操作が競合しないようにしている。
- この種のジェスチャー競合はユニットテスト・lintでは検出できず、実機（エミュレータ含む）での実際のタッチ操作でしか見つからないクラスのバグであり、今回も「実機で動かして初めて分かった」典型例だった。

## v1.5.0 実機デバッグ（2026-09、ユーザー依頼によるリリース前スモーク）

エミュレータ（API 34, arm64, Google APIs, `mm_test` AVD）へテスト用の画像（日付をずらした8枚のJPEG）・音声ファイルを配置し、デバッグビルドで新機能を実際に操作して確認した：

- Manage → Images/Videos がデフォルトでタイル(グリッド)表示になることを確認。
- Manage の「最近のファイル」で画像の実サムネイルが表示されることを確認（従来は汎用アイコン固定）。Audioのトラック一覧でアルバムアートが無い曲は音符アイコンに正しくフォールバックすることを確認。
- Library（写真&動画）が日付ごとにグルーピングされ（"Sep 10, 2026" 等の見出し）、初期表示が自動的に最下部（最新の写真）までスクロールされていることを確認。
- Library の複数選択で「1 selected」ツールバーに全選択・共有・フォルダメニュー（移動/コピー）・削除の4アクションが表示されることを確認。フォルダメニュー→移動→新規フォルダ作成（"TestAlbum"）→作成したフォルダへの移動、を最後まで実行し、実ファイルシステム上でファイルが正しく移動され（移動元から削除・移動先に出現）、Library側の表示にも反映されることを確認。
- `Activity.isInMultiWindowMode()` の追加は、`adb shell am start --windowingMode 5`（freeform）でアプリを起動した際に、OSの`dumpsys activity`上で`mWindowingMode=freeform`として認識されることを確認した（この小型エミュレータでは幅ゲート`600dp`を満たす広いフリーフォームウィンドウを作れなかったため、デスクトップUIへの実際の切替までは検証できていない。**Lenovo Tab 11インチ実機のPCモードでの最終確認を推奨**）。
- 本番署名済みAPK（`apksigner`でv2署名・検証済み、証明書はv0.1.0〜v1.4.0と同一）をアンインストール→クリーンインストールし、起動・Library・Audio画面まで操作してクラッシュがないことを確認。`adb logcat`にFATAL EXCEPTION・AndroidRuntimeエラーは0件。

## v1.4.0 実機デバッグ（2026-09、ユーザー依頼によるリリース前スモーク）

v1.3.0の教訓（「`assembleRelease`成功だけで配布しない」）に従い、本リリースでは**実装直後・リリース前の両方**でAndroidエミュレータ（API 34, arm64, Google APIs）へ実インストールして確認した。

- デバッグビルドで全新機能（DeXキャプションバー検出、ズーム拡大、ビューアーのリネーム/詳細情報、Now Playing/イコライザー、Manage画面刷新一式）を実際に操作して検証：ストレージ使用量バー・最近のファイル・パンくず・検索・リネーム・移動（実ファイルI/O経由でPictures→Moviesへの移動を確認）・イコライザーのプリセット適用（Jazzプリセット選択でバンド値が実際に変化）まで実機上で動作確認済み。
- **実機デバッグで発見・修正したバグ1件**: `FilesScreen`の一覧で表示される`..`（親フォルダへ戻る合成エントリ）が、長押しで複数選択モードに入れてしまい、新設したリネーム/移動/コピー操作の対象になり得た。選択すると「親フォルダそのもの」をリネーム・移動しようとする、意図しない破壊的操作になり得るバグ。`FileItemRow`/`FileItemGrid`の`onLongClick`条件を修正し、`..`は常に選択不可にした。
- バックグラウンド再生・システム通知（メディア通知シェードでの再生/一時停止/スキップ）、画面回転、ホームボタンでのバックグラウンド化→復帰、5回連続の戻るボタンでのアプリ終了、いずれもクラッシュなし。`adb logcat`全体を通してFATAL EXCEPTION・ANRは0件。
- **本番署名済みAPK（`apksigner`でv2+v3署名・検証済み、証明書はv0.1.0〜v1.3.0と同一）をアンインストール→クリーンインストールし、初回コールド起動からホーム画面・Manage画面まで実際に操作してクラッシュがないことを確認**（v1.3.0で「署名済みAPKを一度も実機導入していなかったために起動不能バグが5リリース連続で見逃されていた」教訓を踏まえた必須手順）。

## v1.3.0 実機デバッグ（2026-09、リリース後・配布前に発見）

GitHub Release `v1.3.0` を一度公開した直後、ユーザー依頼により初めてAndroidエミュレータ
（API 34 arm64、Google APIs）へ実際にインストールして動作確認したところ、**単体テスト・lint・
R8ビルドのいずれも検出できない4件の重大バグ**が見つかった。いずれも修正し、release APKを
差し替えた（バージョン番号・versionCodeは据え置き、署名は同じ鍵で再実施）。

### 1. リリースビルドが起動直後に必ずクラッシュ（最重要）

- 症状: `app-release-signed.apk` をインストールして起動すると「Media Master keeps stopping」で
  即座に落ちる。`FATAL EXCEPTION: ... Failed to create an instance of class
  androidx.work.impl.WorkDatabase...`。
- 原因: `androidx.startup.InitializationProvider`（`ContentProvider`、プロセス起動時に必ず生成
  される）が`WorkManagerInitializer`を実行し、WorkManager内部のRoomデータベース
  （`WorkDatabase`/`WorkDatabase_Impl`）を生成しようとするが、R8にRoom/WorkManager向けの
  keepルールが一つも無く、リフレクションによる生成が失敗していた。
- **影響範囲**: R8（`isMinifyEnabled=true`）が有効化されたv0.3.0以降の**全リリースビルドが
  同じ理由でクラッシュしていた可能性が高い**（バックアップ機能でWorkManagerを使用しているため）。
  これまで一度も実機・エミュレータへ署名済みAPKをインストールして起動確認していなかったため
  発覚しなかった。「実機スモーク未実施」が実際にリリースを壊していた実例。
- 修正: `app/proguard-rules.pro`に`androidx.room.RoomDatabase`のサブクラス・`@Database`
  アノテーション付きクラス・`*_Impl`命名のRoom生成クラス・`androidx.work.impl.**`を
  keepするルールを追加。

### 2. ライブラリ/ドキュメント/オーディオ一覧が"Invalid token LIMIT"で読み込み失敗

- 症状: Documents画面の「Document List」タブが赤字の「Invalid token LIMIT」で表示され、
  ファイルが一件も表示されない。Library/Audioも同様に空になる。
- 原因: v1.1.0で導入したMediaStoreページングが、`sortOrder`文字列に直接
  `"_ID ASC LIMIT n OFFSET m"`を連結する方式だった。この方式は一部のMediaProvider実装では
  動作するが、Android 14（API 34）のMediaProviderは`sortOrder`内の`LIMIT`トークンを拒否し
  `IllegalArgumentException: Invalid token LIMIT`を投げる。
- 修正: `MediaRepository.queryMediaVolume()`をAPI 26以上では`ContentResolver`の
  クエリ引数`Bundle`（`QUERY_ARG_SQL_SORT_ORDER`/`QUERY_ARG_SQL_LIMIT`/`QUERY_ARG_OFFSET`、
  いずれも公式サポートAPI）経由に変更。API 24/25では単一の無ページングクエリにフォールバック
  （`CursorWindow`がIPC層で自動的に部分読み込みするため、全件を一度に読んでもメモリ問題は生じない）。

### 3. `.tex`ファイルを開くと正規表現の構文エラーでクラッシュ

- 症状: `.tex`ファイルを開くと`PatternSyntaxException: Syntax error in regexp pattern`で
  クラッシュ。
- 原因: `LatexSourceParser`の環境検出用正規表現内の閉じ`}`がエスケープされていなかった。
  デスクトップJVMの`java.util.regex`はエスケープなしの`}`を許容するため、**JVM上で動く
  単体テストは合格していた**が、Android実機のICU正規表現エンジンはこれを拒否する。
  構造的にJVM単体テストでは検出不可能なクラスのバグ。
- 修正: `\}`へエスケープ。今後新規に正規表現を追加する際は実機・エミュレータでの動作確認を
  必須とする（本件をレビュー観点として残す）。

### 4. Markdown/`.tex`のインライン数式が1文字ずつ改行されて表示される

- 症状: `.tex`の`\(a+b=c\)`のようなインライン数式が、"a +" "b =" "c" のように文字単位で
  縦に折り返されて表示される。
- 原因: `LatexView`の自己サイズ調整（`selfSizing`）は、KaTeXからの実測値が届くまでの初期状態で
  幅をほぼ0pxにしていた。加えて呼び出し側が`horizontalScroll`でラップしていたため、
  Compose側の`fillMaxWidth()`が無限幅制約下で機能せず、極小ビューポートでKaTeXが数式を
  文字単位に折り返し、その"折り返された"レイアウトのまま実測値が確定してしまっていた。
- 修正: `LatexView`は幅を測定値で固定せず`fillMaxWidth()`のみに委ね（高さのみ測定値で調整）、
  さらに数式表示側の`horizontalScroll`ラッパーを撤去（無限幅制約の発生源を除去）。

### 副次的な修正: Documentsリストの種別ラベル

- 全ての非PDFファイルの種別表示が常に「PDF document」になっていた（`pageCount`が-1のときの
  フォールバック文言が固定だったため）。ファイル拡張子（`CSV`/`DOCX`等）を表示するよう修正。

### 検証方法

Android SDK cmdline-tools経由でAPI 34 arm64（Google APIs）システムイメージ・emulatorパッケージを
その場でインストールし、`mm_debug` AVDを新規作成してheadless起動。`adb install`で
（デバッグ鍵署名の）release APKを導入し、テキスト/CSV/JSON/Markdown/PDF/DOCX/PPTX/`.tex`/
Shift_JIS/バイナリの全形式を実際にタップで開き、削除・共有・既定アプリ設定導線を含めて
スクリーンショットとlogcat（`-b crash`）で確認。上記4件を検出・修正後、同じ手順で再検証し
問題なしを確認してから、本番署名鍵で最終APKを作成しGitHub Releaseを差し替えた。

## v1.6.0 の実装内容（ビューアーのスワイプナビゲーション・動画10秒シークボタン）

- **ライブラリのビューアーでスワイプによる次/前メディア移動**: 画像/動画を開いた状態から1本指で左右にスワイプすると、`HorizontalPager`経由で次/前のメディアに移動する。既存の`HorizontalPager`自体はv1.0.0から存在していたが、画像のピンチズーム用ジェスチャーと動画の`PlayerView`（ネイティブView）がスワイプイベントを奪ってしまい実際には機能していなかったバグを、実機デバッグで発見・修正した（詳細は「実機デバッグで発見・修正したバグ」参照）。
- **動画再生に「10秒戻す/進む」ボタンを追加（YouTube風）**: 再生中に画面をタップすると、中央の再生/一時停止ボタンの両脇に「10秒戻す」「10秒進む」ボタンが表示されるようになった。`ExoPlayer.Builder`の`setSeekBackIncrementMs`/`setSeekForwardIncrementMs`を10秒に設定し、`PlayerView`の`setShowRewindButton`/`setShowFastForwardButton`を有効化することで実現（ExoPlayer/media3標準機能の組み合わせで、独自のシークUIを新規実装する必要はなかった）。
- 変更ファイルは[ViewerScreen.kt](app/src/main/java/com/example/ViewerScreen.kt)と[ZoomableBox.kt](app/src/main/java/com/example/ui/components/ZoomableBox.kt)のみ。

## v1.5.0 の実装内容（Manage/ライブラリ追加改善5件）

実機（Lenovo Tab 11インチ）での使用を通じて出た5件の指摘への対応。

### 1. Manage → 画像/動画のデフォルト表示
`FileViewModel`の`_categoryViewMode`はカテゴリ非依存の単一StateFlowのため、`CategoryScreen`側で
`LaunchedEffect(categoryName)`により入場時にカテゴリ種別に応じた初期値（IMAGES/VIDEOSはGRID、
それ以外はLIST）を設定するようにした。

### 2. サムネイル/アイコン表示の統一
新規共有コンポーネント`ui/components/MediaThumbnail.kt`が、`CategoryScreen`/`LibraryScreen`で
確立済みの「画像/動画は`AsyncImage(model = file.contentUri ?: File(file.path))`」パターンを一般化。
音声は`MediaMetadataRetriever.embeddedPicture`を`produceState`+`Dispatchers.IO`で非同期抽出し、
無ければ`Icons.Default.MusicNote`にフォールバック。`FilesScreen`/`AudioScreen`/
`ManageDashboardScreen`の3箇所（従来はいずれも常に汎用アイコン固定だった）に適用。

### 3. DeX / Lenovo PCモード判定の拡張
`Activity.isInMultiWindowMode()`を`DesktopNavigation.kt`の`isDesktopLayout()`にOR条件で追加。
LenovoのPCモード/Productivity Modeのような、第三者向け検出APIを公開していないOEM独自の
デスクトップシェルは、内部的にはAndroid標準のフリーフォーム/マルチウィンドウとしてアプリを
ホストしていることが多く、これが現状取得できる最も汎用的な追加シグナル。ただし100%の保証は
できないため、実機での最終確認が必要（上記「v1.5.0 実機デバッグ」参照）。

### 4. ライブラリのGoogleフォト風グルーピング・ピンチ密度切替
`LibraryScreen`にローカルな`LibraryDensityMode`(`BY_DATE`/`COMPACT_ALL`)を導入。
- `BY_DATE`（デフォルト）: `dayKey()`で日付ごとにグルーピングし、`LazyVerticalGrid`の
  `item(span = { GridItemSpan(maxLineSpan) })`で日付見出し行を挿入。データロード後に
  一度だけ`LazyGridState.scrollToItem()`で最下部（最新）へ自動スクロール。
- `COMPACT_ALL`: `Modifier.pointerInput`+`awaitEachGesture`+`calculateZoom`で2本指ピンチのみを
  検知（1本指ドラッグは`event.changes.size < 2`のため一切consumeせず、グリッドの通常スクロールと
  非干渉）し、フラットな小さめグリッド＋`sortMediaFiles()`（`FileViewModel`から共有関数として
  抽出、`MediaFile.kt`に配置）による並び替えメニューへ切替。

### 5. ライブラリ複数選択アクションの拡張
`LibraryScreen`の選択モードツールバーに全選択・削除（`ConfirmDeleteDialog`再利用）・
フォルダ/アルバムメニュー（移動/コピー、`components/FolderPickerDialog`+
`FileViewModel.moveFile`/`copyFile`を再利用）を追加。`FolderPickerDialog`自体にも
新規フォルダ作成ボタン（`AlertDialog`+`File.mkdir()`）を追加し、`FilesScreen`側の
移動/コピーにも自動的に反映される。

## v1.4.0 の実装内容（安定性・UX改善6件）

ユーザー要望に基づく6件の改善。詳細設計は実装前にプラン化・承認を得たうえで着手した。

### 1. DeX / デスクトップウィンドウイング判定の修正

- `ui/DesktopNavigation.kt`の`isDesktopLayout()`が`Configuration.UI_MODE_TYPE_DESK`のみに依存
  していたため、One UI 8（Android 16ベース）以降で旧来のDeXがAndroid標準の「デスクトップ
  ウィンドウイング」に置き換わり、このフラグが立たない端末でPCモードに切り替わらなくなって
  いた。Android公式ドキュメントも`UI_MODE_TYPE_DESK`を推奨しておらず、ウィンドウサイズ主体の
  判定を推奨している。
- 修正: `UI_MODE_TYPE_DESK`（旧来DeX向け、後方互換のため維持）に加え、`WindowInsets.captionBar`
  （Compose）とその`ViewCompat`フォールバックでシステムキャプションバーの表示有無を検出し、
  いずれかが真ならデスクトップ扱いとする。`MainNavigation.kt`側の`maxWidth >= 600.dp`ゲートは
  変更なし（小さいポップアップ/分割画面ウィンドウが誤ってデスクトップUIになるのを防ぐため）。
  新規Gradle依存の追加なし。

### 2〜3. ピンチズームの拡大

- 既存の画像ビューアーのズーム処理（`ViewerScreen.kt`の`ImageWithOcrOverlay`、OCR座標計算と
  密結合）はそのまま維持し、新規`ui/components/ZoomableBox.kt`を追加。
- 適用先: `ViewerScreen.kt`の動画再生ページ（`PlayerView`をラップ、表示レイヤーでの拡大/パン。
  再エンコードなし）、`ui/viewer/DocumentViewerScreen.kt`の`PdfViewerBody`（各ページ）、
  `DocxViewerBody`/`PptxViewerBody`が使う`InlineDecodedImage`（埋め込み画像）。
- いずれも`HorizontalPager`と共存させるため、ズーム中（`scale > 1`）は`userScrollEnabled`を
  falseにしてページ送りジェスチャーと競合しないようにした。

### 4. ビューアーへのファイル管理機能追加

- `ViewerScreen.kt`のトップバーに「リネーム」「詳細情報」を追加（削除・共有は既存）。
- `FileViewModel.kt`に`renameFile()`を新設。`deleteFile()`と同じデュアルパス処理
  （API29+は`ContentResolver.update(MediaStore.MediaColumns.DISPLAY_NAME)`、レガシーパスは
  `File.renameTo`）。
- 詳細情報は`ModalBottomSheet`で名前/パス/サイズ/更新日時/画像解像度（`BitmapFactory.Options`）・
  動画長（`ExoPlayer.duration`）を表示。

### 5. 音楽プレイヤーの本格化

- バックグラウンド再生・システム通知（`playback/PlaybackService.kt`が`MediaSessionService`+
  ExoPlayer+`MediaSession`で構成）は元々機能していたが、専用のNow Playing画面
  （`ui/NowPlayingScreen.kt`）とシステムイコライザー（`playback/EqualizerController.kt`、
  `android.media.audiofx.Equalizer`をラップ、5-6バンド+プリセット、`ui/EqualizerScreen.kt`）を
  新設。
- 副次的に発見した不具合: `AudioScreen.kt`の`playAudioList()`が`MediaItem.fromUri()`のみで
  `MediaMetadata`を設定しておらず、MiniPlayer/通知にファイルURIやmediaIdがそのまま表示される
  ことがあった。`MediaItem.Builder`でタイトル（拡張子除去したファイル名）を設定するよう修正し、
  `PlaybackManager.kt`に`onMediaMetadataChanged`リスナーを追加してExoPlayerが自動抽出する
  ID3/Vorbisタグ（タイトル/アーティスト/アートワーク）も反映されるようにした。
- **実装中に発見・修正したバグ**: `EqualizerController.attach()`を`Player.Listener
  .onAudioSessionIdChanged`のコールバック内でのみ呼んでいたが、ExoPlayerは`Builder.build()`
  時点で既にオーディオセッションIDを確保しているため、単一トラック再生の通常ライフサイクルでは
  このコールバックが一度も発火せずイコライザーが常に空のまま、という不具合があった。
  `player.audioSessionId`を`build()`直後に直接読んで`attach()`する処理を追加し解消。

### 6. 管理(Manage)画面の刷新（Files by Google / マイファイル準拠）

- `ui/ManageDashboardScreen.kt`にストレージ使用量バー（`StatFs`+`MediaCategory.matches()`による
  カテゴリ別内訳、既存のカテゴリ判定ロジックを再利用）と「最近のファイル」水平リストを追加。
- 新規`ui/components/BreadcrumbBar.kt`（パンくずナビゲーション）、`ui/components/
  FolderPickerDialog.kt`（移動/コピー先を選ぶ、`FileViewModel`の共有状態とは独立した自己完結
  ダイアログ）。
- `FilesScreen.kt`に検索バー（現在フォルダ内をクライアント側でフィルタ）、リネーム（単一選択時）・
  移動・コピーをマルチセレクトのアクションバーに追加（削除・共有は既存）。
- `FileViewModel.kt`に`moveFile()`/`copyFile()`を新設。`copyFile()`は`ContentResolver
  .openInputStream()`（MediaStore経由）または直接`File`読み込みでソースを読み、宛先へは常に
  `File`書き込み（`MediaScannerConnection.scanFile()`でMediaStoreに反映）。`moveFile()`は
  コピー成功後に既存の`deleteFile()`を呼んで元ファイルを削除する構成とし、スコープドストレージの
  `RecoverableSecurityException`処理を新規実装せず再利用した。
- 副次的な修正: `FilesScreen`のタイトルが「内部ストレージ」直下で`currentPath
  .substringAfterLast("/")`により`"0"`（`/storage/emulated/0`の末尾）と表示されていたのを、
  ストレージルート判定時は「Internal Storage」/「External Storage」ラベルを表示するよう修正。

### 検証

- JDK21で`assembleDebug`/`assembleRelease`/`testDebugUnitTest`(68件)/`lintDebug`(0エラー)を
  確認。既存テストに変更なし（新規UIのロジックはユニットテストではなく実機操作で検証）。
- 実機（エミュレータ）デバッグを実装直後とリリース前の2回実施。詳細は上記「v1.4.0 実機デバッグ」
  節を参照。

## v1.3.0 の実装内容（Markdown/`.tex`のLaTeX数式表示、削除ボタン修正）

### LaTeX数式（Markdown）

- `viewer/MathExtractor`: CommonMark解析前に `$...$`/`$$...$$` を抽出し、ASCII制御文字
  （STX/ETX）ベースのプレースホルダに置換（実文書との衝突なし）。`\$`はエスケープとして
  数式化しない。
- `MarkdownParser`にプレースホルダ復元処理を追加。`MdBlock.MathBlock`（表示数式）／
  `MdInline.Math`（インライン数式）を新設し、太字・リンク等と共存可能。
- `ui/viewer/LatexView`: `androidx.webkit:webkit`の`WebViewAssetLoader`で
  `assets/katex/`（KaTeX 0.16.11本体・`auto-render`拡張・フォントwoff2一式のみ、
  MITライセンス、npm registryから取得）を仮想オリジン経由で読み込みKaTeX描画。
  ブロック数式は`@JavascriptInterface`でJSからscrollWidth/Heightを報告させ自己サイズ調整。
  インライン数式はCompose`InlineTextContent`/`Placeholder`で埋め込み、初回は文字数
  ヒューリスティックでサイズ推定→実測値が届き次第そのformula専用キーで補正（1回だけ
  再レイアウトが起きる設計、以降は同じ数式なら即正確なサイズ）。
  `trust:false`（KaTeX既定）を維持し`\href`等の危険コマンドは無効のまま。
- セキュリティ: 数式文字列はJSON文字列としてエスケープした上で、さらに`</`を`<\/`へ
  置換してから`<script>`ブロックへ埋め込む（`</script>`断片によるスクリプトタグ早期終了
  を防止）。ネットワークアクセスは一切なし（全アセットローカル）。
- R8: `@android.webkit.JavascriptInterface`メソッドの`-keepclassmembers`を追加
  （未追加でもビルドは通るが、名前がobfuscateされJSブリッジが実行時に静かに壊れる
  ため必須）。

### `.tex` ソースビューアー

- `viewer/LatexSourceParser`: `$...$`/`$$...$$`/`\(...\)`/`\[...\]`/
  `equation`・`align`・`gather`・`eqnarray`・`multline`（`*`付き含む）環境を検出し、
  数式区間とプレーンテキスト区間に分割する最左優先トークナイザ。完全なLaTeXコンパイルは
  端末上では非現実的なため、数式以外は原文をそのまま等幅表示する簡易ビューアー。
- `ViewerKind.LATEX_SOURCE`（拡張子`tex`/`ltx`、MIME `text/x-tex`）として分類、
  `AndroidManifest.xml`の`ACTION_VIEW` intent-filterと`DeepLinks`のdocViewer
  ルーティング対象にも追加。

### 削除ボタンの修正（全ビューアー共通）

- 従来 `DocumentViewerScreen` の削除ボタンは `FileViewModel.mediaState`
  （画像/動画/音声のみを保持）内を検索して対象ファイルを探しており、CSV/JSON/PDF/
  Office等の文書ファイルでは常にヒットせず削除ボタンが表示されない潜在バグがあった。
  ナビゲーションルートを `docViewer/{uri}?path={path}` に拡張し、呼び出し元
  （`FilesScreen.openMediaFile`）が実ファイルパスを渡した場合のみ削除を許可する設計に
  変更（`FileViewModel.deleteFile(path, uri)`をそのまま利用、既存の削除フローと同一）。
  外部Intent由来の未知URI（`path`なし）では引き続き削除不可＝「破壊的操作は外部から
  到達不可」の設計を維持。共有ボタンは元々無条件動作で変更なし。

### 新規依存

- `androidx.webkit:webkit:1.17.0`（AndroidX公式、`WebViewAssetLoader`用）。
- KaTeX本体はGradle依存ではなく `app/src/main/assets/katex/` にオフライン同梱
  （npm registryの `katex@0.16.11` tarballから`dist/katex.min.js`・`dist/katex.min.css`・
  `dist/contrib/auto-render.min.js`・`dist/fonts/*.woff2`のみ抽出、ttf/woff は
  APKサイズ削減のため同梱せず——CSSの`@font-face`はwoff2を最優先で参照するため
  モダンWebViewでは未使用）。

### 新規テスト

- `MathExtractorTest`（6件）、`LatexSourceParserTest`（8件）、`KatexHtmlTest`（5件、
  `</script>`注入対策の検証含む）、`MarkdownParserTest`に数式関連4件追加。
  全68件通過。

### 残課題（v1.4.0以降の候補）

- インライン数式が多いドキュメントはWebView数に比例して描画が遅くなる（既知の制約）。
- `.tex`のマクロ・パッケージ・非数式コマンドは反映されない（意図的な簡易実装）。

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
- **[v1.3.0で修正]** 上記の`LIMIT … OFFSET …`を`sortOrder`文字列へ直接連結する方式は、
  Android 14（API 34）のMediaProviderでは`IllegalArgumentException: Invalid token LIMIT`で
  拒否されることが実機テストで判明（v1.1.0〜v1.2.0は未検出のまま公開されていた）。
  API 26以上は`ContentResolver`のクエリ引数`Bundle`（`QUERY_ARG_SQL_SORT_ORDER`/
  `QUERY_ARG_SQL_LIMIT`/`QUERY_ARG_OFFSET`）経由に変更し、API 24/25は無ページングの
  単一クエリへフォールバックする設計に修正済み。詳細は本ファイル末尾の
  「v1.3.0 実機デバッグ」参照。

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
3. **汎用ドキュメントビューアー**（v1.2.0〜）— CSV/JSON/TXT/バイナリ/MD/PDF/DOCX/PPTX/
   DOC/PPT をFiles/Documentsから開き、文字化け・レイアウト崩れ・クラッシュがないこと（巨大/破損
   ファイルを含む）。DOC/PPTは外部アプリへの委譲になることを確認。「既定のアプリ」設定から
   システム設定画面に遷移できること。外部アプリの共有シートからMedia Masterで対応形式を
   開けること。
4. **新規: LaTeX数式表示・`.tex`ビューアー・削除ボタン**（v1.3.0〜）— Markdownの `$x^2$`・
   `$$...$$` が正しく組版表示されること（太字/リンク内の数式含む）。`.tex` ファイルを開き、
   数式部分がKaTeX表示、それ以外が原文表示になること。CSV/JSON/PDF等の文書ファイルを
   Files/Documentsから開いた際に削除ボタンが表示され実際に削除できること。外部アプリの
   `ACTION_VIEW` で開いたファイル（Media Masterが管理しないURI）では削除ボタンが
   表示されないこと（意図通り）。WebViewでの数式描画がオフライン（機内モード）でも
   動作すること。
5. 回転・分割・DeX・RTL(ar)・ダーク・TalkBack を確認。
6. `apksigner verify --verbose --print-certs` と SHA-256 記録。

## 主要ファイル

| パス | 役割 |
| --- | --- |
| `app/src/main/java/com/example/ui/DesktopNavigation.kt` | DeX / 大画面のサイドバー、タブ、ホーム |
| `app/src/main/java/com/example/ui/MainNavigation.kt` | 通常UI・デスクトップUIの振り分け、ナビゲーション定義 |
| `app/src/main/java/com/example/FilesScreen.kt` | ファイル一覧、区切り線、フォルダのDnDとコンテキスト操作 |
| `app/src/main/java/com/example/ui/viewer/DocumentViewerScreen.kt` | 汎用ドキュメントビューアー本体（v1.2.0〜） |
| `app/src/main/java/com/example/viewer/` | 文字コード判定・CSV/JSON/Markdown/HEX/PDFの各パーサ・レンダラ（v1.2.0〜） |
| `app/src/main/java/com/example/office/` | `.docx`/`.pptx` 自前zip+XMLパーサ（v1.2.0〜） |
| `app/src/main/java/com/example/ui/viewer/LatexView.kt` | KaTeX WebViewレンダラ（v1.3.0〜） |
| `app/src/main/java/com/example/viewer/MathExtractor.kt`・`LatexSourceParser.kt` | Markdown/`.tex`の数式抽出（v1.3.0〜） |
| `app/src/main/assets/katex/` | オフライン同梱KaTeX本体・フォント（MIT、v1.3.0〜） |
| `app/src/main/java/com/example/ui/components/ZoomableBox.kt` | 動画/PDF/Office埋め込み画像向け共有ピンチズームコンポーネント（v1.4.0〜） |
| `app/src/main/java/com/example/playback/EqualizerController.kt` | システムイコライザー制御（v1.4.0〜） |
| `app/src/main/java/com/example/ui/NowPlayingScreen.kt`・`EqualizerScreen.kt` | 音楽プレイヤーのフル画面UI（v1.4.0〜） |
| `app/src/main/java/com/example/ui/components/BreadcrumbBar.kt`・`FolderPickerDialog.kt` | 管理画面のパンくず・移動/コピー先フォルダピッカー（新規フォルダ作成はv1.5.0〜）（v1.4.0〜） |
| `app/src/main/java/com/example/ui/components/MediaThumbnail.kt` | 画像/動画/音声/フォルダの共有サムネイル・アイコン表示（v1.5.0〜） |
| `app/src/main/java/com/example/ui/LibraryScreen.kt` | ライブラリ（日付グルーピング・ピンチ密度切替・複数選択アクション、v1.5.0で全面改修） |
| `app/src/main/java/com/example/ViewerScreen.kt` | 画像/動画/音声ビューアー本体。`HorizontalPager`によるスワイプナビゲーション、動画10秒シークボタン（v1.6.0〜） |
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
- `commonmark`（Markdownパーサ、v1.2.0で追加）・`androidx.webkit:webkit`（`WebViewAssetLoader`、v1.3.0で追加）以外に汎用ビューアー機能の新規依存はありません。**Apache POI（旧形式.doc/.ppt向け）は検証の上、意図的に不採用**（`java.lang.invoke.MethodHandle`がminSdk26未満でdex化できないため）。将来再検討する場合は、minSdk引き上げの可否をまず確認してください。
- KaTeX（`app/src/main/assets/katex/`、v1.3.0で追加）はGradle依存ではなくアセット同梱。バージョン更新時は `npm registry` から `katex@<version>` のtarballを取得し `dist/katex.min.js`・`dist/katex.min.css`・`dist/contrib/auto-render.min.js`・`dist/fonts/*.woff2`（ttf/woffは同梱不要）を差し替える。ライセンスファイル（`LICENSE`、MIT）も一緒に更新すること。
