---
type: module
title: AI Task Queue Domain — 複数機能の統合taskモデル
description: feature固有taskを共通表示へ投影するモデルとglobal・個別操作の契約を説明する。
tags: [ai-task-queue, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-aab7b7b48f9e383310eca1a9
    resource: repo://feature/ai-task-queue/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskQueueRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# AI Task Queue Domain — 複数機能の統合taskモデル

`:feature:ai-task-queue:domain`

## 統合表示の責務

AI Task Queue Domainは、Summary、RSS推薦、Library整理、SMB metadata正規化、Knowledge再構築、Podcast生成を同じ表示語彙で扱うための契約を所有する。統一するのはtaskの種類、状態、priority、進捗と操作可能性であり、各生成物や元の永続taskの正本をこのDomainへ移すものではない。GradleはJVMのみのmoduleで、AndroidやSQL、各featureの実装を公開モデルへ入れない。

## 表示モデルと操作境界

AiTaskQueueItemはID、種類、タイトル、source、状態に加え、進捗段階とcurrent/total、レビュー待ち件数、errorとproviderラベルを保持する。`canStop`、`canCancel`、`canResume`は個々のitemの操作可否をconsumerへ渡す。状態にはqueued/running/paused/completed/failed/stopped/cancelledとunknownがあり、ownerの未知状態を表示境界で表す余地を残す。

Repositoryは一覧とglobal実行状態を読み、local/cloudのpauseとlocalの充電時再開を設定する。個別commandはtask IDでstop/cancel/resumeし、Booleanで変更の成否を返す。taskCountsの既定実装は一覧からrunning、queued、pausedまたはstoppedの件数を数えるため、completedやfailedはそれらの件数へ含めない。

このmoduleはWorkerやtask tableを持たず、Dataのcomposite adapterがownerのDomain contractへ操作を返す。新しいtask種別はモデルだけでなくadapterのID routing、状態変換、操作可否、UI文言を併せて追加する。focusedな検証はDataの各adapter testとUIの件数・並べ替えtestに置かれている。

## 調査・変更の入口

- [AiTaskQueueRepository.kt](../../../feature/ai-task-queue/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/aitaskqueue/AiTaskQueueRepository.kt)

関連モジュール: [ai-task-queue-data](ai-task-queue-data.md)、[ai-task-queue-ui](ai-task-queue-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
