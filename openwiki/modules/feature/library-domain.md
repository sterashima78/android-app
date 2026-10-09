---
type: module
title: 蔵書 Domain：取得元と整理の契約
description: 蔵書の取得元、手動整理、シリーズ再整理と非同期AI操作の境界を説明する。
tags:
  - library
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-137d1c3ac674ad074c5fc956
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizer.kt
  - id: openwiki-source-491cf473ed282f478d3cc41d
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryModels.kt
  - id: openwiki-source-675d6990ce665987456914ce
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationAiTaskController.kt
  - id: openwiki-source-5ebb13bfa6f8240ca0444997
    resource: repo://feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbLibraryModels.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# 蔵書 Domain：取得元と整理の契約

`:feature:library:domain` は次の責務を持ちます。

## 責務と処理の流れ

蔵書の一覧取得、非表示と復元、手動シリーズ設定、外部サービス由来の取り込みを公開契約で表現します。UIが取得元の保存方法へ依存せず、Dataが同じ蔵書モデルへ変換できる境界です。SMBファイルアクセス、表紙先読み、書誌正規化、Web書誌抽出も、この領域の契約を通して呼び出されます。Androidの接続やデータベース実装は持ちません。

シリーズ再整理は対象を蔵書キーで重複排除し、シリーズ設定済みで同一シリーズであることを先に検証します。その後、保存済み分類と先行書籍で確定した分類を後続書籍のAI入力へ渡し、タグとコレクションを更新します。読書状態は元の値を保持するため、分類の再生成によって読書の進捗を初期化しません。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [LibraryMetadataOrganizer](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizer.kt) | LibraryMetadataOrganizer.reorganizeSeriesは同一シリーズ書籍の候補生成と分類保存を順序づけ、LibrarySeriesReorganizationResultが部分成功件数を返す。内部seriesContextForMetadataReorganizationは既存分類を同シリーズcontextに変換する。 |
| [LibraryModels](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryModels.kt) | LibrarySource enumが取得元、LibraryBook/Series/BookSeriesUpdate/SourceState/Snapshot/SyncResultがカタログと同期結果を表す。LibraryBook.openUrl、isKindlePersonalDocument、kindlePersonalDocumentSourceIdが取得元別の開き方とidentityを統一する。 同じ役割群の公開名: `LibraryBookSeriesUpdate`、`LibrarySourceState`、`LibrarySnapshot`、`LibrarySyncResult`。 |
| [LibraryOrganizationAiTaskController](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationAiTaskController.kt) | LibraryOrganizationAiTaskState/Snapshot/Kind/ReferenceとControllerが単冊候補/シリーズ再整理のenqueue・snapshot・recoverableTasks・dismissを公開する。 同じ役割群の公開名: `LibraryOrganizationAiTaskSnapshot`、`LibraryOrganizationAiTaskKind`、`LibraryOrganizationAiTaskReference`。 |
| [LibraryOrganizationModels](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationModels.kt) | LibraryReadingStatusとBookKey、OrganizationTag/Collection/ItemOrganization/Snapshot/Draft/Update/Suggestion/SeriesContextが分類を表す。Batch/Candidateのenumとdata classが処理結果を保持。organizationKey、snapshot.organizationForがキーを統一し、Repository/Scheduler/Suggesterが保存・背景実行・推論を分離する。 同じ役割群の公開名: `LibraryBookKey`、`LibraryOrganizationTag`、`LibraryCollection`、`LibraryItemOrganization`、`LibraryOrganizationSnapshot`、`LibraryOrganizationDraft`、`LibraryOrganizationUpdate`、`LibraryOrganizationSuggestion`、`LibraryOrganizationSeriesContext`、`LibraryOrganizationBatchStatus`、`LibraryOrganizationCandidateStatus`、`LibraryOrganizationCandidate`、`LibraryOrganizationBatchSnapshot`、`LibraryOrganizationBatchScheduler`。 |
| [LibraryRepository](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryRepository.kt) | LibraryReader.snapshotは表示/非表示と同期状態を返す。LibraryRepositoryはhide/restore・単冊/一括setBookSeries・clearBookSeries・Google Books sync・Amazon JSON importを公開する。 |
| [LibrarySeriesImportSupport](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibrarySeriesImportSupport.kt) | LibrarySeriesImportSupport.importSeriesMetadataJson/clearSeriesMetadataはKindle/Audibleの構造化シリーズ資料をカタログ同期から分ける。 |
| [SmbLibraryModels](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbLibraryModels.kt) | SmbConnectionProfile/LibraryLocationとRepositoryは接続設定/蔵書場所を分離。SmbServerSettings/PreparedLibraryBook/BookFormatは取得入力/Reader準備結果。CoverPrefetchのStatus/WorkerState/WaitReason/RuntimeSnapshot/Item/Snapshotは永続queueと実行状態を区別し、Scheduler/LibraryRepositoryが同期・rename/delete・prepare・先読みを公開する。 同じ役割群の公開名: `SmbLibraryLocation`、`SmbConnectionProfileRepository`、`SmbBookFormat`、`SmbCoverPrefetchStatus`、`SmbCoverPrefetchWorkerState`、`SmbCoverPrefetchWaitReason`、`SmbCoverPrefetchRuntimeSnapshot`、`SmbCoverPrefetchItem`、`SmbCoverPrefetchSnapshot`、`SmbCoverPrefetchScheduler`。 |
| [SmbMediaFileAccess](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMediaFileAccess.kt) | SmbMediaLocation/Fileは汎用media情報。SmbMediaFileAccess.listMediaFiles/openMediaFileとAutoCloseableなSmbMediaReadHandle.readがメディア列挙と位置指定読取を公開する。 |
| [SmbMetadataNormalizationModels](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMetadataNormalizationModels.kt) | SmbBookMetadataProposal/NormalizationItem/BatchSnapshotと状態enumは書誌候補とreview状態。NormalizationRepositoryはbatch開始・採用・保留・却下・再開・再解析、Schedulerはkick/cancel/充電再開を公開する。 同じ役割群の公開名: `SmbMetadataNormalizationBatchStatus`、`SmbMetadataNormalizationStatus`、`SmbMetadataNormalizationItem`、`SmbMetadataNormalizationBatchSnapshot`、`SmbMetadataNormalizationRepository`、`SmbMetadataNormalizationScheduler`。 |
| [SmbMetadataNormalizationPrompt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMetadataNormalizationPrompt.kt) | 既定promptとplaceholder/制約定数、normalize/renderSmbMetadataNormalizationPromptが入力検証・ファイル名埋込を担う。PromptRepository.prompt/update/resetが設定の保存port。 同じ役割群の公開名: `normalizeSmbMetadataNormalizationPrompt`、`SmbMetadataNormalizationPromptRepository`。 |
| [WebLibraryMetadataExtractor](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMetadataExtractor.kt) | WebLibraryMetadataExtractorはURL pattern/関数code/timeoutのdata class、Repository.list/save/deleteが設定capability。timeoutのdefault/min/maxを共有する。 同じ役割群の公開名: `WebLibraryMetadataExtractorRepository`。 |
| [WebLibraryMetadataExtractorTest](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMetadataExtractorTest.kt) | WebLibraryMetadataExtractorTestResultとfun interface Tester.testが未保存抽出ルールの検証結果を返す。 同じ役割群の公開名: `WebLibraryMetadataExtractorTester`。 |
| [WebLibraryMutator](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMutator.kt) | WebLibraryAdder/MutatorはURL追加・再取得/診断付き再取得・削除。MetadataField/ExtractorStatus/Execution/RefreshResultとchangedWebLibraryMetadataFieldsが結果と差分を表す。 同じ役割群の公開名: `WebLibraryMetadataExtractorStatus`、`WebLibraryMetadataExtractorExecution`、`WebLibraryMetadataRefreshResult`。 |

