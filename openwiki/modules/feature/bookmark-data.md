---
type: module
title: Bookmark Data：Curation の永続化と復元
description: Bookmark 状態、タグ・フォルダ、あとで読むを保存し、Content query から一覧を合成する。
tags:
  - bookmark
  - data
  - content
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-81c68d954422d27dc52ee88b
    resource: repo://feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkEnrichmentRepository.kt
  - id: openwiki-source-441796eb50d771496995edb2
    resource: repo://feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

## 責務と所有権

`:feature:bookmark:data` はCurationの保存状態とassociationを所有する。DefaultBookmarkRepositoryはstate、read、tag、folder、associationのstoreを組み合わせる。Contentの行は直接読み取らず、ArticleRepository.findArticlesから取得してBookmarkedArticleのread modelへ合成する。CSV / HTMLの解析sourceや生成metadataの適用もこのmoduleに置く。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [BookmarkAssociationStore](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkAssociationStore.kt) | 内部BookmarkAssociationStoreはタグ差分・単一フォルダmembership・あとで読む・importタグ追加をtransactionで扱う。 |
| [BookmarkContentQuery](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkContentQuery.kt) | DefaultBookmarkContentQueryは入力ID集合内から保存済み/あとで読むIDを返すnamed query実装。 |
| [BookmarkCsv](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkCsv.kt) | 内部BookmarkCsvEntry/ParseResultとparseBookmarkCsvがCSV行を中立入力へ変換し、不正URLをskipする。parseCsvRowsはquoted field・改行・二重引用符を解析する。 |
| [BookmarkDatabaseSchema](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkDatabaseSchema.kt) | bookmarkDatabaseSchemaは保存・タグ・フォルダ・関連tableと未使用タグのcleanup triggerをschema contributionへ登録する。 |
| [BookmarkEnrichmentRepository](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkEnrichmentRepository.kt) | DefaultBookmarkEnrichmentRepository.context/applyGeneratedMetadataは保存済み確認・既存分類候補取得・タグ追加/置換・未分類記事への既存フォルダ設定を扱う。 |
| [BookmarkFolderStore](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkFolderStore.kt) | 内部BookmarkFolderStoreはフォルダCRUDとsystem folder編集禁止を実装。公開object BookmarkDatabaseInitializer.initializeと内部ensureReadLaterFolderが初期状態を作る。 |
| [BookmarkHtml](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkHtml.kt) | 内部BookmarkHtmlEntry/ParseResultとparseBookmarkHtmlがNetscape bookmark HTMLのDL/H3/Aを解析し、フォルダ階層をタグへ変換する。 |
| [BookmarkImportSource](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkImportSource.kt) | DefaultBookmarkImportRepositoryがImportBookmarksUseCaseを組み立てる。内部AndroidBookmarkImportSourceはContentResolverからUTF-8で読取、DefaultBookmarkImportWriterはCuration-owned storeへ保存する。 |
| [BookmarkReadStore](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkReadStore.kt) | 内部BookmarkRecordはCuration側のread model。BookmarkReadStore.listSavedRecords/listAllSavedRecords/listReadLaterRecordsが絞り込みと関連分類の一括取得を行う。 |
| [BookmarkRepository](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkRepository.kt) | DefaultBookmarkRepositoryは公開capabilityを束ね、5つの内部storeへCuration SQLを分配する。composeはArticleRepository.findArticlesの結果とBookmarkRecordをIDで合成する。 |
| [BookmarkStateStore](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkStateStore.kt) | 内部BookmarkStateStore.isBookmarked/listBookmarkedArticleIds/save/unsaveはbookmarks行と保存日時を所有する。 |
| [BookmarkTagStore](../../../feature/bookmark/data/src/main/kotlin/dev/terashima/yomitorirss/feature/bookmark/data/BookmarkTagStore.kt) | 内部BookmarkTagStoreはタグCRUD・一括削除とresolveOrRestoreTagsによるUndo用のタグ再作成を担う。 |

## 主要 API と構成要素の協調

読取は `BookmarkReadStore` からCuration recordを取得し、`DefaultBookmarkRepository.compose` がArticleRepositoryの詳細と合成する。記事のないrecordは除外する。通常一覧は上限付き、全件一覧はstoreでページ取得し、タグ管理の件数に利用する。

保存は `articleGateway.markRead` → `stateStore.save` → 変更通知 → 新規時の補完callback。あとで読むではさらにassociationStore.addReadLaterを呼ぶ。`unsaveArticle` は保存と関連をtransactionで削除し、`removeReadLater` はmembershipだけを削除する。`restoreReadLater` はMutexで解除と競合しないようにし、タグ復元・保存・membership・タグ関連をtransactionで戻す。補完の通常失敗は保存結果から独立し、CancellationExceptionだけ伝播する。

タグ名とフォルダ名は空白正規化と小文字キーで管理する。system folderの編集と予約名YouTubeのユーザー作成は拒否する。AI分類は保存済み確認をtransaction内でも再実行し、フォルダ設定済み記事を勝手に移動しない。importはContentResolver読取→CSV/HTML parse→Domain UseCase→Content gateway/Curation writerと進む。CSVの未閉鎖引用符や必須列欠如はエラー、不正URLはskip件数となる。

[AppContentRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/content/AppContentRuntimeDependencies.kt) がdatabase、Content側query/commandと共有変更通知を注入し、新規保存callbackをSummaryの `BookmarkAutoEnrichmentUseCase` に接続する。

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
