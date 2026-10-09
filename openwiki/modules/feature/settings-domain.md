---
type: module
title: Settings Domain — AIモデル管理と接続設定の契約
description: ローカルモデル設定・download進捗・benchmark、ChatGPTモデル選択とdebug操作を説明する。
tags:
  - settings
  - domain
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-c326182dfa6898224aab7bca
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt
  - id: openwiki-source-ee916e8cc59dff2d6cb38a2a
    resource: repo://feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt
  - id: openwiki-source-73c4f9c447e98268a49b5776
    resource: repo://feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt
  - id: openwiki-source-5f2264a7eaf6fbebd93ba18d
    resource: repo://feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptProviderRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Settings Domain — AIモデル管理と接続設定の契約

`:feature:settings:domain`

## 設定境界の責務

Settings Domainはアプリの設定画面が利用するAIモデル管理、ChatGPT provider選択、接続確認の契約を所有する。設定のUIモデルをcore runtimeの具象型から分け、JVM moduleとしてAndroid componentやHTTP clientを公開しない。SummaryやKnowledgeの実行先選択そのものは各featureの契約であり、このDomainへまとめて移さない。



## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [AiInferenceBackend](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) / [AiContextSizeMode](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) / [AiInferenceSettings](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) | enumとdata class群。CPU/GPU、AUTO/固定context、thinking・speculative設定をruntimeから独立した語彙にする。 |
| [AiModelStatus](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) / [AiModelDownloadProgress](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) / [AiSummaryProgress](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) | data class群。catalogと選択/取得状態、byte進捗・isActive、推論段階を表す。 |
| [AiModelBenchmarkSample](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) / [AiModelBenchmarkComparison](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) | data class群。通常/投機的decodeの測定値と比較を返し、比較不能なら速度倍率はnull。 |
| [AiContextBenchmarkSample](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) / [AiContextBenchmarkReport](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) | data class群。contextごとの時間・PSS・空きメモリ・safe/errorと推奨contextを運ぶ。succeededはerrorの有無。 |
| [AiModelRepository](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt) | interface。四つのFlowとsupport判定、推論設定、取得/選択/削除、二種benchmark・前回結果のcapability。 |
| [ChatGptDebugStatus](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt) / [ChatGptDebugLoginSession](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt) / [ChatGptDebugLoginPollResult](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt) / [ChatGptDebugInferenceResult](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt) | モデル・enum群。接続状態、device login確認先/間隔/期限、PENDING/SLOW_DOWN/AUTHORIZED、応答と所要時間を公開する。 |
| [ChatGptDebugRepository](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt) | interface。defaultModelId、status/startLogin/pollLogin/logout/runInferenceの接続診断契約。 |
| [ChatGptProviderModel](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptProviderRepository.kt) / [ChatGptProviderRepository](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptProviderRepository.kt) | data classとinterface。選択候補とselectedModelId/selectModel/listModelsを定義する。 |

## 主要なAPI・構成要素の接続

`AiModelRepository.models/downloadProgress/summaryProgress/inferenceSettings`はそれぞれcatalog、取得、推論、設定を観測する。`downloadModel/selectModel/deleteModel`はmodel IDを入力し、各setterはDomain enum/Booleanをruntime設定へ渡す。`benchmarkSelectedModel`は通常・投機的decode比較、`benchmarkSelectedModelContexts`はcontext容量の測定、`lastContextBenchmark`は任意の過去reportを返す。

`ChatGptDebugRepository.startLogin`はsessionを返し、`pollLogin(sessionId)`は待機/確認間隔延長/認証済みを返す。`runInference(modelId,prompt)`はtextとelapsedを返す。通常のprovider選択は別の`ChatGptProviderRepository`にある。実装は[DefaultAiModelRepository / DefaultChatGptDebugRepository / DefaultChatGptProviderRepository](settings-data.md)。[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt)がLocalModelManagerとcloud clientを注入し、[AiSettingsViewModel](settings-ui.md)から利用する。


## ローカルモデルと計測

AiModelRepositoryはモデル一覧、download進捗、推論進捗、推論設定をFlowで公開する。model statusはdownload量と選択状態だけでなく、license、量子化、memory注意、thinking・speculative decodingの対応、context token数を持つため、画面が選択に必要な条件を表示できる。backendはCPU/GPU、context sizeはAUTOまたは明示サイズというDomain enumで扱う。

downloadModel、selectModel、deleteModelは要求の入口で、ファイル取得・検証・削除の正本はDataとcore runtimeへ委ねる。download進捗のisActiveはqueued/downloading/verifyingに限定され、completedやfailedを作業中と扱わない。benchmarkは通常とspeculativeの比較や、context別の時間・メモリ・安全性を返す。speculative計測のみの失敗もreportに残せる。

ChatGptProviderRepositoryは候補取得と選択済みIDの管理を提供し、モデルにWeb検索対応を保持する。ChatGptDebugRepositoryは接続状態、login、logout、明示推論テストの境界である。変更時はDataのmodel変換とSettings UIの選択条件を揃える。AiModelProgressTestはactiveなdownload段階の判定を検証する。

## 調査・変更の入口

- [AiModelRepository.kt](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelRepository.kt)
- [ChatGptProviderRepository.kt](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptProviderRepository.kt)
- [ChatGptDebugRepository.kt](../../../feature/settings/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/settings/ChatGptDebugRepository.kt)
- [AiModelProgressTest.kt](../../../feature/settings/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/settings/AiModelProgressTest.kt)

関連モジュール: [settings-data](settings-data.md)、[settings-ui](settings-ui.md)。全体の実行経路は[AIバックグラウンド処理](../../workflows/ai-background.md)を参照する。
