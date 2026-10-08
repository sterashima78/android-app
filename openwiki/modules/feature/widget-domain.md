---
type: module
title: "Widget Domain：記事操作とframework境界"
description: "Widget向け記事投影、既読・あとで読む・更新の契約、providerと起動アクションを説明する。"
tags: [widget, domain, modules]
sources:
  - id: openwiki-source-596a7a5d51ba874542ec1fd4
    resource: repo://feature/widget/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetLaunchContract.kt
  - id: openwiki-source-79598d1496a0fc0aacfb47e5
    resource: repo://feature/widget/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetRefreshScheduler.kt
  - id: openwiki-source-3e4cd9fd5f22939e26cb5835
    resource: repo://feature/widget/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T14:01:52.039Z
---

# Widget Domain：記事操作とframework境界

`:feature:widget:domain` はホーム画面Widgetから使う記事一覧と操作、更新予約、app起動の契約を所有する。JVMモジュールであり、RemoteViews、BroadcastReceiver、WorkManager、databaseを取り込まない。Androidが自動生成するcomponentが利用する能力を狭いinterfaceに分けている。

## 一覧と操作の契約

`WidgetArticle`はid、URL、title、source title、publishedAtを保持する表示用投影である。`WidgetRepository`は同期的な未読一覧取得と、suspendの既読・あとで読む・feed更新を提供する。表示は軽い契約を利用できる一方、永続状態の更新先やnetwork policyは実装層へ委ねる。Widget固有のコピーをdurableなsource of truthとして作る契約ではない。

## frameworkからの入口

`WidgetRepositoryProvider`と`WidgetRefreshSchedulerProvider`はApplicationが提供するRepositoryとenqueue能力をAndroid-created Widgetに渡すための境界となる。起動actionは`WidgetLaunchContract`へ集約され、Task画面を開くaction、記事を開くaction、記事URL extraを共有する。実際のIntent生成やTask完了操作はWidget UIが所有する。

`WidgetArticleTest`は表示情報の保持を確認する。操作追加ではDataのowner repository委譲とUIの非同期broadcast処理を確認し、同期的な一覧APIを変更する場合はRemoteViewsFactoryの更新タイミングも調べる。Task一覧のモデルをこの記事契約へ無理に統合せず、既存Task Domain境界を尊重する。

## 調査・変更の入口

[WidgetRepository](../../../feature/widget/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetRepository.kt)、[WidgetRefreshScheduler](../../../feature/widget/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetRefreshScheduler.kt)、[WidgetLaunchContract](../../../feature/widget/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetLaunchContract.kt)、[WidgetArticleTest](../../../feature/widget/domain/src/test/kotlin/dev/terashima/yomitorirss/widget/WidgetArticleTest.kt) を起点に責務と呼び出し側を確認する。

関連: [widget data](widget-data.md) / [widget ui](widget-ui.md) / [全体構成](../../architecture/system.md)。
