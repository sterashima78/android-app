# ADR-0123: Content / Curation 永続化境界の第二段階を完了する

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0106, ADR-0117, ADR-0119, ADR-0120

## Context

ADR-0106 では ContentItem（現 Article）と Bookmark を別 Aggregate とし、Bookmark が所有する `savedAt` を Curation persistence へ移す方針を定めた。ADR-0117 / ADR-0119 では主要な foreign table access を owner API へ移したが、移行負債として次が残っていた。

- `articles.saved_at` が Curation state を Content table に保持する。
- Bookmark read model が Article / Feed / FeedFolder を直接 JOIN する。
- Summary が Article metadata と Read Later membership を直接 SQL で読む。
- RSS FeedStore が Content rows を直接作成・更新・削除する。
- Summary の永続化 helper が summary、task lifecycle、prepared content、Content read model を一つのファイルで所有する。

ADR-0122 により current architecture の正本が `docs/architecture/` に整理されたため、本判断と同じ変更で current Context Map / persistence documentation も更新する。

## Decision

### 1. Bookmark durable state を Curation-owned `bookmarks` table へ移す

`bookmarks(article_id, saved_at)` を導入し、Bookmark の存在と保存日時の正規な永続状態とする。fresh database の `articles` schema から `saved_at` とその index を削除する。

v24 以前から upgrade する database では v25 migration が legacy `articles.saved_at` を一度だけ読み、`bookmarks` へコピーする。Android が利用する SQLite version 差を考慮し、既存 `articles.saved_at` column の物理 DROP は行わない。upgrade 後は runtime code がこの column を参照せず、legacy compatibility residue としてのみ残る。

この migration の foreign read は一回限りの ownership transfer なので `foreign-table-access-allowlist.tsv` に path / table / ADR を固定し、通常の read/write 例外とは扱わない。

### 2. Curation read model は Content を合成する

`BookmarkReadStore` は `bookmarks`、Tag、Folder、Read Later association のみを読む。Article metadata と effective ContentType は `ArticleRepository.findArticles` を通して取得し、`DefaultBookmarkRepository` が `BookmarkedArticle` projection を合成する。

AI enrichment 起動に必要な URL / source metadata も `ArticleRepository.findArticle` から取得し、Curation data から Article/RSS table JOIN を削除する。

### 3. Bookmark の横断 query を named capability にする

Curation は `BookmarkContentQuery` として次を公開する。

- 指定 Content ID のうち Bookmark で保護されている ID
- 指定 Content ID のうち Read Later に所属する ID

Content cleanup、Summary priority / retry、Source 切断時の Content 保持判定はこの query を利用する。低レベル Bookmark CRUD や table layout は公開しない。

Content retention は `CompositeContentRetentionProtectionQuery` で Curation と Summary の protection query を合成し、Bookmark された既読 Content を30日 cleanup から保護する。Curation の公開 API は Content の retention port を継承せず、composition root が `BookmarkContentQuery.bookmarkedContentIds` を Content-owned `ContentRetentionProtectionQuery` へ適合させる。

### 4. Summary persistence を変更理由で分割する

旧 `SummaryPersistence.kt` を次へ分割する。

- `SummaryStore`: generated summary
- `SummaryPreparedContentStore`: inference 前に準備した本文
- `SummaryTaskStore`: task lifecycle / progress / claim / retry state
- `SummaryPersistenceModels`: persistence record と state constant

Article metadata は `ArticleRepository`、Read Later / Bookmark 判定は `BookmarkContentQuery` から取得する。task priority は Curation table を SQL JOIN せず、task candidate と high-priority ID の集合を入力にする純粋関数として決定する。

WorkManager が生成する inference / content-fetch Worker は `SummaryRuntimeDependenciesProvider` から ArticleRepository、BookmarkContentQuery、BookmarkEnrichmentRepository の domain contract を取得する。

### 5. RSS ingestion は Content-owned command port を利用する

RSS data は `ContentSourceGateway` を利用し、`FeedStore` から `articles` SQL を削除する。Content data が次を所有する。

- Source から取得した Content の upsert
- Feed 表示名変更時の denormalized source title 更新
- Feed 削除時の Bookmark Content 保持、ContentType override 継承、未保存 Content 削除

RSS と Content の更新は別 Context の command になるため、従来の単一 SQLite transaction には戻さない。Feed と Content を常に同一 transaction で更新しなければ成立しない Domain invariant は現在定義されていない。途中失敗時は次回 refresh により Content ingestion を再実行できる。

## Schema migration

- Application DB version: 24 -> 25
- fresh database: `articles.saved_at` を作らない
- upgrade database: `bookmarks` 作成後に legacy `articles.saved_at IS NOT NULL` を `bookmarks` へコピーする
- Bookmark/Tag/Folder/Read Later association の既存 schema は維持する

## Architecture verification

通常 runtime の foreign table access allowlist は全て削除する。残すのは `BookmarkDatabaseSchema` の v25 ownership-transfer migration だけとする。

`table-ownership.tsv` に `bookmarks -> :feature:bookmark:data` を追加する。Summary Worker の framework provider lookup は `SummaryRuntimeDependenciesProvider` に統一し、manifest で entry point を監査する。

## Test strategy

- app schema migration test: v24 の `articles.saved_at` が v25 `bookmarks` へ欠落なく移ること
- BookmarkContentQuery test: Bookmark / Read Later query semantics
- Bookmark repository / enrichment tests: Content schema を直接読まず Curation state で動くこと
- Article boundary test: Content cleanup が Curation/Summary protection query だけに依存すること
- ContentSourceGateway test: Source ingestion、detached Bookmark Content の再関連付け、Feed 削除時の Bookmark Content 保持
- Summary persistence tests: Summary-owned schema だけで task / prepared-content lifecycle が動くこと
- Summary priority test: Curation SQL なしの純粋 priority rule
- CI: `verifyArchitecture`、table ownership verification、全 unit test、lint

## Public repository safety

ADR、manifest、migration test には token、credential、OAuth secret、個人メール、実ユーザー URL、購読情報、個人データを含めない。テスト URL は `example.com` のみを使用する。

## Consequences

- Content と Curation の物理 ownership が Domain model と一致する。
- Bookmark/Summary/RSS が Article/RSS/Curation の durable table layout を直接知らなくなる。
- Content cleanup と Source 削除の保持判定が Curation の named query に統一される。
- Summary persistence の変更理由が分離される。
- cross-context command は単一 DB transaction ではなくなるため、失敗時の再実行可能性を維持する必要がある。
- upgrade 済み DB には legacy `articles.saved_at` column が残るが runtime state としては利用しない。
