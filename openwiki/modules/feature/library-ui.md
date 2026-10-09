---
type: module
title: 蔵書 UI：一覧・書誌レビュー・Reader接続
description: 蔵書画面の状態、同期操作、AI結果復旧とBook Readerへの接続を説明する。
tags:
  - library
  - ui
  - module
sources:
  - id: openwiki-source-3ab6185a5e2ae1401b87dab5
    resource: repo://feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryGrouping.kt
  - id: openwiki-source-1aeb7e781fd31e0e19b6adff
    resource: repo://feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryViewModel.kt
  - id: openwiki-source-1147e461969d759c1e6b575e
    resource: repo://feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookReaderRoute.kt
  - id: openwiki-source-35ef98bc318270b0e350799d
    resource: repo://feature/library/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModelTest.kt
generated: { by: "codex", at: "2026-10-09T08:22:11.354Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T08:22:11.354Z
---

# 蔵書 UI：一覧・書誌レビュー・Reader接続

`:feature:library:ui` は次の責務を持ちます。

## 画面の入口と状態

LibraryFeatureRouteが蔵書画面、外部URL操作、取り込み画面、SMB readerを結びます。LibraryViewModelはカタログと取得元状態に加え、同期・書誌正規化・表紙先読みの操作中状態と表示メッセージをStateFlowへ公開します。依存はLibrary Domainを中心に、Book ReaderのDomain/UIとWeb collectorへ向き、蔵書Dataの具象実装は画面へ持ち込みません。

Google Booksの同期やSMB同期はViewModelから公開Repositoryへ依頼し、成功時にsnapshotを再読込します。SMB同期後は表紙先読みの投入と書誌正規化schedulerの起動を行うため、同期完了と表紙やAI候補の完成は同時ではありません。画面ではそれぞれの進捗を別の状態として表示します。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [AmazonWebLibraryCollectorScripts](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/AmazonWebLibraryCollectorScripts.kt) | AmazonWebLibraryImportDialogが使うKindle/Audible collector scriptを定義し、表示済みlibrary JSONの収集処理をcore web collectorへ渡す。 |
| [AmazonWebLibraryImportDialog](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/AmazonWebLibraryImportDialog.kt) | 内部AmazonWebLibraryImportDialogは取得元別URL/collector configを作り、収集JSONを検証してimport callbackへ返す。WebLibrarySourceConfigが設定をまとめる。 |
| [AudibleWebLibraryImportGuide](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/AudibleWebLibraryImportGuide.kt) | 内部AudibleWebLibraryImportGuideがAudible browser importの手順を表示する。 |
| [KindleWebLibraryImportGuide](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/KindleWebLibraryImportGuide.kt) | 内部KindleWebLibraryImportGuideがKindle browser importの手順を表示する。 |
| [LibraryFeatureRoute](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRoute.kt) | 公開LibraryFeatureRouteはFactoryとDomain capabilityを画面へ接続し、認可・Amazon import・外部開く・Web repair・SMB readerを束ねる。内部LibraryUriHandlerとGoogleBooksLinkType/googleBooksLinkType/normalizeGooglePlayBooksReaderUrl/readerActivityScoreが外部Intentを振り分ける。 |
| [LibraryFiltering](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFiltering.kt) | 内部filterLibraryBooksBySource/ByTextは取得元と書誌文字列で絞り、空queryは全件、複数語はAND条件にする。 |
| [LibraryGrouping](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryGrouping.kt) | 内部LibrarySeriesSection/BookGroupsとinferLibrarySeriesFromTitle/groupLibraryBooks/mergeLibrarySeriesが自動推定・group化・series更新入力を作る。 |
| [LibraryMetadataManagement](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataManagement.kt) | 内部LibraryMetadataBookGroup、libraryTagGroups/libraryCollectionGroups、withoutTag/withoutCollectionが分類別book groupと削除draftを作る。 |
| [LibraryMetadataManagementDialog](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryMetadataManagementDialog.kt) | 公開LibraryMetadataManagementDialogはタグ・collection・seriesの管理と詳細編集を表示する。 |
| [LibraryOrganizationFiltering](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationFiltering.kt) | 公開LibraryOrganizationFilter enumと内部filterLibraryBooksForOrganizationが未整理・読書状態による対象を選ぶ。 |
| [LibraryOrganizationScreen](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationScreen.kt) | 公開LibraryOrganizationDialogは分類編集とAI候補/batchを表示し、内部splitOrganizationNamesが入力欄の名前を分解する。 |
| [LibraryOrganizationViewModel](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModel.kt) | LibraryOrganizationUiState/ViewModel/Factoryは分類snapshot・候補・working・batchを保持。refresh/save/suggest/startBatch/reorganizeSeries/pauseBatch/resumeBatch/dismissMessageを公開し、recoverable taskを再観測する。 |
| [LibraryScreen](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryScreen.kt) | 公開LibraryScreenは全件/シリーズ/非表示/設定を表示。内部LibraryBookTapAction/tapActionがSMB・外部URI・メニューを判定し、thumbnail・series dialog・rename/delete dialogが局所操作を担う。 |
| [LibraryViewModel](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryViewModel.kt) | LibraryUiState/LibraryViewModel/Factoryはカタログ・sync busy・表紙queue・正規化batch/promptを所有。syncGooglePlayBooks/syncSmbLibrary、cover enqueue/retry/reschedule、正規化採用等、SMB設定/rename/delete、Amazon import、hide/restore/series更新を公開する。 |
| [NavigationDestination](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/NavigationDestination.kt) | LIBRARY_ROUTE/LIBRARY_TITLEが蔵書navigation metadata。 |
| [SmbBookFileActionBinding](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookFileActionBinding.kt) | 内部SmbBookFileActionBindingがSMB rename/deleteの画面callbackと操作状態を束ねる。 |
| [SmbBookReaderRoute](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookReaderRoute.kt) | 公開SmbBookReaderRouteがprepareBook→BookDocument→source open→BookReaderScreenを接続。BookPreparationScreen/DownloadProgressが進捗と再試行、DisposableEffectがsource.closeを担当する。 |
| [SmbConnectionProfileProvider](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbConnectionProfileProvider.kt) | 公開ProvideSmbConnectionProfileRepositoryがSMB profile capabilityをCompositionLocalへ注入する。 |
| [SmbLibraryBinding](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbLibraryBinding.kt) | 内部SmbLibraryUiBindingがSMB設定callbackを共有し、canEditSmbMetadataNormalizationPromptが実行状態で編集可否を決め、SmbLibrarySettingsFromBindingが表示へ接続する。 |
| [SmbLibraryLocationSettings](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbLibraryLocationSettings.kt) | 内部SmbLibraryLocationSettingsSectionが共有profileと蔵書場所の選択、queueの進捗/待機/skip理由を表示する。 |
| [SmbLibrarySettings](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbLibrarySettings.kt) | 内部SmbLibrarySettingsSectionは従来server設定と表紙queueを表示。visibleSmbCoverPrefetchItemsが表示対象を選別する。 |
| [SmbMetadataNormalizationPromptSettings](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMetadataNormalizationPromptSettings.kt) | 内部SmbMetadataNormalizationPromptSettingsSectionがprompt入力・保存・既定resetをcallbackへ接続する。 |
| [SmbMetadataNormalizationReview](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbMetadataNormalizationReview.kt) | 内部SmbMetadataNormalizationSettingsSectionとReviewDialog/ReviewCard/CandidateEditDialog/ReanalysisDialogが候補採用・保留・却下・再解析指示を表示する。 |
| [WebLibraryActions](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryActions.kt) | 公開WebLibraryAddActionがURL入力/追加を扱う。内部RefreshItemStatus/ItemUiState/RefreshUiState/SettingsUiBindingとsuccessUiState/statusLabelが再取得の進捗・差分・fallbackを表す。 |
| [WebLibraryDeleteAction](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryDeleteAction.kt) | 内部canDeleteFromLibrary/WebLibraryDeleteDialogがWEBだけの完全削除を確認してcallbackへ渡す。 |
| [WebLibraryMetadataExtractorEditorBottomSheet](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMetadataExtractorEditorBottomSheet.kt) | 内部EditorBottomSheetとTestResultCard/testStatusLabelがURL pattern・関数code・timeout編集と未保存rule試験結果を表示する。 |
| [WebLibraryMetadataExtractorRoute](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMetadataExtractorRoute.kt) | 公開LibraryFeatureRouteのoverloadと内部WebLibraryMetadataExtractorUiBindingが抽出Repository/Testerを通常routeの設定UIへ接続する。 |
| [WebLibraryMetadataRepair](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryMetadataRepair.kt) | 内部needsWebMetadataRepair/missingWebMetadataLabelsがWEBのhost fallback titleと欠落表紙を再取得対象にする。 |
| [WebLibraryOpenRouting](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryOpenRouting.kt) | 内部webLibraryOpenUrlsがWEBのopen URL集合を作り、外部URL処理とWeb蔵書表示を接続する。 |
| [WebLibrarySettingsWithExtractorBottomSheet](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibrarySettingsWithExtractorBottomSheet.kt) | 内部WebLibrarySettingsWithExtractorBottomSheetがURL追加とrule一覧/編集のbottom sheetをまとめる。 |
| [WebLibraryThumbnailImage](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/WebLibraryThumbnailImage.kt) | 内部WebLibraryThumbnailPreview/LibraryThumbnailImage/rememberLibraryThumbnailModelsとwebLibraryImageRequestHeaderCandidates/webLibraryImageCacheKey/isUsableWebLibraryThumbnail/webLibraryImageReferer関数が認証依存画像の表示・fallback・cache分離を扱う。 |

## API と画面/Domain の接続

`LibraryFeatureRoute` はFactoryとLibrary/SMB/Web/Readerのcapabilityを受け取り、LibraryScreenへ一覧操作、設定へbinding、別dialogへ分類/書誌編集を接続する。extractor用overloadはRepositoryとTesterをCompositionLocal bindingへ追加する。接続先は [AppLibraryRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/library/AppLibraryRuntimeDependencies.kt) の公開runtimeであり、UIがData具象を生成しない。

LibraryViewModelのsync/import/hide/restore/series/rename/delete群はDomain呼出後にsnapshotをロードする。cover queueとnormalization batchは活動中だけpollし、prompt更新は解析/処理中のbusy状態で制御する。LibraryOrganizationViewModelは分類save・suggest・series再整理・batch start/pause/resumeを担当し、単冊候補とseries結果をcontrollerから回収して表示状態へ反映する。LibraryMetadataManagementDialogは分類groupから対象bookを選び、withoutTag/withoutCollectionのDraftで読書状態や他分類を保つ。

## 代表的な閲覧・取り込みフロー

1. LibraryScreenのtapActionはSMBをReader、openUrlのある取得元を外部URI、残りをメニューへ振り分ける。LibraryUriHandlerはKindle Personal Documentの専用URIやGoogle Booksリンクを判定し、利用可能なreader ActivityへIntentを送る。起動不能は利用者へmessageを表示する。
2. SMB閲覧はSmbBookReaderRouteがprepareBookをIOで実行してbytes進捗を表示し、BookDocumentを作ってpageSourceFactory.open、BookReaderScreenへ渡す。取得/open失敗は再試行画面になり、DisposableEffectの離脱でsource.closeする。再試行はretryKeyの変更でprepareBookを再実行するが、sourceResultはdocumentとpageSourceFactoryをキーにrememberされるため、同じBookDocumentでのpageSourceFactory.open失敗は再試行後も残る場合がある。
3. AmazonWebLibraryImportDialogはcore web collectorに取得元別のconfig/scriptを渡し、JSONを検証してViewModel.importAmazonLibraryJsonへ渡す。同期完了後の表紙や書誌解析は別queueで進む。
4. Web追加はWebLibraryAddAction、再取得はrouteのrefresh状態、完全削除はWebLibraryDeleteDialogで操作する。extractor editorは未保存ruleのtest結果とPromise/timeoutなどのstatusを表示してから保存できる。

検索は各語がtitle/authors等のいずれかに含まれるAND条件で、series groupingは既存設定を優先し、automaticSeriesExcludedの書籍にタイトル推定を適用しない。シリーズmergeは双方を一括update入力にし、巻数を保つ。サムネイルのreferer/header候補とcache keyはWeb画像の取得条件を反映するため、URLだけで表示可否を判断しない。

## AI結果と閲覧の寿命

整理画面はLibraryOrganizationViewModelがAI task controllerから復旧可能な受付を取得し、候補を利用者の操作単位で消費します。画面の再生成を理由に候補そのものを再生成する設計ではありません。SMB読書はLibrary側でファイル取得を仲介し、Book Readerへローカル文書とページsourceを渡す境界を追います。変更時は書籍タップの取得元別振り分け、候補保存・却下、エラー後のbusy解除、画面再生成後の候補復旧を優先して確認してください。

## 調査と変更の入口

[LibraryFeatureRoute.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRoute.kt)、[LibraryViewModel.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryViewModel.kt)、[LibraryOrganizationViewModel.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModel.kt)、[SmbBookReaderRoute.kt](../../../feature/library/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/library/SmbBookReaderRoute.kt) を入口に、呼び出し側と保存先を併せて追います。 検証の代表例は [LibraryOrganizationViewModelTest.kt](../../../feature/library/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryOrganizationViewModelTest.kt)、[LibraryFeatureRouteTest.kt](../../../feature/library/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/library/LibraryFeatureRouteTest.kt) です。

[library domain](library-domain.md)、[library data](library-data.md)、[媒体連携](../../integrations/media.md)を参照してください。

仕様の正本は[蔵書とBook Reader](../../../docs/spec/07-library-reader.md)です。
