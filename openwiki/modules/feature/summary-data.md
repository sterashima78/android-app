---
type: module
title: Summary Data — 本文準備と永続要約キュー
description: 記事単位のdurable task、local/cloud routing、要約とブックマークmetadata保存を説明する。
tags:
  - summary
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-393d1a36c311379f4f392fc8
    resource: repo://feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryContentRetentionProtectionQuery.kt
  - id: openwiki-source-4880222f602c95e0f110c4d5
    resource: repo://feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryDatabaseSchema.kt
  - id: openwiki-source-9bf01a4dbbfbeb8a42cc2036
    resource: repo://feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryExecutionPreferences.kt
  - id: openwiki-source-fd7867a7976edd87714ba327
    resource: repo://feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryWorker.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Summary Data — 本文準備と永続要約キュー

`:feature:summary:data`

Summary Dataは要約結果・記事単位のdurable task・準備本文とprompt設定を所有するAndroid moduleである。要求をWorkManagerへ接続し、ContentとCurationの公開契約を通して本文取得・metadata適用を行う。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [SummaryRepository](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryRepository.kt) | `DefaultSummaryRepository`（class）はSummaryRepositoryの全capabilityを実装し、通常要求・保存時補完・refresh・一括投入をqueueへ変換する。保存済み読取`findSummary`は推論を起動しない。新規投入前に選択providerの利用可否を確認する。 |
| [SummaryQueue](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryQueue.kt) | `SummaryQueue`（object）はenqueueとbatch投入、kick、provider変更、executionState、local/cloud pause、充電再開、stop/cancel/resumeをWorkManagerへ接続する公開入口。内部pipeline再起動関数は旧work取消と中断taskの再queueを調整する。 |
| [SummaryWorker](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryWorker.kt) | `SummaryWorker`（class）の`doWork`が背景推論scopeで実行候補を取り、local/cloud分岐、claim、progress、要約保存、Bookmark metadata適用、complete/failed/retryを進める。`buildCloudSummaryPrompt`（internal関数）は正確な記事URLを開く要求を組み立てる。 |
| [SummaryContentFetchWorker](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryContentFetchWorker.kt) | `SummaryContentFetchWorker`（class）はlocal候補の記事をArticleContentClientで取得して準備本文storeへ保存し、推論queueを起動する。cloud選択時はlocal本文準備を行わない。cancellationは伝播し、取得失敗はtaskへ記録する。 |
| [SummaryWorkerFactory](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryWorkerFactory.kt) | `SummaryWorkerFactory`（class）はruntime依存と各providerをlazyに解決し、本文取得、要約、cleanup、充電再開、backfill Workerを生成する。`createWorker`は未知class名でnullを返す。 |
| [SummaryTaskQueueRepository](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskQueueRepository.kt) | `DefaultSummaryTaskQueueRepository`（class）はtask storeの記録へArticle title、Bookmarkのあとで読む優先度、現在providerを加えてDomainモデルへ投影する。`taskCounts`はSQL集計、制御操作はSummaryQueueへ委譲する。`retryFailedBookmarkTasks`は現在も保存済みの失敗記事だけを再queueする。 |
| [SummaryExecutionPreferences](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryExecutionPreferences.kt) | `SummaryExecutionPreferences`（class）はSummaryExecutionSettingsを実装する。`currentProvider`/`setProvider`とStateFlowを提供し、変更をSharedPreferencesへ保存した後queue pipelineを切り替える。 |
| [SummaryPromptStore](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryPromptStore.kt) | `SummaryPromptStore`（class）はSummaryPromptSettingsを実装し、正規化済みpromptをSharedPreferencesとStateFlowへ反映する。`update`は無効promptを拒否し、`reset`は既定promptへ戻す。 |
| [ChatGptSummaryCloudInference](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/ChatGptSummaryCloudInference.kt) | `ChatGptSummaryCloudInference`（class）はSummaryCloudInferenceを実装する。接続とモデル選択を確認し、`generate`/`generateFromUrl`をChatGptInferenceClientへ渡す。内部`retryWebTargetOpen`とfetch失敗text判定はweb取得失敗を扱い、`classifySummaryProviderFailure`がprovider errorをDomainのkind/retryableへ変換する。 |
| [HierarchicalSummary](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/HierarchicalSummary.kt) | `HierarchicalSummaryProgressStage`（enum）と`HierarchicalSummaryProgress`（data class）はdirect/chunk/reduction/final進捗を渡す。`BackgroundAiTextInference.summarizeHierarchically`/`summarizeText`（公開拡張関数）がprompt込みのbudgetを守って本文の分割・中間要約・縮約・最終生成を進める。内部`HierarchicalSummaryBudget`/`Text`（HierarchicalSummary接頭辞のobject）がfit判定、normalize、split、pack、joinを担う。 |
| [BookmarkAiEnrichment](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/BookmarkAiEnrichment.kt) | `BookmarkAiGeneratedMetadata`（internal data class）とprompt/candidate builder、`parseBookmarkMetadataEnrichment`（internal関数）が生成JSONをtagと既存folderへ変換する。folder候補からの選択とタグ正規化を一緒に扱う。 |
| [BookmarkAiFolderMetadata](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/BookmarkAiFolderMetadata.kt) | `parseGeneratedFolder`（internal関数）は生成folder名を正規化して既存候補と照合し、新規folderを作らず一致しない場合はnullを返す。 |
| [BookmarkAiMetadata](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/BookmarkAiMetadata.kt) | `normalizeGeneratedTags`（internal関数）が生成タグのprefix/箇条書き記号を除き、空・重複を整理する。metadata解析と適用前の共通処理。 |
| [BookmarkAutoEnrichmentBackfillWorker](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/BookmarkAutoEnrichmentBackfillWorker.kt) | `BookmarkAutoEnrichmentBackfillScheduler`（object）の`schedule`はunique workをKEEPで予約する。`BookmarkAutoEnrichmentBackfillWorker`（class）と`runBookmarkAutoEnrichmentBackfillWorker`（公開suspend関数）が注入されたbackfill処理を呼びWorkManager結果へ変換する。 |
| [SummaryDatabaseSchema](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryDatabaseSchema.kt) | `summaryDatabaseSchema`は要約、記事単位taskと準備本文のtableをdatabase bootstrapへ提供する。prepared contentのtask参照は削除連鎖を持つ。 |
| [SummaryPersistenceModels](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryPersistenceModels.kt) | `SummaryRecord`/`SummaryTaskRecord`/`PreparedSummaryArticleContent`（internal data class）は保存済み要約、state/refresh mode/タグ置換/進捗を持つtask、準備本文を表し、Domain表示モデルとSQL表現を分離する。 |
| [SummaryStore](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryStore.kt) | `YomitoriDatabase.findSummary`/`saveSummary`（internal拡張関数）が要約結果とmodel識別を読み書きする。生成要求のcache確認とWorkerの結果保存の接点。 |
| [SummaryPreparedContentStore](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryPreparedContentStore.kt) | 準備本文のfind、取得候補list、active件数count、`savePreparedSummaryArticleContentIfQueued`（internal database拡張関数）がlocal取得と推論の間をつなぐ。保存時にqueued stateを確認する。 |
| [SummaryTaskStore](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskStore.kt) | internal database拡張関数がtaskのfind/list/enqueue/claim、進捗更新、complete/fail、stop/cancel/resume、失敗再queueと終了ログ削除を実装する。`enqueueSummaryTask`はqueued/runningを重複追加せず、タグ置換にforceRefreshを要求する。`claimSummaryTask`はstateとlocal本文準備をtransaction内で検査する。 |
| [SummaryTaskRetryStore](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskRetryStore.kt) | `YomitoriDatabase.requeueRunningSummaryTaskForRetry`（internal拡張関数）はrunningだけをqueuedへ戻し、進捗を消し、retry messageを保存する。cloud retryの再開点。 |
| [SummaryCloudTaskQueries](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryCloudTaskQueries.kt) | `YomitoriDatabase.listCloudReadySummaryTasks`（internal拡張関数）はcloud向け候補を読む。local prepared contentを必要とするqueryから分離する。 |
| [SummaryTaskCountsPersistence](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskCountsPersistence.kt) | `YomitoriDatabase.countSummaryQueueTasks`（internal拡張関数）はqueued/running/stoppedの件数だけをSQLで集計してSummaryQueueTaskCountsを返す。 |
| [SummaryTaskPriority](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskPriority.kt) | `selectNextSummaryTask`と`summaryTaskPriority`（internal関数）はあとで読むidentityから優先度を決め、候補taskを選択する。BookmarkContentQueryの返すidentityを受け、foreign tableへ直接queryしない。 |
| [SummaryQueueExecutionPreferences](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryQueueExecutionPreferences.kt) | `SummaryQueueExecutionPreferences`（internal class）はlocal/cloudのpauseと充電再開を共通background設定へ接続する。Summary専用の並行pause状態を作らない。 |
| [SummaryResumeOnChargingWorker](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryResumeOnChargingWorker.kt) | `SummaryResumeOnChargingWorker`（class）の`doWork`は充電時の自動再開をSummaryQueueへ要求し、例外時にretryを返す。 |
| [SummaryTaskLogCleanupWorker](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskLogCleanupWorker.kt) | `SummaryTaskLogCleanupWorker`（class）の`doWork`は終了済みtaskの保存期限を計算し削除する。処理失敗はretry、cancellationは伝播する。 |
| [SummaryContentRetentionProtectionQuery](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryContentRetentionProtectionQuery.kt) | `SummaryContentRetentionProtectionQuery`（class）はContent retention契約を実装し、要約済みまたはqueued/running taskのあるContent identityを保護集合として返す。Content削除policyのconsumerへSummary-owned情報を提供する。 |

