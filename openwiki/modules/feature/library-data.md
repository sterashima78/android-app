---
type: module
title: 蔵書 Data：カタログ・同期・処理キュー
description: 取得元別蔵書の保存、手動状態、SMBとAI処理の実装を調べる入口。
tags:
  - library
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-342af0bd381afe21aa4c92d3
    resource: repo://feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepository.kt
  - id: openwiki-source-a5dad64d1af7a7c7e7e3c5d1
    resource: repo://feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultWebLibraryMutator.kt
  - id: openwiki-source-9cc30c0e0df44041d80e1820
    resource: repo://feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataAwareLibraryRepository.kt
  - id: openwiki-source-32b71a1076cba695b9d3596e
    resource: repo://feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationDatabase.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# 蔵書 Data：カタログ・同期・処理キュー

`:feature:library:data` は次の責務を持ちます。

## 責務と同期の境界

蔵書契約をSQLite、外部サービス、WebView、SMB、端末内AIへ接続します。単一の巨大な同期処理だけでなく、カタログ、整理分類、書誌候補、表紙先読み、WorkerをLibraryの実装として配置しています。依存先にはLibrary Domainとデータベース・ネットワーク・AI・backgroundのcore capabilityがあり、UIへは依存しません。

基本カタログではGoogle Booksの取得結果やAmazon由来JSONを蔵書へ変換し、取得元単位で入れ替えます。取得と解析が先に済んだ後、該当取得元の既存行削除、新しい蔵書の挿入、同期時刻の更新をトランザクションでまとめるため、更新途中のカタログを完成扱いしません。Kindleの通常本とPersonal Documentには別の置換範囲があり、混在インポートを拒否します。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [AndroidWebViewLibraryMetadataClient](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/AndroidWebViewLibraryMetadataClient.kt) | WebLibraryRenderedMetadataClient/FetchResultは描画取得の契約。AndroidWebViewLibraryMetadataClientはWebViewとcustom extractorを実行し、内部poll/script/parse/apply関数とCustomMetadata/Poll/例外がPromise状態と結果を処理する。 同じ役割群の公開名: `WebLibraryRenderedMetadataFetchResult`。 |
| [AndroidWebViewLibraryMetadataExtractorTester](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/AndroidWebViewLibraryMetadataExtractorTester.kt) | AndroidWebViewLibraryMetadataExtractorTester.testは未保存ruleをread-onlyなpreview repositoryで描画clientへ渡し、結果を検証UIへ返す。 |
| [AudibleStructuredSeriesMetadata](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/AudibleStructuredSeriesMetadata.kt) | 内部AudibleSeriesMetadata/Scanner/SourceSeriesRepositoryとapplyAudibleSeriesがAudible構造化seriesを保存してsnapshotへ付ける。 |
| [AudibleWebLibraryImporter](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/AudibleWebLibraryImporter.kt) | 内部AudibleWebLibraryImporterとExportParser/ExportがAudible JSONと朗読者・再生時間・シリーズをLibraryBookへ変換する。 |
| [CleaningSmbLibraryRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/CleaningSmbLibraryRepository.kt) | CleaningSmbLibraryRepositoryは基本SMB実装を包み、重複除去・cover cache整備・先読みqueue・正規化decision identityの移動/削除を補う。SmbLibraryDeduplicationCandidate/redundantSmbSourceIdsが冗長identityを判断する。 |
| [DefaultLibraryOrganizationSuggester](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryOrganizationSuggester.kt) | DefaultLibraryOrganizationSuggester.suggestは書籍・既存分類・シリーズcontextを推論へ渡す。buildLibraryOrganizationPrompt/parseLibraryOrganizationSuggestionが入力と分類候補を整形する。 |
| [DefaultLibraryRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepository.kt) | DefaultLibraryRepositoryはカタログSQLとhide/restore・手動series・取得元置換を実装し、GoogleBooksApiClientとKindle/Audible importerへ外部解析を委譲する。 |
| [DefaultSmbConnectionProfileRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultSmbConnectionProfileRepository.kt) | DefaultSmbConnectionProfileRepositoryはprofile/locationのCRUDとcredential保存を実装し、接続共有設定と蔵書場所を分離する。 |
| [DefaultSmbLibraryRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultSmbLibraryRepository.kt) | DefaultSmbLibraryRepositoryは接続・再帰scan・sync・rename/delete・prepareBookをSMBJへ接続する。内部credential storeが秘密を保存し、renamedSmbFileNameが名前制約を検証する。 |
| [DefaultSmbMediaFileAccess](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultSmbMediaFileAccess.kt) | DefaultSmbMediaFileAccessはmedia列挙/位置指定読取を実装し、内部OpenedSmbMediaReadHandle.closeでSMB資源を解放する。normalizeMediaSmbPathはパス検証。 |
| [DefaultWebLibraryMetadataExtractorRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultWebLibraryMetadataExtractorRepository.kt) | DefaultWebLibraryMetadataExtractorRepository.list/save/deleteはSQLルール設定。内部ensureWebLibraryMetadataExtractorSchema/validateWebLibraryMetadataExtractor/webLibraryUrlPatternMatches/findMatchingWebLibraryMetadataExtractor関数が期限とpatternの検証・適用を担当する。 |
| [DefaultWebLibraryMutator](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultWebLibraryMutator.kt) | DefaultWebLibraryMutatorはadd/refresh/report/removeを実装。公開WebLibraryMetadataClient.fetchは静的HTML取得、内部ResolvedMetadata/resolve/merge/parse/normalize関数が静的/描画結果の統合と診断を扱う。 |
| [ForegroundWebLibraryRenderedMetadataClient](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/ForegroundWebLibraryRenderedMetadataClient.kt) | ForegroundWebLibraryRenderedMetadataClientは描画clientを包み、resume済みActivityがある間だけfetch/fetchWithReportを許可する。 |
| [GoogleBooksApiClient](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/GoogleBooksApiClient.kt) | 内部GoogleBooksApiClient.libraryが認可tokenでbookshelf/volumeを取得し、googleBooksReadingUrlが読書URLを構成する。 |
| [GoogleBooksAuthorizationManager](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/GoogleBooksAuthorizationManager.kt) | GoogleBooksAuthorizationManager.requestAccount/resultFromIntentとAuthorizedAccount/AuthorizationOutcomeがGoogle認可結果と追加解決のPendingIntentを表す。 同じ役割群の公開名: `GoogleBooksAuthorizedAccount`、`GoogleBooksAuthorizationOutcome`。 |
| [KindleStructuredSeriesMetadata](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/KindleStructuredSeriesMetadata.kt) | 内部KindleSeriesMetadata/Scanner/SourceSeriesRepositoryとapplyKindleSeries/normalizeAmazonSourceIdが別保存seriesを補完。公開SeriesAwareLibraryRepositoryが基本repositoryを包みKindle/Audible series import契約を実装する。 |
| [KindleTitleNormalizer](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/KindleTitleNormalizer.kt) | 内部normalizeKindleBookTitleがKindle書名を正規化し、既存カタログ読取とimportで使う。 |
| [KindleWebLibraryImporter](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/KindleWebLibraryImporter.kt) | 内部KindleWebLibraryImporterとExportParser/ExportがAmazon JSONを通常本/Personal Documentへ変換し、シリーズ抽出とTitleNormalizerを使う。 |
| [LibraryBackupRestoreInitializer](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryBackupRestoreInitializer.kt) | LibraryBackupRestoreInitializer.initializeがrestore後のLibrary-owned schema初期化を担当する。 |
| [LibraryCatalogQueries](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryCatalogQueries.kt) | 内部findLibraryBookが取得元とsourceIdからLibraryBookを検索し、背景処理の現在カタログ確認に使う。 |
| [LibraryDatabaseSchema](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryDatabaseSchema.kt) | libraryDatabaseSchema/ensureLibrarySchema/CatalogSchema/StructuredSeriesSchemaがLibraryのカタログ・手動状態・series等の保存構造を初期化する。 |
| [LibraryOrganizationAiBackground](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryOrganizationAiBackground.kt) | WorkManagerLibraryOrganizationAiTaskControllerとLibraryOrganizationAiWorkerが単冊/シリーズ要求を実行。private TaskStoreが復旧参照・Suggestion/SeriesResultを保存し、UIのdismissまで保持する。 |
| [LibraryOrganizationBatchWorker](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryOrganizationBatchWorker.kt) | WorkManagerLibraryOrganizationBatchScheduler/ResumeOnChargingWorker/BatchWorkerがqueueを実行し、seriesOrganizationContextForが同シリーズの分類contextを構成する。 同じ役割群の公開名: `LibraryOrganizationResumeOnChargingWorker`。 |
| [LibraryOrganizationDatabase](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryOrganizationDatabase.kt) | DefaultLibraryOrganizationRepositoryは分類snapshot/save/saveAll、batch開始/停止/再開、candidate却下/再実行をSQLへ実装。内部ClaimedLibraryOrganizationBatchItemとschema/name正規化関数が背景claimと分類identityを支える。 |
| [LibraryWorkerFactory](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryWorkerFactory.kt) | LibraryWorkerRuntimeDependenciesはLibrary-owned依存のdata class。LibraryWorkerFactory.createWorkerが整理・表紙・正規化Workerへ具象依存を注入する。 |
| [LocalSmbMetadataNormalizationSuggester](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LocalSmbMetadataNormalizationSuggester.kt) | 内部LocalSmbMetadataNormalizationSuggester.suggestが表紙とファイル名をlocal推論へ渡す。buildSmbMetadataNormalizationPrompt/parseSmbBookMetadataProposal/completeSmbSeriesMetadataFromFileName/normalizedSmbBookFileNameが構造化出力と候補名を作る。 |
| [SharedPreferencesSmbMetadataNormalizationPromptRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SharedPreferencesSmbMetadataNormalizationPromptRepository.kt) | SharedPreferencesSmbMetadataNormalizationPromptRepository.prompt/update/resetが正規化promptを検証して保存し、不正な保存値は既定promptへ戻す。 |
| [SmbBookCovers](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbBookCovers.kt) | 内部resolveSmbBookCover/prefetchRemoteSmbZipCover/ensureSmbBookCoverFromLocalがZIP/PDFの表紙を取得。cleanup/delete/trim/cachePathsToEvictとSmbCoverCacheEntryが容量と寿命を管理し、extractFirstZipImageがZIP表紙を取り出す。 |
| [SmbCoverCacheCoordinator](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbCoverCacheCoordinator.kt) | 内部SmbCoverCacheCoordinator.trimはcache pruningとDBの表紙参照を整合させる。 |
| [SmbCoverPrefetchProcessor](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbCoverPrefetchProcessor.kt) | 内部SmbCoverPrefetchProcessor.prefetchCoverとOutcomeがcover処理の完了/skipを返す。shouldPrefetchPdf等の補助とSmbCredentialReaderがサイズ判定・表示・credential読取を担う。 |
| [SmbCoverPrefetchWorker](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbCoverPrefetchWorker.kt) | WorkManagerSmbCoverPrefetchScheduler/Workerが先読みをschedule/実行。内部RuntimeInspectorと状態変換関数が制約待機を表示へ投影し、QueueStore/Entryがenqueue/claim/progress/complete/skip/fail/requeueをSQLへ保存する。 |
| [SmbMetadataAwareLibraryRepository](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataAwareLibraryRepository.kt) | SmbMetadataAwareLibraryRepositoryはSeriesAwareLibraryRepositoryを包み、表示/非表示の双方にapplyConfirmedSmbMetadataを適用するcatalog decorator。 |
| [SmbMetadataNormalizationDatabase](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationDatabase.kt) | DefaultSmbMetadataNormalizationRepositoryはbatch/candidateの保存と採用/保留/却下/再解析を実装。内部SmbNormalizationInputとsmbNormalizationInput/applyConfirmedSmbMetadata/ensureSmbMetadataNormalizationSchema/migrateSmbMetadataNormalizationIdentity/validateProposedSmbFileNameの関数がrevision照合とcatalog適用を支える。 |
| [SmbMetadataNormalizationIdentity](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationIdentity.kt) | 内部migrateSmbMetadataNormalizationDecisionIdentityがrename後のconfirmed/rejected decisionを新source IDへ移す。 |
| [SmbMetadataNormalizationInferenceProcess](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationInferenceProcess.kt) | 公開SmbMetadataNormalizationInferenceServiceは別process Service。内部RemoteSuggester/RemoteInferenceSessionがBinder接続・要求・process終了を管理し、SmbVisionProcessBatchPolicyがsession寿命を制限。proposalとBundleの変換がIPC契約。 |
| [SmbMetadataNormalizationWorker](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationWorker.kt) | WorkManagerSmbMetadataNormalizationScheduler/ResumeOnChargingWorker/Workerが充電gate・claim・revision確認・表紙待機・推論・候補保存・中断復帰を実行する。 同じ役割群の公開名: `SmbMetadataNormalizationResumeOnChargingWorker`。 |

