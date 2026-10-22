# GitHubへアップロード

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

APKは `app/build/outputs/apk/debug/app-debug.apk` に作成されます。ソースZIPにはこの端末のSDK設定や署名鍵は含みません。自分の環境でビルドしたデバッグAPKは署名が変わり、この会話で配布したAPKに上書きできないことがあります。

## アイコン

- `artwork/app-icon-1024.png`：選択した3番のアイコン、1024×1024 PNG。
- `app/src/main/res/mipmap-*/ic_launcher.png`：Androidの各画面密度用PNG（48/72/96/144/192px）。
- Manifestから上記PNGを通常・丸型アイコンとして参照します。ホーム画面での外形はランチャーによって変わります。

## 公開する内容

アプリは個人用の試作で、PayPay・セブン‐イレブン・Yahoo!の公式アプリではありません。残高は手動設定です。利用者のClient ID・残高・買い物履歴は端末側の設定に保存し、ソースには含めていません。

依存ライブラリのライセンス文書は同梱しています。プロジェクト全体の公開ライセンスは未指定です。再配布・利用を許可する範囲を決めてから、必要に応じてLICENSEを追加してください。
