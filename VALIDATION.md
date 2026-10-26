# v0.11.0 会計バーコードの検証

137件のテスト成功。EAN-13 / EAN-8の生成バーコードをZXingで再読取し、元のJANとの一致を確認。同一JANの数量合算、次の商品への切替、バーコードなしの点数表示、元の買い物一覧を変更しない動作をRobolectricで確認。assembleDebug / testDebugUnitTest / lintDebug が成功。店舗の実機レジでの読取・会計、画面輝度の実機動作は未検証。

versionCode 21 / versionName 0.11.0。既存確認版と同じ署名。APK SHA-256: `c19be0899c8da613dbb125a640bc7b836f3c384095c0eb8620fce3b5f75b2b42`。

以下は前版の検証記録。

# v0.10.0 検証結果 — 2026-10-26

- Android `assembleDebug` / `testDebugUnitTest` / `lintDebug`: BUILD SUCCESSFUL。
- JUnit・Robolectric計134件、失敗0・エラー0。既存115件に加えて、公式価格解析・URL許可リスト・店舗別キー・カタログ検索・旧データ移行・店舗切り替え・JAN登録の確認・店舗別キャッシュ利用を検証。
- Lint: エラー0、警告25件（SetTextI18n、UnusedResources、IconLauncherShape、LockedOrientationActivity、DiscouragedApi）。
- 公式サイトから当日取得した最小HTMLブロックで、ローソンのおにぎり322円、ファミマのカレー465円の通常税込価格と商品名を確認。キャンペーン価格・非表示要素・複数価格・税抜のみの場合をテスト。
- 同梱カタログ: ローソン1,105件、ファミマ1,678件。カテゴリ取得エラー0。重複URL・価格・推測JANを含まないことを確認。全PB商品の網羅を保証する数値ではない。
- APK内の `store-products.json` 2,783件と日本語OCRモデルを確認。
- `apksigner verify`: 成功。署名証明書SHA-256は既存確認版と同じ `68d703739b59b035411d0f311ad4d4e44606ca8ee85d624de094ba3821ea9416`。
- package: `dev.enadorry.kaimonoscan.community.preview`、versionCode 20、versionName 0.10.0、minSdk 24、targetSdk 35。
- APK SHA-256: `146066a6e47c68a8cd726b2043b5868e95847517a505fd8417c841730fa3f7c9`。
- 既存Cloudflare Worker・Web版・`wrangler.jsonc` はv0.9.0配布物とバイト一致。サーバーへのテスト投稿・デプロイ・D1変更は行っていない。

実機でのカメラ・振動・Android通信・上書きインストールは未検証。公式サイトの構造変更、店舗別価格・地域限定品・終売商品は実際の店頭で確認する必要がある。未登録JANと公式商品は初回にユーザーが商品・容量を確認してひも付ける。ローソン・ファミマの共有価格は既存APIの店舗未確認形式であり、共有データのみで店舗を確定しない。

## ローカルでの再現

JDK 17、Gradle 8.9、Android SDK 35 / Build Tools 34.0.0を用意し、プロジェクト直下で `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` を実行する。署名キーを配布物に含めていないため、ローカルビルドは利用者自身の署名となる。確認版と同じアプリを配布する場合は自身のアプリID・署名方針を設定する。