## composition とカタログの流れ

[AppLibraryRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/library/AppLibraryRuntimeDependencies.kt) が公開契約と具象をapplication scopeで接続する。snapshotは `SmbMetadataAwareLibraryRepository` → `SeriesAwareLibraryRepository` → `DefaultLibraryRepository` と進み、手動seriesと構造化Kindle/Audible series、採用済みSMB書誌を段階的に合成する。分類と読書状態はLibraryOrganizationRepositoryの別snapshotで、同期のカタログ置換に巻き込まない。

SMB `CleaningSmbLibraryRepository.sync` は基本実装のscan/sync後に冗長identityを削除し、ファイル/表紙cacheを整備して先読みqueueを投入する。renameはremoteファイルとcatalog identityを更新し、decoratorが書誌decisionも移す。prepareBookは同じサイズのcacheを再利用し、未取得ならtemporaryへdownload→長さ検証→cache rename→表紙更新を行い、finallyでtemporaryを消す。位置指定media readはDefaultSmbMediaFileAccessの別handleで、closeがファイル/share/session/connectionを解放する。

## 背景queue・書誌候補・失敗の関係

表紙Workerは永続queueの中断RUNNINGを戻し、claim→processor→進捗→complete/skip/failを保存する。queue snapshotとWorkManager runtime snapshotを合成するため、待機条件のあるENQUEUEDと項目FAILEDを区別できる。正規化Workerは表紙readyをQUEUEDへ昇格、local AI gateのpermit内でclaim、ファイル名/サイズ/更新日時を再確認し、RemoteSuggesterへ表紙とpromptを渡してPENDING_REVIEW候補を保存する。表紙消失時は再取得へ戻し、中断時はcurrentをrequeueする。

