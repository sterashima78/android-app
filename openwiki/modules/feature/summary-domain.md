---
type: module
title: Summary Domain — 要約要求・読取と補完の契約
description: 要約結果の分類、ブックマーク補完と再生成、記事単位タスクの公開境界を説明する。
tags: [summary, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-f877326d95cf39e9631e9798
    resource: repo://feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryRepository.kt
  - id: openwiki-source-b7644b5826c066d69d3bad5d
    resource: repo://feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryTaskQueueRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Summary Domain — 要約要求・読取と補完の契約

`:feature:summary:domain`

## 要約の意味とconsumer

Summary Domainは、保存済み要約の読取と非同期生成の要求を別のcapabilityとして公開する。SummaryReaderはarticleIdから保存済み文字列だけを返し、ChatやKnowledgeなどのconsumerへ生成処理を要求しない。SummaryRequesterは生成要求に対しCached、Processing、PreviousFailure、Enqueuedを返す。enqueue受理と生成完了は別で、accepted=falseの要求を新たな結果と扱わない。

## ブックマーク補完と実行境界

BookmarkEnrichmentRequesterは保存を起点とした要約とAIタグ準備を要求し、保存済み要約をmetadata生成へ再利用する。明示的refreshは要約を再生成し、metadata生成成功後に既存タグを置換する契約である。一括操作も同じSummary-owned queueへ投入し、queued/runningの同一記事を重複追加しない方針を公開する。

SummaryTaskQueueRepositoryは記事単位の状態、進捗段階、priorityと実行providerを返し、ローカル・クラウドのpause、充電時再開、停止・cancel・resumeを扱う。具体的なtask tableとWorkerはDataに置く。DomainはArticle/Bookmark/RedditのDomain境界を使い、AndroidやSQL実装へ依存しない。

promptの正規化・本文挿入・cache key構築もこのmoduleにあり、モデルや設定変更時の生成結果識別を支える。SummaryPromptTestはplaceholder有無、空prompt拒否、promptやthinking modeの変更がcache keyへ反映されることを検証する。補完の連携はBookmarkAutoEnrichmentUseCaseTest、タスクモデルはSummaryQueueTaskTestを参照し、durable遷移は仕様のQuint/Alloyへ降りて確認する。

## 調査・変更の入口

- [SummaryRepository.kt](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryRepository.kt)
- [SummaryTaskQueueRepository.kt](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryTaskQueueRepository.kt)
- [SummaryPrompt.kt](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryPrompt.kt)
- [SummaryPromptTest.kt](../../../feature/summary/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryPromptTest.kt)

関連モジュール: [summary-data](summary-data.md)、[summary-ui](summary-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
