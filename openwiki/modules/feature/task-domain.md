---
type: module
title: Task Domain：階層と読み書きの契約
description: タスクの階層表示、期限判定、読み取り能力と変更通知の境界。
tags:
  - task
  - domain
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-71ebfde9498f9da3e42b12ac
    resource: repo://feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskChangeNotifyingRepository.kt
  - id: openwiki-source-84efa1dcc13f2eb5b18b8727
    resource: repo://feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskTree.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Task Domain：階層と読み書きの契約

`:feature:task:domain` はタスクの値と読み書きの契約、および階層を表示行へ変換する規則を持つ Kotlin/JVM モジュールである。Android の保存 API や Compose に依存せず、UI と保存実装が同じ期限・完了・親子関係を扱うための入口になる。`TaskReader` は一覧取得だけを提供し、`TaskRepository` は作成・更新・削除・完了変更を加える。Calendar のような参照用途は Reader を受け取り、変更権限を持たない。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| TaskItem（data class） | 親 ID、期日、完了時刻、作成時刻と兄弟の sortOrder を保持する。completed は completedAt != null の派生値。 | [TaskItem.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskItem.kt) |
| TaskReader / TaskRepository（interface） | listTasks の参照契約と createTask / updateTask / deleteTask / setCompleted の変更契約を分離する。 | [TaskRepository.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskRepository.kt) |
| TaskRepositoryProvider（interface） | framework が生成する integration が application の taskRepository を得る接点。 | [TaskRepositoryProvider.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskRepositoryProvider.kt) |
| TaskFilter / TaskSort / TaskStatus（enum）、TaskTreeRow（data class） | 表示対象、兄弟順、完了・期限状態と depth / hasChildren を持つ行モデル。 | [TaskTree.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskTree.kt) |
| taskStatus / taskCount / taskTreeRows（関数） | today と一覧から状態・件数・階層行を計算する。taskCount は一致項目を数え、祖先の補完行は加算しない。 | [TaskTree.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskTree.kt) |
| TaskChangeNotifyingRepository（class） | Repository decorator。成功 command の後だけ onChanged を呼ぶ。 | [TaskChangeNotifyingRepository.kt](../../../feature/task/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskChangeNotifyingRepository.kt) |

## 主要 API と接続

`createTask` は親 ID と nullable な期日を入力し、Domain の返値は Unit。Data の `DefaultTaskRepository` が `TaskStore` に保存を委譲する。`updateTask` は既存 ID のタイトル・説明・期日だけを変更し、親移動を公開しない。`TaskRepositoryProvider` は [アプリ入口](../application/app.md) の framework 接続用に使う。

## 代表的な処理フロー

`TaskScreen` → `taskTreeRows` → filter 一致 ID と祖先 ID の集合 → 兄弟ごとの comparator → ルートから展開ノードの子を再帰表示する。孤立した親参照は root として扱い、visited / seen 集合で同じ ID の再訪を抑える。画面の変更 command には composition が通知 decorator を付け、Widget 更新を接続する。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

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
