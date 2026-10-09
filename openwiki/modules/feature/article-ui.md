---
type: module
title: Article UI：共通記事一覧と操作の投影
description: RSS、Bookmark、Reddit が再利用する記事一覧、日付見出し、スワイプと種別選択を描画する。
tags:
  - article
  - ui
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-ca286c70c3f4c914a2c941c6
    resource: repo://feature/article/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleContentTypeDialog.kt
  - id: openwiki-source-2987bcfff9ed5f930081eb70
    resource: repo://feature/article/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleList.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と利用者

`:feature:article:ui` は特定sourceのRepositoryを所有せず、与えられたArticle一覧とcallbackからComposeの共通表示を作る。RSSの未読、Bookmark一覧、Reddit記事などが利用し、取得・永続化・要約実行は呼出元へ戻す。Data moduleへの依存は持たず、ContentとCurationのDomainモデル、designsystemのスワイプ部品を利用する。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [ArticleContentTypeDialog](../../../feature/article/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleContentTypeDialog.kt) | 内部Composable ArticleContentTypeDialogは継承・記事・漫画を選択し、displayLabel拡張関数が表示名を与える。 |
| [ArticleList](../../../feature/article/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleList.kt) | SwipeChoice/ArticleMenuActionはcallbackを持つdata class。公開Composable ArticleListが日付グループと行を生成し、内部SwipeArticleItem/ArticleContentがスワイプと長押しメニューを接続する。 |

## API の入力・callback と表示フロー

`ArticleList` はArticle一覧、空状態の文言、記事ID別のBookmark詳細・表示日時・注記を受け取る。`SwipeChoice` を左右/遠側に注入し、確定時に対象Articleをcallbackへ渡す。`ArticleMenuAction` は利用画面が独自操作を追加する拡張点である。行の通常タップは `onOpen`、長押しはローカルメニューを開き、要約・タグ・フォルダ・種別変更は呼出元へ委譲する。

日付見出しと行の日時は同じoverrideを使う。日付解析失敗時は「日付不明」、時刻は空表示になる。タグ/フォルダ操作はBookmark詳細のある行だけ表示し、種別操作は `onSetContentType` がある場合だけ表示する。dialogの継承選択はnullを返す。保存状態は所有せず、メニュー/編集中の一時状態だけを記事IDでrememberする。利用例は [BookmarkFeature](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkFeature.kt) と [TagManagerScreen](../../../feature/bookmark/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/TagManagerScreen.kt)。

## 表示から操作まで

ArticleListは入力順の一覧を表示日時の日付でグループ化し、sticky headerと記事identityをkeyにした行を作る。displayedAtByArticleIdがあればその日時を見出しと行に使い、なければ公開日時を使う。Bookmark詳細や推薦注記は別mapとして渡せるため、記事のDomainモデルへ画面ごとの表示情報を追加せずに済む。

## 状態と拡張点

左右と深い左右のスワイプはSwipeChoiceとして注入され、確定時に対象Articleをcallbackへ渡す。開く・要約・タグ・フォルダ・追加メニューも呼出元に委譲する。長押しメニューや種別dialogの開閉は行の一時Compose状態で、保存状態をここに複製しない。種別選択の「継承」はnullを返し、実効種別の再計算と保存は呼出元の責務となる。

## 変更と検証

共通一覧の変更はRSSとBookmarkとRedditに波及するため、日付override、注記、追加メニューとスワイプ距離の受け渡しを確認する。このmoduleに直接のtest sourceはない。表示日時とソートの検証は利用側のReadLaterDisplayedAtTestとBookmarkSortOptionTest、深い左スワイプ設定はRssUiStateTestを参照する。これらはArticleList全体のCompose描画テストではない。

## ソースと検証の入口

- [ArticleList](../../../feature/article/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleList.kt)
- [ArticleContentTypeDialog](../../../feature/article/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/article/ArticleContentTypeDialog.kt)
- [ReadLaterDisplayedAtTest](../../../feature/rss/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/ReadLaterDisplayedAtTest.kt)
- [BookmarkSortOptionTest](../../../feature/bookmark/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkSortOptionTest.kt)
- [RssUiStateTest](../../../feature/rss/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/rss/RssUiStateTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [article-domain module](article-domain.md)
- [rss-ui module](rss-ui.md)
- [bookmark-ui module](bookmark-ui.md)
- [reddit-ui module](reddit-ui.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
