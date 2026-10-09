---
type: module
title: RSS Domain：購読更新と推薦ルール
description: Feed 契約、同時更新の制御、推薦評価と除外 feedback 学習の状態規則を定義する。
tags:
  - rss
  - domain
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-3c1d7866f55cea3d234d7718
    resource: repo://feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedModels.kt
  - id: openwiki-source-4d62659608ab970f0b42bc09
    resource: repo://feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RefreshFeedsUseCase.kt
  - id: openwiki-source-4793116c589a3f6af58cbffc
    resource: repo://feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeedContentReader.kt
  - id: openwiki-source-d7f71befc8829b1f358389ee
    resource: repo://feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssRecommendation.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と入口

`:feature:rss:domain` はFeedとfolder、import、Web scraping rule、および推薦処理の契約を定義するJVM moduleである。記事そのものの保存はContent-owned gatewayへつなぐDataの責務とし、Bookmark状態はCurationに残す。PodcastがURLからfeedを読むためのRssFeedContentReaderも公開するが、Podcastのsource登録や生成履歴をここに所有しない。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [FeedModels](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedModels.kt) | `Feed`/`FeedFolder`（data class）は購読identityとfolder、取得validator・error・ContentType overrideを渡す。`FeedCandidate`/`FeedInspection`はURL検査の候補と直接feed判定結果。`Feed.effectiveContentType(folder)`と`FeedFolder.effectiveContentType()`（拡張関数）はfeed指定→folder指定→ARTICLEの優先順位を定義する。 |
| [FeedRepository](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedRepository.kt) | `FeedRepository`（interface）はfeed/folder一覧、`inspect`/`addFeed`、rename/delete/create/move、feed/folderのContentType設定、`refreshFeed`とchangesを公開する。Web scraping ruleの一覧・保存・削除・testも同じ購読capabilityに含む。`markExistingArticlesRead`は登録時の記事既読policyであり、Reddit thread adapterが利用する。 |
| [FeedImportRepository](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedImportRepository.kt) | `FeedImportRepository`（interface）の`importFeedOpml(documentUri)`は文書URIからimportを要求し、`FeedOpmlImportResult`（data class）で追加・skip・失敗数を返す。 |
| [RefreshFeedsUseCase](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RefreshFeedsUseCase.kt) | `RefreshFeedsUseCase`（class）の`invoke(feeds,onProgress)`が並列数を制限し、全件終了後`RefreshFeedsResult`（data class）の総数・失敗数を返す。repository単位の失敗を集計して残りのfeedを継続する。 |
| [RssFeedContentReader](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeedContentReader.kt) | `RssFeedContentReader`（interface）は登録済みfeed IDを渡す`latestEntries`と未登録sourceを渡す`latestEntriesFromSources`を提供する。`RssFeedContentSource`/`RssFeedContentEntry`（data class）が入力sourceとfeed本文を表す。記事リンク先ページは取得しない契約で、Podcastが具体実装の利用者。 |
| [RssWebScrapingRule](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssWebScrapingRule.kt) | `RssWebScrapingRule`（data class）はURL pattern、function code、timeoutと更新日時。`RssWebScrapingItemPreview`/`RssWebScrapingPreview`はrule試行結果をUIへ返す。既定timeout定数もこの契約に属する。 |
| [RssRecommendation](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssRecommendation.kt) | `RssRecommendationPolicy`/`RssRecommendationFeedback`/`RssRecommendationSnapshot`/`RssRecommendationTask`と`RssRecommendationLearningDecision`（data class）が条件revision、除外参考、表示snapshot、durable task、学習出力を表す。`RssRecommendationAssessment`/`RssRecommendationDecision`（sealed interface）と`RssRecommendationExecutionProvider`/`RssRecommendationUnscoredReason`/`RssRecommendationLearningOutcome`/`RssRecommendationTaskState`（enum）が数値評価・未評価と実行種別を区別する。`RssRecommendationTaskReader`、`RssRecommendationTaskScheduler`、`RssRecommendationRepository`、`RssRecommendationEngine`（同接頭辞のinterface）は読取・work予約・永続化・推論を分離し、`RssRecommendationService`（class）がそれらのDomain規則をまとめる。 |

