---
type: module
title: "Integrated UI：RSS・Reddit・メールの横断表示"
description: "各機能の画面状態を統合一覧へ投影し、source ごとの ViewModel に操作を戻す presentation module。"
tags: [integrated, ui, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-b86daa9c0cafcbcf6f74668c
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedProjection.kt
  - id: openwiki-source-b54846ffde7316b1da5973c6
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedRoute.kt
  - id: openwiki-source-101a3256f48d88cb1015b07d
    resource: repo://feature/integrated/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/integrated/ui/IntegratedTargetDispatcher.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と所有権

`:feature:integrated:ui` はRSS、Reddit、メールの画面状態を横断して表示するUI専用moduleである。独自のDomain/Data moduleやdurable未読・保存状態を持たず、IntegratedRouteが各機能のViewModelを受け取ってprojectionとdispatcherを接続する。購読型動画を統合一覧やsource filterへ加えない。

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
