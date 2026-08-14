# ADR-0053: Kindle 表紙補完に Google Books と構造化診断を追加する

- Status: Superseded
- Date: 2026-08-14
- Superseded by: ADR-0057

## Context

この ADR は Kindle の表紙補完で Google Books を追加し、取得失敗の原因を構造化診断として保存する設計を定義していた。

ADR-0057 で Kindle Web Library JSON の `coverUrl` を唯一の表紙データとし、表紙補完そのものを廃止したため、本判断は不要になった。

## Superseded decision

表紙用 Google Books フォールバック、構造化診断トレース、取得失敗履歴は削除する。Web JSON に表紙がない場合は追加検索しない。

現行判断は ADR-0057 を参照する。
