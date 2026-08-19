# ADR-0117: Content / Curation / Delivery 間の永続化アクセスを公開契約へ寄せる

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0106

## Context

ADR-0106 は、同一 SQLite database を利用していても table ownership を共有しているとは扱わず、他 Context は owner の Domain API / named Query API を利用する方針を定めた。

第一段階の実装では次の直接アクセスが残っていた。

- Bookmark data が `articles` を直接更新し、共有 URL の Content 作成や `saved_at` / `read_at` 更新を行う。
- Widget data が `articles` を直接 read/write する。
- LAN Web が Article / RSS / Bookmark table を直接 read する。
- Summary data が Bookmark/Curation の tag / folder table を直接 read/write する。

一方で `saved_at` はまだ `articles` に格納されており、ADR-0106 が定義した最終的な Bookmark-owned schema への migration は未実施である。この schema migration まで同じ変更で行うと Article cleanup、Bookmark query、backup 等へ変更範囲が広がるため、永続化 API 境界の整理と物理 schema migration を分離する。

## Decision

### 1. Bookmark から Content table への write は一時的な consumer-owned port を介す

Curation 側に `BookmarkArticleGateway` contract を置き、その実装を `:feature:article:data` が提供する。

この contract は、現在 `articles.saved_at` に存在する Curation state を Article data が技術的に更新するための移行用 port であり、Article Aggregate が Bookmark を所有することを意味しない。

対象は次の操作とする。

- bookmark 状態確認
- save + read
- unsave
- 共有 URL の Content 作成または再利用
- Bookmark import 時の Content 作成または再利用

`articles.saved_at` を Bookmark-owned table へ移行した時点で、この port は廃止する。

### 2. Bookmark の tag / folder 操作は Curation capability として公開する

Summary が `tags` / `article_tags` / `bookmark_folders` / `article_folders` を直接操作しないよう、`BookmarkEnrichmentRepository` を Curation の公開 contract とする。

Summary は次だけを要求する。

- enrichment 用の既存 tag / folder 候補を取得する
- 生成された tag / folder を Bookmark に適用する

tag の upsert、folder の存在確認、association の永続化、変更通知は Curation data が所有する。

### 3. Widget と LAN Web は durable Domain table を参照しない

Widget は次の Repository のみを利用する。

- `ArticleRepository`: 未読取得、既読化
- `BookmarkRepository`: bookmark 状態、Read Later
- `FeedRepository`: RSS 更新

LAN Web は次の Repository のみを利用する。

- `ArticleRepository`: 未読 Content
- `BookmarkRepository`: Bookmark / Read Later
- `FeedRepository`: Feed

LAN Web 固有の SQL helper と `YomitoriDatabase` dependency は削除する。

### 4. Android framework が生成する entry point は domain provider から依存を取得する

`SummaryWorker` と `LanWebServerService` は Android framework により生成されるため constructor injection を直接利用できない。

ADR-0101 の Knowledge Worker、ADR-0024 の Widget と同様に、Application が domain-level provider contract を実装する。

- Summary: `BookmarkEnrichmentRepositoryProvider`
- LAN Web: `LanWebRepositoryProvider`

feature data は `YomitoriApplication` や `AppContainer` の concrete type を参照しない。

### 5. `saved_at` の foreign read は今回の移行例外として残す

Bookmark の一覧取得・未分類判定等は、`saved_at` が `articles` に存在する限り Content table を read する必要がある。

これは最終設計として許可するものではなく、ADR-0106 Phase 2 の schema migration までの明示的な移行例外とする。

また Bookmark 一覧の `feeds` / `feed_folders` JOIN も effective content type の既存実装に由来する移行負債として残る。これは ADR-0106 Phase 3 の Content Classification 整理と合わせて解消する。

## Transaction boundary

従来 BookmarkStore が同一 SQLite transaction 内で Article と Curation table を同時更新していた操作は、公開 contract を跨ぐ orchestration に変わるため、DB 上の単一 transaction ではなくなる。

今回対象の操作について、Article/Bookmark の双方を常に単一 transaction で更新しなければ成立しない Domain invariant は定義されていないため、Context boundary を優先する。

特に import は全件 all-or-nothing transaction ではなく、Content 作成/再利用と tag association を entry ごとに進める。将来これが Domain invariant と判明した場合は直接 SQL へ戻すのではなく Aggregate / Application Service 境界を再検討する。

## Consequences

### Positive

- Widget / LAN Web / Summary が他 Context の table layout を知らなくなる。
- Bookmark の Article write が Article data に集約される。
- AI enrichment の永続化責務が Curation に戻る。
- Android background/runtime entry point から app concrete implementation への依存を増やさない。
- ADR-0106 の物理 schema migration を独立した次段階として実施できる。

### Negative

- `BookmarkArticleGateway` という移行用 abstraction が一時的に増える。
- Bookmark 内の `articles.saved_at` read は schema migration まで残る。
- 複数 Context を跨ぐ bookmark 操作は単一 SQLite transaction ではなくなる。
- LAN Web / Widget の同期 API から suspend Repository を利用する箇所では background thread 上で `runBlocking` を利用する。

## Follow-up

ADR-0106 Phase 2 として次を行う。

1. Bookmark-owned table を導入し `articles.saved_at` を migration する。
2. `BookmarkArticleGateway` の bookmark state 操作を廃止する。
3. Bookmark query から `articles.saved_at` への直接依存を除去する。
4. table ownership manifest / allowlist を `verifyArchitecture` に導入し、残存する foreign access を可視化する。
5. Content Classification 整理後に Bookmark read model の `feeds` / `feed_folders` JOIN を解消する。
