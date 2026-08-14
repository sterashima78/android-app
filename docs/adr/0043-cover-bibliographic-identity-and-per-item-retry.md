# ADR-0043: 表紙補完で書誌同定と画像取得を分離し、書籍単位で再試行する

- Status: Superseded
- Date: 2026-08-14
- Superseded by: ADR-0057

## Context

この ADR は Kindle の複数プロバイダーによる表紙補完で、一時障害と書誌同定失敗を区別し、書籍単位で再試行する設計を定義していた。

ADR-0057 で Kindle Web Library JSON の `coverUrl` を唯一の表紙データとし、アプリ内の表紙補完と再試行を廃止したため、本判断は不要になった。

## Superseded decision

書誌同定、プロバイダーフォールバック、書籍単位の再試行状態は削除する。Web JSON に表紙がない場合は追加検索しない。

現行判断は ADR-0057 を参照する。
