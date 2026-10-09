---
type: module
title: Reddit Data：RSS capability を利用する購読 adapter
description: Reddit 固有の入力と重複検査を RSS FeedRepository へ適合し、購読と更新結果を公開する。
tags:
  - reddit
  - data
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-b40cffd1490e98cb27462fab
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt
  - id: openwiki-source-5ef34b20493d1df075d7352e
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt
  - id: openwiki-source-7b8be6ef333687a999de6f59
    resource: repo://feature/reddit/data/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/data/DefaultRedditRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と構成

`:feature:reddit:data` はRedditRepositoryをDefaultRedditRepositoryで実装する。Reddit独自のdatabaseやnetwork clientを追加せず、RSS DomainのFeedRepositoryから購読情報と取得処理を利用する。Gradle依存はReddit、Content、RSSのDomainとcoroutinesで、具体的RSS Dataを直接参照しない。変更通知はFeedRepositoryのStateFlowをそのまま公開する。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [DefaultRedditRepository](../../../feature/reddit/data/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/data/DefaultRedditRepository.kt) | `DefaultRedditRepository`（class）は`RedditRepository`を実装するadapter。`listSubscriptions`でRSS購読をRedditモデルへ投影し、`addCommunity`/`subscribeThread`はURLまたはpost identityの重複を検査する。`unsubscribeThread`/`deleteSubscription`は既存購読のidentityを検証して削除する。`refreshAll`は購読ごとの失敗を集計する。 |

## 公開APIと構成要素間の接続

[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt)が`DefaultRedditRepository(feedRepository)`を作り、RSS Domain契約と接続する。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)の`redditViewModelFactory`がReddit・Article・BookmarkのRepositoryをUIへ注入する。購読状態の保存者はRSS、記事はContent、保存/あとで読むはCurationに分かれる。

## 購読の追加と解除

一覧はReddit feedだけを選び、community / threadの判定、取得日時とerrorをRedditSubscriptionへ投影する。community入力を正規化して同一URLの購読を拒否し、threadはpost identityで重複を確認する。thread追加時は既存コメントを既読として取り込み、以後の新着を未読にするためmarkExistingArticlesReadを指定する。解除では正規化したthread identityに対応するFeedを削除する。

## 更新と失敗

全件更新はReddit feedだけを順に処理する。各Feedの失敗は件数へ集計し、次のFeedへ進んで進捗を返すため、1件の取得失敗で他の購読を打ち切らない。無効入力、重複、未購読のthread、存在しない購読は説明付きerrorになる。永続化やsource記事の切り離しはRSSとContentの既存capabilityを通して行う。

## 変更と検証

Reddit固有の取得policyを変えるときはRSSの一般購読を巻き込まないfilterを保つ。DefaultRedditRepositoryTestは通常Feedの除外、community URLの大文字小文字・末尾slashを無視した重複拒否、更新の部分失敗集計を検証する。thread追加の既読設定はproductionコードで確認できるが、このtest classはその振る舞いを直接assertしていない。

## ソースと検証の入口

- [DefaultRedditRepository](../../../feature/reddit/data/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/data/DefaultRedditRepository.kt)
- [DefaultRedditRepositoryTest](../../../feature/reddit/data/src/test/kotlin/dev/terashima/yomitorirss/feature/reddit/data/DefaultRedditRepositoryTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [reddit-domain module](reddit-domain.md)
- [reddit-ui module](reddit-ui.md)
- [rss-data module](rss-data.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
