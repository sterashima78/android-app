---
type: module
title: Podcast Data：保存・Worker・AI境界
description: Podcastの永続状態とWorker、RSS境界、AI生成実装を説明する。
tags:
  - podcast
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-5945a98a127e92c77061e074
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt
  - id: openwiki-source-92f7a8f73f969b28f95b61c1
    resource: repo://feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastDatabaseSchema.kt
  - id: openwiki-source-7d324167f63ff25c4618b848
    resource: repo://feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorker.kt
  - id: openwiki-source-14f9fe925e8af6cc70cef342
    resource: repo://feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastRepositoryPersistenceTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Podcast Data：保存・Worker・AI境界

`:feature:podcast:data` は Podcast 所有のdurableモデルをSQLiteへ保存し、RSS readerとAI inferenceの公開能力を生成UseCaseへ接続するAndroid実装です。WorkerとWorkManager controllersは画面寿命から独立した生成・予定管理を所有し、compositionがUI factoryとworker factoryへ配線します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `DefaultPodcastNewsCategorizer`（class）、`parsePodcastCategoryToolCall`（関数）、`buildPodcastCategorizationToolPrompt`（関数） | feed category 不足を推論で補う分類 adapter。推論結果の cardinality と値を検証する。 主なメソッド: `categorize`、`newsLine`。 | [DefaultPodcastNewsCategorizer.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/DefaultPodcastNewsCategorizer.kt) |
| `DefaultPodcastNewsClusterer`（class）、`parsePodcastClusterToolCall`（関数）、`buildPodcastClusteringToolPrompt`（関数） | ニュースの候補 index 群を推論して検証し、不正/失敗時は単記事群へ fallback する。 主なメソッド: `cluster`、`compactLine`。 | [DefaultPodcastNewsClusterer.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/DefaultPodcastNewsClusterer.kt) |
| `DefaultPodcastNewsExcluder`（class）、`parsePodcastExclusionToolCall`（関数）、`buildPodcastExclusionToolPrompt`（関数） | 除外プロンプトによる採否を推論し、候補一覧に対応する結果を検証する。 主なメソッド: `filter`。 | [DefaultPodcastNewsExcluder.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/DefaultPodcastNewsExcluder.kt) |
| `DefaultPodcastScriptGenerator`（class）、`limitPodcastPrompt`（関数） | LOCAL/CLOUD の provider に応じて既存 inference 能力へ原稿生成を委譲する。 主なメソッド: `generate`。 | [DefaultPodcastScriptGenerator.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/DefaultPodcastScriptGenerator.kt) |
| `PodcastCloudTextInference`（interface）、`DefaultPodcastCloudTextInference`（class）、`retryPodcastCloudInference`（関数）、`defaultPodcastRetryJitterMillis`（関数）、`podcastCloudFailureMessage`（関数）、`PODCAST_CLOUD_RETRY_DELAYS_MILLIS`（値） | 選択 cloud model の取得と原稿推論、応答不成立時の再試行を包む adapter。 主なメソッド: `selectedModel`、`generate`。 | [PodcastCloudTextInference.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastCloudTextInference.kt) |
| `podcastDatabaseSchema`（値） | DatabaseSchemaContribution として Podcast 所有表・index・migration を登録する。 | [PodcastDatabaseSchema.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastDatabaseSchema.kt) |
| `PodcastGenerationWorker`（class）、`PodcastGenerationOperation`（enum class）、`podcastGenerationOperation`（関数）、`shouldUsePodcastGenerationForeground`（関数）、`podcastGenerationProgressText`（関数）、`PodcastGenerationWorkerFactory`（class）、`WorkManagerPodcastGenerationController`（class）、`WorkManagerPodcastScheduleController`（class）、`shouldSkipPodcastGeneration`（関数）、`nextRunDelayMillis`（関数） | Worker、operation、factory、手動 generation/schedule controller を接続し background 実行と次回登録を所有する。 主なメソッド: `doWork`、`createWorker`、`generate`、`regenerate`、`sync`、`ensure`、`scheduleNext`、`cancel`。 | [PodcastGenerationWorker.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorker.kt) |
| `RssPodcastFeedContentSource`（class） | RssFeedContentReader へ source URL を渡し、本文のある候補を重複整理して返す。 主なメソッド: `latestEntries`。 | [RssPodcastFeedContentSource.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSource.kt) |
| `SqlitePodcastCandidateFilter`（class） | 番組内の消費/除外記録を照合して再利用できる候補だけ返す。 主なメソッド: `availableEntries`。 | [SqlitePodcastCandidateFilter.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/SqlitePodcastCandidateFilter.kt) |
| `SqlitePodcastRepository`（class） | Podcast 所有表へモデルを保存し、episode 予約・章 checkpoint・再生成・削除を更新する。 主なメソッド: `listSources`、`findSource`、`saveSource`、`deleteSource`、`listPrograms`、`findProgram`、`saveProgram`、`deleteProgram`、`recordExcludedEntries`、`listEpisodes`、`findEpisode`、`archiveEpisode`、`restoreEpisode`、`deleteEpisode`、`listGenerationTasks`、`findInterruptedGenerationEpisode`、`claimPendingEpisode`、`reserveEpisode`、`prepareEpisodeRetry`、`prepareEpisodeRebuild`、`markChapterGenerating`、`completeChapter`、`failChapter`、`failRegeneration`、`completeEpisode`、`failEpisode`。 | [SqlitePodcastRepository.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/SqlitePodcastRepository.kt) |


