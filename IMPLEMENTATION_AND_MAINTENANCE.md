# Media Master v0.1.0 実装・保守メモ

最終更新: 2026-08-18

## リリース情報

| 項目 | 内容 |
| --- | --- |
| バージョン | `0.1.0` (`versionCode 1`) |
| アプリケーションID | `com.yukiorita.mediamaster` |
| 最小 SDK / target SDK | 24 / 36 |
| ライセンス | MIT |
| 著作者 | Yuki_Orita |
| release APK | `app/build/outputs/apk/release/app-release.apk`(未生成。初回リリースビルド後に配布予定) |
| GitHub Release | 未作成 |

release APK は RSA 4096 ビット鍵で APK Signature Scheme v2 署名する想定です。公開前には毎回 `apksigner verify --verbose` で署名を確認してください。

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

- Android が desk mode を返す場合、または表示幅が `840dp` 以上の場合に有効化。
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

## 主要ファイル

| パス | 役割 |
| --- | --- |
| `app/src/main/java/com/example/ui/DesktopNavigation.kt` | DeX / 大画面のサイドバー、タブ、ホーム |
| `app/src/main/java/com/example/ui/MainNavigation.kt` | 通常UI・デスクトップUIの振り分け、ナビゲーション定義 |
| `app/src/main/java/com/example/FilesScreen.kt` | ファイル一覧、区切り線、フォルダのDnDとコンテキスト操作 |
| `app/src/main/java/com/example/SettingsRepository.kt` | DataStore設定（ピン留めを含む） |
| `app/src/main/java/com/example/SettingsViewModel.kt` | 設定操作のViewModel |
| `app/src/main/res/values*/strings*.xml` | UI翻訳リソース |
| `app/build.gradle.kts` | アプリID、v0.1.0、署名設定、依存関係 |
| `README.md` | 利用・ビルド・公開の概要 |
| `LICENSE` | MITライセンス |
| `index.html` / `tokens.css` / `assets/site.css` | 紹介サイト。Cloudflare Pages([https://media-master-9o5.pages.dev/](https://media-master-9o5.pages.dev/))へデプロイ済み |
| `robots.txt` / `sitemap.xml` | 紹介サイトのSEO設定 |

## 開発・検証

前提条件は Android SDK 36 と JDK 21 です。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

主な確認ポイント:

1. phone / tablet / 横画面 / 分割画面で表示が切れないこと。
2. DeXまたは幅840dp以上でサイドバーとホームが表示されること。
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

v0.1.0 の APK SHA-256 は、初回リリースビルド完了後にここへ記載する(現時点では未生成)。

```text
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

## 依存関係と保守上の注意

- OCRは ML Kit Text Recognition 16.0.1、Document Scanner は 16.0.0 を利用。16KBページサイズと関連するLint警告を解消済み。
- `QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES` はストア配布時に審査対象となるため、アプリ／APK管理機能の必要性をストア申請で説明してください。
- Lintはエラー0件。依存関係の更新提案、未使用リソース、既存APIの非推奨警告などの非ブロッキング警告は残り得ます。機能変更時は `lintDebug` を再実行してください。
- release APKは現時点でv2署名を使用しています。Play App Signingを利用する場合は、作成済みのアップロード鍵を安全なバックアップ先へ保管してください。
