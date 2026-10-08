---
type: module
title: "RSS UI：未読・推薦と「あとで読む」レビュー"
description: "RSS の閲覧・購読設定、推薦注記、除外参考と開始時点に固定した「あとで読む」レビューを提供する。"
tags: [rss, ui, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-69d9222d78384bea6ea4f37c
    resource: repo://feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/ReadLaterReviewScreen.kt
  - id: openwiki-source-618eb184f7457b7f18292205
    resource: repo://feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeature.kt
  - id: openwiki-source-d1a2d1a8a0ca9b033a988609
    resource: repo://feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と入口

`:feature:rss:ui` は未読、あとで読む、feed管理、設定のpresentationを担う。RssViewModelがContentとCurationの状態を読み、FeedViewModelが購読の編集と更新を扱う。記事表示はArticle UIを利用し、推薦推論とdurable taskはRSS Domainのservice・schedulerへ要求する。Repository実装やWorkerを画面内に作らない。

## 未読から除外参考へ

未読一覧では通常左を既読、深い左を除外参考、右をBookmark、深い右をあとで読むへ結び付ける。推薦状態から数値、評価待ち、情報不足、判定失敗を注記として投影する。除外参考はfeedbackを先に記録して学習を予約し、Contentを既読にする。既読化に失敗した場合はfeedbackを取り消し、一時非表示を解除して理由を表示する。

## レビューと一時状態

あとで読むの表示日時は保存日時を渡す一方、一覧順序は記事公開日時の古い順・新しい順で切り替える。レビュー開始時のidentity一覧と現在indexをrememberSaveableに固定し、あとから追加された記事を同じsessionへ差し込まない。現在の保存状態に存在しなくなった記事は次へ進め、要約の準備や再試行はcallbackへ委譲する。Undoは短時間SnackbarからCurationの復元操作へ戻す。

## 変更と検証

RssUiStateTestは推薦注記と深い左スワイプ設定、ReadLaterDisplayedAtTestは保存日時の受け渡しを検証する。RssRecommendationLearningStatusTestは未処理feedbackの表示を確認する。レビューの固定snapshotや要約要求の調整では、開始後の追加、削除、戻り遷移と要約失敗中の整理を実際の画面フローでも確認する。進捗のための新しいdurable tableは作らない。

## ソースと検証の入口

- [RssViewModel](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssViewModel.kt)
- [RssFeature](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeature.kt)
- [ReadLaterReviewScreen](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/ReadLaterReviewScreen.kt)
- [RssUiStateTest](../../../feature/rss/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/RssUiStateTest.kt)
- [ReadLaterDisplayedAtTest](../../../feature/rss/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/ReadLaterDisplayedAtTest.kt)
- [RssRecommendationLearningStatusTest](../../../feature/rss/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/RssRecommendationLearningStatusTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [rss-domain module](rss-domain.md)
- [rss-data module](rss-data.md)
- [article-ui module](article-ui.md)
- [bookmark-data module](bookmark-data.md)
- [integrated-ui module](integrated-ui.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
