---
type: module
title: AI Task Queue Data — owner taskの合成と操作委譲
description: 各featureのtask adapter、global pauseの連携と失敗時の補償を説明する。
tags: [ai-task-queue, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-c746d52fb1f1ed0c36928b74
    resource: repo://feature/ai-task-queue/data/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/data/CompositeAiTaskQueueRepository.kt
  - id: openwiki-source-23c0193f126e4117e5e05487
    resource: repo://feature/ai-task-queue/data/src/test/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/data/CompositeAiTaskQueueRepositoryTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# AI Task Queue Data — owner taskの合成と操作委譲

`:feature:ai-task-queue:data`

## 合成の入口

CompositeAiTaskQueueRepositoryはSummaryとLibraryを基本に、Knowledge、SMB metadata、Podcast、RSS推薦の任意adapterを組み合わせる。各adapterはownerのDomain契約を受け、taskを共通AiTaskQueueItemへ変換する。GradleはJVMでDomain群に依存し、SQLiteやWorkManagerの具象実装には依存しない。統合用の新しいtask tableを作る責務ではなく、永続状態は各ownerへ残る。

listTasksはSummaryから取得したglobal local/cloud pauseをLibrary、SMB、RSSなどの投影に渡し、各task listを連結する。global実行状態もSummaryの公開queue contractを共通モデルへ変換する。個別操作はadapterのtask ID routingを通してstop/cancel/resumeへ返すので、IDのprefixや操作可否を変更する場合は対応adapterを調べる。

## global操作の協調

local pauseはSummaryの設定を先に更新し、Library、SMB、local providerのRSS・Knowledgeにそれぞれのpause処理を要求する。連携が途中で失敗した場合はSummary設定や処理対象の状態を戻す補償を試みてから例外を返す。resumeや充電再開の設定にも同様のowner連携がある。cloud pauseはcloud対象へ限定し、local Library処理を停止させない。個別に止めたLibraryのbatchをglobal解除で勝手にresumeする扱いにはしない。

同じ「統合一覧」でもownerごとに停止・cancel・再開の能力が異なるので、compositeから新しいWorkerを直接起動せずadapterのcapabilityを使う。CompositeAiTaskQueueRepositoryTestは混在一覧、local/cloud停止の分離、個別Library停止の保持と一冊だけの再試行を検証する。owner追加時は各adapter testも確認する。

## 調査・変更の入口

- [CompositeAiTaskQueueRepository.kt](../../../feature/ai-task-queue/data/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/data/CompositeAiTaskQueueRepository.kt)
- [SummaryTaskQueueAdapter.kt](../../../feature/ai-task-queue/data/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/data/SummaryTaskQueueAdapter.kt)
- [KnowledgeTaskQueueAdapter.kt](../../../feature/ai-task-queue/data/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/data/KnowledgeTaskQueueAdapter.kt)
- [CompositeAiTaskQueueRepositoryTest.kt](../../../feature/ai-task-queue/data/src/test/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/data/CompositeAiTaskQueueRepositoryTest.kt)

関連モジュール: [ai-task-queue-domain](ai-task-queue-domain.md)、[ai-task-queue-ui](ai-task-queue-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
