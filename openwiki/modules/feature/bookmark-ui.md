---
type: module
title: "Bookmark UI：一覧・タグ・フォルダと import"
description: "Curation の一覧と編集を表示し、Content と Bookmark の変更通知から画面状態を再読込する。"
tags: [bookmark, ui, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-2f7c96bb988d86e3c68113a1
    resource: repo://feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkFeature.kt
  - id: openwiki-source-83f66b4b06716310959351c8
    resource: repo://feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と入口

`:feature:bookmark:ui` は一覧、フォルダ管理、タグ管理、import画面を提供する。BookmarkRouteが画面とViewModelを接続し、BookmarkScreenがtabに応じて表示を選ぶ。記事行はArticle UIを再利用し、保存操作はBookmark Domain、種別変更はArticle Domain、蔵書移動はBookmark-owned use caseに委譲する。具体的Data実装を画面へ持ち込まない。

## 読込と操作

BookmarkViewModelはContentとCurationのchangesを観測して一覧とcatalogを再読込する。選択したタグ・フォルダはUiStateで保持する。蔵書移動では処理中の記事を一時的に非表示にし、成功後に再読込、失敗時にはhidden identityを解除してmessageを表示する。CSV / HTML importの成功時はfilterを解除し、新規・重複・スキップ件数と完了flagを反映する。

## 表示状態と境界

一覧の日時基準は保存日時と記事公開日時から選べ、それぞれ新しい順・古い順を提供する。既定は保存日時の新しい順で、sort選択は画面内のCompose状態である。画面の選択・hidden identity・messageは永続Bookmark状態とは別のpresentation状態として扱う。AI補完の一括再処理は渡されたcallbackへ要求し、UI自身で推論やqueueを所有しない。

## 変更と検証

ソート変更では見出しや行の日付と選んだ日時基準が一致することを確認する。BookmarkSortOptionTestは4通りの順序を検証する。BookmarkUiStateTestは初期の未選択・空一覧、TagManagerScreenTestはタグ画面の表示補助を確認する。これらは保存やLibrary移動全体のintegration testではなく、その契約はDomain/Data側のテストを併用する。

## ソースと検証の入口

- [BookmarkViewModel](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkViewModel.kt)
- [BookmarkFeature](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkFeature.kt)
- [BookmarkRoute](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkRoute.kt)
- [BookmarkSortOptionTest](../../../feature/bookmark/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkSortOptionTest.kt)
- [BookmarkUiStateTest](../../../feature/bookmark/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkUiStateTest.kt)
- [TagManagerScreenTest](../../../feature/bookmark/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/TagManagerScreenTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [bookmark-domain module](bookmark-domain.md)
- [bookmark-data module](bookmark-data.md)
- [article-ui module](article-ui.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
