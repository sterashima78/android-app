---
type: module
title: "Reddit Domain：source 判定と購読 identity"
description: "Reddit の community / thread URL 正規化と分類を所有し、通常 RSS との境界を公開する。"
tags: [reddit, domain, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3ff5904482694e3de9e50787
    resource: repo://feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditModels.kt
  - id: openwiki-source-9bf001d74854705d933cad99
    resource: repo://feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditSourceBoundary.kt
  - id: openwiki-source-2e6d18c781544d94f50e2cae
    resource: repo://feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditUrls.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と所有権

`:feature:reddit:domain` はReddit固有の入力、source分類と購読契約を定義するJVM moduleである。communityとthreadを別の購読種別として表し、購読一覧、追加・解除、全件更新と進捗のAPIを公開する。ArticleモデルはContent Domainを再利用するが、Redditを通常RSSと区別するルールはReddit-owned boundaryへ集約する。

## 入力と identity の流れ

community入力はr/名前や対応するRedditのcommunity URLからnewのRSS URLへ正規化する。threadはcommentsのpost identityを抽出し、comments RSS URLへ変換する。threadのpermalinkをcommunity登録と誤認しない。URL解析はReddit hostを検査し、無効な入力ではnullを返して呼出元に追加拒否や説明を委ねる。

## 他 module との境界

RedditSourceBoundaryはArticleを閲覧先URLではなくsourceFeedUrlから分類する。RSS画面の登録入力判定ではcommunity shorthand、thread、Reddit feedを通常RSSから除外する。consumerはこのboundaryの肯定・否定queryを利用し、独自の文字列判定を複製しない。Domain自身はSQLite、HTTP client、Android ViewModelを持たず、購読取得の具体化はDataへ任せる。

## 変更と検証

対応URL形式を増やすときは正規化先、post identity、host判定とcommunity / threadの排他性を一緒に確認する。RedditUrlsTestはshorthandとpermalinkの正規化および誤認防止、RedditSourceBoundaryTestは通常RSSの通過とReddit分類を検証する。RedditSourceBoundaryUsageArchitectureTestはboundary利用を維持するための入口となる。

## ソースと検証の入口

- [RedditSourceBoundary](../../../feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditSourceBoundary.kt)
- [RedditUrls](../../../feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditUrls.kt)
- [RedditModels](../../../feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditModels.kt)
- [RedditUrlsTest](../../../feature/reddit/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditUrlsTest.kt)
- [RedditSourceBoundaryTest](../../../feature/reddit/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditSourceBoundaryTest.kt)
- [RedditSourceBoundaryUsageArchitectureTest](../../../feature/reddit/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditSourceBoundaryUsageArchitectureTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [reddit-data module](reddit-data.md)
- [reddit-ui module](reddit-ui.md)
- [rss-domain module](rss-domain.md)
- [article-domain module](article-domain.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
