---
type: module
title: Settings UI — AI実行先とモデル・接続管理
description: モデル管理画面、ChatGPT接続と実行先選択、関連feature設定の合成を説明する。
tags:
  - settings
  - ui
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-52e3ad1391d7d0e0d005819d
    resource: repo://feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt
  - id: openwiki-source-bbf55abc9df03f4c88cb0394
    resource: repo://feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsScreen.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Settings UI — AI実行先とモデル・接続管理

`:feature:settings:ui`

## 設定画面の入口と状態

SettingsFeatureScreenとSettingsContentはモデル管理、AI実行設定、ChatGPT接続確認、SMB接続、backupやAI task queueなどの入口を合成する。Settings UIは関連featureのDomain/UIに依存するが、具体的なData実装には接続しない。AiSettingsViewModelはAiModelRepository、provider/debug Repository、Summaryのpromptと実行先設定、Knowledgeの実行先設定を注入され、それぞれのowner capabilityへ操作を戻す。

モデル一覧、download進捗、推論設定、要約prompt、Summary/KnowledgeのproviderをFlowから表示へ反映する。downloadのcompleted/failedはactiveな進捗として表示しない。benchmarkやcontext計測のresult/error、接続状態、モデル候補、login session、debug responseは画面用状態で、durableなmodelやcredentialの正本にはしない。



## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [SETTINGS_ROUTE](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/NavigationDestination.kt) / [SETTINGS_TITLE](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/NavigationDestination.kt) | 公開const。navigation用のrouteと表示名。 |
| [SettingsFeatureScreen](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsScreen.kt) / [SettingsOverlay](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsScreen.kt) / [SettingsContent](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SettingsContent.kt) | 公開画面・内部enum・公開表示Composable。設定行とmodalを接続し、通知権限、背景取得、更新間隔、生体lock、SAF/Web serverをapp callbackへ渡す。SettingsContentは同名ファイルにある。 |
| [AiSettingsUiState](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt) / [AiSettingsViewModel](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt) / [Factory](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt) | 表示モデル・公開ViewModel・factory。model、benchmark、prompt、cloud login/model/provider、診断応答と通知を所有し、各Domain setterを呼ぶ。 |
| [ModelManagerDialog](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ModelManagerDialog.kt) | 公開Composable。取得/選択/削除、backend/context/thinking/speculative設定、二種benchmarkのcallbackと結果を表示する。 |
| [ChatGptDebugDialog](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugDialog.kt) / [AiExecutionSettingsScreen](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiExecutionSettingsScreen.kt) | 公開Composable群。device login、候補選択と診断推論、Summary/Knowledge provider選択を表示する。後者はAiExecutionSettingsScreen.ktにある。 |
| [SmbConnectionSettingsDialog](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SmbConnectionSettingsDialog.kt) / [SmbConnectionProfileEditDialog](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/SmbConnectionSettingsDialog.kt) | internal/private Composable群。LibraryのSmbConnectionProfileRepositoryを使い接続設定の一覧、編集、削除を扱う。 |
| [ContextBenchmarkKey](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt) / [summaryExecutionProviderAfterChatGptLogout](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt) / [knowledgeExecutionProviderAfterChatGptLogout](../../../feature/settings/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiSettingsViewModel.kt) | 内部keyとinternal関数。model/backend/speculativeに対応するreportだけを反映し、logout後のproviderをLOCALに戻す。 |

## 主要なAPI・構成要素の接続

`AiSettingsViewModel`はmodels、downloadProgress、summaryProgress、inferenceSettingsとSummary prompt/provider、Knowledge providerをcollectしてstateを更新する。`prepareModelManager/prepareChatGptDebug`はmodal表示前の結果整理・接続再読を行う。model操作は`downloadModel/selectModel/deleteModel`、prompt編集は`updateSummaryPrompt/resetSummaryPrompt`、推論設定は`setInferenceBackend/setContextSizeMode/setThinkingEnabled/setSpeculativeDecodingEnabled`へ進む。通知は`dismissMessage`で消費する。

cloud操作は`startChatGptLogin/pollChatGptLogin/logoutChatGpt`、`refreshChatGptModels/selectChatGptModel`、`setSummaryExecutionProvider/setKnowledgeExecutionProvider`、診断入力の`setChatGptModelId/setChatGptPrompt`と`runChatGptDebugInference`に分かれる。CHATGPT選択はログインと選択modelを要求し、logoutは二ownerのproviderをLOCALへ戻す。診断推論も選択済みprovider model IDを使う。

`runModelBenchmark/runContextBenchmark`は同じbenchmarkRunningで重複を抑止し、cancelを再throw、一般失敗を専用errorへ格納する。`refreshContextBenchmarkIfNeeded`はmodel/backend/speculativeのkeyを保存して過去結果を取得し、key変更後に戻った古い結果を反映しない。

[BackupViewModel](backup-ui.md)、[AiTaskQueueRoute](ai-task-queue-ui.md)、[Library SMB契約](library-domain.md)は各ownerから受ける。SettingsFeatureScreenはoverlayだけを所有し、権限取得や永続設定は外側のcallback/Domainへ委譲する。


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
