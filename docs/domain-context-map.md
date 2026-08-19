# Domain Context Map

この文書は ADR-0106 に基づく現在の Domain model の作業用 Context Map である。

Gradle の `feature/<name>` は ownership / build boundary であり、Bounded Context や Aggregate と 1 対 1 で対応するとは限らない。

## Content 周辺

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

現在の実装は `:feature:article`。

Domain 上は RSS の記事に限定されない ContentItem に近い。

所有候補:

- content identity
- URL / title / source metadata
- published / fetched state
- reading state
- content classification override

現段階では `Article` から `ContentItem` への rename は行わない。

### Curation

現在の実装は主に `:feature:bookmark`。

所有する概念:

- Bookmark
- savedAt
- Tag
- Folder
- Read Later membership

Bookmark は ContentItemId を参照する。

`articles.saved_at` は現在の物理 schema と Domain ownership が一致していないため migration 対象とする。

### RSS / Reddit / YouTube

Content の上流 Source Context として扱う。

各 Source 固有の購読、同期、取得状態、認証、外部 API semantics は各 Context が所有する。

同じ Content を供給するという理由だけで1つの generic source module には統合しない。

### Summary

Content を入力として派生 summary と task lifecycle を所有する。

Curation の tag 等を変更する場合は Curation の公開 command を利用する。

### Knowledge

Content / Curation を資料として参照し、Knowledge page / source relationship / generated state を所有する。

## Cross-context operation classification

### Application Service

複数 Aggregate の command を1つのユーザー操作としてまとめる。

候補:

- Save shared content
- Save and mark read
- Mark read later

各 owner の Domain API を利用し、foreign table には直接 write しない。

### Domain Service

永続状態を所有せず、複数 Aggregate / Context の情報から Domain rule を解決する。

候補:

- Content Classification
  - ContentItem override
  - Feed override
  - FeedFolder override
  - effective ContentType

### Read Model / Projection

複数 Context の大量 read で、Repository 合成が実測上問題になる場合だけ導入する。

ルール:

- read-only
- named responsibility
- referenced contexts/tables を明示
- generic `cross-feature` module を作らない
- command を提供しない

## Persistence ownership rule

```text
Owner data module
  -> owned table の直接 SELECT / INSERT / UPDATE / DELETE

Other context
  -> owner Domain API / named Query API

Named Projection
  -> 明示された foreign table の SELECT のみ

Foreign table write
  -> 禁止
```

同じ SQLite database を共有していることは共同 ownership を意味しない。

## Current violations / migration targets

### High priority

- `feature/widget:data` が `articles` を直接 read/write
- `feature/web:data` が Article / RSS / Bookmark table を直接 read
- `feature/bookmark:data` が `articles.saved_at` と Article row を直接 write
- `feature:summary:data` が Bookmark-owned tag table を直接 write

### Requires domain redesign

- ArticleRepository が RSS table を JOIN して effective content type を解決
- Article cleanup が Summary table を参照
- BookmarkSourceMetadataReader が Article / RSS table を JOIN

これらは単純な SQL 移動ではなく Domain Service / Application Service / Projection のどれに該当するかを決めてから移行する。

## Migration order

1. Web / Widget の不要な foreign table access を owner API に置換する。
2. Bookmark-owned `bookmarks` table を導入し `articles.saved_at` を移行する。
3. Summary -> Curation write を Curation command API に置換する。
4. Content Classification と retention rule を明示的な Domain Service / Query contract にする。
5. foreign table access を architecture verification で検出する。
6. ubiquitous language が安定した後、Article -> ContentItem rename と module restructuring の必要性を再評価する。

## Other application contexts

Library、Knowledge、Asset、Task、Workout、Mail、Chat などは現時点では Content/Curation の Aggregate に統合しない。

AI Task Queue、Backup、Settings は主に supporting/application capability として扱い、Domain table の共同 owner にはしない。
