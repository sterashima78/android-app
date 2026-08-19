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
  -> owner Domain API / named Query API

Named Projection
  -> 明示された foreign table の SELECT のみ

Foreign table write
  -> 禁止
```

foreign key の存在、同一 transaction の利用、同一 SQLite file の利用は、別 Context の table を自由に読み書きする理由にはならない。

複数 Aggregate を常に単一 transaction で変更しなければ invariant を維持できない場合、foreign write の例外を追加するのではなく Aggregate / Context boundary を再検討する。

## Machine-readable table ownership

`config/architecture/table-ownership.tsv` は `gradle/table-ownership.gradle.kts` が検査する durable table ownership の機械可読な正本である。

現在 manifest に登録されている ownership は次のとおり。

| Table | Owner module |
| --- | --- |
| `articles` | `:feature:article:data` |
| `feeds` | `:feature:rss:data` |
| `feed_folders` | `:feature:rss:data` |
| `tags` | `:feature:bookmark:data` |
| `article_tags` | `:feature:bookmark:data` |
| `bookmark_folders` | `:feature:bookmark:data` |
| `article_folders` | `:feature:bookmark:data` |
| `article_summaries` | `:feature:summary:data` |
| `summary_tasks` | `:feature:summary:data` |
| `summary_article_content` | `:feature:summary:data` |

この表を手作業の完全な schema catalog として扱わない。正確な検査対象は [`config/architecture/table-ownership.tsv`](../../config/architecture/table-ownership.tsv)、実際の schema definition は各 feature data module の `DatabaseSchemaContribution` を参照する。

ADR-0047 が記録する broader ownership には Mail、Library、Knowledge、Asset、Task、Chat、YouTube 等の durable data も含まれる。table ownership verification の対象を拡張するときは manifest と fixture を同じ変更で更新する。

## Cross-context query patterns

### Owner API

通常は owner が Repository / Query port を公開する。他 Context は table layout ではなく意味のある contract に依存する。

例:

- Content Classification は RSS table を JOIN せず `ContentClassificationSourceQuery` を利用する。
- Content retention は Summary table を参照せず `ContentRetentionProtectionQuery` を利用する。
- Summary が Curation state を変更する場合は Curation command を利用する。

### Named Projection

owner API の合成で実測上の性能問題がある read path に限り read-only Projection を利用できる。

Projection は次を満たす。

- purpose-specific name を持つ
- read-only
- 参照 Context / table を明示する
- generic `shared` / `cross-feature` module に置かない
- command API を提供しない
- schema change を検出できる integration test を持つ

## Transitional foreign access

既知の移行負債は [`config/architecture/foreign-table-access-allowlist.tsv`](../../config/architecture/foreign-table-access-allowlist.tsv) に repository path、table、ADR に基づく理由を明示する。

現時点では主に次が残る。

- Bookmark read/enrichment から Content / RSS persistence への transitional read
- RSS ingestion から Content persistence への transitional write path
- Summary queue/read model から Content metadata への transitional read
- Summary priority から Curation Read Later membership への transitional read

allowlist は恒久的な例外集ではない。file/table が消えた entry は stale として verification を失敗させ、follow-up 完了時に削除する。

## Persistence change checklist

新しい durable data または schema change では次を確認する。

1. Domain/Context owner はどこか。
2. schema contribution と migration は owner data module にあるか。
3. app-level database version / contribution order の変更が必要か。
4. 他 Context が table を直接参照していないか。
5. cross-context read が必要なら owner Query API で十分か。
6. Projection が必要なら目的、参照 table、read-only 制約、integration test が明示されているか。
7. `table-ownership.tsv` または allowlist の更新が必要か。
8. backup/restore compatibility への影響があるか。

## Sources

- [`config/architecture/table-ownership.tsv`](../../config/architecture/table-ownership.tsv)
- [`config/architecture/foreign-table-access-allowlist.tsv`](../../config/architecture/foreign-table-access-allowlist.tsv)
- [ADR-0047](../adr/0047-feature-owned-database-schema-contributions.md)
- [ADR-0098](../adr/0098-unified-user-database.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0117](../adr/0117-cross-context-persistence-boundary-phase1.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
