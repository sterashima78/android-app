# ADR-0041: 表紙取得失敗を再現可能な情報で観測する

- Status: Superseded
- Date: 2026-08-14
- Superseded by: ADR-0057, ADR-0058

## Context

この ADR は Kindle / Audible のバックグラウンド表紙補完が失敗した際に、原因調査のための診断情報を保存する方針を定義していた。

ADR-0057 と ADR-0058 により、Kindle / Audible とも Web Library から生成する JSON の表紙 URL を正規データとし、アプリ内の表紙補完自体を廃止した。

## Superseded decision

表紙補完に伴う診断トレース、再試行状態、取得キュー UI は削除する。Web JSON に表紙がない場合は追加取得せず「表紙なし」とする。

現行判断は ADR-0057 と ADR-0058 を参照する。
