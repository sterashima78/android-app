# Domain Context Map

この文書は ADR-0106 と ADR-0122 に基づく現在の Domain model の作業用 Context Map である。

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

現在の実装は `:feature:article`。Domain 上は RSS の記事に限定されない ContentItem に近い。

所有する状態:

- content identity
- URL / title / source metadata
- published / fetched state
- reading state
- content classification override

Bookmark の保存状態は所有しない。`Article` から `ContentItem` への rename は ubiquitous language がさらに安定した後に判断する。

### Curation

現在の実装は主に `:feature:bookmark`。

所有する状態:

- `bookmarks(article_id, saved_at)`
- Tag / Folder
- Read Later membership

Bookmark は ContentItemId を参照する。v25 以降、`savedAt` の正規 persistence は Curation-owned `bookmarks` table である。upgrade 済み DB に残る legacy `articles.saved_at` column は runtime state として利用しない。

### RSS / Reddit / YouTube

Content の上流 Source Context として扱う。各 Source 固有の購読、同期、取得状態、認証、外部 API semantics は各 Context が所有する。

RSS から Content への ingestion は Content-owned `ContentSourceGateway` を利用し、Source data は Content table を直接更新しない。

### Summary

Content を入力として派生 summary と task lifecycle を所有する。

- Article metadata は `ArticleRepository` から取得する。
- Read Later priority / Bookmark retry は `BookmarkContentQuery` を利用する。
- Curation tag/folder 更新は `BookmarkEnrichmentRepository` を利用する。
- Summary data は Article / Curation table を直接参照しない。

### Knowledge

Content / Curation を資料として参照し、Knowledge page / source relationship / generated state を所有する。

## Cross-context operation classification

### Application Service / command port

複数 Aggregate の command を1つの操作としてまとめる場合、owner API / command port を利用する。

現在の例:

- Bookmark import: `ImportBookmarksUseCase`
- Curation -> Content: `BookmarkArticleGateway`
- RSS -> Content: `ContentSourceGateway`

同一 SQLite database を共有していても foreign table へ直接 write しない。

### Domain Service

永続状態を所有せず、複数 Context の情報から Domain rule を解決する。

現在の例:

- Content Classification
- Content Retention Policy

Content retention では Curation の `BookmarkContentQuery.bookmarkedContentIds` と Summary の protection query を composition root で Content-owned `ContentRetentionProtectionQuery` へ適合・合成する。Curation の公開 API 自体は Content の retention policy に依存しない。

### Read Model / named Query

複数 Context が owner state を必要とする場合、低レベル SQL ではなく目的を表す query contract を利用する。

現在の例:

- `ArticleRepository.findArticle(s)`
- `BookmarkContentQuery.bookmarkedContentIds`
- `BookmarkContentQuery.readLaterContentIds`
- `ContentClassificationSourceQuery`
- `ContentRetentionProtectionQuery`

## Persistence ownership rule

```text
Owner data module
  -> owned table の直接 SELECT / INSERT / UPDATE / DELETE

Other context
  -> owner Domain API / named Query / command port

Named Projection
  -> 明示された foreign table の SELECT のみ

Foreign table write
  -> 禁止
```

同じ SQLite database を共有していることは共同 ownership を意味しない。

## Current migration exception

通常 runtime の Content / Curation / Summary / RSS 間 foreign table access は ADR-0122 で解消した。

残る明示的な例外は v24 -> v25 migration のみである。

- `BookmarkDatabaseSchema` が legacy `articles.saved_at` を一度だけ読み、Curation-owned `bookmarks` へ ownership transfer する。
- この参照は `foreign-table-access-allowlist.tsv` に ADR-0122 とともに固定する。
- migration 完了後の runtime code は legacy column を参照しない。

## Other application contexts

Library、Knowledge、Asset、Task、Workout、Mail、Chat などは Content/Curation の Aggregate に統合しない。

AI Task Queue、Backup、Settings は主に supporting/application capability として扱い、Domain table の共同 owner にはしない。
