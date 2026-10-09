---
type: module
title: Integrated UI：RSS・Reddit・メールの横断表示
description: 各機能の画面状態を統合一覧へ投影し、source ごとの ViewModel に操作を戻す presentation module。
tags:
  - integrated
  - ui
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-b86daa9c0cafcbcf6f74668c
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedProjection.kt
  - id: openwiki-source-b54846ffde7316b1da5973c6
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedRoute.kt
  - id: openwiki-source-5436d14c914fc4de7f847685
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt
  - id: openwiki-source-101a3256f48d88cb1015b07d
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Integrated UI：RSS・Reddit・メールの横断表示

## 責務と所有権

`:feature:integrated:ui` はRSS、Reddit、メールの画面状態を横断して表示するUI専用moduleである。独自のDomain/Data moduleやdurable未読・保存状態を持たず、IntegratedRouteが各機能のViewModelを受け取ってprojectionとdispatcherを接続する。購読型動画を統合一覧やsource filterへ加えない。



## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [INTEGRATED_ROUTE](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/NavigationDestination.kt) / [INTEGRATED_TITLE](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/NavigationDestination.kt) | 公開const。appのnavigation destination契約。 |
| [IntegratedRoute](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedRoute.kt) | 公開Composable。RSS/Reddit/Feed/Mail ViewModelを観測し、mailbox切替、projection、dispatch、刷新、message通知を接続する。 |
| [IntegratedTab](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [IntegratedSource](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [IntegratedItem](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [IntegratedItemAction](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) | 公開enum・data class群。未読/後で読む/履歴とsource filter、key付き表示行、long press actionを表す。 |
| [IntegratedScreen](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) | 公開Composable。itemsとcallbackを受け、source counts、refresh、tab、swipeとlong press menuを表示する。 |
| [IntegratedTarget](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedProjection.kt) / [IntegratedEntry](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedProjection.kt) / [integratedEntries](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedProjection.kt) | internal sealed型・data class・関数。RSS/Reddit記事・Mail threadの元targetと表示projectionを結び、tab別の抽出・時刻順を計算する。 |
| [IntegratedArticleTargetActions](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt) / [IntegratedMailTargetActions](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt) / [IntegratedTargetDispatcher](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt) / [integratedTargetDispatcher](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt) | internal callbackモデル・class・factory。markProcessed、除外参考、未読、保存/後で読む解除、star/archive/openをsource ownerへ振り分ける。 |
| [integratedItemActions](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedItemActions.kt) | internal関数。RSS/Redditのはてなcomment、Reddit要約とthread購読/解除menuを作る。 |
| [IntegratedSwipeOperation](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [IntegratedSwipeTone](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [IntegratedSwipeActionSpec](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [IntegratedSwipeActions](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) | internal enum・data class群。tab/sourceごとのoperation、tone、dismiss可否、近/遠actionを宣言する。 |
| [integratedSwipeActions](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) / [integratedLeftSwipeBehavior](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreen.kt) | internal関数。操作仕様を選び、未読RSS左側の遠actionだけDeliberateFarActionへ変更する。 |

## 主要なAPI・構成要素の接続

`IntegratedRoute`はmailのqueryとaccountを初期化し、tab変更をUNREAD/READ_LATER/INBOX mailboxへ戻す。`integratedEntries`は非表示RSS/Reddit記事を除き、Mailはinbox/unread/read-later状態で絞る。未読・履歴は降順、後で読むは昇順にする。keyにはsource、Mailではaccountも含み、同名IDの衝突を避ける。

`IntegratedScreen`はsource filterをrememberSaveableで保存し、件数はfilter前itemsから計算する。operation確定は`targetsByKey`→`IntegratedTargetDispatcher`→owner ViewModelへ流れ、Integratedは独自Repository・tableを作らない。null targetは無操作、RSS除外参考はRSSだけ、star/archiveはMailだけ、save/unsaveは記事だけに適用する。

未読RSSは左swipeで既読、遠左で除外参考、右で保存、遠右で後で読む。Mailのstar/archiveと後で読む解除はtabによりdismiss可否が異なる。遠左の除外参考は`integratedLeftSwipeBehavior`で意図的な遠操作へ接続する。長押しmenuは`integratedItemActions`でowner種別に応じて生成する。

app presentationがowner ViewModelと記事/メールnavigation callbackをRouteへ渡す。失敗表示と永続stateはownerにあり、RouteはRSS/Mail messageをsnackbar表示後にdismissする。境界は[IntegratedRouteAdapterTest](../../../feature/integrated/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedRouteAdapterTest.kt)、dispatchは[IntegratedTargetDispatcherTest](../../../feature/integrated/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcherTest.kt)で確認する。


## 投影と並び順

IntegratedProjectionは一覧itemと元の操作targetを組にする。未読ではRSS / Redditのhidden記事を除き、メールは未読かつ受信トレイ内に限定する。未読と履歴は新しい順、あとで読むは古い順に並ぶ。記事履歴では既読日時を優先し、時刻解析に失敗した場合は公開・取得日時へ順にfallbackする。RSS推薦注記も既存stateから投影して独自評価を持たない。

## 操作と lifecycle

IntegratedTargetDispatcherはtargetのsourceに応じて既読、保存、あとで読む、開く等を元のViewModelへ返す。除外参考はRSSだけ、starとarchiveはメールだけに適用する。Routeは選択tabをrememberSaveableで保持し、tabに対応するmailboxを選ぶ。更新操作はFeed、Reddit、Mailへ個別に要求し、各featureの初期化完了を待って一覧を表示する。

## 変更と検証

source追加はprojectionだけでなくkeyの一意性、並び順、filter、dispatcherとowner側capabilityを合わせて検討する。IntegratedRouteAdapterTestは投影対象・並び順・推薦注記・元target保持を検証する。IntegratedTargetDispatcherTestはsource別の委譲と対象外操作、IntegratedScreenTestはtabや表示補助を確認する。統合表示を理由に別Contextのtableへ直接アクセスしない。

## ソースと検証の入口

- [IntegratedProjection](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedProjection.kt)
- [IntegratedTargetDispatcher](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt)
- [IntegratedRoute](../../../feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedRoute.kt)
- [IntegratedRouteAdapterTest](../../../feature/integrated/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedRouteAdapterTest.kt)
- [IntegratedTargetDispatcherTest](../../../feature/integrated/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcherTest.kt)
- [IntegratedScreenTest](../../../feature/integrated/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedScreenTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [rss-ui module](rss-ui.md)
- [reddit-ui module](reddit-ui.md)
- [bookmark-domain module](bookmark-domain.md)
- [mail-ui module](mail-ui.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
