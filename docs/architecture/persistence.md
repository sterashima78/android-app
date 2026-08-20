# Persistence Architecture

この文書は durable data の schema、migration、table access ownership と cross-context read/write の現在の規則を示す。

## Physical database

アプリの durable user data は原則として単一 SQLite database を共有する。ただし、同じ database に存在することは共同 ownership を意味しない。

```text
Single physical SQLite database
        |
        +-- Content-owned tables
        +-- RSS-owned tables
        +-- Curation-owned tables
        +-- Summary-owned tables
        +-- Mail / Library / Asset / Task / ... owned tables
```

各 feature/context が自身の schema contribution と migration の意味を所有する。

## Schema and migration ownership

- `:core:database` は connection、transaction、`DatabaseSchema`、`DatabaseSchemaContribution`、`DatabaseMigration`、migration runner 等の汎用 mechanism を提供する。
- feature 固有 table/index の schema と migration は owning `:feature:<name>:data` が所有する。
- `:app` は composition root として schema contribution を集約し、単一 database version と contribution order を定義する。
- Worker / Service が独自の schema composition を持たず、application-level schema composition を利用する。
- database version は単一 DB 全体の値であり、個別 feature の ownership ではない。

## Access ownership rule

```text
Owner data module
  -> owned table の直接 SELECT / INSERT / UPDATE / DELETE

Other context
  -> owner Domain API / named Query API / command port

Named Projection
  -> 明示された foreign table の SELECT のみ

Foreign table write
  -> 禁止
```

foreign key の存在、同一 transaction の利用、同一 SQLite file の利用は、別 Context の table を自由に読み書きする理由にはならない。

複数 Aggregate を常に単一 transaction で変更しなければ invariant を維持できない場合、foreign write の例外を追加するのではなく Aggregate / Context boundary を再検討する。

## Machine-readable table ownership

`config/architecture/table-ownership.tsv` は `gradle/table-ownership.gradle.kts` が検査する durable table ownership の機械可読な正本である。

主要な ownership は次のとおり。

| Table | Owner module |
| --- | --- |
| `articles` | `:feature:article:data` |
| `feeds` | `:feature:rss:data` |
| `feed_folders` | `:feature:rss:data` |
| `bookmarks` | `:feature:bookmark:data` |
| `tags` | `:feature:bookmark:data` |
| `article_tags` | `:feature:bookmark:data` |
| `bookmark_folders` | `:feature:bookmark:data` |
| `article_folders` | `:feature:bookmark:data` |
| `article_summaries` | `:feature:summary:data` |
| `summary_tasks` | `:feature:summary:data` |
| `summary_article_content` | `:feature:summary:data` |
| `smb_library_servers` | `:feature:library:data` |
| `smb_cover_prefetch_queue` | `:feature:library:data` |

SMB 表紙先読みキューは Library Context が所有する派生処理状態であり、WorkManager 自身の状態だけに依存せず `smb_cover_prefetch_queue` に待機・実行・失敗・完了・対象外と転送進捗を保持する。schema は `libraryDatabaseSchema` に含め、app-level database version 26 で既存 DB に追加する。

この表を手作業の完全な schema catalog として扱わない。正確な検査対象は [`config/architecture/table-ownership.tsv`](../../config/architecture/table-ownership.tsv)、実際の schema definition は各 feature data module の `DatabaseSchemaContribution` を参照する。

## Cross-context query / command patterns

通常は owner が Repository / named Query / command port を公開する。他 Context は table layout ではなく意味のある contract に依存する。

現在の例:

- Content Classification は RSS table を JOIN せず `ContentClassificationSourceQuery` を利用する。
- Content retention は Curation の `BookmarkContentQuery` と Summary protection query を composition root で `ContentRetentionProtectionQuery` へ適合・合成する。
- Bookmark read model は Article metadata を `ArticleRepository.findArticle(s)` から取得する。
- Summary は Article metadata を `ArticleRepository`、Bookmark / Read Later membership を `BookmarkContentQuery` から取得する。
- RSS ingestion は Content table を直接 write せず `ContentSourceGateway` を利用する。

### Named Projection

owner API の合成で実測上の性能問題がある read path に限り read-only Projection を利用できる。Projection は purpose-specific name、read-only、参照 Context/table の明示、integration test を必要とし、command API を提供しない。

## Transitional foreign access

通常 runtime の Content / Curation / Summary / RSS 間 foreign table access は ADR-0123 で解消した。

残る明示的な例外は v24 -> v25 migration のみである。

- `BookmarkDatabaseSchema` が legacy `articles.saved_at` を一度だけ読み、Curation-owned `bookmarks` へ ownership transfer する。
- この参照は `foreign-table-access-allowlist.tsv` に ADR-0123 とともに固定する。
- migration 完了後の runtime code は legacy column を参照しない。

allowlist は恒久的な例外集ではない。file/table が消えた entry は stale として verification を失敗させる。

## Persistence change checklist

新しい durable data または schema change では次を確認する。

1. Domain/Context owner はどこか。
2. schema contribution と migration は owner data module にあるか。
3. app-level database version / contribution order の変更が必要か。
4. 他 Context が table を直接参照していないか。
5. cross-context read が必要なら owner Query API で十分か。
6. cross-context write が必要なら owner command port / Application Service を利用しているか。
7. Projection が必要なら目的、参照 table、read-only 制約、integration test が明示されているか。
8. `table-ownership.tsv` または allowlist の更新が必要か。
9. backup/restore compatibility への影響があるか。

## Sources

- [`config/architecture/table-ownership.tsv`](../../config/architecture/table-ownership.tsv)
- [`config/architecture/foreign-table-access-allowlist.tsv`](../../config/architecture/foreign-table-access-allowlist.tsv)
- [ADR-0047](../adr/0047-feature-owned-database-schema-contributions.md)
- [ADR-0098](../adr/0098-unified-user-database.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0123](../adr/0123-content-curation-persistence-phase2.md)
- [ADR-0133](../adr/0133-smb-cover-prefetch-queue.md)
