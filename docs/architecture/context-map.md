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

Bookmark は ContentItemId を参照する。`savedAt` の正規 persistence は Curation-owned `bookmarks` table である。legacy `articles.saved_at` は現行 schema に存在せず、runtime state としても利用しない。

Bookmark から Web Library への移動は Curation-owned `MoveBookmarkToLibraryUseCase` が所有する。Bookmark domain は Library domain の narrow `WebLibraryAdder` capability だけに一方向依存し、移動先の作成成功後に Bookmark を解除する。Library から Bookmark への逆方向移動は提供しない。

### Source contexts: RSS / Reddit / YouTube

Content の上流 Source Context として扱う。各 Source 固有の subscription、synchronization / fetch state、authentication、external API / site semantics、source-specific metadata は各 Context が所有する。

RSS から Content への ingestion は Content-owned `ContentSourceGateway` を利用し、RSS data は Content table を直接更新しない。

RSS は通常の RSS / Atom discovery に加え、RSS を公開していない Web ページから synthetic feed を生成する取得方法も所有する。user-defined Web scraping rule は `rss_web_scraping_rules` に URL glob pattern、Promise ベースの JavaScript function、timeout を保存し、RSS-owned `FeedRepository` 経由で管理・実行する。Library の custom metadata extractor と execution pattern は似ているが、RSS から Library Context の repository/client へ依存せず、それぞれの source semantics と durable state を各 Context 内に閉じる。

組み込みの site-specific synthetic feed client は ADR-0184 で廃止済みであり、feed 取得は一致する user-defined rule を優先し、該当 rule がなければ通常の RSS / Atom discovery / fetch へ進む。特定 host を根拠に新規 feed を暗黙に `COMIC` へ分類する処理も持たず、必要な分類は feed / folder の設定で明示する。

### Summary

Content を入力として generated summary と task lifecycle / priority の Summary 側規則を所有する。

- Article metadata は `ArticleRepository` から取得する。
- Read Later priority / Bookmark retry は `BookmarkContentQuery` を利用する。
- Curation tag/folder 更新は `BookmarkEnrichmentRepository` を利用する。
- Summary data は Content / Curation table を直接参照しない。

### Knowledge

Content / Curation を資料として参照し、Knowledge page、source relationship、generated / edited state、Knowledge 固有 background build lifecycle を所有する。

### Health

現在の主要な実装 module は `:feature:health:{domain,data,ui}`。Health Connect を外部データソースとして、歩数・運動・心拍・睡眠・体重の read-only overview と、体脂肪率・栄養情報の read-only 履歴を提供する。

Health Connect の Record 型と permission API は Health Data/UI の platform boundary に閉じ、Health Domain は `HealthOverview`、`BodyFatMeasurement`、availability など read model のみを扱う。運動履歴の raw `ExerciseSessionRecord` は、Data 層で完全一致を除去したうえで、提供元が異なり時間帯と長さが強く重なる record を同一の実運動候補として近似重複除去する。詳細付き session に `ExerciseSegment` がある場合は、別提供元の segment なし standalone session が各 segment と強く一致するケースも同一候補として統合する。合計運動時間は raw 一覧の単純加算ではなく `ExerciseSessionRecord.EXERCISE_DURATION_TOTAL` の Aggregate API を利用し、Health Connect の activity data 優先度・重複除去を尊重する。exercise type と data origin は判定のため Data 層内だけで利用し、Domain/UI へ公開しない。durable table は所有せず、Health Connect 由来データを Backup、AI task、外部 API へ流さない。

Health と Workout は別 Context とする。Workout はアプリ内でユーザーが記録する状態の source of truth であり、Health Connect への一方向 export は Workout-owned outbound adapter `HealthConnectWorkoutHistoryExporter` が担当する。Health Connect は Context ではなく複数 Context が目的別に利用できる external platform と捉える。Health Connect -> Workout の import / 同期や、Health Connect から読み取った運動の書き戻しは行わない。書き込み失敗や権限未付与でも Workout のローカル記録は維持する。

### Calendar

現在の主要な実装 module は `:feature:calendar:{domain,data,ui}`。Calendar は独自の durable event state を所有せず、日付軸の read-only projection として扱う。

- Android Calendar Provider の `CalendarContract.Instances` を端末カレンダー source として読む。
- Task は `TaskReader` 経由で期限を `DEADLINE` event へ投影する。
- Workout は `WorkoutReader` 経由で実績を `ACTIVITY` event へ投影する。
- Domain からは全 source を共通 `CalendarEvent` として扱い、`source` / `kind` / external source metadata で表示上の意味を保持する。
- Task / Workout の table や private storage は直接参照しない。

