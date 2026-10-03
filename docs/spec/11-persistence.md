# 11. 永続化

- durable relational user dataは原則として単一のSQLite database `yomitori-rss.db` に保存する。
- database fileを共有していてもtable ownershipは共有しない。
- 各feature data moduleが自身のschema contributionとmigrationを所有し、`:app` がapplication-level schemaをcompositionする。
- 他Contextのtableへ直接writeしない。cross-context操作はowner API、command port、query APIを利用する。
- 現在のschema versionやtable一覧はコードとmachine-readable manifestを正本とし、この仕様書では固定値を持たない。

<!-- formal-requirement
id: PERSISTENCE-MUTATION-NOTIFICATION-001
models:
  - spec-models/quint/persistence_mutation_notification.qnt
-->
- 通常runtimeのdurable database mutationは `DatabaseConnection.write` / `transaction`、backup対象外のlocal/cache/transient mutationは `localWrite` / `localTransaction` を利用する。
- `localTransaction` 内でdurable `write` / `transaction` が実行された場合は、同じSQLite transactionを共有する別`DatabaseConnection` wrapperからの呼び出しでも、外側transactionをdurable changeへ昇格する。
- `PersistenceChangeNotifier` はoutermost transactionが成功commitし、transaction内で実際にdatabase変更があり、かつscopeがdurableまたは昇格済みの場合だけcommit後に1回通知する。transaction中、rollback、実変更のないdurable scope、durable mutationを含まないlocal-only commitでは通知しない。
<!-- /formal-requirement -->

## 形式モデル

- [Quint: `persistence_mutation_notification.qnt`](../../spec-models/quint/persistence_mutation_notification.qnt) — durable/local mutation、nested durable promotion、commit/rollback/no-opとPersistenceChangeNotifierの通知条件を検査する。

詳細は `docs/architecture/persistence.md` を参照する。
