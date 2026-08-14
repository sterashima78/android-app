# ADR-0037: Audible 表紙を商品ページと Catalog API から補完する

- Status: Superseded
- Date: 2026-08-13
- Amended: 2026-08-14
- Superseded by: ADR-0058

## Context

この ADR は、Amazon Request Your Data の Audible インポートでは表紙 URL が欠けることを前提に、商品ページと Catalog API を使ったバックグラウンド表紙補完を定義していた。

その後、ADR-0058 で Audible Web Library と Catalog API から生成する JSON を正規入力とし、インポート JSON 自体に `coverUrl` を含める方式へ変更した。このため、インポート後に同じ Catalog API を再度問い合わせる専用バックグラウンド処理は不要になった。

## Superseded decision

旧実装では次を行っていた。

- Audible 商品ページの OGP から表紙を探索
- 見つからない場合は Catalog API の ASIN 指定・書名検索へフォールバック
- WorkManager で表紙なし書籍をバックグラウンド処理
- 結果を `library_item_external_metadata` に保存
- `NOT_FOUND` / `AMBIGUOUS` を30日後に再確認

これらの Audible 専用処理は ADR-0058 により廃止する。

Kindle の表紙補完については ADR-0036 および ADR-0057 の判断を維持する。

## Current decision

Audible は Web Library エクスポート JSON の `coverUrl` を正規の表紙情報として保存する。表紙が欠損していても、アプリ側では Audible 商品ページや Catalog API を使った追加補完を行わない。

表紙取得キュー、Worker、scheduler、専用クライアントは Kindle のみを対象とする。

詳細は ADR-0058 を参照する。