`DefaultSmbMetadataNormalizationRepository.applyCandidate` は候補状態と現在revisionを確認してからremote renameし、採用書誌を保存する。保存に失敗してidentityが変わった場合は元名へのrenameを試みる。APPLIED候補の後編集ではファイル名を再変更しない。[AndroidManifest](../../../feature/library/data/src/main/AndroidManifest.xml) のSmbMetadataNormalizationInferenceServiceは非exportedな別processであり、RemoteInferenceSessionがBinder deathとprocess解放を扱う。local推論寿命をUIへ持ち込まない。

分類batchはLibraryOrganizationRepositoryでdurableに保持し、Workerが書籍をclaimして分類候補を作る。単冊候補/シリーズ再整理はLibraryOrganizationAiTaskControllerが参照と結果を保持し、画面はsnapshot/recoverableTasks/dismissで消費する。全WorkerはLibraryWorkerFactoryを通じ同じownerのRepositoryとschedulerを受け取る。

## Web 書誌と抽出ルールの経路

DefaultWebLibraryMutatorは静的HTTPの成功/失敗を保持し、HTTPSかつ強制再取得・custom rule一致・情報不足の場合に描画clientを使う。静的と描画の双方が成功すればmergeし、描画だけ失敗なら静的結果とfallback理由を返す。キャンセルはfallbackへ変換しない。refreshは既存sourceIdを保ち、removeはWEB以外を拒否する。

