---
type: module
title: Knowledge Data — 資料収集・永続ページ・背景生成
description: 保存済み要約からの生成、編集済みページ保護、分割統合とfeature-owned Workerを説明する。
tags:
  - knowledge
  - data
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-09c454479e4f3bbfb25a88bc
    resource: repo://feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ChatGptKnowledgeTextInference.kt
  - id: openwiki-source-3f6c6869ac6858037d6d69bc
    resource: repo://feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeGenerationService.kt
  - id: openwiki-source-d7b4b0954afec5809848ffac
    resource: repo://feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ManagingKnowledgeRepository.kt
  - id: openwiki-source-315fd2b1fd6c687db540a5fb
    resource: repo://feature/knowledge/data/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeApplicationServiceArchitectureTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Knowledge Data — 資料収集・永続ページ・背景生成

`:feature:knowledge:data`

## 資料からページへの経路

DataはSqlKnowledgePageStoreを永続化境界とし、DefaultKnowledgeRepositoryは読取だけを委譲する。DefaultKnowledgeGenerationServiceがBookmarkReaderとSummaryReaderから資料を集め、topic計画、prompt生成、推論結果の解析、storeへの保存を担当する。RepositoryにSQLとAI処理を混ぜず、generation serviceもSQLを直接操作しないことはarchitecture testで確認される。

再構築計画はsource fingerprintを比較し、資料が変わらないtopicやeditor-managedなページを再利用する。対象topicごとの実行時にもfingerprintと編集保護を再確認するため、計画後に状態が変わっても単純に上書きしない。本文と出典はKnowledge-ownedのknowledge_pagesとknowledge_page_sourcesへ保存する。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [ChatGptKnowledgeTextInference](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ChatGptKnowledgeTextInference.kt) | ChatGptKnowledgeTextInferenceはBackgroundAiTextInferenceをcloud clientへ接続し、classifyKnowledgeProviderFailureが再試行可能性と利用者向けmessageをDomain例外へ変換する。 |
| [DefaultKnowledgeGenerationService](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeGenerationService.kt) | DefaultKnowledgeGenerationServiceはBookmarkReader/SummaryReaderから資料収集し、planRebuild/rebuildTopic/rebuild/createPage/editPageを実行する。buildKnowledgePage/Refresh/Creation/EditPromptが用途別promptを組み立てる。 |
| [DefaultKnowledgeRepository](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeRepository.kt) | DefaultKnowledgeRepositoryはKnowledgeReader実装であり、listPages/findPage/changesをSqlKnowledgePageStoreへ委譲する。 |
| [KnowledgeBuildBackground](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeBuildBackground.kt) | WorkManagerKnowledgeBuildTaskControllerは要求の寿命と再開を管理。内部KnowledgeBuildWorkerが計画、KnowledgeTopicBuildWorkerがtopic実行、ResumeOnChargingWorkerが充電後再開。公開KnowledgeWorkerFactoryがrunner/controllerをWorkerへ注入する。 |
| [KnowledgeBuildQueueStateStore](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeBuildQueueStateStore.kt) | KnowledgeBuildQueueStateStoreはrequest/stop/fail/error/pending topicをSharedPreferencesへ保存し、request IDで旧実行の更新を拒否する。 |
| [KnowledgeDatabaseSchema](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeDatabaseSchema.kt) | knowledgeDatabaseSchemaはKnowledge-ownedページと出典tableをDatabaseSchemaContributionとして登録する。 |
| [KnowledgeExecutionPreferences](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeExecutionPreferences.kt) | KnowledgeExecutionPreferencesはKnowledgeExecutionSettingsをSharedPreferencesとStateFlowで実装し、変更時だけonProviderChangedを呼ぶ。 |
| [KnowledgePageAiBackground](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgePageAiBackground.kt) | WorkManagerKnowledgePageAiTaskControllerは要求ファイルとWorkManager参照を作り、recoverableTask/snapshot/dismissで未消費結果を管理。KnowledgePageAiWorkerがcreate/editを実行。privateなKnowledgePageAiRequest/Operation/RequestStoreとKnowledgePageAiTaskStoreが本文入力と寿命を保持する。 |
| [KnowledgeTopics](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeTopics.kt) | 内部KnowledgeGenerationSource/KnowledgeTopic/GeneratedKnowledgeDocumentが生成入力とtopic/出力を表す。buildKnowledgeTopics/selectKnowledgeSourcesが集約/検索選択、parseGeneratedKnowledgeDocument/fallbackKnowledgeTitle/sha256が整形と識別を支える。 |
| [ManagingKnowledgeRepository](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ManagingKnowledgeRepository.kt) | ManagingKnowledgeRepositoryは読取decoratorと削除/分割/統合を提供。内部KnowledgeSplitContent/MergeContentとknowledgeSplitHeadings/splitKnowledgePage/mergeKnowledgePagesが本文と引用を変換する。 |
| [RoutingKnowledgeGenerationService](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/RoutingKnowledgeGenerationService.kt) | RoutingKnowledgeGenerationServiceはKnowledgeBuilder/BuildRunner/PageAiRunner実装で、明示providerによりlocal/cloud serviceとautoWikiSourceLimitを選ぶ。 |
| [SqlKnowledgePageStore](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/SqlKnowledgePageStore.kt) | SqlKnowledgePageStoreはquery・出典・fingerprint・editor-managed ID・不要ページ削除・persistPageを扱うSQL境界。内部KnowledgePageHeaderがtopic identityと編集管理情報を保持する。 |

