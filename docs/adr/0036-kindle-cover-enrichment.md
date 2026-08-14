# ADR-0036: Kindle 表紙を Amazon 商品ページと Open Library から補完する

- Status: Superseded
- Date: 2026-08-13
- Updated: 2026-08-14
- Superseded by: ADR-0057

## Context

この ADR は Amazon Request Your Data 由来の Kindle 蔵書に表紙 URL がないことを前提に、Amazon 商品ページや外部書誌サービスから表紙をバックグラウンド補完する方式を定義していた。

ADR-0057 で Kindle Web Library から生成する JSON を正規入力とし、同 JSON の `coverUrl` を表紙の唯一の正規データとする方式へ変更したため、本 ADR の前提はなくなった。

## Superseded decision

旧実装の Amazon 商品ページ、Google Books、Open Library、NDL への問い合わせ、外部メタデータキャッシュ、WorkManager による再試行は廃止する。

Web Library JSON に表紙がない書籍は追加検索せず「表紙なし」と表示する。

現行判断は ADR-0057 を参照する。
