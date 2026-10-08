---
type: module
title: "Bookmark Domain：保存・整理と Context 間操作"
description: "Curation の保存・タグ・フォルダ契約と、Content 作成、import、Library への移動の順序を定義する。"
tags: [bookmark, domain, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-ff439d6ad2c74986af9a18c1
    resource: repo://feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkCrossContext.kt
  - id: openwiki-source-a76a986f05233b0f3989deeb
    resource: repo://feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkImport.kt
  - id: openwiki-source-2250676ba8a46befa8f3c83d
    resource: repo://feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/MoveBookmarkToLibraryUseCase.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と所有権

`:feature:bookmark:domain` はCurationのモデルと公開capabilityを定義する。Contentの本文や既読日時を持つのではなく、保存日時、タグ、フォルダと「あとで読む」を記事identityに結び付ける。読み取り、catalog管理、状態変更、共有保存を分けた契約を提供し、利用側は必要な範囲のcapabilityだけを受け取れる。

## import の流れ

ImportBookmarksUseCaseは外部文書を中立的なentryへ解析するsource、Contentを検索・作成するBookmarkArticleGateway、Curationを保存するwriterを順に使う。各entryでは記事identityを取得して保存日時を反映し、新規と重複を数え、タグを付与する。解析側のスキップ数とともに結果を返し、完了時に変更通知を行う。解析失敗時はこの完了通知まで到達しない。

## Context を越える境界

Library移動はWebLibraryAdderによる追加を先に行い、成功後にBookmarkを解除する。Library追加に失敗した場合はBookmarkを残す順序が契約で、分散transactionやLibrary側からの逆向き解除ではない。BookmarkContentQueryは他Context向けの保存・あとで読むidentityのnamed queryであり、他ContextにCurationのSQL構造を要求しない。

## 変更と検証

import形式の追加は解析sourceとidentityPrefix、重複件数の意味を確認する。Library移動はMoveBookmarkToLibraryUseCaseTestが順序と追加失敗時の保持を検証する。ImportBookmarksUseCaseTestはContent作成、Curation保存、タグ付与と結果集計を検証する。補完metadataの適用契約は通常追加と既存タグ置換を明示的に分ける。

## ソースと検証の入口

- [BookmarkImport](../../../feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkImport.kt)
- [MoveBookmarkToLibraryUseCase](../../../feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/MoveBookmarkToLibraryUseCase.kt)
- [BookmarkCrossContext](../../../feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkCrossContext.kt)
- [BookmarkRepository](../../../feature/bookmark/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/BookmarkRepository.kt)
- [ImportBookmarksUseCaseTest](../../../feature/bookmark/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/ImportBookmarksUseCaseTest.kt)
- [MoveBookmarkToLibraryUseCaseTest](../../../feature/bookmark/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/MoveBookmarkToLibraryUseCaseTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [bookmark-data module](bookmark-data.md)
- [bookmark-ui module](bookmark-ui.md)
- [article-domain module](article-domain.md)
- [library-domain module](library-domain.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