## API・composition と代表的な生成フロー

[AppKnowledgeRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/knowledge/AppKnowledgeRuntimeDependencies.kt) は一つのSqlKnowledgePageStoreを読取とlocal/cloudの生成serviceに共有し、管理decoratorとrouting serviceをapplication scopeで作る。[AppKnowledgeTaskRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/knowledge/AppKnowledgeTaskRuntimeDependencies.kt) がexecution preference、controller、Worker factoryをrunnerへ接続する。

再構築は BookmarkReader.listAllSavedArticles → SummaryReader.findSummary → buildKnowledgeTopics → fingerprints/editor-managed照合 → topic Worker → prompt → inference → parseGeneratedKnowledgeDocument → persistPage の順となる。要約のない保存記事はskipに数え、通常フォルダとタグをtopic入力にする。topic実行は資料を再取得し、存在しないtopic/変更のないtopic/編集保護対象ならfalseを返す。ユーザー作成と編集は空指示・元ページ不存在・利用可能な資料なしを拒否し、成功ページをeditor-managedで保存する。

WorkManagerへproviderとrequest IDを渡し、state storeは同じrequest IDの結果だけを反映する。LOCALは端末内AIの停止・充電gate、CHATGPTはcloud pauseとnetwork制約に従う。retryableなcloud例外はResult.retry、非retryableはfailureとなる。API認証詳細や任意例外のraw messageをcloud adapterからそのままUIへ出さない。個別要求は入力文字列をWorkManager Dataへ直接詰めずrequest storeを使い、terminal結果はdismiss後にforgetする。

SQL storeはページ更新と出典全置換をtransactionにまとめる。管理操作は見出し前後が空になる分割や同じID同士の統合を拒否する。統合はarticleIdで出典を重複排除し、旧引用番号を新番号へ変換して本文を連結する。

## ユーザー操作と実行寿命

ManagingKnowledgeRepositoryは削除・分割・統合をtransactionで実行する。自動ページの削除はeditor-managedなtombstoneとして残して通常の読取から隠し、再構築による復活を防ぐ。ユーザー作成ページは条件を満たせば物理削除する。統合では重複出典と引用番号を調整し、変更通知を発行する。

WorkManagerKnowledgeBuildTaskControllerは要求とtopic計画を画面外で保持し、親Workerが計画しtopic Workerが生成する。stopは要求を保持し、cancelは要求とworkを解除、resumeは停止・失敗から再起動する。個別ページの作成・AI編集は別のKnowledgePageAiBackgroundで結果回収を支える。provider routingやretry方針を変更する際はbackground codeとqueue stateを同時に追う。KnowledgePageManagementTest、KnowledgeBuildBackgroundTest、KnowledgeApplicationServiceArchitectureTestが主要な入口になる。

## 調査・変更の入口

- [DefaultKnowledgeGenerationService.kt](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeGenerationService.kt)
- [ManagingKnowledgeRepository.kt](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ManagingKnowledgeRepository.kt)
- [KnowledgeBuildBackground.kt](../../../feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeBuildBackground.kt)
- [KnowledgePageManagementTest.kt](../../../feature/knowledge/data/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgePageManagementTest.kt)
- [KnowledgeApplicationServiceArchitectureTest.kt](../../../feature/knowledge/data/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeApplicationServiceArchitectureTest.kt)

関連モジュール: [knowledge-domain](knowledge-domain.md)、[knowledge-ui](knowledge-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
