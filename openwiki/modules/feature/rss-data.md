---
type: module
title: RSS Data：取得・永続化と推薦 background work
description: 通常 feed と Web scraping の取得、RSS-owned 保存、推薦 queue と feedback 学習の
  WorkManager 接続を実装する。
tags:
  - rss
  - data
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-e9f3f47229ba7ed81b4610db
    resource: repo://feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedRepository.kt
  - id: openwiki-source-4cbdd6ff46fe9ed50e2effe3
    resource: repo://feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssContentClassificationSourceQuery.kt
  - id: openwiki-source-5bbd006ada9b61d59b34772e
    resource: repo://feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssRecommendationBackground.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と構成

`:feature:rss:data` はFeedRepository、OPML import、feed content reader、推薦repositoryとengineを実装する。FeedStoreが購読情報を保持し、記事の作成・source切り離しはContentSourceGatewayへ要求する。RSS-owned設定、推薦結果、feedbackとtaskの永続化はこのmoduleのstoreに閉じ、ContentやCurationのtable ownershipを移さない。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [DefaultRssFeedContentReader](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/DefaultRssFeedContentReader.kt) | `DefaultRssFeedContentReader`（class）はRssFeedContentReaderを実装する。`latestEntries`は購読IDからsourceを取り、`latestEntriesFromSources`はad-hoc sourceを取得してfeed本文とcategoryを結果へ投影する。購読登録や記事リンク先の全文取得は行わない。 |
| [DefaultRssRecommendationEngine](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/DefaultRssRecommendationEngine.kt) | `DefaultRssRecommendationEngine`（class）の`score`/`improveLearnedCondition`が選択providerへ推論を依頼する。内部prompt builderと`parseScoringToolCall`/`parseLearnedConditionToolCall`はstructured tool出力を検証しDomain decisionへ戻す。 |
| [FeedImportRepository](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedImportRepository.kt) | `DefaultFeedImportRepository`（class）はContentResolverでOPMLを読み、parserのfolder情報をFeedStoreへ、購読追加をFeedRepositoryへ渡す。重複、無効URL、各追加失敗をimport結果に集計する。 |
| [FeedOpml](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedOpml.kt) | `OpmlFeed`/`OpmlParseResult`（data class）と`parseFeedOpml`（トップレベル関数）はoutlineとfolderを解釈する。`normalizedFeedUrlKey`は重複判定キーを返し、内部DocumentBuilder factoryは外部entity等を抑止する。 |
| [FeedRepository](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedRepository.kt) | `DefaultFeedRepository`（class）はFeedRepositoryを実装する。`inspect`/`addFeed`/`refreshFeed`が通常clientと保存済みruleのclientを選択し、store更新とchanges通知を行う。folder編集・ContentType override・rule操作も公開契約を具体化する。 |
| [FeedStore](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedStore.kt) | `FeedStore`（internal class）はfeed/folderのCRUDと取得結果を保存し、ContentSourceGatewayへsource情報・本文itemsの同期とsource切り離しを要求する。`ensureFolder`はimport用、`updateFeedSuccess`/`NotModified`/`Error`（updateFeed接頭辞）は取得結果保存の境界。 |
| [RssContentClassificationSourceQuery](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssContentClassificationSourceQuery.kt) | `RssContentClassificationSourceQuery`（class）はContentのsource分類queryを実装し、`findOverrides(sourceIds)`でfeed/folder overrideを返す。Content-owned記事tableを書かず、RSS-owned設定を問い合わせるadapter。 |
| [RssDatabaseSchema](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssDatabaseSchema.kt) | `rssDatabaseSchema`はfeed/folder、推薦policy・評価・feedback・taskとWeb scraping ruleのtable作成をdatabase bootstrapへ登録する。 |
| [RssRecommendationBackground](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssRecommendationBackground.kt) | `WorkManagerRssRecommendationTaskScheduler`（class）がfeed/未読のenqueue、kick、feedback学習debounce、pauseと充電再開をWorkManagerへ変換する。内部`RssRecommendationWorker`/`LearningWorker`/`ResumeOnChargingWorker`（RssRecommendation接頭辞）が評価・学習・再開を分担し、公開`RssRecommendationWorkerFactory`が依存providerを接続する。候補選択・delay・pause判定のinternal関数は独立testの対象。 |
| [RssRecommendationBackupRestoreInitializer](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssRecommendationBackupRestoreInitializer.kt) | `RssRecommendationBackupRestoreInitializer`（class）の`initialize`は復元後の推薦schemaを初期化して復元された実行taskを削除する入口。backup featureから呼び出される。 |
| [RssRecommendationStore](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssRecommendationStore.kt) | `DefaultRssRecommendationRepository`（class）はpolicy・評価・feedbackとtaskを保存する。条件変更とrevision更新、feedback消費付き条件適用、task enqueue/claim/complete/requeue/clearをtransaction境界で扱う。 |
| [RssWebScrapingRuleStore](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssWebScrapingRuleStore.kt) | `RssWebScrapingRuleStore`（internal class）がruleの一覧・保存・削除を行う。内部validate/normalize/pattern matching関数が保存前検査とURLからの適用rule選択を担う。 |
| [FeedClient](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/network/FeedClient.kt) | `FeedClient`（internal class）は`inspect`で直接feedまたはHTML中の候補を調べ、`fetchFeed`でHTTP validator付きRSS/Atomを取得・解析する。相対URLとcategoryを内部networkモデルへ変換する。 |
| [FeedNetworkModels](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/network/FeedNetworkModels.kt) | `ParsedArticle`/`ParsedFeed`/`FetchResult`（internal data class）は解析本文、feed情報とnot-modified/validatorをclientからstoreへ渡す。Domain公開モデルとは別の取得結果表現。 |
| [WebScrapingFeedClient](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/network/WebScrapingFeedClient.kt) | `WebScrapingFeedClient`（internal class）の`inspect`/`fetchFeed`/`test`がWebViewでrule functionを実行する。内部`WebScrapingPromisePoll`、start/poll/cleanup script builderとparserは非同期JavaScript結果・timeout・安全URLを扱い、previewへ戻す。 |

