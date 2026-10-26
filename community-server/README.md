# v0.7.5 自動収集追加

[今回の更新手順・自動送信の管理](AUTO_SHARE_SETUP.md) を先に参照してください。初期導入の手順は以下のとおりです。

# みんなの価格データベース

Cloudflare Workers＋D1で動かす共有APIです。**このソースをGitHubに置くだけでは共有APIは公開されません。** 1回デプロイし、全員のAndroidに同じHTTPS URLを設定してください。

## データの流れ

1. 投稿者がアプリでJAN・商品名/容量/味・1個の税込価格・店舗・地域・確認日を入力し、店頭確認にチェックして投稿。
2. APIに審査待ちで保存。管理者が承認した報告だけ公開。
3. 別の人が同じJANをスキャンすると、店舗・確認日つきの価格一覧を表示。利用者が選んで買い物へ追加。

商品にはJANを主キーとした名前を持たせ、価格は報告履歴として別テーブルに保存します。価格は店舗・地域・日時で異なるため、一つの全国共通価格には上書きしません。公開検索は直近90日の承認済み報告を最大20件表示します。商品名の正規名は現在承認されている報告の中から最後に承認した名前を使い、各報告の商品名も保持します。非公開化した報告の名前が正規名として残り続けないよう更新します。

## 1. Cloudflareへ配置

Node.js 22以降・npm、CloudflareのアカウントとWorkers/D1を用意します。以下はこの `community-server` フォルダ内で実行します。

```sh
npm ci
npx wrangler login
npx wrangler d1 create kaimono-community
```

返ってきた `database_id` を `wrangler.jsonc` の `REPLACE_WITH_CREATED_DATABASE_ID` に設定します。binding名は `DB` のままです。

```sh
npx wrangler d1 migrations apply kaimono-community --remote
npx wrangler secret put ADMIN_TOKEN
npx wrangler deploy
```

`ADMIN_TOKEN` には自分で生成した32文字以上のランダムな文字列を設定します。生成例：`python -c "import secrets; print(secrets.token_hex(32))"`。表示されたキーはパスワード管理ソフト等に保存し、Wranglerのプロンプトで入力してください。ソース・`.env`・APKに埋め込みません。

デプロイ後のURL（例：`https://kaimono-community.YOUR_SUBDOMAIN.workers.dev`）の `/health` を開き、`api_version: 1` と `status: ok` が返ることを確認します。料金や利用上限はCloudflare側の設定・ダッシュボードで確認してください。ドメインの購入は必須ではありません。

## 2. Androidに設定

設定 → みんなの価格データベース → 共有API URLへ、上記URLのホスト部分だけを入力 → 接続確認 → 保存。

閲覧は投稿用キーなしで利用できます。既存の手入力保存価格・セブンとのひも付けがあるJANでは、それらを優先します。未登録JANで共有APIを検索し、共有報告がなければ従来のYahoo!・セブン検索へ進みます。

## 3. 投稿者を招待

管理操作はPython 3の `admin.py` で行います。管理用キーと投稿用キーは別物です。

PowerShellで管理用の環境変数を設定します。

```powershell
$env:COMMUNITY_API_URL = "https://kaimono-community.YOUR_SUBDOMAIN.workers.dev"
$secret = Read-Host "管理用キー" -AsSecureString
$env:COMMUNITY_ADMIN_TOKEN = [System.Net.NetworkCredential]::new("", $secret).Password
python admin.py invite "友人A"
```

出力の `token` をその参加者だけに渡し、Androidの「投稿用キー」へ設定してもらいます。1人に1キーを発行すると、あとからその人だけ投稿を停止できます。管理用キーは参加者へ渡しません。キー発行の出力を公開ログへ貼らないでください。

商品行の「みんなに店頭価格を投稿」から投稿できます。1個の税込価格を確認してから送信し、通販セットや自動取得しただけの価格を投稿しないでください。写真・PayPay残高・Client ID・他の買い物情報・GPSは送信しません。投稿用キーにひも付く内部IDは保存しますが、公開APIには出しません。

