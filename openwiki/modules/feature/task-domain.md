---
type: module
title: "Task Domain：階層と読み書きの契約"
description: "タスクの階層表示、期限判定、読み取り能力と変更通知の境界。"
tags: [task, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-71ebfde9498f9da3e42b12ac
    resource: repo://feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskChangeNotifyingRepository.kt
  - id: openwiki-source-84efa1dcc13f2eb5b18b8727
    resource: repo://feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskTree.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Task Domain：階層と読み書きの契約

`:feature:task:domain` はタスクの値と読み書きの契約、および階層を表示行へ変換する規則を持つ Kotlin/JVM モジュールである。Android の保存 API や Compose に依存せず、UI と保存実装が同じ期限・完了・親子関係を扱うための入口になる。`TaskReader` は一覧取得だけを提供し、`TaskRepository` は作成・更新・削除・完了変更を加える。Calendar のような参照用途は Reader を受け取り、変更権限を持たない。

## 表示規則と通知

`taskStatus` は完了を期限超過より優先する。`taskTreeRows` はフィルタに合う子の祖先も表示対象へ含め、展開されたノードだけ子を出す。親が見つからないタスクはルートとして扱う。登録順と期日順は兄弟間の比較に使い、期日なしは期日順の後ろへ置く。これは保存データを並べ替える command ではなく、入力一覧から表示行を作る処理である。

`TaskChangeNotifyingRepository` は保存実装を包む decorator で、delegate の command が正常終了した後に変更コールバックを呼ぶ。一覧取得では通知せず、delegate が例外を送出すると通知まで進まない。通知先や widget の Android component はこの層で決めない。階層フィルタや期限判定を変更するときは `TaskTreeTest`、通知順序と失敗時動作は `TaskChangeNotifyingRepositoryTest` を確認する。
## 変更時の調査先

- [TaskRepository.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskRepository.kt)
- [TaskTree.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskTree.kt)
- [TaskChangeNotifyingRepository.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskChangeNotifyingRepository.kt)
- [TaskTreeTest.kt](../../../feature/task/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/task/TaskTreeTest.kt)
- [TaskChangeNotifyingRepositoryTest.kt](../../../feature/task/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/task/TaskChangeNotifyingRepositoryTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [data 層](task-data.md)、[ui 層](task-ui.md)、[システム全体](../../architecture/system.md)。