## 公開APIと構成要素間の接続

[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt)が`DefaultFeedRepository`、`DefaultFeedImportRepository`、`DefaultRssFeedContentReader`を公開契約へ接続する。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)は`RssViewModel.Factory`と`FeedViewModel.Factory`へ公開契約を渡し、`RedditSourceBoundary`のpredicateで通常RSSの表示・登録対象を分離する。RSS UIがRSS Dataを直接生成する接続ではない。

## 取得経路と失敗

入力URLに一致する保存済みWeb scraping ruleがあればそのruleを使い、なければ通常のRSS / Atom clientを使う。更新では通常clientへETagとLast-Modifiedを渡し、not-modified応答は取得日時だけを更新する。成功後は変更通知と更新callbackを発行する。失敗時はFeedにuser messageを保存して通知し、errorを呼出元に伝播する。Web scrapingの実行にはAndroid contextが必要である。

## 推薦 queue と実行条件

WorkManagerRssRecommendationTaskSchedulerは未評価、古いrevision、推論失敗の記事をdurable queueへ追加する。条件が無効なら評価queueを空にするが、未処理feedbackがあれば学習の起動を継続する。Cloud選択時はnetwork接続をconstraintにする。LocalとCloudのpauseをそれぞれ尊重し、充電再開はLocal向けに予約する。feedback学習は最後の追加からのdelayを計算し、新規追加時にunique workを置き換える。

## 変更と検証

FeedClientTestはRSS / Atom解析、相対URL、categoryと条件付きrequestを確認する。WebScrapingFeedClientTestとRssWebScrapingRuleStoreTestはrule実行と保存の入口となる。RssRecommendationBackgroundTestは再評価候補と30秒debounceの計算を確認するが、OSによる全Worker実行を保証するテストではない。provider routingとstructured tool出力は対応するRSS Dataテストで検証する。

## ソースと検証の入口

- [FeedRepository](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedRepository.kt)
- [RssRecommendationBackground](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssRecommendationBackground.kt)
- [FeedStore](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedStore.kt)
- [DefaultRssRecommendationEngine](../../../feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/DefaultRssRecommendationEngine.kt)
- [FeedClientTest](../../../feature/rss/data/src/test/kotlin/dev/terashima/yomitorirss/rss/data/network/FeedClientTest.kt)
- [RssRecommendationBackgroundTest](../../../feature/rss/data/src/test/kotlin/dev/terashima/yomitorirss/rss/data/RssRecommendationBackgroundTest.kt)
- [RssRecommendationProviderRoutingTest](../../../feature/rss/data/src/test/kotlin/dev/terashima/yomitorirss/rss/data/RssRecommendationProviderRoutingTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [rss-domain module](rss-domain.md)
- [rss-ui module](rss-ui.md)
- [article-data module](article-data.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
