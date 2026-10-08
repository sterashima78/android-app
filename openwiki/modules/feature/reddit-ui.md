---
type: module
title: "Reddit UI：購読と source 固有記事の閲覧"
description: "Reddit の未読・あとで読む・履歴と購読管理を表示し、Content / Curation の公開操作へ委譲する。"
tags: [reddit, ui, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-6917f9301ade70b6884fdefb
    resource: repo://feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と入口

`:feature:reddit:ui` はRedditRouteとRedditScreenを提供し、RedditViewModelが記事一覧と購読状態をまとめる。Article UIの共通行を利用し、Reddit-owned Domainでsourceを判定する。購読を持つ場合は初期読込後に更新し、Content、Curation、Redditそれぞれの変更通知から状態を再読込する。Dataの保存構造は画面へ公開しない。

## 購読から更新まで

community追加やthread購読・解除をRedditRepositoryへ要求し、成功後の再読込とmessageを発行する。更新中は重複更新を抑止し、Repositoryから渡された完了数・総数を進捗表示へ変換する。結果は購読なし、全成功、全失敗、部分成功でmessageを分ける。初期更新は完了messageを抑え、ユーザーが手動更新した場合に結果を知らせる。

## 状態と操作の境界

既読・未読はContent、保存・あとで読むはCurationへ委譲する。処理中のarticle identityをUiStateのhidden集合へ入れて即時表示を調整し、永続状態としては保存しない。threadの購読状態はRedditのidentityルールから得る。統合画面でもこのViewModelを利用するので、source固有操作を統合moduleに再実装する必要はない。

## 変更と検証

RedditViewModelTestは通常RSSの記事を混ぜず対象記事を状態へ投影することと、購読なしの手動更新messageを検証する。初期refresh、部分失敗のmessage、threadメニューの変更ではDomain/Data側のテストも併用し、UIとsource判定の意味を合わせる。共通スワイプ部品を変更する場合はArticle UIと統合表示の利用者も確認する。

## ソースと検証の入口

- [RedditViewModel](../../../feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditViewModel.kt)
- [RedditScreen](../../../feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditScreen.kt)
- [RedditRoute](../../../feature/reddit/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditRoute.kt)
- [RedditViewModelTest](../../../feature/reddit/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/reddit/RedditViewModelTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [reddit-domain module](reddit-domain.md)
- [reddit-data module](reddit-data.md)
- [article-ui module](article-ui.md)
- [integrated-ui module](integrated-ui.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