## 公開APIと構成要素間の接続

`SummaryQueue.enqueue(context,articleId,forceRefresh,replaceBookmarkTags)`系の投入とbatch APIは受理結果を返す。`stop`は停止、`cancel`は取消、`resume`は再開のstate変更後にworkを調整する。`setLocalPaused`/`setCloudPaused`はprovider別のglobal実行条件を変え、`setResumeLocalWhenCharging`は充電再開workを調整する。`SummaryWorkerFactory.createWorker`は登録済みWorker class名だけを生成し、未知名はnullへ戻す。

[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt)が`SummaryExecutionPreferences`、`SummaryPromptStore`、`ChatGptSummaryCloudInference`を各Domain契約に接続し、`DefaultSummaryRepository`へdatabase・local model reader・execution settings・cloud inferenceを渡す。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`summaryViewModelFactory`がこのRepositoryをUIへ注入する。Workerは`SummaryWorkerFactory`から`SummaryRuntimeDependencies`のContent/Curation契約を受け取る。

## 要求からWorkerまで

DefaultSummaryRepositoryは通常要求で保存済み要約を優先し、既存taskのqueued/runningとfailedを結果へ変換する。新規要求は選択providerが使えるか確認してSummaryQueueへ投入する。queue、本文取得Worker、要約Worker、retry store、prompt設定がこのfeatureのDataにまとまり、画面を離れても処理状態を保持する。

