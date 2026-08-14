# ADR-0054: Library の runtime と UI state を feature が所有する

- Status: Superseded
- Date: 2026-08-14
- Superseded: 2026-08-14
- Superseded by: ADR-0057, ADR-0058

## Context

ADR-0003 は `:app` を composition root と navigation に限定し、feature 固有 UI state は feature 側で所有すると定めている。

本 ADR 採用時の Library には、Kindle / Audible の表紙取得 Worker、WorkManager scheduling、表紙取得キュー監視、再試行・キャンセル、取得完了後の再読み込みなどの runtime が存在していた。それらを `app` から `feature:library` へ移すため、本 ADR は ownership の具体的な境界を定めた。

## Superseded decision

当時は次の構成を採用した。

- `:feature:library:domain` が `LibraryCoverEnrichmentCoordinator` contract と UI 向け work state を所有する
- `:feature:library:data` が表紙取得 Worker、WorkManager scheduling、取得状態 repository を所有する
- `:feature:library:ui` が表紙取得キューと work completion に応じた UI state を所有する
- `:app` は依存 wiring、Google Books の Activity Result、document picker の platform adapter に限定する

これにより表紙取得 runtime を Library feature の変更理由として閉じ込めていた。

## Current decision

ADR-0057 と ADR-0058 により Kindle / Audible の蔵書入力を Web Library JSON へ統一し、`coverUrl` を入力 JSON の正規データとした。追加の表紙検索を行わないため、次は実装から削除済みである。

- `LibraryCoverEnrichmentCoordinator`
- Kindle / Audible の表紙取得 Worker
- WorkManager scheduling
- 表紙取得キュー、診断、再試行・キャンセル UI

したがって本 ADR の表紙取得 runtime に関する具体的な ownership decision は役目を終えた。

一方、`app` を composition / navigation に限定し、Library 固有の domain / data / UI を `feature:library` が所有するという一般原則は ADR-0003 / ADR-0004 に基づき引き続き有効である。Google Books 認証 resolution と Android document picker の application-level adapter は現在も `app` route から feature へ接続する。

## Consequences

- 存在しない cover runtime を現行設計として追跡する必要がなくなる
- Library の ownership は ADR-0003 / ADR-0004 と、現在の入力方式を定める ADR-0057 / ADR-0058 から判断できる
- 将来 Library に新しい background runtime を追加する場合は、その時点の責務境界を新しい ADR で判断する

## Relationship to other ADRs

- ADR-0003: feature-first ownership と `app` composition root
- ADR-0004: concept-oriented ownership
- ADR-0057: Kindle Web Library JSON を正規入力とする
- ADR-0058: Audible Web Library JSON を正規入力とする
