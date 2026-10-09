---
type: module
title: Bookmark UI：一覧・タグ・フォルダと import
description: Curation の一覧と編集を表示し、Content と Bookmark の変更通知から画面状態を再読込する。
tags:
  - bookmark
  - ui
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-2f7c96bb988d86e3c68113a1
    resource: repo://feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkFeature.kt
  - id: openwiki-source-83f66b4b06716310959351c8
    resource: repo://feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と入口

`:feature:bookmark:ui` は一覧、フォルダ管理、タグ管理、import画面を提供する。BookmarkRouteが画面とViewModelを接続し、BookmarkScreenがtabに応じて表示を選ぶ。記事行はArticle UIを再利用し、保存操作はBookmark Domain、種別変更はArticle Domain、蔵書移動はBookmark-owned use caseに委譲する。具体的Data実装を画面へ持ち込まない。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [BookmarkDialogs](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkDialogs.kt) | 公開Composable ArticleFolderDialog/ArticleTagsDialogがフォルダまたはタグID集合を選択してcallbackへ渡す。 |
| [BookmarkFeature](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkFeature.kt) | BookmarkTabと内部BookmarkSortOptionが表示種別/並び順を表す。公開BookmarkScreenがタブ画面を振り分け、sortBookmarksが保存日時/公開日時の昇降順を適用する。 |
| [BookmarkImportScreen](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkImportScreen.kt) | 内部BookmarkImportScreenがCSV/HTML document pickerへの操作を表示する。URI読取とimportはViewModel以降へ委譲する。 |
| [BookmarkRoute](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkRoute.kt) | BookmarkRouteはViewModel状態と画面callbackを接続。BookmarkEditController/rememberBookmarkEditControllerが編集対象を持ち、BookmarkEditHostが他画面から共用するタグ/フォルダdialogを表示する。 |
| [BookmarkViewModel](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkViewModel.kt) | BookmarkUiState/BookmarkViewModelとFactoryがStateFlow・IO操作・再読込を所有する。selectTag/selectFolder、分類CRUD、unsave/moveToLibrary、setArticleContentType、importCsv/importHtmlを公開する。 |
| [FolderManagerScreen](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/FolderManagerScreen.kt) | 内部FolderManagerScreenは名前入力と通常フォルダのrename/delete UIを持ち、system folderと未分類を区別する。 |
| [NavigationDestination](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/NavigationDestination.kt) | BOOKMARKS/FOLDERS/TAGS/IMPORT_ROUTEとbookmarkTabForRoute/routeForBookmarkTab/bookmarkDestinationTitleがnavigation metadataを提供する。 |
| [TagManagerScreen](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/TagManagerScreen.kt) | 内部TagManagerScreenはタグ一覧・記事数・詳細記事・未使用タグ削除確認を表示。countArticlesByTag/articlesWithTagは一時非表示IDを除外する。 |

## 公開 API・接続と処理フロー

`BookmarkRoute` はViewModel.Factoryから状態を収集し、BookmarkScreenと編集dialogへcallbackを渡す。`BookmarkEditController` はタグ編集/フォルダ移動の対象Articleを保持し、`BookmarkEditHost` により別画面からも同じ編集UIを使える。NavigationDestinationの関数はrouteとtab/タイトルを相互変換し、未知routeはnullを返す。

ViewModelはArticleとBookmarkのchangesをcombineして `reload` する。reloadはMutexで直列化し、削除済みのフィルタを解除、通常一覧と全件詳細を分けて読み、StateFlowへ反映する。`selectTag/selectFolder` はquery条件、分類CRUDや種別更新はDomain capabilityを呼び成功後に再取得する。画面はArticleListへ保存日時を表示日時として渡し、ユーザーが選ぶ並び順を適用する。

`unsave/moveToLibrary` は対象IDを一時非表示にしてIOで実行し、成功後にreloadする。失敗時にはhidden IDを戻してmessageへ理由を設定する。CSV/HTML importは結果の新規・重複・skip数を通知し、選択filterを解除、importCompletedをrouteが消費する。Repository実装やSQLはUIに注入せず、factoryへの実装接続は [AppContentRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppContentRouteDependencies.kt) を確認する。

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
