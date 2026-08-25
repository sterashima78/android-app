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
- owner data module が lazy/idempotent に schema を確認する必要がある場合、feature の schema contribution と同じ明示的 initializer を呼ぶ。Repository の read method や `snapshot()` の副作用を schema initialization contract にしない。
- 同一 table の `CREATE TABLE` 定義を Repository と schema contribution に複製しない。

現在の互換性 baseline は database version 27 である。version 27 到達のための過去 migration は削除済みで、fresh install は各 feature の `createSchema` を正本とする。今後 schema version を上げる場合は version 27 以降の直前 baseline から必要な migration を owner data module に追加する。

バックアップも現在の application schema と同じ database version の snapshot のみを復元対象とする。schema version が異なる snapshot は復元処理へ進む前に拒否する。今後 database version を上げる際に直前 version のバックアップを維持する場合は、schema migration と restore baseline を同じ変更で更新する。

### Library schema

Library Context の fresh DB schema は `LibraryDatabaseSchema.kt` から到達する initializer 群を正本とする。catalog (`library_items` / `library_sources` / hidden / series)、Web source の URL pattern 別 metadata extractor (`web_library_metadata_extractors`)、SMB server・表紙 queue・書誌正規化、organization/read status を同じ Library-owned schema composition で作成する。

`web_library_metadata_extractors` は Web Library の title / thumbnail 取得方法を端末上で変更する durable user data で、URL pattern、同期 JavaScript function code、更新日時を保存する。function code は repository source や fixture に転記せず通常の database snapshot backup に含め、アクセスは Library-owned `WebLibraryMetadataExtractorRepository` capability を経由する。現行 version 27 baseline では Library の idempotent schema initializer から作成し、この table 追加だけを理由とした database version bump は行わない。

`DefaultLibraryRepository.snapshot()` は Library snapshot を取得する read operation であり、他 Repository / Worker が schema 初期化のために呼び出さない。単体 SMB 書籍が必要な処理は catalog query を直接利用し、必要な schema 初期化も catalog initializer を明示的に呼ぶ。

これにより単体 lookup が全蔵書 snapshot 構築や Kindle title normalization 等の無関係な処理を暗黙に実行することを避ける。

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

`config/architecture/table-ownership.tsv` は durable table ownership の機械可読な完全登録簿である。新しい durable table を追加する場合は owner data module の schema と同じ変更で必ず登録する。

主要な ownership は次のとおり。

| Table group | Owner module |
| --- | --- |
| `articles` | `:feature:article:data` |
| `feeds`, `feed_folders` | `:feature:rss:data` |
| `bookmarks`, tags/folders | `:feature:bookmark:data` |
| `article_summaries`, `summary_*` | `:feature:summary:data` |
| `mail_*` | `:feature:mail:data` |
| `library_*`, `web_library_metadata_extractors`, `hidden_library_items`, `smb_*` | `:feature:library:data` |
| `knowledge_*` | `:feature:knowledge:data` |
| `asset_*` | `:feature:asset:data` |
| `tasks` | `:feature:task:data` |
| `chat_*` | `:feature:chat:data` |
| `channels`, `videos` | `:feature:youtube:data` |

`gradle/table-ownership.gradle.kts` は owner data source 内の `CREATE TABLE IF NOT EXISTS` を抽出し、次を失敗させる。

- `table-ownership.tsv` に未登録の durable table
- table を作成する module と登録 owner の不一致
- owner 以外の feature data からの direct table access（明示された migration allowlist を除く）
- 存在しない/stale な allowlist entry

`table-ownership.tsv` は ownership の完全登録簿だが、column/index/constraint まで含む schema DDL の正本ではない。実際の schema definition は各 feature data module の `DatabaseSchemaContribution` / initializer を参照する。

SMB 表紙先読みキューは Library Context が所有する派生処理状態であり、WorkManager 自身の状態だけに依存せず `smb_cover_prefetch_queue` に待機・実行・失敗・完了・対象外と転送進捗を保持する。schema は現行 `libraryDatabaseSchema` の一部として定義する。