Foreground clientはresume済みActivityを要求し、AndroidWebViewLibraryMetadataClientがWebViewの寿命、renderer終了、timeoutとPromise形式custom extractorの開始/poll/cleanupを管理する。DefaultWebLibraryMetadataExtractorRepositoryはURL pattern/関数/timeoutを検証して保存する。Testerは未保存ruleだけのread-only repositoryで同じ描画経路を試すため、試験で本設定を上書きしない。

## 手動状態と変更時の注意

非表示はカタログ行の削除ではなく、取得元とsource IDに対応する別状態として保存します。手動シリーズの一括更新では入力を検証してからトランザクションで反映し、シリーズ除外設定との整合も取ります。同期形式や書誌解析の変更では、外部データを再取得しても利用者の手動判断が残るかを確認してください。SMB処理の変更は関連する表紙キュー・正規化キューのテストも参照し、カタログ更新とバックグラウンド処理状態を混同しないよう追跡します。

## 調査と変更の入口

[DefaultLibraryRepository.kt](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepository.kt)、[LibraryDatabaseSchema.kt](../../../feature/library/data/src/main/kotlin/dev/terashima/yomitorirss/feature/library/data/LibraryDatabaseSchema.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [DefaultLibraryRepositorySeriesMergeTest.kt](../../../feature/library/data/src/test/kotlin/dev/terashima/yomitorirss/feature/library/data/DefaultLibraryRepositorySeriesMergeTest.kt)、[SmbCoverPrefetchQueueStoreTest.kt](../../../feature/library/data/src/test/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbCoverPrefetchQueueStoreTest.kt)、[SmbMetadataNormalizationQueueTest.kt](../../../feature/library/data/src/test/kotlin/dev/terashima/yomitorirss/feature/library/data/SmbMetadataNormalizationQueueTest.kt) です。

[library domain](library-domain.md)、[library ui](library-ui.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
