---
type: module
title: SQLite 接続・スキーマと永続変更通知
description: 共有 SQLite の接続、機能別スキーマの合成、commit 後通知、整合したスナップショットを提供する。
tags:
  - database
  - persistence
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-3a2d0957ae9e028aa54a266c
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt
  - id: openwiki-source-cf1d990d89cca11e4c36b971
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DataChangeNotifier.kt
  - id: openwiki-source-5dca312bd9642cab0fb705f8
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/PersistenceChangeNotifier.kt
  - id: openwiki-source-2a81d079c8ed447770ff1884
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# SQLite 接続・スキーマと永続変更通知

## 責務と処理の入口

`:core:database` は業務 repository から使う SQLite 基盤で、各機能の table や業務判断はその機能の Data が所有する。[DatabaseSchema](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchema.kt) は owner が重複しない contribution をまとめ、対象 version と BEFORE_SCHEMA / AFTER_SCHEMA で migration を選ぶ。Application が提供する合成済み schema を [YomitoriDatabase](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt) が受け取り、作成時に外部キーと WAL を有効にする。upgrade は schema 前 migration、schema 作成、schema 後 migration の順で進む。

## 主要な構成要素と API

| 宣言・種類 | 責務・主要 API | 根拠 |
| --- | --- | --- |
| `DatabaseMigrationPhase` enum / `DatabaseMigration` class | schema 作成前後の区分、targetVersion と migration callback。初期版以下を拒否する。 | [schema 契約](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchema.kt)。 |
| `DatabaseSchemaContribution` / `DatabaseSchema` class | feature owner の create/migration callback を合成。owner 重複や未来版 migration を拒否する。 | 同上。内部 `migrationsFor` は対象区間・phase で選び version 順にする。 |
| `DatabaseSchemaProvider` interface | Application が `databaseSchema` を供給する契約。 | 同上。 |
| `DatabaseConnection` class | `readable` / `writable` と durable/local の `write` / `transaction`。block の値を返し、失敗時は rollback。 | [接続](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt)。 |
| `DataChangeNotifier` class | `version` StateFlow と `notifyChanged()`。feature の表示更新用 signal と shared instance。 | [表示通知](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DataChangeNotifier.kt)。 |
| `PersistenceChangeNotifier` class | 同形の signal だが durable commit の通知用。バックアップ観測と表示通知を区別する。 | [永続通知](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/PersistenceChangeNotifier.kt)。 |
| `YomitoriDatabase` class | SQLiteOpenHelper。`create(context[, schema])`、`schemaVersion`、`createSnapshot` / `markSnapshot` / `validateSnapshot` / `replaceWithSnapshot`。 | [DB と snapshot](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt)。 |

## 合成と通知の処理フロー

[AppDatabaseSchema](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt) が feature contribution を合成し、[AppContainer](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppContainer.kt) が database 接続と共有 notifier を repository へ渡す。

1. repository が `write` / `transaction` を呼ぶと `DatabaseConnection.transact` が transaction を開始し変更量を測定する。
2. 入れ子は同じ SQLiteDatabase の thread-local scope に参加する。管理外 transaction に durable 書き込みを混ぜると拒否する。
3. 成功かつ変更ありの最外 commit 後だけ `PersistenceChangeNotifier.notifyChanged` を一度呼ぶ。`DataChangeNotifier` の表示更新は repository 等の利用側が必要に応じて通知する。
4. バックアップ側は snapshot 作成後 `markSnapshot` で識別子を付け、復元側は `validateSnapshot` で同版と整合性を確認して置換する。自動的な旧版 snapshot migration は提供しない。

## 書き込み・通知・復元

[DatabaseConnection](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt) の `write` / `transaction` は durable データの変更を transaction に包み、実変更のある成功 commit 後だけ永続変更を通知する。cache / transient 用の `localWrite` / `localTransaction` は通知しないが、内側に durable 書き込みが入ると外側の commit を通知対象へ昇格する。同一 helper を包む別 wrapper 間でも thread-local の scope を共有する。例外時は rollback され、通知は出ない。

snapshot 作成は WAL を checkpoint して主 DB を安定させ、コピーを同期する。復元前には application ID、現在と同じ schema version、quick_check を確認する。新 DB を staging して旧 DB を退避し、配置・検証の失敗時は旧 DB へ戻す。table の追加は core に業務 schema を置かず、feature contribution と app の合成を変更する。

## 変更時の確認

[DatabaseConnectionTest](../../../core/database/src/test/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnectionTest.kt) は no-op、rollback、commit 単位の一度の通知、local 内の durable 書き込みと別 wrapper を検証する。migration の順序・owner 制約は DatabaseSchemaTest、snapshot の互換性は app の DatabaseSnapshotBackupTest を入口にする。[合成](../application/composition.md) と [全体構成](../../architecture/system.md) から利用側を追える。

仕様・設計の正本: [architecture/persistence.md](../../../docs/architecture/persistence.md)、[spec/11-persistence.md](../../../docs/spec/11-persistence.md)。
