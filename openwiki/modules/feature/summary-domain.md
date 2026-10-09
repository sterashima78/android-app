---
type: module
title: Summary Domain — 要約要求・読取と補完の契約
description: 要約結果の分類、ブックマーク補完と再生成、記事単位タスクの公開境界を説明する。
tags:
  - summary
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-7ea0f2324b1032ea70fc11d9
    resource: repo://feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/BookmarkAutoEnrichmentUseCase.kt
  - id: openwiki-source-f877326d95cf39e9631e9798
    resource: repo://feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryRepository.kt
  - id: openwiki-source-b7644b5826c066d69d3bad5d
    resource: repo://feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryTaskQueueRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Summary Domain — 要約要求・読取と補完の契約

`:feature:summary:domain`

Summary Domainは要約の生成要求・保存済み読取・Bookmark補完・実行設定を公開するJVM moduleである。taskとpromptの契約を所有し、具体的DBと推論providerをDataへ委ねる。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [BookmarkAutoEnrichmentUseCase](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/BookmarkAutoEnrichmentUseCase.kt) | `BookmarkAutoEnrichmentUseCase`（class）はarticle identityから自動補完対象を判定して要求する。`BackfillBookmarkAutoEnrichmentUseCase`/`ReprocessBookmarkAutoEnrichmentUseCase`（class）は保存済みBookmarkを対象にmissing追加/明示refreshを分担する。`shouldRequestBookmarkEnrichment`（トップレベル関数）はContentType、動画URL、Reddit source/URLを検査して自動補完の対象を決める。 |
| [SummaryCloudFailure](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryCloudFailure.kt) | `SummaryCloudFailureKind`（enum）が認証・rate limit・transient・reject・unknownを区別し、`SummaryCloudInferenceException`（class）がkindとretryableをData Workerへ渡す。 |
| [SummaryCloudInference](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryCloudInference.kt) | `SummaryCloudInference`（interface）はavailable/model ID読取、`generate(prompt)`、`generateFromUrl(url,prompt)`を公開する。`SummaryCloudGenerationResult`（data class）は実際に使ったmodel IDと生成textを返す。 |
| [SummaryExecutionSettings](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryExecutionSettings.kt) | `SummaryExecutionProvider`（enum）はLOCAL/CHATGPTを区別する。`SummaryExecutionSettings`（interface）はprovider StateFlow、現在値、`setProvider`の設定境界。 |
| [SummaryPrompt](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryPrompt.kt) | 既定promptとplaceholderを公開し、`normalizeSummaryPrompt`は空入力を拒否する。`renderSummaryPrompt`は本文を挿入し、`summaryCacheKey`はmodel・正規化prompt・variantの識別を作るトップレベル関数。 |
| [SummaryPromptSettings](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryPromptSettings.kt) | `SummaryPromptSettings`（interface）はprompt StateFlow、`update`/`reset`を公開し、保存方式をDataへ委ねる。 |
| [SummaryRepository](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryRepository.kt) | `SummaryRequestResult`（sealed interface）のCached/Processing/PreviousFailure/Enqueuedが要求の結果を区別する。`SummaryRequester`、`SummaryReader`、`BookmarkEnrichmentRequester`/`BookmarkEnrichmentRefreshRequester`/`BookmarkEnrichmentBatchRequester`（interface）が通常生成、保存済み読取、保存時補完、明示再生成、一括投入を分け、`SummaryRepository`（interface）がこれらをまとめる。 |
| [SummaryRuntimeDependencies](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryRuntimeDependencies.kt) | `SummaryRuntimeDependencies`（data class）はArticleRepository、BookmarkContentQueryとBookmarkEnrichmentRepositoryをWorkerへ渡す。記事とCurationの所有者を具体Data型で迂回しないための依存束。 |
| [SummaryTaskQueueRepository](../../../feature/summary/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/SummaryTaskQueueRepository.kt) | `SummaryQueueTaskState`/`SummaryQueueTaskPriority`/`SummaryQueueTaskProgressStage`（enum）と`SummaryQueueTask`/`SummaryQueueTaskCounts`/`SummaryQueueExecutionState`（data class）が実行状態を公開する。`SummaryTaskQueueRepository`（interface）は一覧/count、executionState/kick、local/cloud pause、充電再開、stop/cancel/resumeと失敗Bookmark task再試行を提供する。 |

## 公開APIと構成要素間の接続

補完APIは`requestBookmarkEnrichment`（保存時）、`requestBookmarkEnrichmentRefresh`（1件の明示再生成）、`enqueueMissingBookmarkEnrichment`（missing一括）、`enqueueBookmarkEnrichmentRefresh`（一括再生成）に分かれる。`SummaryReader.findSummary`は未保存ならnull。設定契約は`SummaryExecutionSettings.currentProvider`/`setProvider`と`SummaryPromptSettings.update`/`reset`を公開し、Cloud契約は`isAvailable`/`selectedModelId`/`generate`/`generateFromUrl`で利用可否と実行を分ける。

`SummaryRequester.request(articleId,forceRefresh)`は記事identityと再生成指定を受け、完成textまたは背景処理状態を返す。保存時補完と明示refreshは別契約であり、batch APIは受理した件数を返す。自動補完UseCaseは記事が存在しない場合に何も要求せず、対象predicateを通った記事だけを投入する。

`SummaryTaskQueueRepository.stop`/`cancel`/`resume`は記事単位のcommandで結果Booleanを返す。global pauseはlocal/cloudを別々に設定し、`setResumeLocalWhenCharging`はlocalの充電再開を予約する。task状態の更新に関するSQL、WorkManager unique-work policy、provider retryの具体規則はDataを調べる。

[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt)が`SummaryExecutionPreferences`、`SummaryPromptStore`、`ChatGptSummaryCloudInference`を各Domain契約に接続し、`DefaultSummaryRepository`へdatabase・local model reader・execution settings・cloud inferenceを渡す。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`summaryViewModelFactory`がこのRepositoryをUIへ注入する。Workerは`SummaryWorkerFactory`から`SummaryRuntimeDependencies`のContent/Curation契約を受け取る。

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
