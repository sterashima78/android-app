---
type: module
title: Summary Data — 本文準備と永続要約キュー
description: 記事単位のdurable task、local/cloud routing、要約とブックマークmetadata保存を説明する。
tags: [summary, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-4880222f602c95e0f110c4d5
    resource: repo://feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryDatabaseSchema.kt
  - id: openwiki-source-fd7867a7976edd87714ba327
    resource: repo://feature/summary/data/src/main/kotlin/dev/terashima/yomitorirss/feature/summary/data/SummaryWorker.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Summary Data — 本文準備と永続要約キュー

`:feature:summary:data`

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
