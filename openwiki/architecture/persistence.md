---
type: architecture
title: 永続化とデータ所有権
description: 共有 SQLite と feature-owned schema の組み立て、移行、バックアップ境界を理解する。
tags: [persistence, ownership, backup]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T11:54:35.312Z
sources:
  - id: openwiki-source-5945a98a127e92c77061e074
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt
  - id: openwiki-source-3a2d0957ae9e028aa54a266c
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt
  - id: openwiki-source-2c79af7034d8c32df1bd7f09
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchema.kt
  - id: openwiki-source-2a81d079c8ed447770ff1884
    resource: repo://core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt
  - id: openwiki-source-2fe887ba2ef0d022e4e50589
    resource: repo://core/database/src/test/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchemaTest.kt
  - id: openwiki-source-61810f4c8f4f71b5d7c7b67f
    resource: repo://docs/architecture/persistence.md
  - id: openwiki-source-d1d2160a3e9a979d5488e3ca
    resource: repo://docs/architecture/principles.md
generated: { by: "codex", at: "2026-10-08T11:54:35.312Z" }
---

# 永続化とデータ所有権

Mosaic は物理的には共有 SQLite を利用するが、テーブルの意味と書き込み権限は各 Context が所有する。新しいデータを追加するときは、まず [table ownership manifest](../../config/architecture/table-ownership.tsv) で owner を確認する。全体像は [システム構成](system.md)、設計の正本は [Persistence Architecture](../../docs/architecture/persistence.md) にある。

## スキーマを組み立てる場所

[DatabaseSchema](../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchema.kt) は owner ごとの contribution をまとめる仕組みで、owner の重複と全体 version を越える migration を拒否する。各 feature の Data 層がテーブル作成・移行を提供し、[AppDatabaseSchema](../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt) が単一 DB の version と contribution を組み立てる。

[YomitoriDatabase](../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/YomitoriDatabase.kt) の更新順は、BEFORE_SCHEMA migration、schema 作成、AFTER_SCHEMA migration。各 phase の migration は更新元と更新先の範囲で絞り、target version の順に実行する。[DatabaseSchemaTest](../../core/database/src/test/kotlin/dev/terashima/yomitorirss/core/database/DatabaseSchemaTest.kt) が phase・version の順序と不正な contribution を検証する。

## 他 Context のデータを扱う

Content の記事、RSS の購読、Curation の Bookmark、Summary の要約は同じ DB にあっても別 owner である。foreign table write は設計上禁止され、cross-context read は owner の公開 API・目的を表す query・明示された projection を通す。接続や SQL が使えるという理由で別 feature の永続化を操作しない。

具体例は [コンテンツの取り込みと整理](../workflows/content.md)。テーブルと module の境界を変更するときは [検証の選び方](../testing/contracts.md) と [Principles](../../docs/architecture/principles.md) を確認する。

## durable と local の mutation

通常の durable mutation は [DatabaseConnection](../../core/database/src/main/kotlin/dev/terashima/yomitorirss/core/database/DatabaseConnection.kt) の write / transaction を通し、commit 後に persistence change を通知する。transient queue や device/cache-only state は localWrite / localTransaction の境界を使う。local transaction 内の durable mutation は durable change として扱う。どちらに属するかは保存場所だけで決めず、[Persistence Architecture](../../docs/architecture/persistence.md) の規則を確認する。

## バックアップと復元

snapshot の復元前に、Mosaic の application ID、現在の schema version との一致、SQLite の quick_check を検証する。別アプリ・異なる schema・破損した snapshot をそのまま live DB へ置き換えない。

自動 backup の予約は Backup Context が所有するユーザー設定のローカル時刻に基づく。persistence change 通知を購読する方式ではない。WorkManager の実行は OS・ネットワーク条件で遅延し得る。credential と通常 user data の archive boundary を混ぜない。詳細は [backup spec](../../docs/spec/12-backup-restore.md) と [Persistence Architecture](../../docs/architecture/persistence.md) を参照する。
