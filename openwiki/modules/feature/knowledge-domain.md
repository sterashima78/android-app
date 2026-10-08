---
type: module
title: Knowledge Domain — ページ操作と生成タスクの契約
description: ナレッジの読取・編集管理・AI生成を分離し、再構築とページ生成の状態を定義する。
tags: [knowledge, domain, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-c7c8c652306ace0fe35effd2
    resource: repo://feature/knowledge/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgePageAiTaskController.kt
  - id: openwiki-source-b23705c373997e5870cbe414
    resource: repo://feature/knowledge/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Knowledge Domain — ページ操作と生成タスクの契約

`:feature:knowledge:domain`

## 責務と利用者

Knowledge DomainはMarkdown本文と出典を持つKnowledgePage、および一覧用のKnowledgePageSummaryを所有する。KnowledgeSourceは記事ID、URL、資料名、保存日時と引用番号を保持するため、生成本文と出典の関係をUIやChatが同じ契約で扱える。Readerには変更通知のStateFlowがあり、consumerは永続化の方式を知らずに一覧と個別ページを再取得できる。

## 管理操作とAI実行の分離

`KnowledgeRepository`はReaderとPageManagerを束ね、削除・見出しでの分割・ページ統合を提供する。再構築はKnowledgeBuilder、ユーザー要求からの作成はPageCreator、指示付き編集はPageEditorという別application capabilityである。これにより、読取consumerへAI推論やcross-context資料収集まで要求しない。

画面からのAI作成・編集はKnowledgePageAiTaskControllerへ登録し、request IDでsnapshotを読む。成功はpageId、失敗はerrorで伝え、未消費taskはrecoverableTaskで再発見してdismissできる。一方、全体再構築のControllerはkick、stop、cancel、resumeと充電時再開予約を扱い、QUEUED/RUNNING/PAUSED/STOPPED/FAILEDを投影する。両者の状態集合と消費手順を混同しない。

DomainはDB本文やWorkManager Dataを保持せず、Dataの実装へ委ねる。契約変更はKnowledge UI、ChatのReader利用、統合AIキューのbuild adapterを併せて確認する。状態遷移の正本は端末内AI仕様とそこから参照するKnowledgeのQuintモデルである。

## 調査・変更の入口

- [KnowledgeRepository.kt](../../../feature/knowledge/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeRepository.kt)
- [KnowledgeModels.kt](../../../feature/knowledge/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeModels.kt)
- [KnowledgeBuildTaskController.kt](../../../feature/knowledge/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeBuildTaskController.kt)
- [KnowledgePageAiTaskController.kt](../../../feature/knowledge/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgePageAiTaskController.kt)

関連モジュール: [knowledge-data](knowledge-data.md)、[knowledge-ui](knowledge-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
