# ADR-0054: Library の runtime と UI state を feature が所有する

- Status: Accepted
- Date: 2026-08-14

## Context

ADR-0003 は `:app` を composition root と navigation に限定し、feature 固有 UI state は feature 側で所有すると定めている。

Library の初期実装では、`app/src/main/.../feature/library` に次の責務が残っていた。

- Kindle / Audible の表紙取得 Worker と WorkManager scheduling
- 表紙取得キューの状態監視と再試行・キャンセル
- 表紙取得完了後の Library snapshot 再読み込み
- 表紙取得キュー画面の UI state
- Google Play Books の URI routing

これらは Library の変更理由に従う feature 固有責務であり、`app` の dependency wiring / navigation の責務を越えている。

一方で、Google Books の認証 resolution と Android document picker は Activity Result API を利用する application-level adapter であり、composition root から feature の処理へ接続する必要がある。

## Decision

Library の ownership を次のように整理する。

### Domain

`:feature:library:domain` は表紙取得 runtime を抽象化する `LibraryCoverEnrichmentCoordinator` contract と、UI が利用する work state を所有する。

Domain API には WorkManager の `WorkInfo` など Android implementation type を露出しない。

### Data

`:feature:library:data` は次を所有する。

- Kindle / Audible cover enrichment Worker
- WorkManager scheduling
- background data fetch constraint の適用
- cover acquisition status repository と Worker state の domain model への変換
- `LibraryCoverEnrichmentCoordinator` の implementation

Worker は application process から起動されるが、Library 固有の background execution であるため `app` ではなく Library Data の ownership とする。

### UI

`:feature:library:ui` は次を所有する。

- Library screen の state collection
- cover work completion に応じた Library state refresh
- cover queue の表示状態、再読み込み、メッセージ
- Google Play Books の URI routing

UI は concrete Data implementation に依存せず、Domain contract のみを利用する。

### App

`:app` に残す Library route は composition adapter とする。

担当するのは次だけとする。

- `YomitoriApplication` から database dependency を取得する
- Domain contract と ViewModel に concrete implementation を wiring する
- Google Books の Activity Result resolution を接続する
- Android document picker と `ContentResolver` を Library import callback に接続する
- feature UI entry point を呼び出す

feature 固有の Worker、WorkManager state、画面状態は `app` に置かない。

## Consequences

### Positive

- ADR-0003 の `app` composition root 境界と実装が一致する
- Library の background execution と UI state の変更が feature 配下で完結する
- UI module から concrete Data implementation への依存を追加せずに ownership を整理できる
- WorkManager type が Domain / UI API に漏れない
- cover queue と cover worker の責務を Library feature として追跡しやすくなる

### Negative

- Library Data module が WorkManager と `core:background` に依存する
- `LibraryCoverEnrichmentCoordinator` という feature 内の runtime boundary が追加される
- Google Books 認証と document picker の platform adapter は引き続き `app` route に残る

## Relationship to other ADRs

ADR-0001 の UI / Domain / Data の責務分離に従う。

ADR-0002 の方針に従い、cover runtime は状態・依存・ライフサイクルを持つ class とし、module 境界は interface で表現する。

ADR-0003 の feature-first ownership と `app` composition root の判断を Library feature に具体適用する。

ADR-0004 の concept-oriented ownership に従い、表紙取得は Library に属する変更理由として Library feature が所有する。
