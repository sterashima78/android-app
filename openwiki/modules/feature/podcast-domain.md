---
type: module
title: Podcast Domain：番組生成と章checkpoint
description: 番組独立source、ニュース生成、章checkpointと再開の契約を説明する。
tags:
  - podcast
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T04:30:12.060Z
sources:
  - id: openwiki-source-3391ea41cc8fdc84138466e7
    resource: repo://feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt
  - id: openwiki-source-cafc036dfaf531d238cf1a8d
    resource: repo://feature/podcast/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastInterruptedRecoveryTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Podcast Domain：番組生成と章checkpoint

`:feature:podcast:domain` は Podcast の入力source・番組・episode・章checkpointと生成UseCaseを所有するJVMモジュールです。Repository/入力取得/AI生成/採否分類/生成受付/予定管理の公開capabilityを分け、Data実装をUIとbackground実行へ接続する契約を提供します。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `PodcastGenerationProvider`（enum class）、`PodcastSchedule`（data class）、`PodcastSource`（data class）、`PodcastProgram`（data class） | LOCAL/CLOUD・予定時刻・入力source・番組設定。constructorが値域と必須値を検証する。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastFeedEntry`（data class）、`PodcastEpisodeArticle`（data class） | 候補とdurable記事snapshot。後者が章位置・checkpoint・原稿・エラーを持つ。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastEpisode`（data class）、`PodcastPlaybackChapter`（data class） | episodeモデルと再生章投影。chapterGroups/playbackChaptersで保存章と原稿markerを対応付ける。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastChapterGenerationStatus`（enum class）、`PodcastRegenerationStatus`（enum class）、`PodcastClusteringStatus`（enum class）、`PodcastEpisodeStatus`（enum class） | 章生成・作り直し・cluster fallback・episode寿命を独立して表すenum。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastGenerationTaskState`（enum class）、`PodcastGenerationTask`（data class）、`PodcastGenerationTaskReader`（interface） | 背景生成の観測投影。listGenerationTasksでdurable章進捗を一覧する。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastFeedContentSource`（interface）、`PodcastCandidateFilter`（interface）、`AllPodcastCandidates`（object） | latestEntriesで候補取得、availableEntriesで消費判定。Allは候補をそのまま返す既定実装。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastRepository`（interface） | source/program/episode CRUD、候補消費・予約・章checkpoint・再実行準備・状態確定を定義する保存契約。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastScriptGenerator`（interface） | generateでproviderとpromptから原稿を返す生成契約。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastNewsExclusionResult`（data class）、`PodcastNewsExcluder`（interface）、`IncludeAllPodcastNews`（object） | filterの採用/除外結果と契約。IncludeAllはすべて採用する既定実装。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastNewsClusteringResult`（data class）、`PodcastNewsClusterer`（interface）、`SingletonPodcastNewsClusterer`（object） | clusterの候補index groupとstatus。Singletonは単記事ごとのgroup。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastNewsCategorizer`（interface）、`OtherPodcastNewsCategorizer`（object）、`PODCAST_OTHER_CATEGORY`（定数） | categorizeで章ごとのcategoryを返す。既定値はその他。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastGenerationProgress`（data class）、`PodcastGenerationAlreadyRunningException`（class）、`PodcastGenerationResult`（sealed interface） | 完成章進捗、番組競合例外、Generated/NoNewArticlesの戻り結果を区別する。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `GeneratePodcastEpisodeUseCase`（class） | generate/retry/regenerate/resumeInterruptedで番組guardと章checkpointを介した生成を調整する。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `buildPodcastChapterPrompt`（関数）、`buildPodcastEpisodeScript`（関数） | 公開のprompt構成とepisode原稿結合関数。単記事/記事groupを章原稿へ変換する。 | [Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt) |
| `PodcastGenerationController`（interface） | generate/regenerateをbackgroundへ受付する契約。 | [PodcastGenerationController.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastGenerationController.kt) |
| `PodcastScheduleController`（interface） | sync/ensure/scheduleNext/cancelで予定変更/復旧/次回登録/取消を区別する。 | [PodcastScheduleController.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastScheduleController.kt) |


## API・生成能力と composition

モデル群のうち `PodcastSource` と `PodcastProgram` は入力sourceと番組設定、`PodcastFeedEntry` は生成候補、`PodcastEpisodeArticle` は保存済み記事と章checkpoint、`PodcastEpisode` は原稿と生成/再生成状態です。`PodcastSchedule`、各 status enum、`PodcastGenerationProgress` は時刻・状態・進捗の値域を区別します。`PodcastPlaybackChapter` は再生向け投影、`PodcastGenerationTask` / `PodcastGenerationTaskState` / `PodcastGenerationTaskReader` は背景処理を観測するための契約です。

`PodcastRepository` の API は source/program の list/find/save/delete、episode の list/find/archive/restore/delete、候補の `recordExcludedEntries` と `reserveEpisode`、`claimPendingEpisode` / `findInterruptedGenerationEpisode`、再実行準備 `prepareEpisodeRetry` / `prepareEpisodeRebuild`、章の mark/complete/fail、episode の complete/fail と `failRegeneration` に分かれます。更新イベントは `changes`。生成に必要な独立能力は `PodcastFeedContentSource.latestEntries`、`PodcastCandidateFilter.availableEntries`、`PodcastNewsExcluder.filter`、`PodcastNewsClusterer.cluster`、`PodcastNewsCategorizer.categorize`、`PodcastScriptGenerator.generate` です。`AllPodcastCandidates` / `IncludeAllPodcastNews` / `SingletonPodcastNewsClusterer` / `OtherPodcastNewsCategorizer` はそれぞれ無制限候補・除外なし・単記事群・その他分類の既定実装です。

[AppPodcastRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/podcast/AppPodcastRuntimeDependencies.kt) が [SqlitePodcastRepository と各 Data adapter](podcast-data.md) を UseCase、WorkerFactory、ViewModel.Factory に注入します。`PodcastGenerationController.generate/regenerate` は WorkManager 実装へ接続し、`PodcastScheduleController.sync/ensure/scheduleNext/cancel` は予定置換・維持・次回追加・取り消しを区別します。

## 入口から checkpoint への具体フロー

`GeneratePodcastEpisodeUseCase.generate(programId)` は候補の採否を検証し、除外済みを記録、`clusterCandidates` で全候補がちょうど一度 group に含まれることを検証し、feed category と不足分の分類から章順序を作ります。Repository の `reserveEpisode` が記事 snapshot と章位置を保存してから `generateReserved` に進みます。`retry` は FAILED の章checkpoint再利用、`regenerate` は READY/FAILED episode の保存済み記事に現在の条件を適用した rebuild、`resumeInterrupted` は中断した episode の再開を意味します。

`generateReserved` は READY で原稿のある章を飛ばし、残りを `generateChapter` → `markChapterGenerating` → `buildPodcastChapterPrompt` → generator → `completeChapter` の順で処理します。CLOUD は semaphore 付き並行生成、LOCAL は逐次生成です。最後に章原稿を結合して `completeEpisode` を呼びます。公開 `buildPodcastChapterPrompt` は単記事/記事群の入力から prompt を作ります。公開 `buildPodcastEpisodeScript` は各記事を一章として結合する互換入口で、生成UseCaseでは内部 `buildPodcastEpisodeScriptFromChapters` が保存済み章group単位で原稿を結合します。`chapterGroups()` は章位置順の group、`playbackChapters()` は marker を検証し、整合しない原稿は全体一章へ戻します。

通常の例外は章/episode/再生成の失敗保存を試みて再throw、キャンセルは伝播します。進捗callbackの通常例外は無視され、checkpointを壊しません。番組単位の guard 競合は `PodcastGenerationAlreadyRunningException`、候補なしは `PodcastGenerationResult.NoNewArticles`、成功は `Generated` で区別します。

## 責務と生成の流れ

番組、入力source、記事snapshot、ニュースcluster、episode、章checkpointと生成結果の契約を所有します。入力はPodcastFeedContentSource、候補消費判定はPodcastCandidateFilter、保存はPodcastRepositoryへ委譲し、AndroidのWorkerやSQLiteをDomainへ持ち込みません。Podcast sourceは番組が参照する独立したsourceで、reader画面の購読や既読をそのまま生成状態として使う設計ではありません。

GeneratePodcastEpisodeUseCaseは番組を取得し、予約済みepisodeがあれば先に処理します。新規生成ではsourceから候補を取得して利用可能なentryを絞り、除外条件とニュースclusterを適用した後でepisodeを予約します。候補が残らなければ新しいepisodeを作らず終了します。本文生成と分類の能力を分けているため、記事の採否や章境界と原稿生成の問題を別々に追えます。

## 再開と変更時の注意

中断episodeは保存済み記事snapshotと章checkpointを用いて再開します。新しいfeed取得を再開の前提にせず、作り直しでも保存済み記事を用います。処理は番組ごとのgeneration guardで同時実行を抑え、キャンセルは通常失敗へ変換しないよう扱います。章のモデル変更では記事と章の対応、既存原稿の保持、READY checkpointの再利用を確認してください。代表的な検証は中断復旧と再生成のDomain testであり、実際の予約・保存の原子性はData testも参照します。

## 調査と変更の入口

[Podcast.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/Podcast.kt)、[PodcastGenerationController.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastGenerationController.kt)、[PodcastScheduleController.kt](../../../feature/podcast/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastScheduleController.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [PodcastInterruptedRecoveryTest.kt](../../../feature/podcast/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastInterruptedRecoveryTest.kt)、[PodcastRegenerationTest.kt](../../../feature/podcast/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/podcast/PodcastRegenerationTest.kt) です。

[podcast data](podcast-data.md)、[podcast ui](podcast-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

設計の正本は[Podcast architecture](../../../docs/architecture/podcast.md)です。
