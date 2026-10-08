---
type: module
title: SQLite 接続・スキーマと永続変更通知
description: 共有 SQLite の接続、機能別スキーマの合成、commit 後通知、整合したスナップショットを提供する。
tags: [database, persistence, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3a2d0957ae9e028aa54a266c
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt
  - id: openwiki-source-2a81d079c8ed447770ff1884
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# SQLite 接続・スキーマと永続変更通知

## 責務と処理の入口

`:core:database` は業務 repository から使う SQLite 基盤で、各機能の table や業務判断はその機能の Data が所有する。[DatabaseSchema](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchema.kt) は owner が重複しない contribution をまとめ、対象 version と BEFORE_SCHEMA / AFTER_SCHEMA で migration を選ぶ。Application が提供する合成済み schema を [YomitoriDatabase](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt) が受け取り、作成時に外部キーと WAL を有効にする。upgrade は schema 前 migration、schema 作成、schema 後 migration の順で進む。

## 書き込み・通知・復元

[DatabaseConnection](../../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt) の `write` / `transaction` は durable データの変更を transaction に包み、実変更のある成功 commit 後だけ永続変更を通知する。cache / transient 用の `localWrite` / `localTransaction` は通知しないが、内側に durable 書き込みが入ると外側の commit を通知対象へ昇格する。同一 helper を包む別 wrapper 間でも thread-local の scope を共有する。例外時は rollback され、通知は出ない。

snapshot 作成は WAL を checkpoint して主 DB を安定させ、コピーを同期する。復元前には application ID、現在と同じ schema version、quick_check を確認する。新 DB を staging して旧 DB を退避し、配置・検証の失敗時は旧 DB へ戻す。table の追加は core に業務 schema を置かず、feature contribution と app の合成を変更する。

## 変更時の確認

[DatabaseConnectionTest](../../../core/database/src/test/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnectionTest.kt) は no-op、rollback、commit 単位の一度の通知、local 内の durable 書き込みと別 wrapper を検証する。migration の順序・owner 制約は DatabaseSchemaTest、snapshot の互換性は app の DatabaseSnapshotBackupTest を入口にする。[合成](../application/composition.md) と [全体構成](../../architecture/system.md) から利用側を追える。

仕様・設計の正本: [architecture/persistence.md](../../../docs/architecture/persistence.md)、[spec/11-persistence.md](../../../docs/spec/11-persistence.md)。
