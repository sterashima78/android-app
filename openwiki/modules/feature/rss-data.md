---
type: module
title: "RSS Data：取得・永続化と推薦 background work"
description: "通常 feed と Web scraping の取得、RSS-owned 保存、推薦 queue と feedback 学習の WorkManager 接続を実装する。"
tags: [rss, data, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-e9f3f47229ba7ed81b4610db
    resource: repo://feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/FeedRepository.kt
  - id: openwiki-source-5bbd006ada9b61d59b34772e
    resource: repo://feature/rss/data/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/data/RssRecommendationBackground.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と構成

`:feature:rss:data` はFeedRepository、OPML import、feed content reader、推薦repositoryとengineを実装する。FeedStoreが購読情報を保持し、記事の作成・source切り離しはContentSourceGatewayへ要求する。RSS-owned設定、推薦結果、feedbackとtaskの永続化はこのmoduleのstoreに閉じ、ContentやCurationのtable ownershipを移さない。

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