SMB 表紙画像は app cache に置く再生成可能な派生データで、database snapshot backup には画像本体を含めない。復元後は Backup Context が Library-owned `LibraryBackupRestoreInitializer` を呼び、SMB の `file:` scheme の `thumbnail_url` と復元前の `smb_cover_prefetch_queue` を無効化する。Backup Context 自身は Library table を直接 write しない。SMB credential は backup 対象外なので復元直後には自動実行せず、credential 再設定後の通常の Library 経路で未取得表紙を再キューする。

SMB 書誌正規化は Library Context が `smb_metadata_normalization_batches` / `smb_metadata_normalization_items` に解析・レビュー状態を保持し、`smb_metadata_normalization_decisions` にユーザーが反映または却下して確定した判断を保持する。`library_items` は同期キャッシュのままとし、`APPLIED` の確定書誌は Library snapshot で SMB 書籍へ overlay する。これらの schema も現行 `libraryDatabaseSchema` に含める。

## Cross-context query / command patterns

通常は owner が Repository / named Query / command port を公開する。他 Context は table layout ではなく意味のある contract に依存する。

現在の例:

- Content Classification は RSS table を JOIN せず `ContentClassificationSourceQuery` を利用する。
- Content retention は Curation の `BookmarkContentQuery` と Summary protection query を composition root で `ContentRetentionProtectionQuery` へ適合・合成する。
- Bookmark read model は Article metadata を `ArticleRepository.findArticle(s)` から取得する。
- Summary は Article metadata を `ArticleRepository`、Bookmark / Read Later membership を `BookmarkContentQuery` から取得する。
- RSS ingestion は Content table を直接 write せず `ContentSourceGateway` を利用する。
- AI task queue は SMB 書誌正規化 table を直接参照せず、Library-owned `SmbMetadataNormalizationRepository` を通じて task projection と再試行を行う。
- Backup restore は Library の cache invalidation を `LibraryBackupRestoreInitializer` に委譲し、Library-owned table を直接変更しない。

### Named Projection

owner API の合成で実測上の性能問題がある read path に限り read-only Projection を利用できる。Projection は purpose-specific name、read-only、参照 Context/table の明示、integration test を必要とし、command API を提供しない。

## Transitional foreign access

通常 runtime の Content / Curation / Summary / RSS 間 foreign table access は ADR-0123 で解消済みである。ADR-0138 で v24 -> v25 の bookmark ownership transfer migration も互換性 baseline から外れたため、現在の `foreign-table-access-allowlist.tsv` に例外 entry はない。

allowlist は恒久的な例外集ではない。新たな移行で一時的な foreign access が不可避な場合だけ ADR に根拠を記録して追加し、移行 baseline から外れた時点で削除する。file/table が消えた entry は stale として verification を失敗させる。

## Persistence change checklist

新しい durable data または schema change では次を確認する。

1. Domain/Context owner はどこか。
2. schema contribution と migration は owner data module にあるか。
3. fresh DB と lazy initializer が同じ schema definition を利用しているか。
4. app-level database version / contribution order の変更が必要か。
5. 他 Context が table を直接参照していないか。
6. cross-context read が必要なら owner Query API で十分か。
7. cross-context write が必要なら owner command port / Application Service を利用しているか。
8. Projection が必要なら目的、参照 table、read-only 制約、integration test が明示されているか。
9. 新しい durable table を `table-ownership.tsv` に登録したか。
10. 現在の database / backup compatibility baseline をどこまで維持するか。

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
- [ADR-0134](../adr/0134-smb-multimodal-metadata-normalization.md)
- [ADR-0135](../adr/0135-smb-cover-cache-backup-restore.md)
- [ADR-0138](../adr/0138-database-v27-compatibility-baseline.md)
- [ADR-0172](../adr/0172-web-library-custom-metadata-extractors.md)
