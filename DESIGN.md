# Material 3 / Pixel風UI

## 画面

- メイン：32spの見出し、48dpの設定アイコン、合計カード、入力操作、商品リスト、画面下部の64dpスキャンボタン。
- 合計カード：primaryContainer色、28dp角丸、最大56spで幅に合わせて縮小する金額表示。
- 商品カード：surfaceVariant色、24dp角丸。商品名、単価、数量、商品ごとの合計を表示。詳細は48dpの操作メニューへ。
- スキャン：上側の金額カード、下側の32dp角丸カメラ。読取状態・再読取・ライト・一覧への操作はカメラ下に配置。
- 商品候補：商品名・掲載価格・地域の折り返し表示と区切り線を維持。

## テーマと部品

Material Components 1.12.0 / Theme.Material3.DayNight.NoActionBarを使用。MaterialButton、MaterialAlertDialogBuilder、MaterialCheckBox、MaterialSwitchを使用し、全体をシステムのライト/ダーク設定に合わせる。

DynamicColorsをActivity作成時に適用。Android 12以降の対応端末はシステムの壁紙由来のパレットを使用。未対応端末はブルー系の固定パレット。画面本体もテーマ属性colorSurface、colorPrimaryContainer、colorOnPrimaryContainerなどから色を取得する。

システムの標準sans-serifフォントを使用。Google Sansは同梱しない。装飾の炎・発光・セリフ体は使用しない。

## 操作性

アイコンには内容説明、タップ領域は48dp以上。長い商品名は折り返し、金額は幅に合わせて縮小。スクロール領域の外にスキャンボタンを固定。システムバーのInsetsを保持。

検索・計算・保存・カメラの連続読取・バイブ・共有送信の処理は既存のものを維持。実機で確認すべき事項はVALIDATION.mdを参照。
