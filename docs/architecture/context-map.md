# Domain Context Map

この文書は現在の Domain Context と Context 間関係を表す。Gradle module の物理構成は [module-map.md](module-map.md)、table access ownership は [persistence.md](persistence.md) を参照する。

Gradle の `feature/<name>` は ownership / build boundary であり、Bounded Context や Aggregate と 1 対 1 で対応するとは限らない。

## Content around contexts

```text
+----------------------+        +----------------------+
| RSS context          |        | Reddit context       |
| Feed / FeedFolder    |        | source-specific data |
+----------+-----------+        +----------+-----------+
           |                               |
           | content identity / metadata   |
           +---------------+---------------+
                           |
                           v
                  +------------------+
                  | Content context  |
                  | ContentItem      |
                  | (current Article)|
                  +--------+---------+
                           |
             ContentItemId |
          +----------------+-------------------+
          |                |                   |
          v                v                   v
+----------------+ +----------------+ +------------------+
| Curation       | | Summary        | | Knowledge        |
| Bookmark       | | summaries      | | wiki / sources   |
| Tag / Folder   | | task pipeline  | | generated state  |
| Read Later     | +----------------+ +------------------+
+----------------+

+----------------------+
| YouTube context      |
| channels / videos    |
+----------+-----------+
           |
           +---- content integration ----> Content context

Presentation / delivery:
  Web / Widget / Integrated UI
  -> Domain API または named read-only Query API の consumer
  -> durable Domain table は所有しない
```

## Context responsibilities

### Content

現在の主要な実装 module は `:feature:article`。Domain 上は RSS の Article に限定されない `ContentItem` に近い。

所有する概念:

- content identity
- URL / title / source metadata
- published / fetched state
- reading state
- content classification override

`Article` -> `ContentItem` の rename は migration のため現時点では行わない。persistence ownership と公開 API 境界を優先して整える。

### Curation

現在の主要な実装 module は `:feature:bookmark`。

所有する概念:

- Bookmark
- savedAt
- Tag
- Folder
- Read Later membership

Bookmark は ContentItemId を参照する。`articles.saved_at` は Domain ownership と現在の物理 schema が一致していない transitional state であり、Curation-owned persistence への migration 対象とする。

### Source contexts: RSS / Reddit / YouTube

Content の上流 Source Context として扱う。

各 Source 固有の次の責務は各 Context が所有する。

- subscription
- synchronization / fetch state
- authentication
- external API / site semantics
- source-specific metadata

同じ Content を供給することだけを理由に generic `source` module へ統合しない。ubiquitous language と lifecycle が実際に収束した場合にのみ再評価する。

### Summary

Content を入力として次を所有する。

- generated summary
- summary persistence
- task lifecycle / priority の Summary 側規則

Curation state を変更する場合は Curation の公開 command を利用する。Content retention を保護する情報は Summary が query contract として公開する。

### Knowledge

Content / Curation を資料として参照し、次を所有する。

- Knowledge page
- source relationship
- generated / edited state
- Knowledge 固有 background build lifecycle

### Other application contexts

Library、Asset、Task、Workout、Mail、Chat、Game 等は現在 Content/Curation Aggregate へ統合しない。

AI Task Queue、Backup、Settings は主に supporting/application capability として扱い、他 Domain table の共同 owner にはしない。

## Cross-context operation classification

### Application Service

複数 Aggregate / Context の command を1つのユーザー操作として orchestration する。

例:

- shared content の保存
- save and mark read
- mark read later
- bookmark import

各 owner の公開 Domain API / port を利用し、foreign table を直接 write しない。

### Domain Service

永続状態を所有せず、複数 Aggregate / Context の情報から Domain rule を解決する。

代表例は Content Classification で、Content 自身、Source、Source container の override から effective ContentType を決定する。

### Read Model / Projection

複数 Context の大量 read で Repository / Query API 合成が実測上問題になる場合だけ導入する。

- read-only
- named responsibility
- referenced Context/table を明示
- generic `cross-feature` module に置かない
- Domain command を提供しない

## Current transition targets

主要な既知 migration target は次のとおり。

1. `articles.saved_at` を Curation-owned persistence へ移す。
2. Bookmark read model の Content / RSS table 直接 read を公開 read contract へ移す。
3. Summary の Read Later priority を Curation の named query へ移す。
4. RSS ingestion の Content write を Content command / application boundary へ移す。
5. foreign-table allowlist を follow-up 完了ごとに削除する。
6. ubiquitous language が安定した後に `Article` -> `ContentItem` rename / module restructuring を再評価する。

現在の transitional foreign access の正確な一覧は [`config/architecture/foreign-table-access-allowlist.tsv`](../../config/architecture/foreign-table-access-allowlist.tsv) を正本とする。

## Sources

- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
