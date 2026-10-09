---
type: module
title: Widget UI：RemoteViewsと記事・Task操作
description: ホーム画面の未読記事とTask Widget、非同期broadcast処理と変更通知からの再描画を説明する。
tags:
  - widget
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-4c64147c561e8d4f89c67323
    resource: repo://feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt
  - id: openwiki-source-5737dd0f50455c692134d892
    resource: repo://feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetProvider.kt
  - id: openwiki-source-124791cc418e066ea195268c
    resource: repo://feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetRefreshObserver.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Widget UI：RemoteViewsと記事・Task操作

`:feature:widget:ui` は未読記事とTaskのAppWidgetProvider、RemoteViewsService、layout資産、updaterを所有するAndroidモジュールである。Widget DomainとTask Domainを利用し、databaseやWorkManagerの具体実装には依存しない。Android-created componentはApplicationのproviderから能力を取得する。

## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [UnreadArticlesWidgetProvider](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetProvider.kt) / [UnreadArticlesWidgetUpdater](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetProvider.kt) / [UnreadArticlesWidgetService](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetProvider.kt) | 公開receiver/object/service。onUpdate/onReceive、updateAll、onGetViewFactoryで未読記事RemoteViewsと読了・後で読む・更新commandを接続する。 |
| [UnreadArticlesRemoteViewsFactory](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetProvider.kt) | private factory。listUnreadArticlesを同期再読し、getViewAtで行を作りIDをstableに保つ。 |
| [TaskWidgetProvider](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt) / [TaskWidgetUpdater](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt) / [TaskWidgetService](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt) | 公開receiver/object/service。task一覧更新と完了・アプリ起動を接続し、表示はTaskRepositoryに委譲する。 |
| [TaskRemoteViewsFactory](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt) | private factory。task snapshotから未完了tree行を作り、階層titleと期限labelを表示する。 |
| [UnreadArticlesWidgetRefreshObserver](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetRefreshObserver.kt) | 公開class。startでDataChangeNotifierのversion Flowをapplication scopeで観測し、updateAllを呼ぶ。 |
| [requireTaskRepository](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/WidgetRepositoryAccess.kt) / [configureTaskWidgetLaunch](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt) | internal Context/Intent拡張群。ApplicationのTaskRepositoryProvider取得とtask launch action設定。後者はTaskWidgetProvider.ktにある。 |

## 主要なAPI・構成要素の接続

未読receiverは`onReceive`で項目actionを判別し、goAsyncの寿命内でRepositoryのread/read-later、またはScheduler.enqueueを呼んでRemoteViewsを更新する。factoryはApplicationのWidgetRepositoryProviderから同期一覧を得る。記事項目openはURLをACTION_VIEWで開き、task openは共有[WidgetLaunchContract](widget-domain.md)を使う`configureTaskWidgetLaunch`でapp Intentを設定する。

task receiverの完了actionはTaskRepositoryへ戻し、`TaskWidgetUpdater.updateAll`で一覧を再表示する。`TaskWidgetService.onGetViewFactory`は内部factoryを作り、task所有状態はTask Domain/Dataに残す。Applicationが必要providerを実装しない場合は明示errorとなる。

`UnreadArticlesWidgetRefreshObserver.start`はjobを保持して二重観測を避け、更新例外はrunCatchingで観測全体へ伝播させない。[AppBackgroundRuntime](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/AppBackgroundRuntime.kt)がこのobserverを起動する。receiver/serviceのexportedとbind権限は[AndroidManifest](../../../feature/widget/ui/src/main/AndroidManifest.xml)にある。


## ホーム画面からの操作

未読記事はRemoteViewsFactoryで一覧へ投影し、開く・既読・あとで読むを別actionで処理する。記事を開く操作はURLを確認して外部ACTION_VIEWへ渡す。既読とあとで読むは`goAsync()`を使いIO coroutineでRepositoryへ渡し、再描画後にfinallyでpending resultを終了する。Task側も完了をTask Repositoryへ渡して一覧を更新し、開く操作はTask画面への起動contractを利用する。



## 更新と寿命

未読記事の更新ボタンはDomain schedulerへ予約を要求する。`UnreadArticlesWidgetRefreshObserver`はapplication contextとIO scopeを保持し、dataChangesのFlowごとに全Widgetを再描画する。startはjobが存在すれば追加購読せず、複数回の初期化でobserverを増やさない。Widget表示はアプリ画面が表示されていることを前提にしない。

`TaskWidgetProviderTest`はTask起動actionとactivity再利用flags、`UnreadArticlesWidgetProviderTest`は操作識別子の分離を確認する。変更時はRemoteViewsのitem action、manifestのservice/receiver、非同期broadcast完了を照合し、失敗が吸収されてもpending resultを残さないようにする。

## 調査・変更の入口

[UnreadArticlesWidgetProvider](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetProvider.kt)、[TaskWidgetProvider](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProvider.kt)、[UnreadArticlesWidgetRefreshObserver](../../../feature/widget/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadArticlesWidgetRefreshObserver.kt)、[TaskWidgetProviderTest](../../../feature/widget/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/widget/TaskWidgetProviderTest.kt)、[UnreadArticlesWidgetProviderTest](../../../feature/widget/ui/src/test/kotlin/dev/terashima/yomitorirss/widget/UnreadArticlesWidgetProviderTest.kt) を起点に責務と呼び出し側を確認する。

関連: [widget domain](widget-domain.md) / [widget data](widget-data.md) / [全体構成](../../architecture/system.md)。
