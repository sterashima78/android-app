---
type: module
title: Reddit Domain：source 判定と購読 identity
description: Reddit の community / thread URL 正規化と分類を所有し、通常 RSS との境界を公開する。
tags:
  - reddit
  - domain
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-3ff5904482694e3de9e50787
    resource: repo://feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditModels.kt
  - id: openwiki-source-9bf001d74854705d933cad99
    resource: repo://feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditSourceBoundary.kt
  - id: openwiki-source-2e6d18c781544d94f50e2cae
    resource: repo://feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditUrls.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と所有権

`:feature:reddit:domain` はReddit固有の入力、source分類と購読契約を定義するJVM moduleである。communityとthreadを別の購読種別として表し、購読一覧、追加・解除、全件更新と進捗のAPIを公開する。ArticleモデルはContent Domainを再利用するが、Redditを通常RSSと区別するルールはReddit-owned boundaryへ集約する。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [RedditModels](../../../feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditModels.kt) | `RedditSubscriptionKind`（enum）はcommunityとthreadを区別し、`RedditSubscription`（data class）は購読identity・取得結果を渡す。`RedditRefreshResult`は全件更新の総数と失敗数。`RedditRepository`（interface）は一覧、追加、解除、更新と変更通知の公開境界。`Article.isRedditArticle()`はsource feedによる分類を提供する。 |
| [RedditSourceBoundary](../../../feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditSourceBoundary.kt) | `RedditSourceBoundary`（object）はArticle/feedの肯定・否定query、thread identity取得、通常RSS登録入力判定を集約する。RSSやSummary consumerがRedditのURL規則を複製せず利用する入口。 |
| [RedditUrls](../../../feature/reddit/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditUrls.kt) | トップレベル関数`redditCommunityFeedUrl`、`redditThreadId`、`redditThreadFeedUrl`は入力を正規化し、認識できない入力にnullを返す。`isRedditFeedUrl`は分類、`redditSubscriptionKind`は購読種別判定を担う。内部URI helperがhostとpathを検査する。 |

## 公開APIと構成要素間の接続

[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt)が`DefaultRedditRepository(feedRepository)`を作り、RSS Domain契約と接続する。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`redditViewModelFactory`がReddit・Article・BookmarkのRepositoryをUIへ注入する。購読状態の保存者はRSS、記事はContent、保存/あとで読むはCurationに分かれる。

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
