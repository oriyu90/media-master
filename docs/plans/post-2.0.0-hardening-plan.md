# v2.0.0 事後 品質精査・修正計画書（post-2.0.0 hardening）

- 作成日: 2026-10-31
- 対象: `oriyu90/media-master`
- 基準版: v2.0.0 (`versionCode 24`, commit `dac6cbb`, tag `v2.0.0`)
- 位置づけ: リリース済み v2.0.0 の外部メディア表示機能と、その周辺（ビューア / ナビゲーション / 権限ゲート / デスクトップシェル）に対する横断品質監査と修正
- 版: 本修正では `versionName` を据え置く（未リリースの番号を公開物と一致させないため。次回リリース時に 2.0.1 へ更新する）

## 1. 精査の観点と方法

| 観点 | 具体的に確認したこと |
|---|---|
| メモリ安全性 | 外部 URI 解決のライフサイクル、Cursor/Stream の close、ExoPlayer と PlayerView、OCR クライアント、Coil キャッシュ |
| クラッシュ安全性 | Pager インデックス、ナビゲーション例外、共有 Intent、不正 URI、プロバイダ例外、プロセス再生成 |
| 動作の安定性 | 再スキャン中の状態遷移、ページャ位置の保持、没入モードの復帰、破棄中のコルーチン |
| GUI の完全性 | ツールバー（幅 360/411dp 実機）、外部専用項目の操作可否、ローディング/エラー導線、デスクトップシェル、ミニプレーヤー |
| 設定内容の整合性 | ライブラリの並び順・除外フォルダ・表示モードとの一致、デスクトップ上書き |
| 互換の対応 | ACTION_VIEW / EDIT / SEND、MIME 不明、`file://`、Android 11+ 全ファイルアクセス、API 24〜36、RTL |
| 表示の正確性 | 情報シートのサイズ・日時、タイトル、動画/画像の誤判定、没入時のシステムバー、OCR オーバーレイ |

方法: コード読解（`ViewerScreen.kt` / `external/*` / `DeepLinks.kt` / `MainNavigation.kt` / `MainActivity.kt` / `MediaRepository.kt` / `DesktopNavigation.kt` / `LibraryScreen.kt`）、
ピュアロジックの単体テスト、エミュレータ（Android 16.1、release APK）での実機確認、
独立監査 2 名（読取専用・別コンテキスト。クラッシュ/メモリ担当と GUI/設定/互換担当）によるクロスチェック。

## 2. 指摘一覧

重大度: CRITICAL（即時修正・リリース不可）/ HIGH（必須）/ MEDIUM（要修正）/ LOW（改善）/ INFO（記録のみ）