## 実装接続と主要 API

`podcastDatabaseSchema` は Core Database の `DatabaseSchemaContribution` 値です。[AppDatabaseSchema](../../../app/composition/src/main/java/dev/terashima/yomitorirss/AppDatabaseSchema.kt) が application の schema contribution 一覧へ登録し、feature が所有する表の生成を共通 database の初期化へ接続します。Podcast の migration は除外条件と除外履歴の追加も担当します。

`SqlitePodcastRepository` は Domain の Repository と task reader を実装し、`podcastDatabaseSchema` が所有表を提供します。`list/find` はモデル読込、save/delete と archive/restore は durable 状態変更、予約・checkpoint API は transaction と更新通知を介して状態を変更します。`SqlitePodcastCandidateFilter.availableEntries` が消費・除外済みを排除し、`RssPodcastFeedContentSource.latestEntries` は Podcast source URL を RSS の公開 reader に渡します。

`DefaultPodcastScriptGenerator.generate` は LOCAL を BackgroundAiTextInference、CLOUD を `PodcastCloudTextInference` へ委譲します。`DefaultPodcastCloudTextInference.selectedModel/generate` は選択 model と cloud 原稿応答を扱います。`DefaultPodcastNewsExcluder.filter`、`DefaultPodcastNewsClusterer.cluster`、`DefaultPodcastNewsCategorizer.categorize` は既存 text/structured inference を利用し、採否・group index・category を Domain の値へ戻します。推論失敗と不正出力の fallback は adapter ごとに異なり、原稿生成の失敗を一律に成功へ変換するものではありません。

[AppPodcastRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/podcast/AppPodcastRuntimeDependencies.kt) が Repository、RSS reader adapter、AI adapter を `GeneratePodcastEpisodeUseCase` へ注入し、`PodcastGenerationWorkerFactory.createWorker` が対象 Worker 名だけを生成します。Application 起動時には composition が保存済み番組を観測して schedule を復旧し、中断生成を再開します。

## 保存と外部能力の接続

SqlitePodcastRepositoryがsource、番組、episode、記事snapshot、章checkpoint、消費・除外済みentryを保存します。RSS Domainのfeed-content readerを通して番組指定のsourceを読み、RSS readerの購読テーブルをPodcast生成状態として使いません。AI分類や原稿生成は既存のinference capabilityへ接続し、Podcast Domainに対して生成・分類契約を実装します。

章checkpointとepisodeの状態は永続化されるので、Workerの実行状態とは分けて調査します。episodeのアーカイブや削除で消費済みentryを再利用可能へ戻さないこと、存在しないsourceを参照する番組を保存しないことはRepositoryのpersistence testが確認します。状態変更はchanges projectionを通してUIの再読込へ伝わります。

## Workerと失敗

PodcastGenerationWorkerは手動・定刻・作り直しを判別し、同時生成ならretry、キャンセルなら伝播、通常の生成失敗ならworkを終了します。episode予約後の失敗は生成処理がepisodeや章のFAILED状態への保存を試みますが、保存自体が失敗する場合もあり、予約前のfeed取得などの失敗では新しいepisodeがない場合があります。定刻workは次回予定の登録も担います。現コードではforegroundへ昇格するのは手動生成と作り直しで、定刻生成は対象外です。[設計文書](../../../docs/architecture/podcast.md)の「処理開始時にforeground workへ昇格」という記述はこの区別を明記していないため、挙動変更時はコードと正本の両方を確認してください。operation未指定の旧workは定刻として解釈し、永続Worker inputとの互換を維持します。

## 手動と定刻の登録経路

`WorkManagerPodcastGenerationController.generate/regenerate` は operation と番組/episode ID を one-time work に載せ、番組別 unique work を KEEP で登録します。`WorkManagerPodcastScheduleController.sync` は REPLACE、`ensure` は KEEP、`scheduleNext` は APPEND_OR_REPLACE、`cancel` は定刻と手動の両 work を取り消します。定刻時刻は `nextRunDelayMillis` が現在のローカル wall-clock に基づく次回を計算し、ネットワーク接続制約を付けます。

`PodcastGenerationWorker.doWork` → AI background runtime → operation 判定 → UseCase が実行経路です。定刻時は番組設定や provider の一時停止を確認し、手動は foreground 通知で進捗を投影します。通常失敗を durable episode と WorkManager Result の同じ状態だと扱わない点は前節の通りです。

## 調査と変更の入口

[SqlitePodcastRepository.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/SqlitePodcastRepository.kt)、[PodcastGenerationWorker.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorker.kt)、[RssPodcastFeedContentSource.kt](../../../feature/podcast/data/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSource.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [PodcastRepositoryPersistenceTest.kt](../../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastRepositoryPersistenceTest.kt)、[PodcastGenerationWorkerTest.kt](../../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/PodcastGenerationWorkerTest.kt)、[RssPodcastFeedContentSourceTest.kt](../../../feature/podcast/data/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/data/RssPodcastFeedContentSourceTest.kt) です。

[podcast domain](podcast-domain.md)、[podcast ui](podcast-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Podcast architecture](../../../docs/architecture/podcast.md)です。
