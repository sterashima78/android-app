---
type: module
title: "Task Data：タスク保存と親子の完了整合"
description: "共有 database 上の tasks 保存、子孫の完了変更と祖先更新。"
tags: [task, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-7ad68c8d29472e1061248681
    resource: repo://feature/task/data/src/main/kotlin/dev/terashima/yomitorirss/feature/task/data/TaskStore.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Task Data：タスク保存と親子の完了整合

`:feature:task:data` は`TaskRepository` を `DatabaseConnection` 上に実装する Android library である。公開入口の `DefaultTaskRepository` は内部の `TaskStore` に委譲し、feature 所有の schema contribution が `tasks` table を定義する。UI や Calendar から table を直接読ませず、Domain の capability を公開境界とする。database 自体の接続・transaction は core が担う。

## 書き込みの流れ

作成はタイトルと説明を trim し、空タイトルや存在しない親を拒否する。UUID、作成時刻、同じ親の中で次となる並び順を生成して挿入する。親付きの追加は祖先の完了状態も同期する。更新はタイトル・説明・期日を変更し、親の付け替えはこの command に含まれない。

完了変更は対象と全子孫を一つの transaction で同じ完了状態にし、その後に祖先をたどる。祖先は直接の子が全て完了した場合だけ完了になる。削除も元の親を取得したうえで祖先を同期する。これらの整合処理を UI ごとに実装すると、widget や別の command 呼び出しとの不一致が生じるため、保存境界で確認する。入力検査と SQL 例外は Repository を通じて呼び出し側へ返る。

`DefaultTaskRepositoryContractTest` はこの Repository の契約を調べる入口である。親子の完了連動を変更する場合は、単一ノードだけでなく子孫と祖先の両方向を検証し、schema 変更は database 側の migration と所有権も調べる。
## 変更時の調査先

- [DefaultTaskRepository.kt](../../../feature/task/data/src/main/kotlin/dev/terashima/yomitorirss/feature/task/data/DefaultTaskRepository.kt)
- [TaskStore.kt](../../../feature/task/data/src/main/kotlin/dev/terashima/yomitorirss/feature/task/data/TaskStore.kt)
- [TaskDatabaseSchema.kt](../../../feature/task/data/src/main/kotlin/dev/terashima/yomitorirss/feature/task/data/TaskDatabaseSchema.kt)
- [DefaultTaskRepositoryContractTest.kt](../../../feature/task/data/src/test/kotlin/dev/terashima/yomitorirss/feature/task/DefaultTaskRepositoryContractTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](task-domain.md)、[ui 層](task-ui.md)、[システム全体](../../architecture/system.md)。