Calendar は Task / Workout の command owner ではなく、初期実装は読み取り専用とする。

### Library

現在の主要な実装 module は `:feature:library:{domain,data,ui}`。Library は Google Play Books、Kindle、Audible、SMB、Web を `LibraryBook` catalog の source として扱い、`library_items` と Library 固有の整理 metadata を所有する。

Web source は URL を identity とし、Library data layer がまず HTTP(S) ページの OGP / HTML metadata を取得する。通常は HTTPS ページで metadata が不足する場合だけ短命な WebView で JavaScript 実行後の DOM metadata を補完するが、Library-owned `WebLibraryMetadataExtractor` が requested URL に一致する場合は静的 metadata が揃っていても WebView を実行し、登録された Promise ベースの非同期関数の `title` / `thumbnailUrl` をサイト固有の override として利用する。custom extractor は専用 WebView profile の既存 security boundary 内でのみ動作し、native JavaScript bridge は公開しない。URL pattern と function code は `web_library_metadata_extractors` に保存する Library-owned durable user data である。

既存 Web 蔵書は `WebLibraryMutator.refreshWebBook` で明示的に再取得でき、手動再取得では rendered metadata を優先する。複数項目の再取得は WebView を同時起動せず直列に行う。Web 固有の追加だけを必要とする consumer には `WebLibraryAdder` を公開し、追加・再取得・削除が必要な Library 自身の consumer には `WebLibraryMutator` capability を公開する。extractor rule の管理は `WebLibraryMetadataExtractorRepository` capability として公開する。いずれも Bookmark / Curation の永続化には触れない。

Library は Bookmark への逆方向移動を所有せず、Bookmark の保存 capability に依存しない。Bookmark から Library への移動時も Library は `WebLibraryAdder` として移動先の作成だけを担当する。

### Other application contexts

Asset、Task、Workout、Mail、Chat、Game 等は現在 Content/Curation Aggregate へ統合しない。

Workout の完了済み記録を Health Connect へ公開する処理は Workout Context 自身の outbound adapter とし、`WorkoutHistoryExporter` を domain port、`:feature:workout:data` の Health Connect implementation を adapter とする。Workout write permission は Workout UI から要求し、Health Context の read permission と混在させない。

AI Task Queue、Backup、Settings は主に supporting/application capability として扱い、他 Domain table の共同 owner にはしない。

## Cross-context operation classification

### Application Service / command port

複数 Aggregate / Context の command を1つの操作として orchestration する場合、各 owner の公開 Domain API / command port を利用し、foreign table を直接 write しない。依存方向と操作 owner が一意に定まる場合は、必ずしも `:app` の対称な Application Service に持ち上げず、owning feature が他 Context の narrow capability を利用してよい。

現在の例:

- Bookmark import: `ImportBookmarksUseCase`
- Curation -> Content: `BookmarkArticleGateway`
- RSS -> Content: `ContentSourceGateway`
- Bookmark -> Web Library: Bookmark-owned `MoveBookmarkToLibraryUseCase` が `WebLibraryAdder` と `BookmarkMutator` を順に呼ぶ

Workout -> Health Connect は別 Context の command orchestration ではなく、Workout-owned data を external platform へ publish する outbound adapter として ADR-0189 で再分類した。

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

ADR-0138 で database version 27 を互換性 baseline としたため、最後に残っていた v24 -> v25 ownership transfer migration も終了した。現在 `foreign-table-access-allowlist.tsv` に例外 entry はない。

`Article` -> `ContentItem` rename / module restructuring は ubiquitous language が安定した後に再評価する。

## Sources

- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0123](../adr/0123-content-curation-persistence-phase2.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
- [ADR-0128](../adr/0128-calendar-read-model-and-android-calendar-provider.md)
- [ADR-0131](../adr/0131-workout-health-connect-export.md)
- [ADR-0132](../adr/0132-health-connect-exercise-session-deduplication.md)
- [ADR-0138](../adr/0138-database-v27-compatibility-baseline.md)
- [ADR-0143](../adr/0143-web-library-source-and-bookmark-transfer.md)
- [ADR-0154](../adr/0154-web-library-rendered-metadata-fallback.md)
- [ADR-0173](../adr/0173-web-library-custom-metadata-extractors.md)
- [ADR-0180](../adr/0180-rss-custom-web-scraping-rules.md)
- [ADR-0184](../adr/0184-remove-site-specific-manga-rss-clients.md)
- [ADR-0186](../adr/0186-bookmark-to-library-one-way-ownership.md)
- [ADR-0189](../adr/0189-workout-owned-health-connect-export-adapter.md)