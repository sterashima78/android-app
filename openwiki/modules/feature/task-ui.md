---
type: module
title: Task UI：階層画面と編集状態
description: TaskRoute、ViewModel の再読込、フィルタと展開状態、説明リンク。
tags:
  - task
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-23f8ab301b52652e019befaa
    resource: repo://feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskDescriptionLinks.kt
  - id: openwiki-source-eb5ba90134d214718f6cb667
    resource: repo://feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Task UI：階層画面と編集状態

`:feature:task:ui` はTask の Compose 画面、編集操作、説明文のリンク表示を所有する。`TaskRoute` は渡された `TaskViewModel.Factory` から ViewModel を取得し、`TaskScreen` に渡す。保存実装の生成を画面内に持ち込まず、Domain の Repository を注入して利用する。ナビゲーション metadata は feature にあり、アプリのルート接続と組み合わせる。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| TaskRoute（Composable 関数） | TaskViewModel.Factory から ViewModel を取得し TaskScreen に渡す。 | [TaskRoute.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskRoute.kt) |
| TaskEditorRequest（data class）、TaskScreen（Composable） | 新規・子追加・編集の文脈を分け、表示行、フィルタ、並び順、削除確認を表示する。 | [TaskScreen.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskScreen.kt) |
| TaskUiState / TaskViewModel / Factory（data class・class） | 一覧、filter / sort、展開 ID、初期化・エラーを所有する。selectFilter / selectSort / toggleExpanded は表示状態だけを更新する。 | [TaskViewModel.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskViewModel.kt) |
| TaskRow / TaskEditorDialog（private Composable） | 階層行の完了・長押し操作とタイトル・説明・nullable 期日入力。日付 picker は UTC で暦日を変換する。 | [TaskScreen.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskScreen.kt) |
| TaskDescriptionUrl / findTaskDescriptionUrls / taskDescriptionAnnotatedString / TaskDescriptionText（internal 型・関数） | HTTP/HTTPS の範囲を抽出し、日本語境界・末尾句読点を除いて Compose LinkAnnotation にする。 | [TaskDescriptionLinks.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskDescriptionLinks.kt) |
| TASKS_ROUTE / TASKS_TITLE（定数） | アプリの navigation metadata。 | [NavigationDestination.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/NavigationDestination.kt) |

## 主要 API と接続

`createTask / updateTask / deleteTask / setCompleted` は suspend な Repository command を private `mutate` に渡す。`reload` / `loadTasks` が一覧を更新する。Factory は `AppSupportingRouteDependencies` で `TaskChangeNotifyingRepository` を受け、command 成功後の `TaskWidgetUpdater.updateAll` を接続する。Widget 更新失敗は composition の runCatching に収まり、保存失敗と同じ扱いにはならない。

## 代表的な処理フロー

`TaskScreen` が `TaskEditorRequest` を作る → `TaskEditorDialog` が保存 callback を返す → `TaskViewModel.createTask` または `updateTask` → `mutate` → Domain Repository → 成功後 `loadTasks` → `TaskUiState` → Domain の `taskTreeRows` で画面行を再投影する。説明リンクは表示処理であり保存文字列は変更しない。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

## 一覧更新と一時状態

ViewModel の初期化で一覧を読み込む。フィルタ、並び順、展開 ID、エラーは `TaskUiState` に保持する。正常読込時に initialized が false なら全タスクを展開対象にし、true なら既存の展開 ID と現在の一覧 ID の共通部分を残す。失敗時も initialized を true にするため、初回取得が失敗した後の最初の成功では全展開にならず、既存の展開状態を引き継ぐ。フィルタや並び順の変更は表示状態だけを更新する。階層の行生成と期限判定は Domain を使い、UI が独自の別ルールを持たない。

作成・更新・削除・完了変更は IO coroutine で Repository を呼び、成功したら一覧を再読込する。失敗は `error` に変換し、読み込み済みのタスクを無条件で空にしない。画面の寿命は ViewModel scope に従い、これは durable な background queue ではない。説明中の HTTP/HTTPS URL は `TaskDescriptionLinks` が表示用のリンクへ変換する。

変更時は `TaskUiStateTest` で状態の既定値、`TaskDescriptionLinksTest` で日本語文や句読点を含む URL 境界を確認する。保存の親子整合は Data、階層フィルタは Domain のテストを追う。
## 変更時の調査先

- [TaskRoute.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskRoute.kt)
- [TaskViewModel.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskViewModel.kt)
- [TaskDescriptionLinks.kt](../../../feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskDescriptionLinks.kt)
- [TaskUiStateTest.kt](../../../feature/task/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/task/TaskUiStateTest.kt)
- [TaskDescriptionLinksTest.kt](../../../feature/task/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/task/TaskDescriptionLinksTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](task-domain.md)、[data 層](task-data.md)、[システム全体](../../architecture/system.md)。
