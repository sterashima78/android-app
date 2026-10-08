---
type: module
title: "Bookmark Data：Curation の永続化と復元"
description: "Bookmark 状態、タグ・フォルダ、あとで読むを保存し、Content query から一覧を合成する。"
tags: [bookmark, data, content]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-441796eb50d771496995edb2
    resource: repo://feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

## 責務と所有権

`:feature:bookmark:data` はCurationの保存状態とassociationを所有する。DefaultBookmarkRepositoryはstate、read、tag、folder、associationのstoreを組み合わせる。Contentの行は直接読み取らず、ArticleRepository.findArticlesから取得してBookmarkedArticleのread modelへ合成する。CSV / HTMLの解析sourceや生成metadataの適用もこのmoduleに置く。

## 保存から通知まで

「保存して既読」と「あとで読む」はContent-owned gatewayへ既読化を要求してからCuration保存を行う。「あとで読む」ではsystem folder membershipも追加する。共有保存はContent identityの検索・作成後に保存の新規性を判定する。変更versionを通知し、新規保存時の補完処理callbackを呼ぶが、通常のAI処理失敗は既に成功した保存を失敗にしない。キャンセルは伝播する。

## 削除と Undo

保存解除はmutex下のtransactionで保存状態と関連付けをまとめて消す。「あとで読む」解除はmembershipだけを変更する。復元では消えたタグを再解決または復元し、保存状態・membership・タグ関連を1つのtransactionで戻すため、関連付けの復元が失敗すれば部分状態を残さない。生成metadataの通常適用はタグを追加し、明示的な再処理では生成成功後に既存タグを置き換える。

## 変更と検証

read modelの拡張はContentの公開queryを維持し、foreign tableをJOINしない。BookmarkReadLaterRestoreTestは削除後のタグ復元、同名タグの再利用、失敗時rollbackを検証する。BookmarkEnrichmentRepositoryTestは未保存記事の拒否と通常追加・再生成置換を確認する。BookmarkContentQueryTestは外部Contextへ公開するnamed queryの入口となる。

## ソースと検証の入口

- [BookmarkRepository](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkRepository.kt)
- [BookmarkEnrichmentRepository](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkEnrichmentRepository.kt)
- [BookmarkContentQuery](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkContentQuery.kt)
- [BookmarkReadLaterRestoreTest](../../../feature/bookmark/data/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkReadLaterRestoreTest.kt)
- [BookmarkEnrichmentRepositoryTest](../../../feature/bookmark/data/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkEnrichmentRepositoryTest.kt)
- [BookmarkContentQueryTest](../../../feature/bookmark/data/src/test/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkContentQueryTest.kt)
- [コンテンツ仕様](../../../docs/spec/04-content.md)
- [Context と ownership](../../../docs/architecture/context-map.md)

## 関連ページ

- [bookmark-domain module](bookmark-domain.md)
- [bookmark-ui module](bookmark-ui.md)
- [article-data module](article-data.md)
- [コンテンツ取得から整理まで](../../workflows/content.md)
