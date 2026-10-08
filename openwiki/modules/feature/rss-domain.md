---
type: module
title: "RSS Domain：購読更新と推薦ルール"
description: "Feed 契約、同時更新の制御、推薦評価と除外 feedback 学習の状態規則を定義する。"
tags: [rss, domain, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-4d62659608ab970f0b42bc09
    resource: repo://feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RefreshFeedsUseCase.kt
  - id: openwiki-source-d7f71befc8829b1f358389ee
    resource: repo://feature/rss/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssRecommendation.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と入口

`:feature:rss:domain` はFeedとfolder、import、Web scraping rule、および推薦処理の契約を定義するJVM moduleである。記事そのものの保存はContent-owned gatewayへつなぐDataの責務とし、Bookmark状態はCurationに残す。PodcastがURLからfeedを読むためのRssFeedContentReaderも公開するが、Podcastのsource登録や生成履歴をここに所有しない。

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