## 4. 承認・訂正

```powershell
python admin.py pending
python admin.py approve REPORT_ID
python admin.py reject REPORT_ID
python admin.py contributors
python admin.py revoke CONTRIBUTOR_ID
```

価格・容量・店舗・日付を確認し、承認する報告を決めてください。`reject` は公開済み報告を非公開に戻す用途にも使えます。キー停止は投稿を止める操作であり、過去に承認した報告を自動で撤去しません。既に利用者の買い物一覧に入った価格も後から自動で書き換えません。

## 再送・上限

投稿前に端末へ未送信データを保存し、UUIDをつけて送ります。通信が切れたら設定の「未送信の投稿を確認・再送」から再送します。同じID・内容の再送はDBで重複させません。共有先URLが変わった状態では、別のサーバーへうっかり投稿用キーを送らないよう再送を止めます。

投稿は各キーで過去24時間に30件まで。未送信データは端末内に最大30件。アプリのデータ削除・アンインストールでは未送信データも消えます。APIの参照にはアカウント登録はありません。現時点では招待制の収集機能で、無制限の一般公開サインアップは実装していません。

## 検証・ローカル開発

```sh
npm test
npx wrangler d1 migrations apply kaimono-community --local
npx wrangler dev
```

`npm test` にはNode.jsとPython3が必要です。WorkerのHTTPハンドラーを動かし、D1互換のSQLiteアダプターでDBと投稿・承認・検索を通します。本番D1そのものを使った試験ではありません。`python test/local_e2e.py` では実際のローカルWorkers＋D1を起動し、2人の投稿・承認・別ユーザーからの検索を通せます。管理機能をローカルで試す場合だけ `.dev.vars` に管理用キーを置きます（Git対象外）。AndroidはHTTPSのみのため、ローカルHTTP URLでは接続できません。

## API

| メソッド | パス | 内容 |
|---|---|---|
| GET | /health | DB接続とAPI版 |
| GET | /v1/products/{JAN} | 承認済みの最近の報告 |
| POST | /v1/reports | 投稿用Bearerキーで審査待ち投稿 |
| POST | /v1/admin/contributors | 管理者が投稿キーを作成 |
| GET | /v1/admin/reports | 審査待ち最大100件 |
| POST | /v1/admin/reports/{ID}/approve | 承認 |
| POST | /v1/admin/reports/{ID}/reject | 非公開化 |
| GET | /v1/admin/contributors | キーの内部IDと状態（秘密値を返さない） |
| POST | /v1/admin/contributors/{ID}/revoke | 投稿キーを停止 |

投稿JSONの例（IDは投稿ごとにUUID v4で生成）：

```json
{"request_id":"11111111-1111-4111-8111-111111111111","jan":"4901234567894","name":"商品名・容量","unit_cents":18252,"store_name":"店舗名","area":"長崎県","observed_on":"2026-10-23","shelf_confirmed":true}
```

`unit_cents` は円の100分の1を表す整数です。18252は182.52円で、通貨はJPY、税込・1個分のみです。上記は形式例で、実商品の価格データとして初期登録していません。

日付・JANチェックサム・価格整数・本文上限をAPI側でも確認し、SQLはバインド変数を利用しています。HTTPS・認証・投稿上限・管理者承認を備えますが、価格の正しさを自動で保証するものではありません。大規模公開時はWAFなどの追加のレート制限、データ管理方針・投稿利用条件、バックアップ・運営体制を準備してください。DBの削除やデータ消去は管理者のCloudflare操作で行い、このAPIには全件削除エンドポイントを作っていません。

公式資料：https://developers.cloudflare.com/d1/get-started/ 、https://developers.cloudflare.com/d1/worker-api/d1-database/


管理CLIのHTTP要求には `KaimonoScan-Admin/0.7.0` のUser-Agentを設定しています。Python標準User-AgentでCloudflareから403/1010が返った実例に対応しました。401は管理用キー、503はWorkerの設定、403は経路・アクセス制御を確認するための案内を表示します。
