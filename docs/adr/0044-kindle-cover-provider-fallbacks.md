# ADR-0044: Kindle 表紙補完で一時障害と日本語書誌解決を分離する

- Status: Superseded
- Date: 2026-08-14
- Superseded by: ADR-0057

## Context

この ADR は Kindle 表紙補完で Amazon、Google Books、Open Library、NDL 等のプロバイダーを使い分ける設計を定義していた。

ADR-0057 で Kindle Web Library JSON の `coverUrl` を唯一の表紙データとし、アプリから外部プロバイダーへ表紙を問い合わせる処理を廃止した。

## Superseded decision

プロバイダーのフォールバック、OAuth を使った表紙検索、書誌識別子による追加取得は行わない。Web JSON に表紙がない場合は追加検索しない。

現行判断は ADR-0057 を参照する。
