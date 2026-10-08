---
type: module
title: "Task UI：階層画面と編集状態"
description: "TaskRoute、ViewModel の再読込、フィルタと展開状態、説明リンク。"
tags: [task, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-eb5ba90134d214718f6cb667
    resource: repo://feature/task/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/task/TaskViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Task UI：階層画面と編集状態

`:feature:task:ui` はTask の Compose 画面、編集操作、説明文のリンク表示を所有する。`TaskRoute` は渡された `TaskViewModel.Factory` から ViewModel を取得し、`TaskScreen` に渡す。保存実装の生成を画面内に持ち込まず、Domain の Repository を注入して利用する。ナビゲーション metadata は feature にあり、アプリのルート接続と組み合わせる。

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
