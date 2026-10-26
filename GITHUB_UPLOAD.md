# GitHubへアップロード（共有版 v0.8.0）

`community-server/` に共有API・DB・承認ツールも含まれます。APIのデプロイは [community-server/README.md](community-server/README.md) を参照してください。GitHubへ置くだけでは共有サーバーは動きません。

このZIPは、展開するとすぐAndroid Studioで開けるプロジェクトです。README.md、app/、gradle/、gradlew等がZIPの直下に入っています。ZIPファイルそのものをリポジトリに置くとソース表示にならないため、展開した中身をアップロードしてください。

## Gitを使う場合（Windows / PowerShell）

GitHubで空のリポジトリを作り、展開したフォルダでターミナルを開きます。次のURLは自分のリポジトリURLに置き換えてください。

```powershell
git init
git add .
git commit -m "Add Kaimono Scan Android app"
git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPOSITORY.git
git push -u origin main
```

既にREADMEやコミットのあるリポジトリへ追加する場合は、先にそのリポジトリをcloneし、展開した中身をそこへコピーしてからadd/commit/pushします。

## ブラウザでアップロードする場合

リポジトリのファイル追加画面から展開した中身をアップロードします。`.gitignore`も含めてください。README.mdとapp/がリポジトリの直下にある配置にします。Gitを使う方法なら、ファイル数・サイズのあるソース一式を扱いやすく、gradlewの実行権限も管理できます。

## ビルド

Android Studioでプロジェクトを開き、JDK17・SDK35で同期します。Windowsでは次を実行します。

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Linux/macOSでは必要に応じて `chmod +x gradlew` の後、`./gradlew assembleDebug testDebugUnitTest lintDebug` を実行します。

APKは `app/build/outputs/apk/debug/app-debug.apk` に作成されます。標準ではアプリIDが `dev.enadorry.kaimonoscan.community` の共有版をビルドします。従来のアプリとは別にインストールできます。ソースZIPにはこの端末のSDK設定や署名鍵は含みません。自分の環境でビルドしたデバッグAPKは署名が変わり、この会話で配布したAPKに上書きできないことがあります。

## アイコン

- `artwork/app-icon-1024.png`：選択した3番のアイコン、1024×1024 PNG。
- `app/src/main/res/mipmap-*/ic_launcher.png`：Androidの各画面密度用PNG（48/72/96/144/192px）。
- Manifestから上記PNGを通常・丸型アイコンとして参照します。ホーム画面での外形はランチャーによって変わります。

## 公開する内容

アプリは個人用の試作で、PayPay・セブン‐イレブン・Yahoo!の公式アプリではありません。残高は手動設定です。利用者のClient ID・残高・買い物履歴は端末側の設定に保存し、ソースには含めていません。

依存ライブラリのライセンス文書は同梱しています。プロジェクト全体の公開ライセンスは未指定です。再配布・利用を許可する範囲を決めてから、必要に応じてLICENSEを追加してください。


## v0.7.0から今回の候補リスト修正だけ反映する場合

`app/src/main/java/dev/enadorry/kaimonoscan/MainActivity.java` と `app/build.gradle` を置き換えて再ビルドします。DBの設定済みIDが入った `community-server/wrangler.jsonc` は保持してください。サーバー再デプロイとDB移行は不要です。変更の説明は `SEVEN_PB_RESEARCH.md` にあります。

ソース一式と確認版の更新APKを提供します。インストール済みアプリへの上書きには、以前と同じ署名鍵でビルドしてください。異なる署名のAPKは上書きできません。元のアプリを消すと端末内のひも付け等が失われるため、更新のためにアンインストールする操作は案内していません。


## v0.7.2 確認版

配布APKは `dev.enadorry.kaimonoscan.community.preview`（かいものスキャン 確認版）です。前回の v0.7.1 確認版と同じ署名鍵を使用し、versionCodeを12から13へ更新します。再ビルド時は `KaimonoScan-preview-build-kit.zip` の `preview.gradle` と署名鍵を使用してください。標準のGradleビルドとはアプリIDが異なります。

APKの上書き更新では既存の設定・価格のひも付け・買い物一覧を維持します。署名鍵をZIPや公開リポジトリへ含めないでください。

## v0.7.3 確認版

連続スキャン画面を上段の金額表示と下段のカメラに変更。versionCode 14。v0.7.2確認版と同じアプリID・署名鍵を使用します。

## v0.7.4 確認版

versionCode 15。Cloudflareを標準の共有APIに設定。Yahoo!連携は任意。CommunityConfig.java と CommunityConfigTest.java を含めてアップロードしてください。サーバーの再デプロイとD1移行は不要です。

## v0.7.5 自動送信

versionCode 16。APKとサーバーを両方更新します。新しいAutoShare.java・テスト、community-server/src/index.js、migrations/0002_auto_bindings.sql、admin.py、更新手順を含めてください。既存の本番wrangler.jsoncは維持してください。

## v0.7.6 PB名索引

versionCode 17。PBLookup.java、assets/pb-names.json、MainActivity.java、PBLookupTest.java、app/build.gradleを含めて更新します。community-server/wrangler.jsoncの既存database_idはそのまま維持してください。サーバー更新・DB移行は不要です。配布APKは確認版用の既存署名鍵を使用します。署名鍵はGitHubへアップロードしません。

## v0.8.0 Material 3 UI

versionCode 18。MainActivity、app/build.gradle、res配下の色・テーマ・アイコン、PixelUiTest.javaを更新します。Material Components 1.12.0を使用。署名鍵は以前の確認版キットを使用し、GitHubには含めません。既存のcommunity-server/wrangler.jsoncとdatabase_idは維持してください。サーバー・D1の更新は不要です。
