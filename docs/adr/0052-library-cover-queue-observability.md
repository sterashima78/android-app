# ADR-0052: 表紙取得キューを既存DBと WorkManager から可視化する

- Status: Superseded
- Date: 2026-08-14
- Amended: 2026-08-14
- Superseded by: ADR-0057, ADR-0058

## Context

この ADR は Kindle / Audible のバックグラウンド表紙補完を WorkManager と DB から可視化し、取得待ち、失敗、再試行状態を表示する方針を定義していた。

ADR-0057 と ADR-0058 により、Kindle / Audible とも Web Library から生成する JSON の表紙 URL を正規データとし、アプリ内のバックグラウンド表紙補完を廃止した。

## Superseded decision

表紙取得状況画面、取得キュー、再試行・キャンセル操作、表紙補完用 WorkManager unique work は削除する。

Web JSON に表紙 URL がない書籍は追加検索せず「表紙なし」と表示する。

現行判断は ADR-0057 と ADR-0058 を参照する。
