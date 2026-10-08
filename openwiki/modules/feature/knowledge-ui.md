---
type: module
title: Knowledge UI — ページ閲覧とAIタスクの回収
description: 検索・ページ管理と背景AIタスク登録、画面再生成後の結果回収を説明する。
tags: [knowledge, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-a80fbeeca7bcf4f03245e4f0
    resource: repo://feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Knowledge UI — ページ閲覧とAIタスクの回収

`:feature:knowledge:ui`

## 画面の責務

KnowledgeRouteはfactoryからViewModelを取得し、一覧検索、個別ページ表示、作成・編集・削除・分割・統合の操作をKnowledgeScreenへ接続する。KnowledgeUiStateは検索語、選択ページ、composer入力、確認dialog、作業中表示と通知を所有する。本文の永続化と生成要求の保持はDomain capabilityの向こう側にあり、この表示状態へ移さない。

## AI操作と再接続

作成要求や編集指示はtrimして空ならエラーを表示し、作業中の二重操作を抑止する。AI生成はPageAiTaskControllerへenqueueし、返されたrequest IDのsnapshotを観測する。QUEUED/RUNNINGでは待機し、SUCCEEDEDではpageIdから保存済みページを読み直して一覧と選択ページを更新する。成功結果を読めない場合も表示上のエラーにする。FAILED/CANCELLEDは通知して作業中を解除し、terminalな参照はdismissする。

初期化時にはrecoverableTaskへ再接続するため、画面再生成の前に始めた作成・編集も回収できる。repository.changesを購読して、ページ操作や背景処理によるデータ変更を表示へ反映する。全体再構築ボタンはbuilderを画面内で実行せず、注入されたscheduleRebuildを呼ぶ。

削除・分割・統合の表示や入力制御はViewModel、本文の分割や引用の修正規則はDataへ降りて調べる。KnowledgeViewModelTestは空入力がtask登録されないこと、未消費taskへの再接続、失敗結果のdismiss、再構築のscheduler委譲を検証する。

## 調査・変更の入口

- [KnowledgeRoute.kt](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeRoute.kt)
- [KnowledgeViewModel.kt](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModel.kt)
- [KnowledgeViewModelTest.kt](../../../feature/knowledge/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModelTest.kt)

関連モジュール: [knowledge-domain](knowledge-domain.md)、[knowledge-data](knowledge-data.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
