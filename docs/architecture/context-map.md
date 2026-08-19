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
  Web / Widget / Integrated UI / Calendar
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

Bookmark の保存状態は所有しない。`Article` -> `ContentItem` の rename は ubiquitous language がさらに安定した後に判断する。

### Curation

現在の主要な実装 module は `:feature:bookmark`。

所有する概念:

- `bookmarks(article_id, saved_at)`
- Tag / Folder
- Read Later membership

Bookmark は ContentItemId を参照する。v25 以降、`savedAt` の正規 persistence は Curation-owned `bookmarks` table である。upgrade 済み DB に残る legacy `articles.saved_at` column は runtime state として利用しない。

### Source contexts: RSS / Reddit / YouTube

Content の上流 Source Context として扱う。各 Source 固有の subscription、synchronization / fetch state、authentication、external API / site semantics、source-specific metadata は各 Context が所有する。

RSS から Content への ingestion は Content-owned `ContentSourceGateway` を利用し、RSS data は Content table を直接更新しない。

### Summary

Content を入力として generated summary と task lifecycle / priority の Summary 側規則を所有する。

- Article metadata は `ArticleRepository` から取得する。
- Read Later priority / Bookmark retry は `BookmarkContentQuery` を利用する。
- Curation tag/folder 更新は `BookmarkEnrichmentRepository` を利用する。
- Summary data は Content / Curation table を直接参照しない。

### Knowledge

Content / Curation を資料として参照し、Knowledge page、source relationship、generated / edited state、Knowledge 固有 background build lifecycle を所有する。

### Health

現在の主要な実装 module は `:feature:health:{domain,data,ui}`。Health Connect を外部データソースとして、歩数・運動・心拍・睡眠・体重の read-only overview を提供する。

Health Connect の Record 型と permission API は Data/UI の platform boundary に閉じ、Domain は集計済みの `HealthOverview` と availability のみを扱う。初期実装では durable table を所有せず、Health Connect 由来データを Backup、AI task、外部 API へ流さない。

Health と Workout は別 Context とする。Workout はアプリ内でユーザーが記録する状態を所有し、Health は Health Connect のデータを参照する。相互同期や永続コピーは行わず、将来統合表示が必要な場合は目的別 read-only Query / Projection で接続する。

### Calendar

現在の主要な実装 module は `:feature:calendar:{domain,data,ui}`。Calendar は独自の durable event state を所有せず、日付軸の read-only projection として扱う。

- Android Calendar Provider の `CalendarContract.Instances` を端末カレンダー source として読む。
- Task は `TaskReader` 経由で期限を `DEADLINE` event へ投影する。
- Workout は `WorkoutReader` 経由で実績を `ACTIVITY` event へ投影する。
- Domain からは全 source を共通 `CalendarEvent` として扱い、`source` / `kind` / external source metadata で表示上の意味を保持する。
- Task / Workout の table や private storage は直接参照しない。

Calendar は Task / Workout / device calendar の command owner ではなく、初期実装は読み取り専用とする。

### Other application contexts

Library、Asset、Task、Workout、Mail、Chat、Game 等は現在 Content/Curation Aggregate へ統合しない。

AI Task Queue、Backup、Settings は主に supporting/application capability として扱い、他 Domain table の共同 owner にはしない。

## Cross-context operation classification

### Application Service / command port

複数 Aggregate / Context の command を1つの操作として orchestration する場合、各 owner の公開 Domain API / command port を利用し、foreign table を直接 write しない。

現在の例:

- Bookmark import: `ImportBookmarksUseCase`
- Curation -> Content: `BookmarkArticleGateway`
- RSS -> Content: `ContentSourceGateway`

### Domain Service

永続状態を所有せず、複数 Aggregate / Context の情報から Domain rule を解決する。

現在の例:

- Content Classification
- Content Retention Policy

Content retention では Curation の `BookmarkContentQuery.bookmarkedContentIds` と Summary の protection query を composition root で Content-owned `ContentRetentionProtectionQuery` へ適合・合成する。Curation の公開 API 自体は Content の retention policy に依存しない。

### Read Model / named Query

他 Context が owner state を必要とする場合、低レベル SQL ではなく目的を表す query contract を利用する。

現在の例:

- `ArticleRepository.findArticle(s)`
- `BookmarkContentQuery.bookmarkedContentIds`
- `BookmarkContentQuery.readLaterContentIds`
- `ContentClassificationSourceQuery`
- `ContentRetentionProtectionQuery`
- Calendar の `TaskReader` / `WorkoutReader` 合成 read model

大量 read で owner API の合成が実測上問題になる場合だけ、read-only かつ purpose-specific な Named Projection を検討する。

## Current transition targets

ADR-0123 により、次の移行は完了した。

1. `articles.saved_at` の Curation-owned persistence への移行。
2. Bookmark read model の Content / RSS table 直接 read の owner API 化。
3. Summary の Read Later / Bookmark cross-context read の named query 化。
4. RSS ingestion の Content write の Content-owned command port 化。
5. これら runtime path に対する foreign-table allowlist の削除。

残る例外は v24 -> v25 migration が legacy `articles.saved_at` を一度だけ読む ownership transfer のみである。正確な一覧は [`config/architecture/foreign-table-access-allowlist.tsv`](../../config/architecture/foreign-table-access-allowlist.tsv) を正本とする。

`Article` -> `ContentItem` rename / module restructuring は ubiquitous language が安定した後に再評価する。

## Sources

- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0123](../adr/0123-content-curation-persistence-phase2.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
- [ADR-0128](../adr/0128-calendar-read-model-and-android-calendar-provider.md)
