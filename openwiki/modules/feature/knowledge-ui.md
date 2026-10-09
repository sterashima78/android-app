---
type: module
title: Knowledge UI — ページ閲覧とAIタスクの回収
description: 検索・ページ管理と背景AIタスク登録、画面再生成後の結果回収を説明する。
tags:
  - knowledge
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-a80fbeeca7bcf4f03245e4f0
    resource: repo://feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Knowledge UI — ページ閲覧とAIタスクの回収

`:feature:knowledge:ui`

## 画面の責務

KnowledgeRouteはfactoryからViewModelを取得し、一覧検索、個別ページ表示、作成・編集・削除・分割・統合の操作をKnowledgeScreenへ接続する。KnowledgeUiStateは検索語、選択ページ、composer入力、確認dialog、作業中表示と通知を所有する。本文の永続化と生成要求の保持はDomain capabilityの向こう側にあり、この表示状態へ移さない。

## 主要な構成要素

| 構成要素と所在 | 種類・責務・主な API と関係 |
| --- | --- |
| [KnowledgeRoute](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeRoute.kt) | 公開KnowledgeRouteはViewModel.Factoryを受け状態を収集し、KnowledgeScreenの全操作callbackをViewModelへ接続する。 |
| [KnowledgeScreen](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeScreen.kt) | 公開KnowledgeScreenが一覧/詳細を切替。内部KnowledgePageList/PageDetail/CreateDialog/DeleteDialog/SplitDialog/MergeDialogが各操作を表示し、splitHeadingCandidatesが有効な分割見出しを選ぶ。 |
| [KnowledgeViewModel](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModel.kt) | KnowledgeUiState/KnowledgeViewModel/Factoryは検索・選択・入力・dialog・working・通知をStateFlowへ公開。create/editはtask登録、delete/split/mergeはRepository、rebuildはscheduler callbackへ委譲する。 |
| [NavigationDestination](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/NavigationDestination.kt) | KNOWLEDGE_ROUTE/KNOWLEDGE_TITLEがnavigation metadataを公開する。 |

## API・画面間の関係と管理フロー

`KnowledgeRoute(viewModelFactory)` は公開入口、`KnowledgeScreen(state, callbacks)` は表示入口である。入力のupdateQuery/updateComposerRequest/updateEditInstructionとdialogのstart/cancel群は表示状態を更新する。openPage/closePageは選択ページ、deletePage/splitPage/mergePageはRepositoryの管理APIを呼び、成功後に検索一覧と選択ページを更新する。

検索の `refresh` は呼出時queryを保持し、返却時にもqueryが同じ場合だけStateFlowへ反映する。変更通知後の `refreshAfterDataChange` はqueryと選択IDを照合するため、遅れたquery結果で新しい選択を置き換えない。split dialogは本文の有効な第2レベル見出しだけを候補とし、merge候補から自分自身を除く。

Factoryの実装接続は [AppKnowledgeRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/knowledge/AppKnowledgeRuntimeDependencies.kt) と [AppKnowledgeTaskRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/knowledge/AppKnowledgeTaskRuntimeDependencies.kt) を追う。ViewModelはAI推論や永続要求を所有せず、controllerのrequest IDを観測して保存済み結果を読み直す。端末再生成で失われるのは一時入力や選択状態であり、WorkManager要求の寿命とは分けて扱う。

## AI操作と再接続

作成要求や編集指示はtrimして空ならエラーを表示し、作業中の二重操作を抑止する。AI生成はPageAiTaskControllerへenqueueし、返されたrequest IDのsnapshotを観測する。QUEUED/RUNNINGでは待機し、SUCCEEDEDではpageIdから保存済みページを読み直して一覧と選択ページを更新する。成功結果を読めない場合も表示上のエラーにする。FAILED/CANCELLEDは通知して作業中を解除し、terminalな参照はdismissする。

初期化時にはrecoverableTaskへ再接続するため、画面再生成の前に始めた作成・編集も回収できる。repository.changesを購読して、ページ操作や背景処理によるデータ変更を表示へ反映する。全体再構築ボタンはbuilderを画面内で実行せず、注入されたscheduleRebuildを呼ぶ。

削除・分割・統合の表示や入力制御はViewModel、本文の分割や引用の修正規則はDataへ降りて調べる。KnowledgeViewModelTestは空入力がtask登録されないこと、未消費taskへの再接続、失敗結果のdismiss、再構築のscheduler委譲を検証する。

## 調査・変更の入口

- [KnowledgeRoute.kt](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeRoute.kt)
- [KnowledgeViewModel.kt](../../../feature/knowledge/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModel.kt)
- [KnowledgeViewModelTest.kt](../../../feature/knowledge/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/knowledge/KnowledgeViewModelTest.kt)

関連モジュール: [knowledge-domain](knowledge-domain.md)、[knowledge-data](knowledge-data.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