## 公開APIと構成要素間の接続

`FeedRepository`の編集APIは`renameFeed`/`deleteFeed`、`createFolder`/`renameFolder`/`deleteFolder`、`moveFeedToFolder`、`setFeedContentType`/`setFolderContentType`に分かれる。分類設定のnullは指定解除として継承へ戻す。rule APIは`listWebScrapingRules`/`saveWebScrapingRule`/`deleteWebScrapingRule`/`testWebScrapingRule`で、testはpreviewを返し保存操作と分かれる。`FeedImportRepository.importFeedOpml`は文書URIを入力にし、DomainがContentResolverを所有する契約ではない。

`RssRecommendationService.snapshot(articleIds)`は現在policy・対象評価・未処理feedback数を一緒に読み、`scoreArticle`は有効条件とrevisionを確認してengineのdecisionを保存する。`recordExclusionFeedback`/`cancelExclusionFeedback`は除外参考と失敗時の取消入口、`improvePendingFeedback`は開始snapshotだけを消費する学習入口。条件保存・provider切替・学習resetはrepositoryへ委譲する。

`RssRecommendationRepository`はpolicy読書き、評価読書き、feedback追加/一覧/削除と`applyLearnedConditionAndConsumeFeedback`、task投入/claim/complete/requeue/中断復帰/clearを公開する。`RssRecommendationEngine.score`/`improveLearnedCondition`は推論結果を返すだけで永続化しない。`RssRecommendationTaskScheduler.enqueueForFeed`/`enqueueUnread`と`kick`/`scheduleFeedbackLearning`は処理予約を、pause判定・global gate pause・充電再開設定は実行条件を扱う。

[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt)が`DefaultFeedRepository`、`DefaultFeedImportRepository`、`DefaultRssFeedContentReader`を公開契約へ接続する。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)は`RssViewModel.Factory`と`FeedViewModel.Factory`へ公開契約を渡し、`RedditSourceBoundary`のpredicateで通常RSSの表示・登録対象を分離する。RSS UIがRSS Dataを直接生成する接続ではない。

## 更新と評価の流れ

RefreshFeedsUseCaseは渡されたFeedをSemaphoreで制限しながら並列更新し、各件の失敗を集計して進捗と総数を返す。推薦では手動条件と学習条件を別々に保持し、評価時に結合する。RssRecommendationServiceは現在revisionの条件が有効なときだけ記事タイトルをengineへ渡す。情報不足と推論失敗は数値スコアに変えず、別の未評価理由として保存する。

## 学習と状態境界

学習開始時のfeedbackとpolicyをsnapshotにする。推論失敗、revisionまたはproviderの変更、結果種別と条件更新の不一致があれば結果を適用せずfeedbackを保持する。成功時は開始時に取得したfeedback identityだけを消費し、実行中に追加されたfeedbackを次回へ残す。schedulerとrepositoryの契約は持つがWorkManagerやAI providerの具体実装はData側に閉じる。

## 変更と検証

RefreshFeedsUseCaseTestは部分失敗でも全件処理することと同時数の上限を検証する。RssRecommendationServiceTestは条件無しの評価抑止、未評価理由、古いpolicyの拒否、失敗保持と追加feedbackの持越しを確認する。学習の状態遷移は[Quintモデル](../../../spec-models/quint/rss_feedback_learning.qnt)にも投影されているため、意味を変えるときは仕様と併せて確認する。

## ソースと検証の入口

- [RefreshFeedsUseCase](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RefreshFeedsUseCase.kt)
- [RssRecommendation](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssRecommendation.kt)
- [FeedRepository](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedRepository.kt)
- [RssFeedContentReader](../../../feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeedContentReader.kt)
- [RefreshFeedsUseCaseTest](../../../feature/rss/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/RefreshFeedsUseCaseTest.kt)
- [RssRecommendationServiceTest](../../../feature/rss/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/RssRecommendationServiceTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [rss-data module](rss-data.md)
- [rss-ui module](rss-ui.md)
- [article-domain module](article-domain.md)
- [podcast-domain module](podcast-domain.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
