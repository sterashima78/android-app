---
type: module
title: Article Data：Content の保存と source 接続
description: articles の永続化、既読状態、source 取り込みと共有ブックマークの Content 作成を担う。
tags:
  - article
  - data
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-459db603db2b328780770e54
    resource: repo://feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ArticleRepository.kt
  - id: openwiki-source-21a507cf6f18f75c66e8b95d
    resource: repo://feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/BookmarkArticleGateway.kt
  - id: openwiki-source-4078a6bfcc3a6bc7f50afdb3
    resource: repo://feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ContentSourceGateway.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と所有権

`:feature:article:data` はarticlesの永続化を所有する。DefaultArticleRepositoryがContentのqueryと状態更新を実装し、RSSからの書き込みはDefaultContentSourceGateway、Curationからの作成はDefaultBookmarkArticleGatewayで受ける。sourceやBookmarkのtableを直接JOINしてread modelを作らず、分類設定と削除保護は注入された公開queryから得る。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [ArticleDatabaseSchema](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ArticleDatabaseSchema.kt) | articleDatabaseSchemaはarticlesとsource内identityのunique indexをDatabaseSchemaContributionとして公開する。 |
| [ArticleRepository](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ArticleRepository.kt) | DefaultArticleRepositoryはArticleRepositoryのSQL実装。ArticleRowが保存行を保持し、articlesがsource queryとclassification serviceを使ってArticleへ変換する。 |
| [BookmarkArticleGateway](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/BookmarkArticleGateway.kt) | DefaultBookmarkArticleGatewayはBookmarkArticleGatewayのContent側adapter。markReadは存在を要求し、findOrCreateSharedArticle/findOrCreateImportedArticleはURLで既存行を再利用する。 |
| [ContentSourceGateway](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ContentSourceGateway.kt) | DefaultContentSourceGatewayはsource commandをSQLへ変換し、DefaultBookmarkContentQueryに保持する記事IDの判定を委譲する。 |
| [ArticleContentClient](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/network/ArticleContentClient.kt) | ArticleContentClient.fetchArticleText/fetchArticleTitleはcore HttpClientとJsoupでHTMLから本文またはtitleを取得する。 |

## 主要 API・代表的な処理フロー

1. `DefaultArticleRepository.findArticles` は空白IDを除去・重複排除してSQLを分割実行し、内部 `articles` でsource IDを一括queryする。`ContentClassificationService.resolve` により各行の実効種別を付けて返す。未読queryは公開日時、履歴queryは既読日時に基づく。
2. `cleanupExpiredArticles` はsourceから切り離された既読記事だけを期限候補にし、外部Contextの保護queryで除外してtransaction内で削除する。削除された場合だけ変更通知する。
3. `upsertSourceContent` はdetached記事からBookmark保存済みIDを探してsourceを再関連付けし、insertの競合は無視する。`detachSourceContent` は保存済み記事だけに継承種別を必要なら固定し、source IDを解除して残りを削除する。
4. `findOrCreateSharedArticle` はタイトルの必要な補完を先に行い、URLで最新記事を探して再利用する。通常のHTML取得失敗ではfallbackで継続し、CancellationExceptionは伝播する。importでは保存日時を既読日時として扱い、既存の既読日時は保つ。

`ArticleContentClient` は入力URLをHTTPSへ正規化し、HTTP失敗・空本文・空titleを例外にする。本文取得は非本文要素を除去してarticle/mainを優先する。[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt) が共有HttpClient、DatabaseConnection、DataChangeNotifierを注入する。gateway自身は変更通知を持たず、呼出元のSource/Curation workflowが通知を担う。

## 取り込みと切り離し

取り込みはtransaction内でidentityとURLを照合し、保存済みのdetached Contentが一致すればsourceへ再関連付けする。insertの競合は無視するので再取得によって同一identityの既読日時を上書きしない。source削除時はBookmark済みContentを切り離し、未指定の種別にはsourceから継承した種別を固定して保持する。保存されていないsource記事は削除する。

## 閲覧状態と失敗

未読・履歴をarticlesから検索し、読込後にsource分類queryを適用する。既読日時や種別の更新後は変更versionを通知する。期限削除はsourceから切り離された既読記事だけを候補にし、保護queryに含まれる記事を残す。共有URLでは具体的な共有タイトルを優先し、タイトルがsource名のfallback値の場合だけ静的HTMLから補完する。タイトル取得の通常失敗ではfallbackを使い、CancellationExceptionは伝播する。

## 変更時の確認

identityやsource削除の変更はCurationの保存を失わないことをContentSourceGatewayTestで確認する。BookmarkArticleGatewayTestとArticleContentClientTestは共有タイトルの補完を調べる入口となる。別Contextのtable参照を増やす前にArticleRepositoryBoundaryTestでownershipの規則を確認する。

## ソースと検証の入口

- [ArticleRepository](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ArticleRepository.kt)
- [ContentSourceGateway](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/ContentSourceGateway.kt)
- [BookmarkArticleGateway](../../../feature/article/data/src/main/kotlin/dev/terashima/yomitorirss/feature/article/data/BookmarkArticleGateway.kt)
- [ContentSourceGatewayTest](../../../feature/article/data/src/test/kotlin/dev/terashima/yomitorirss/feature/article/data/ContentSourceGatewayTest.kt)
- [BookmarkArticleGatewayTest](../../../feature/article/data/src/test/kotlin/dev/terashima/yomitorirss/feature/article/data/BookmarkArticleGatewayTest.kt)
- [ArticleRepositoryBoundaryTest](../../../feature/article/data/src/test/kotlin/dev/terashima/yomitorirss/feature/article/data/ArticleRepositoryBoundaryTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [article-domain module](article-domain.md)
- [bookmark-data module](bookmark-data.md)
- [rss-data module](rss-data.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