| ID | 観点 | 重大度 | 指摘 | 根拠 | 修正 |
|---|---|---|---|---|---|
| H-01 | クラッシュ安全性 | HIGH | 外部から `file://` で渡された写真を「共有」すると `FileUriExposedException` でクラッシュ（targetSdk 24+、未捕捉） | `ViewerScreen.kt` 共有処理、`DeepLinks.isLocalMediaUri` が `file` を許可 | FileProvider 経由の URI に変換し、失敗時はトースト。`runCatching` で包む |
| H-02 | クラッシュ安全性 | HIGH | ページャの page ラムダが `mediaList[page]` を無検査。再スキャン/削除でリストが縮むと `IndexOutOfBoundsException` の余地（416行の `safePage` だけが防御済みで非対称） | `ViewerScreen.kt` pager content | `getOrNull(page) ?: return@HorizontalPager` |
| M-01 | クラッシュ安全性 | MEDIUM | deep link の `navigate` 失敗を `runCatching` で握りつぶしたまま「処理済み」にし、ルートを破棄（無言で Home のまま） | `MainNavigation.kt` の deep link effect | 成功時のみ `onDeepLinkHandled()`。失敗時は保留して次回再試行 |
| M-02 | 動作の安定性 | MEDIUM | プロセス死亡復帰時、元の `ACTION_VIEW` intent を再適用し、閉じた後でも外部画像に戻される | `MainActivity.onCreate` が `savedInstanceState` を見ない | 新規起動時のみ deep link を解決（NavController は自前で復元） |
| M-03 | 互換の対応 | MEDIUM | 「全ファイルアクセス」許可後、`hasAllFilesAccess` を再評価する契機がなく、外部 VIEW が開かないことがある | `MainNavigation.kt` の一発読み（同型の修正が `DesktopNavigation.kt` に既存） | `ON_RESUME` tick で再評価 |
| M-04 | GUI の完全性 | MEDIUM | ツールバーのタイトルが潰れる。画像は 7 アクションで 411dp では「…」、360dp では消える。320dp では溢れる | 実機スクリーンショット（411dp / 360dp）、`ViewerScreen.kt` の actions | 削除・名前変更・情報を `⋮` オーバーフローへ移動（`more_options` を 5 ロケール追加） |
| M-05 | GUI の完全性 | MEDIUM | 画像/動画の読み込み失敗時、没入のままツールバーが見えず「黒画面＋再試行」だけになり閉じ方が分からない | `startFullscreen = true` 固定、エラー時に chrome を戻す処理なし | エラー時に chrome とシステムバーを表示 |
| M-06 | 表示の正確性 | MEDIUM | 情報シートが不明なサイズ・日時を「0 B」「1970/1/1」と表示（SAF/チャット添付で発生） | `FileInfoSheet` が 0 をそのまま整形 | `<= 0` は `R.string.unknown` |
| M-07 | メモリ安全性 | MEDIUM | `PlayerView` を player から切り離さず破棄。動画⇄画像の往復で view（Activity Context）がビューア終了まで蓄積 | `AndroidView(factory/update)` に `onRelease` なし | `onRelease = { it.player = null }` |
| M-08 | GUI の完全性 | MEDIUM | 音声再生中はミニプレーヤーがビューア下に常駐し、外部写真が下 84dp 欠ける（全画面でない） | `PhoneScaffold` の bottomBar が無条件 | メディア提示ルートではミニプレーヤーを出さない |
| M-09 | GUI の完全性 | MEDIUM | 2 枚目の外部画像を開くとシステムバーが再表示され、没入が解除されたままになる（旧 destination の `onDispose` が show、新 destination の hide が先） | 実機（2 回目の ACTION_VIEW）、`ViewerScreen.kt` の bar 制御 | hide を `LaunchedEffect` へ移し、**次フレームで再 hide**。ウィンドウのフォーカス取得時にも再 hide。→ 通常の表示・エディタ往復・アプリ復帰では没入を維持。**別アプリからの 2 枚目（タスク遷移を伴う）では再表示が残存**（I-07） |
| L-01 | 動作の安定性 | LOW | 下スワイプのアニメーション中に画面が破棄されると `CancellationException` を飲み、`closeViewer()` が走って 1 画面余分に閉じる | `settleDismiss` の `runCatching` | `CancellationException` は再送出 |
| L-02 | 表示の正確性 | LOW | OCR 結果がページ切替後に届くと、前の画像のテキストを新しいページに重ねる | OCR 状態がビューア全体で 1 つ | 世代カウンタで不一致なら破棄 |
| L-03 | 表示 / RTL | LOW | 動画の手動ページスワイプが RTL で逆方向 | `dragTotal.x < 0` 固定 | `dragPageDelta()` に抽出し `LayoutDirection` で反転 |
| L-04 | 表示の正確性 | LOW | 外部画像はデコード完了まで完全な黒画面（コールドスタートで 1 秒前後） | `AsyncImage` のみ | 先頭フレームまでスピナー |
| L-05 | 表示の正確性 | LOW | 「documents-provider の `image:<id>` も ID 照合する」というコメントが実装（MediaStore 限定）と不一致 | `ExternalMedia.kt` KDoc | コメントを実装に合わせる |
| I-01 | 互換 | INFO | `ACTION_SEND`（共有）は未登録。共有シートに本アプリは出ない | manifest | プロダクト判断のため保留（VIEW は対応済み） |
| I-02 | 設定の整合性 | INFO | ライブラリのコンパクト表示（降順）中は、外部ページャの昇順と並びが逆 | `compactSortOption` は LibraryScreen ローカル状態 | 既定の BY_DATE と一致。仕様として記録 |
| I-03 | 性能 | INFO | 外部を開くたびに `libraryMediaOrder`（filter+sort）をコンポジション中に実行 | `ViewerScreen.kt` | 実測で問題なし。将来 `produceState` 化の余地 |
| I-04 | 動作の安定性 | INFO | 外部表示中に再スキャンが走ると `Loading` で一瞬 1 ページに縮小し位置が戻る | `allFiles` が Loading で空になる | 稀。記録のみ |
| I-05 | 動作の安定性 | INFO | ドラッグ中に pointerInput のキーが変わると dismiss が固着し得る | 動画ページの gesture | 再現せず。記録のみ |
| I-06 | 互換 | INFO | 全ファイル権限が未許可だと、外部 VIEW は権限画面を経由する（許可後に開く） | 設計 | M-03 で「戻っても開かない」変種は解消 |
| I-07 | GUI の完全性 | INFO | 別アプリから 2 枚目の画像を渡した際、タスク遷移中にシステムバーが再表示されることがある（アプリ内の遷移・復帰では再現しない） | 実機（release、`am start` 連続 2 回）。hide は即時＋次フレーム＋フォーカス取得で再主張しているが、遷移完了後に OS が表示する経路が残る | 画面操作は可能で、タップでクロームを出せば通常状態に戻る。恒久対策はバー所有をシェルへ移す設計変更（別計画） |

## 3. 問題なしと確認した項目

