---
type: module
title: Knowledge Data — 資料収集・永続ページ・背景生成
description: 保存済み要約からの生成、編集済みページ保護、分割統合とfeature-owned Workerを説明する。
tags: [knowledge, data, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3f6c6869ac6858037d6d69bc
    resource: repo://feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/DefaultKnowledgeGenerationService.kt
  - id: openwiki-source-d7b4b0954afec5809848ffac
    resource: repo://feature/knowledge/data/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/ManagingKnowledgeRepository.kt
  - id: openwiki-source-315fd2b1fd6c687db540a5fb
    resource: repo://feature/knowledge/data/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/data/KnowledgeApplicationServiceArchitectureTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Knowledge Data — 資料収集・永続ページ・背景生成

`:feature:knowledge:data`

## 資料からページへの経路

DataはSqlKnowledgePageStoreを永続化境界とし、DefaultKnowledgeRepositoryは読取だけを委譲する。DefaultKnowledgeGenerationServiceがBookmarkReaderとSummaryReaderから資料を集め、topic計画、prompt生成、推論結果の解析、storeへの保存を担当する。RepositoryにSQLとAI処理を混ぜず、generation serviceもSQLを直接操作しないことはarchitecture testで確認される。

再構築計画はsource fingerprintを比較し、資料が変わらないtopicやeditor-managedなページを再利用する。対象topicごとの実行時にもfingerprintと編集保護を再確認するため、計画後に状態が変わっても単純に上書きしない。本文と出典はKnowledge-ownedのknowledge_pagesとknowledge_page_sourcesへ保存する。

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
