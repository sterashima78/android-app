---
type: module
title: RSS UI：未読・推薦と「あとで読む」レビュー
description: RSS の閲覧・購読設定、推薦注記、除外参考と開始時点に固定した「あとで読む」レビューを提供する。
tags:
  - rss
  - ui
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-5ef34b20493d1df075d7352e
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt
  - id: openwiki-source-69d9222d78384bea6ea4f37c
    resource: repo://feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/ReadLaterReviewScreen.kt
  - id: openwiki-source-618eb184f7457b7f18292205
    resource: repo://feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeature.kt
  - id: openwiki-source-d1a2d1a8a0ca9b033a988609
    resource: repo://feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と入口

`:feature:rss:ui` は未読、あとで読む、feed管理、設定のpresentationを担う。RssViewModelがContentとCurationの状態を読み、FeedViewModelが購読の編集と更新を扱う。記事表示はArticle UIを利用し、推薦推論とdurable taskはRSS Domainのservice・schedulerへ要求する。Repository実装やWorkerを画面内に作らない。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [FeedDialogs](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedDialogs.kt) | `AddFeedDialog`と`CandidateDialog`（Composable）が入力URLとfeed候補選択をcallbackへ返す。取得・登録はFeedViewModelの責務。 |
| [FeedScreen](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedScreen.kt) | `FeedScreen`（Composable）は購読・folder一覧、rename/delete/moveとContentType編集をcallbackで公開する。内部dialogとheader/cardが選択状態と継承表示を組み立てる。 |
| [FeedViewModel](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/FeedViewModel.kt) | `FeedUiState`/`WebScrapingRuleTestUiState`（data class）が購読、候補、import・更新進捗、rule previewを保持する。`FeedViewModel`（class）は`refresh`、`inspectAndAddFeed`/`addFeedCandidate`、feed/folder編集と移動、ContentType設定、rule保存/削除/test、`importOpml`をDomainへ委譲する。候補dismiss、追加/import完了consume、rule test clear、message dismissは一時UI状態だけを更新する。`Factory`がrepository、更新UseCase、import契約とRSS選択predicateを注入する。 |
| [NavigationDestination](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/NavigationDestination.kt) | RSS route定数と`rssTabForRoute`/`routeForRssTab`/`rssDestinationTitle`（トップレベル関数）がタブと遷移情報を変換する。 |
| [ReadLaterReviewScreen](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/ReadLaterReviewScreen.kt) | `ReadLaterReviewScreen`（internal Composable）は開始時の記事identity snapshot、indexとUndoを所有する。内部actions/header/summary/completed表示が整理と要約準備callbackを接続する。 |
| [RssFeature](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssFeature.kt) | `RssTab`（enum）と`RssScreen`（Composable）が未読・あとで読む・feed・設定を分岐する。`RssUiState.recommendationAnnotationFor`（公開拡張関数）は推薦結果を注記へ、内部`readLaterDisplayedAtByArticleId`は保存日時を一覧へ投影する。 |
| [RssRoutes](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssRoutes.kt) | `RssRouteController`（class）と`rememberRssRouteController`が`requestAddFeed`/`requestMarkAllRead`で追加dialogと全件既読確認を管理する。`RssRoute`/`FeedRoute`/`RssSettingsRoute`（Composable）は各ViewModelのstateと画面callbackを接続する。 |
| [RssViewModel](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssViewModel.kt) | `RssUiState`（data class）を所有する`RssViewModel`（class）が記事一覧・Bookmark状態・推薦snapshotをまとめる。`refresh`による再取得、既読/未読、保存/解除、あとで読む/復元、一括既読、除外参考/復元は各Domain操作へ委譲する。`setArticleContentType`による記事分類設定、推薦条件・provider・学習reset変更はserviceとschedulerへ渡す。内部reloadが変更通知から再取得し、`Factory`がconsumer側の記事選択predicateを受ける。 |
| [RssWebScrapingRulesUi](../../../feature/rss/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/rss/RssWebScrapingRulesUi.kt) | `RssWebScrapingRulesUi`（Composable）はrule一覧・編集・削除・testのcallbackをまとめる。内部編集dialogはURL pattern/function/timeoutを入力し、preview結果と失敗を表示する。 |

## 公開APIと構成要素間の接続

記事操作では`markRead`/`markUnread`、`saveAndRead`/`unsave`、`readLater`/`removeReadLater`を使う。レビュー専用の`reviewUnsave`/`reviewRemoveReadLater`はUndoに必要な保存状態を扱い、`restoreReadLater`が復元する。`markAsExclusionReference`がfeedback記録と既読化を調整し、`saveRecommendationCondition`/`setRecommendationExecutionProvider`/`resetRecommendationLearning`がservice・schedulerへ渡す設定入口になる。`setArticleContentType`は記事単位のoverrideを変更する。

[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt)が`DefaultFeedRepository`、`DefaultFeedImportRepository`、`DefaultRssFeedContentReader`を公開契約へ接続する。[AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt)は`RssViewModel.Factory`と`FeedViewModel.Factory`へ公開契約を渡し、`RedditSourceBoundary`のpredicateで通常RSSの表示・登録対象を分離する。RSS UIがRSS Dataを直接生成する接続ではない。

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
