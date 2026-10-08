---
type: module
title: "Widget Data：owner委譲とbackground更新"
description: "Content・Bookmark・RSS Domainへの操作委譲とWidget更新Workerのnetwork・失敗処理を説明する。"
tags: [widget, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-2e3f49475891b405ca0b07ba
    resource: repo://feature/widget/data/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/data/DefaultWidgetRepository.kt
  - id: openwiki-source-48478db12649c9cc95a50e99
    resource: repo://feature/widget/data/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadWidgetRefreshWorker.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Widget Data：owner委譲とbackground更新

`:feature:widget:data` は`DefaultWidgetRepository`と更新scheduler・Worker factoryを実装する。依存はWidget、Article、RSS、BookmarkのDomainとcore backgroundであり、他featureの具体的Dataやdatabaseを受け取らない。Widgetの表示に必要な連携を担当し、記事のdurable state所有権は元のContextへ残す。

## 状態と更新フロー

未読一覧はArticle Repositoryから取得し、sourceSelectorで絞ってWidgetArticleへ変換する。既読はArticleのmarkArticleRead、あとで読むはBookmarkのmarkReadLaterへ委譲する。feed更新はRSSのfeed一覧を同じselectorで絞り、一件ずつrefreshする。各feedの失敗をrunCatchingで包むので、一件の失敗だけで残りの更新を止めない。

`WorkManagerWidgetRefreshScheduler`は共通background取得制約を持つone-shot workをunique nameでREPLACEする。factoryがRepositoryと完了callbackをWorkerへ注入するため、Worker自身はUI updaterへ依存しない。実行時にも取得policyを再確認し、許可されなければretry、更新後callbackまで成功すればsuccess、例外ならfailureとなる。個々のfeed失敗が吸収される点とWorker全体の失敗は区別して読む。

## 確認先

`DefaultWidgetRepositoryBoundaryTest`はconstructorにDomain Repositoryを使いdatabaseを受け取らない境界、contract testはWidgetRepository実装を確認する。source selector変更では一覧と更新対象が一致するか、callback変更ではcompositionとWidget UI更新の接続を確認する。

## 調査・変更の入口

[DefaultWidgetRepository](../../../feature/widget/data/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/data/DefaultWidgetRepository.kt)、[WidgetBackgroundRuntime](../../../feature/widget/data/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/data/WidgetBackgroundRuntime.kt)、[UnreadWidgetRefreshWorker](../../../feature/widget/data/src/main/kotlin/dev/terashima/yomitorirss/feature/widget/UnreadWidgetRefreshWorker.kt)、[DefaultWidgetRepositoryBoundaryTest](../../../feature/widget/data/src/test/kotlin/dev/terashima/yomitorirss/feature/widget/data/DefaultWidgetRepositoryBoundaryTest.kt) を起点に責務と呼び出し側を確認する。

関連: [widget domain](widget-domain.md) / [widget ui](widget-ui.md) / [全体構成](../../architecture/system.md)。
