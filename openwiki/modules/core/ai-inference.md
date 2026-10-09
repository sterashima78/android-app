---
type: module
title: provider 共通の背景 AI 推論契約
description: ローカル・クラウド共通の単発推論、モデル情報、構造化 tool 出力と Worker 実行境界を定義する。
tags:
  - ai
  - contracts
  - background
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-c326182dfa6898224aab7bca
    resource: repo://app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt
  - id: openwiki-source-6028a81a33e28dd66ee82d27
    resource: repo://core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiBackgroundInferenceScope.kt
  - id: openwiki-source-d18851009c12f0d6ee65a925
    resource: repo://core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiStructuredTextInference.kt
  - id: openwiki-source-8eee59785bf468a07a499bd1
    resource: repo://core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInference.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# provider 共通の背景 AI 推論契約

## 依存の向き

`:core:ai-inference` は具体的なモデル engine や OAuth を含まない provider 共通契約で、feature はこの契約から単発生成を依頼する。[AiTextInference](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInference.kt) は選択モデル、token count、準備・生成 progress と BackgroundAiTextInference を定義する。モデルには context / 入力 / prompt budget、cache variant があり、空 ID や非正の budget を拒否する。実装は [ai-runtime](ai-runtime.md) または [ai-cloud-openai](ai-cloud-openai.md) にあり、app composition が接続する。

## 主要な構成要素と API

| 宣言・種類 | 責務・メソッド | 根拠 |
| --- | --- | --- |
| `AiTextInferenceStage` enum / `AiTextInferenceProgress` data class | モデル準備・応答生成の段階、モデル名、推定時間を通知する。 | [自由文契約](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInference.kt)。 |
| `AiTextInferenceModel` data class | 選択モデルの容量と入力予算、再利用判定用 cacheVariant をまとめ、不正値を構築時に拒否する。 | 同上。 |
| `AiTextInferenceModelReader` interface | `progress` Flow、`selectedModel()`、`countTokens(text)` を公開。モデルなしは null。 | 同上。 |
| `BackgroundAiTextInference` abstract class | model reader を継承。`generate(prompt)` が実行境界を検証し、protected `generateInBackground` に委譲する。 | 同上。 |
| `AiStructuredToolArgumentType` enum / `AiStructuredToolArgument` data class | 引数の型・必須性・説明を定義する。 | [構造化契約](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiStructuredTextInference.kt)。 |
| `AiStructuredTool` / `AiStructuredToolCall` data class | 前者は schema と追加引数許可、後者は結果の名前と文字列 map。 | 同上。 |
| `BackgroundAiStructuredTextInference` abstract class | `generateToolCall(systemInstruction, userMessage, tool)` が境界を検証し provider に委譲。未生成は null。 | 同上。 |
| `CoroutineWorker.withAiBackgroundInference` 拡張関数 / `requireAiBackgroundInferenceExecution` 関数 | Worker ID を coroutine context に付与し、生成側で存在を検証する。private context element は caller が直接構築できない。 | [実行 scope](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiBackgroundInferenceScope.kt)。 |

## 実装と呼び出しの関係

[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt) は自由文/構造化それぞれを `ProcessIsolatedLocalAiTextInference` / `ProcessIsolatedLocalAiStructuredTextInference`、`ChatGptTextInference` / `ChatGptStructuredTextInference` に接続する。前二者は [local runtime](ai-runtime.md)、後二者は [cloud adapter](ai-cloud-openai.md)で説明する。

feature の Worker が `withAiBackgroundInference` の block 内で契約の生成入口を呼び、共通クラスが `validateBackgroundExecution`、provider の protected 生成メソッドの順に呼ぶ。context がなければ provider 処理前に例外となる。進捗・token 計数は実行結果の保存とは独立し、task の永続化・成功/再試行状態は feature 側が所有する。
## Worker から生成へ

生成の公開入口は必ず validateBackgroundExecution を通る。[AiBackgroundInferenceScope](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiBackgroundInferenceScope.kt) の withAiBackgroundInference は実際の CoroutineWorker receiver から coroutine context に実行境界を設ける。生成 adapter はこの context がない呼び出しを拒否するため、UI / ViewModel coroutine から単発 AI を実行する経路を防ぐ。選択モデルの照会と token 計数は生成の入口とは別に使える。

自由文と構造化出力は別契約である。[AiStructuredTextInference](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiStructuredTextInference.kt) の generateToolCall は system instruction、user message、tool schema を受けて名前と引数の tool call、または null を返す。required、型、追加引数許可を明示でき、tool / argument の空名称と重複引数を拒否する。tool の業務実行や durable task state は caller の責務で、共通契約は所有しない。

## 確認と追加実装

[AiTextInferenceTest](../../../core/ai-inference/src/test/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInferenceTest.kt) と AiStructuredTextInferenceTest が実行境界と入力制約の確認入口になる。provider を追加する場合は契約を継承し、背景実行の検証を維持して生成部分を差し替える。progress や cache variant を変更するときは、それを使う summary / knowledge / library の再利用判定も調べる。

仕様・設計の正本: [architecture/ai-runtime.md](../../../docs/architecture/ai-runtime.md)、[spec/13-background-execution.md](../../../docs/spec/13-background-execution.md)。
