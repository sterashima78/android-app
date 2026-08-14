# ADR-0047: Feature が database schema と migration を所有する

- Status: Accepted
- Date: 2026-08-14

## Context

ADR-0003 は `:core:database` を横断的な database capability とし、接続、transaction、汎用的な schema migration mechanism は提供できる一方、Bookmark など feature 固有 migration の意味は各 feature の `data` が所有すると定めている。

しかし `YomitoriDatabase` は RSS、Article、Bookmark、Summary、Mail の table 定義と migration をすべて保持していた。この状態では feature 固有の schema 変更が `:core:database` の変更理由となり、`core` がアプリ固有概念を知ることになる。

一方、既存データとの互換性のため、現在の単一 SQLite database と database version、migration の実行順序は維持する必要がある。

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
```

## Migration ordering

既存 database version 12 の互換性を維持する。

従来の `YomitoriDatabase.onUpgrade` は version 10 migration のみ schema 作成前に実行し、その後 schema を補完して version 6、7、8、9、11、12 migration を実行していた。

この順序を保持するため、migration は次の phase を持つ。

- `BEFORE_SCHEMA`: schema 補完より前に必要な migration
- `AFTER_SCHEMA`: schema 補完後に実行する通常の migration

同一 phase 内では target version の昇順で実行する。

現在は RSS の version 10 migration のみ `BEFORE_SCHEMA` とし、Mail の version 6、7、8、9、11 と Summary の version 12 は `AFTER_SCHEMA` とする。

## Version ownership

SQLite の database version は単一DB全体の値であり、特定 feature の所有物ではない。そのため `:app` の schema composition が現在の database version を指定する。

個々の migration の内容と target version は、その migration を必要とする feature の `data` module が所有する。

## Consequences

### Positive

- ADR-0003 の `core` と feature ownership の境界に一致する。
- RSS、Bookmark、Summary、Mail の schema 変更がそれぞれの feature 内で完結する。
- `:core:database` に新しいアプリ固有概念が蓄積しにくくなる。
- Worker や Service は同じ application-level schema composition を利用できる。
- 単一DBと既存 migration の互換性を維持できる。

### Negative

- database 全体の schema を確認するには複数 feature を横断する必要がある。
- 新しい migration では app-level database version の更新と feature contribution の追加を同時に行う必要がある。
- feature 間の外部キー依存があるため、`:app` の contribution 順序には意味がある。

## Relationship to ADR-0003

ADR-0003 の「`:core:database` は汎用的な schema migration mechanism を提供し、feature 固有 migration の意味は feature の `data` が所有する」という決定を具体化する。ADR-0003 を変更または置き換えるものではない。
