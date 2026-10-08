---
type: module
title: Settings UI — AI実行先とモデル・接続管理
description: モデル管理画面、ChatGPT接続と実行先選択、関連feature設定の合成を説明する。
tags: [settings, ui, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-52e3ad1391d7d0e0d005819d
    resource: repo://feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Settings UI — AI実行先とモデル・接続管理

`:feature:settings:ui`

## 設定画面の入口と状態

SettingsScreenとSettingsContentはモデル管理、AI実行設定、ChatGPT接続確認、SMB接続、backupやAI task queueなどの入口を合成する。Settings UIは関連featureのDomain/UIに依存するが、具体的なData実装には接続しない。AiSettingsViewModelはAiModelRepository、provider/debug Repository、Summaryのpromptと実行先設定、Knowledgeの実行先設定を注入され、それぞれのowner capabilityへ操作を戻す。

モデル一覧、download進捗、推論設定、要約prompt、Summary/KnowledgeのproviderをFlowから表示へ反映する。downloadのcompleted/failedはactiveな進捗として表示しない。benchmarkやcontext計測のresult/error、接続状態、モデル候補、login session、debug responseは画面用状態で、durableなmodelやcredentialの正本にはしない。

## 接続とprovider選択

ChatGPT候補の取得は未接続時と取得中の二重開始を避ける。候補選択では一覧内のモデルを確認し、Web検索非対応は拒否する。SummaryまたはKnowledgeをCHATGPTへ切り替える際も接続済みかつモデル選択済みを確認してからowning featureの設定を更新する。ログアウトではdebug Repositoryを呼び、両featureのproviderをLOCALへ戻して、接続情報と結果表示を解除する。

重い計測やdebug要求はbusy状態と例外表示を管理し、coroutine cancellationを通常失敗に変換しない。モデル画面変更はModelManagerDialog、実行設定はAiExecutionSettingsScreen、接続確認はChatGptDebugDialogから追う。AiSettingsUiStateTestは初期状態とlogout時のprovider変換を検証するが、接続のHTTP挙動やmodel fileの検証はData/coreのテスト範囲である。

## 調査・変更の入口

- [AiSettingsViewModel.kt](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt)
- [SettingsContent.kt](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsContent.kt)
- [ModelManagerDialog.kt](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ModelManagerDialog.kt)
- [AiExecutionSettingsScreen.kt](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiExecutionSettingsScreen.kt)
- [AiSettingsUiStateTest.kt](../../../feature/settings/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsUiStateTest.kt)

関連モジュール: [settings-domain](settings-domain.md)、[settings-data](settings-data.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
