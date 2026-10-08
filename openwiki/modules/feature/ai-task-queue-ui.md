---
type: module
title: AI Task Queue UI — 状態一覧と停止・再開操作
description: task polling、表示順序・件数、globalと個別操作の結果表示を説明する。
tags: [ai-task-queue, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-74694aae4e0c397658c6cad0
    resource: repo://feature/ai-task-queue/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskQueueViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# AI Task Queue UI — 状態一覧と停止・再開操作

`:feature:ai-task-queue:ui`

## 一覧と観測寿命

AiTaskQueueRouteは共通Repositoryを画面へ渡し、AiTaskQueueViewModelがtask一覧、件数、local/cloud pause、充電時再開とaction errorをStateFlowで公開する。feature自身の生成結果は表示modelの向こう側に残り、UIはAI推論やDBを直接実行しない。

startObservingはpolling jobの重複を抑止し、初回reloadとqueue kickの後、定期的にRepositoryを読み直す。stopObservingはjobを取り消して画面の監視を終える。監視停止だけではowner taskを止めるcommandを送らない。task一覧の取得後、件数は元のsnapshotから計算し、表示用の一覧ではcompletedを除外する。

## 表示順序と操作結果

並べ替えはrunning、queued、paused、その他の順で、同じstate群ではhigh/normal/low priorityを使う。queuedの件数とfailedの表示行は役割が異なり、失敗行も一覧に残して操作へつなげる。global pauseは表示を更新してRepository commandを呼び、成功・失敗に関係なくreloadで実際の状態へ合わせる。

個別stop/cancel/resumeはBoolean結果を確認する。falseなら状態が変わったため操作できないという通知を出し、例外ならactionErrorに変換する。取得失敗時もloadingを解除してerrorを残す。状態文言や失敗理由の変更はScreen、AiTaskFailureReasonの表示処理とDomain enumを併せて追う。AiTaskQueueViewModelTestはcompleted除外・並び順・取得済み一覧からの件数を、AiTaskFailureReasonTestは失敗表示を検証する。

## 調査・変更の入口

- [AiTaskQueueViewModel.kt](../../../feature/ai-task-queue/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskQueueViewModel.kt)
- [AiTaskQueueScreen.kt](../../../feature/ai-task-queue/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskQueueScreen.kt)
- [AiTaskQueueViewModelTest.kt](../../../feature/ai-task-queue/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskQueueViewModelTest.kt)
- [AiTaskFailureReasonTest.kt](../../../feature/ai-task-queue/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskFailureReasonTest.kt)

関連モジュール: [ai-task-queue-domain](ai-task-queue-domain.md)、[ai-task-queue-data](ai-task-queue-data.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