## 主要 API・実装の関係

`LibraryRepository.snapshot` は表示/非表示を分けて返し、hide/restoreは手動状態を変更する。series一括更新の既定実装は単冊更新の反復であり、原子性が必要なDataはoverrideする。WebLibraryMutatorはURL取得結果と変更field/抽出実行/fallbackを返すため、取得成功とcustom extractor成功を区別できる。

`SmbLibraryRepository.prepareBook(book, onProgress)` はローカルpathとZIP/PDF形式を返すReader準備契約。media accessは別の位置指定read handleを公開し、閉じる責務はconsumerにある。`SmbConnectionProfile` はpasswordを返さずcredentialConfiguredだけを表し、profileとlibrary locationは別モデルである。

[AppLibraryRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/library/AppLibraryRuntimeDependencies.kt) はカタログに `SmbMetadataAwareLibraryRepository`、SMB操作に `CleaningSmbLibraryRepository`、分類保存に `DefaultLibraryOrganizationRepository`、推論に `DefaultLibraryOrganizationSuggester` を接続する。Web更新は `DefaultWebLibraryMutator` とHTTP/foreground WebViewのclient、背景capabilityはWorkManager scheduler/controllerとLibraryWorkerFactoryへ接続する。

## 代表的な処理フローと制約

シリーズ再整理はLibraryMetadataOrganizerが対象キーを重複排除し同一seriesを検証、snapshotを取得し、各bookでSuggester.suggest→readingStatus保持のDraft→Repository.saveと進む。成功draftの分類が後続bookのcontextに入り、通常失敗はfailed件数、キャンセルは上位へ伝播する。

SMB正規化はstartBatch→表紙待ち/解析待ち→候補→採用/保留/却下/再解析という公開境界を持ち、ファイル実変更はapplyCandidate以降に限定する。promptのnormalizeは空/長さ超過を拒否し、renderはplaceholderを置換、なければファイル名を追記する。タスク状態・保存分類・書誌候補・表紙queueはそれぞれ別モデルであり、進捗の一つを他の完了として扱わない。

## 状態・失敗と拡張

通常の書籍ごとの解析失敗は失敗件数へ加算し、成功した書籍は保存されます。一方、キャンセルは握りつぶさず呼び出し側へ伝播します。単冊候補とシリーズ再整理はAI task controllerの受付・結果取得の契約があり、画面が長時間推論そのものを所有する必要はありません。取得元追加ではモデル変換だけでなく、蔵書キーの安定性、非表示の維持、整理分類の引き継ぎを確認してください。

## 調査と変更の入口

[LibraryRepository.kt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryRepository.kt)、[LibraryMetadataOrganizer.kt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizer.kt)、[LibraryOrganizationAiTaskController.kt](../../../feature/library/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationAiTaskController.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [LibraryMetadataOrganizerTest.kt](../../../feature/library/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataOrganizerTest.kt) です。

[library data](library-data.md)、[library ui](library-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
