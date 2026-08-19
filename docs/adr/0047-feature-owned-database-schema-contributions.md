# ADR-0047: Feature が database schema と migration を所有する

- Status: Accepted
- Date: 2026-08-14
- Amended by: ADR-0059, ADR-0098, ADR-0106

## Context

ADR-0003 は `:core:database` を横断的な database capability とし、接続、transaction、汎用的な schema migration mechanism は提供できる一方、Bookmark など feature 固有 migration の意味は各 feature の `data` が所有すると定めている。

しかし `YomitoriDatabase` は RSS、Article、Bookmark、Summary、Mail の table 定義と migration をすべて保持していた。この状態では feature 固有の schema 変更が `:core:database` の変更理由となり、`core` がアプリ固有概念を知ることになる。

本 ADR 採用時は、当時の既存データとの互換性のため、単一 SQLite database と database version、migration の実行順序を維持する必要があった。

## Decision

単一の `yomitori-rss.db` は維持しつつ、schema と migration の ownership を feature の `data` module へ分離する。

- `:core:database` は `DatabaseSchema`、`DatabaseSchemaContribution`、`DatabaseMigration` と migration runner を提供する。
- 各 feature の `data` module は、自身が所有する table/index の作成と migration を `DatabaseSchemaContribution` として公開する。
- `:app` は composition root として contribution を集約し、database version と contribution の作成順序を定義する。
- `:core:database` は feature module に依存しない。
- `YomitoriDatabase.create(context)` は `Application` が提供する `DatabaseSchema` を利用する。Worker や Service が個別の schema 集約を持たないようにする。

現時点の ownership は次とする。

```text
:feature:rss:data
  feed_folders
  feeds

:feature:article:data
  articles

:feature:bookmark:data
  tags
  article_tags
  bookmark_folders
  article_folders

:feature:summary:data
  article_summaries
  summary_tasks

:feature:mail:data
  mail_accounts
  mail_labels
  mail_threads
  mail_messages

:feature:library:data
  smb_library_servers
  library_organization_*
  library_item_*

:feature:knowledge:data
  knowledge_pages
  knowledge_page_sources

:feature:asset:data
  asset_entries
  asset_categories
  asset_category_definitions

:feature:task:data
  tasks

:feature:chat:data
  chat_sessions
  chat_messages

:feature:youtube:data
  channels
  videos
```

## Migration ordering

migration mechanism は `BEFORE_SCHEMA` と `AFTER_SCHEMA` の phase を持ち、同一 phase 内では target version の昇順で実行する。

`BEFORE_SCHEMA` は、既存 table へ列を追加してから `CREATE INDEX IF NOT EXISTS` などの schema 補完を実行する必要がある場合に利用できる。通常の migration は `AFTER_SCHEMA` を使う。

本 ADR 採用時に存在した version 6〜12 の migration は、ADR-0059 により現行 version 12 を更新元ベースラインとしたため削除した。phase と migration runner は今後の version 13 以降の schema 変更に利用できる汎用機構として維持する。

## Version ownership

SQLite の database version は単一DB全体の値であり、特定 feature の所有物ではない。そのため `:app` の schema composition が現在の database version を指定する。

個々の migration の内容と target version は、その migration を必要とする feature の `data` module が所有する。

## Consequences

### Positive

- ADR-0003 の `core` と feature ownership の境界に一致する。
- 各 feature の schema 変更がそれぞれの feature 内で完結する。
- `:core:database` に新しいアプリ固有概念が蓄積しにくくなる。
- Worker や Service は同じ application-level schema composition を利用できる。
- 過去 migration を削除しても、今後の schema migration の ownership と実行機構を維持できる。
- ADR-0098 により Task / Chat / YouTube も同じ physical database と schema contribution mechanism に統一された。

### Negative

- database 全体の schema を確認するには複数 feature を横断する必要がある。
- 新しい migration では app-level database version の更新と feature contribution の追加を同時に行う必要がある。
- feature 間の外部キー依存があるため、`:app` の contribution 順序には意味がある。

## Relationship to ADR-0003, ADR-0059 and ADR-0098

ADR-0003 の「`:core:database` は汎用的な schema migration mechanism を提供し、feature 固有 migration の意味は feature の `data` が所有する」という決定を具体化する。ADR-0003 を変更または置き換えるものではない。

ADR-0059 はサポートする更新元のベースラインを version 12 へ進め、本 ADR に記録されていた過去 migration を廃止する。本 ADR の schema/migration ownership の決定は維持する。

ADR-0098 は Task / Chat / YouTube に残っていた独立 SQLite database を `yomitori-rss.db` へ統合し、本 ADR の単一 database 方針を全 durable user data に適用する。

ADR-0106 は本 ADR の schema/migration ownership を通常の persistence access ownership まで拡張し、他 Context 所有 table への直接 write を禁止する。単一 database 方針は維持する。
