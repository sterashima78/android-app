# 11. 永続化

- durable relational user dataは原則として単一のSQLite database `yomitori-rss.db` に保存する。
- database fileを共有していてもtable ownershipは共有しない。
- 各feature data moduleが自身のschema contributionとmigrationを所有し、`:app` がapplication-level schemaをcompositionする。
- 他Contextのtableへ直接writeしない。cross-context操作はowner API、command port、query APIを利用する。
- 現在のschema versionやtable一覧はコードとmachine-readable manifestを正本とし、この仕様書では固定値を持たない。

詳細は `docs/architecture/persistence.md` を参照する。