- 外部画像/動画は `ACTION_VIEW` で全画面表示され、ライブラリ一致時のみライブラリ順（画像+動画、除外フォルダ非表示、`dateModified` 昇順）でページングする。順序は `LibraryScreen.kt` の既定グリッドと一致。
- 一致判定は content URI → 解決パス → MediaStore `_ID`（**同一ボリュームのみ**）。第三者プロバイダの数値パスを誤一致させない。
- 外部専用項目では削除・名前変更を出さない。一致後は再評価され、通常のライブラリ項目として操作できる。
- 1 ページ時に水平スワイプは移動せず、下スワイプ / 戻る / ESC / 閉じるでライブラリへ戻る。閉じる経路は 4 種すべて `closeViewer()` を通る。
- `ExoPlayer` は 1 本を共有し `onDispose` で release。`ON_STOP` で pause。外部→ライブラリ遷移でも停止する。
- 外部 URI の照会はすべて `runCatching` + `use` で保護。Cursor はリークしない。
- デスクトップ（DeX/ウィンドウ）では `externalViewer/` がメディア提示ルートとして認識され、シェルが畳まれ、離脱時に復帰する。
- 5 ロケール（en/ja/zh/ar/nl）の文字列パリティを維持（`more_options` を追加）。

## 4. 修正（Work Packages）

| WP | 指摘 | 変更 | 受け入れ条件 | 検証 |
|---|---|---|---|---|
| WP-1 | H-01 | `shareableUri()` を追加し、共有を FileProvider 変換 + `runCatching` + 失敗トーストに | `file://` の外部画像で共有してもクラッシュしない | 実機（file:// 共有） |
| WP-2 | H-02 | pager の page 参照を `getOrNull` に | リスト縮小で落ちない | コードレビュー + 全テスト |
| WP-3 | M-01 | deep link を navigate 成功時のみ消費 | 失敗時にルートを捨てない | コードレビュー |
| WP-4 | M-02 | `MainActivity.onCreate` を `savedInstanceState == null` でガード | 復帰時に外部画像へ戻らない | コードレビュー |
| WP-5 | M-03 | `ON_RESUME` tick で権限を再評価 | 許可→戻る→外部 VIEW が開く | 実機 |
| WP-6 | M-04 | 削除/名前変更/情報を `⋮` へ。`more_options` 追加 | 360dp でタイトルが読める | 実機 360/411dp |
| WP-7 | M-05 | 画像/動画エラーで chrome + システムバーを表示 | 失敗時に閉じられる | 実機（不正 URI） |
| WP-8 | M-06 | 情報シートの不明値 | 「0 B」「1970」を出さない | 実機 |
| WP-9 | M-07 | `PlayerView.onRelease` で player を外す | 往復で view が残らない | コードレビュー |
| WP-10 | M-08 | メディア提示ルートでミニプレーヤー非表示 | 全画面が欠けない | コードレビュー |
| WP-11 | M-09 | バー hide を `LaunchedEffect` + `withFrameNanos` 再主張 + `OnWindowFocusChangeListener` へ | アプリ内の遷移・復帰でバーが出ない（別タスクからの 2 枚目は残存） | 実機（2 回目 ACTION_VIEW） |
| WP-12 | L-01 | `CancellationException` 再送出 | 破棄中に余分な pop をしない | 全テスト |
| WP-13 | L-02 | OCR 世代カウンタ | 前画像のテキストを出さない | コードレビュー |
| WP-14 | L-03 | `dragPageDelta()` + 単体テスト | RTL で順方向 | 単体テスト |
| WP-15 | L-04 | 読込中スピナー | 黒画面だけの時間をなくす | 実機 |
| WP-16 | L-05 | KDoc 修正 | 実装と一致 | レビュー |

## 5. リスクと回避

- ツールバー変更は全ビューア（ライブラリ/外部/動画）に影響する。`R.string.delete` / `rename` / `file_info` の文言は維持し、オーバーフロー項目のテキストとして到達可能。ビューアのスクリーンショット参照は存在しないため再記録は不要。
- 権限 tick は再コンポーズ回数を増やすだけで、判定の意味は変えない。
- Pager の防御・エラー時の chrome 表示は、正常時の表示内容を変えない。
- ダイアログをツールバーの外へ出しても、表示位置・内容は同一（モーダル）。

## 6. 検証

1. `./gradlew :app:testDebugUnitTest`（220 件、追加 5 件を含む）
2. `./gradlew :app:lintDebug`（0 errors）
3. `./gradlew :app:assembleRelease` → エミュレータへインストール
4. 実機: 外部 VIEW（一致/不一致/動画/`file://`）、共有、360dp/411dp のツールバー、情報シート、エラー導線、2 回目の ACTION_VIEW、権限の付与→復帰、回転、下スワイプ
5. `logcat -b crash` に FATAL がないこと

## 7. 完了条件

- 上記 WP の受け入れ条件を実測で満たす。
- テスト・lint が緑。既存の外部表示の挙動（一致時のページング、不一致時の 1 ページ、閉じる導線）に回帰がない。
- 結果を本計画書および `IMPLEMENTATION_AND_MAINTENANCE.md` に記録する。