永続化はarticle_summaries、記事IDを主キーとするsummary_tasks、準備本文のsummary_article_contentを所有する。SummaryWorkerは中断taskを戻して候補を読み、BookmarkContentQueryの「あとで読む」情報から優先度を決める。localでは共通gateのpermitを取ってclaimし、準備済み本文を階層的に要約する。cloudでは選択モデルで記事の正確なURLを開くpromptを送り、local本文の取得完了をclaim条件にしない。

## 保存順序と障害

生成した要約を保存した後、ブックマーク補完contextがあれば要約を資料にタグ・folderを生成し、Bookmark-owned capabilityで適用する。foreign tableをここで書く構成にはせず、タグ置換指定をcommandへ渡す。metadata処理まで終えた後にtaskをcompleteする。cloudのretryable failureはrunning taskを再queueしてWorker retryを返し、通常の失敗はfailedとして記録する。coroutine cancellationは伝播する。

一覧adapterもArticleRepositoryとBookmarkContentQueryからタイトルとpriorityを得て、task記録へ投影する。失敗bookmarkの一括再試行は、現在もブックマーク済みのIDに絞る。保存・cloud routing・優先度の変更はSummaryPersistenceTest、SummaryCloudRoutingTest、SummaryTaskPriorityTestを参照する。正確なqueue遷移はSummaryTaskStoreとSummaryQueueを追う。

## 調査・変更の入口

- [SummaryRepository.kt](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryRepository.kt)
- [SummaryWorker.kt](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryWorker.kt)
- [SummaryDatabaseSchema.kt](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryDatabaseSchema.kt)
- [SummaryTaskQueueRepository.kt](../../../feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryTaskQueueRepository.kt)
- [SummaryCloudRoutingTest.kt](../../../feature/summary/data/src/test/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryCloudRoutingTest.kt)

関連モジュール: [summary-domain](summary-domain.md)、[summary-ui](summary-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
