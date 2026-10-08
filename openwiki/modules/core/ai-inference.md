---
type: module
title: provider 共通の背景 AI 推論契約
description: ローカル・クラウド共通の単発推論、モデル情報、構造化 tool 出力と Worker 実行境界を定義する。
tags: [ai, contracts, background, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-6028a81a33e28dd66ee82d27
    resource: repo://core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiBackgroundInferenceScope.kt
  - id: openwiki-source-d18851009c12f0d6ee65a925
    resource: repo://core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiStructuredTextInference.kt
  - id: openwiki-source-8eee59785bf468a07a499bd1
    resource: repo://core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInference.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# provider 共通の背景 AI 推論契約

## 依存の向き

`:core:ai-inference` は具体的なモデル engine や OAuth を含まない provider 共通契約で、feature はこの契約から単発生成を依頼する。[AiTextInference](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInference.kt) は選択モデル、token count、準備・生成 progress と BackgroundAiTextInference を定義する。モデルには context / 入力 / prompt budget、cache variant があり、空 ID や非正の budget を拒否する。実装は [ai-runtime](ai-runtime.md) または [ai-cloud-openai](ai-cloud-openai.md) にあり、app composition が接続する。

## Worker から生成へ

生成の公開入口は必ず validateBackgroundExecution を通る。[AiBackgroundInferenceScope](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiBackgroundInferenceScope.kt) の withAiBackgroundInference は実際の CoroutineWorker receiver から coroutine context に実行境界を設ける。生成 adapter はこの context がない呼び出しを拒否するため、UI / ViewModel coroutine から単発 AI を実行する経路を防ぐ。選択モデルの照会と token 計数は生成の入口とは別に使える。

自由文と構造化出力は別契約である。[AiStructuredTextInference](../../../core/ai-inference/src/main/kotlin/dev/terashima/yomitorirss/core/aiinference/AiStructuredTextInference.kt) の generateToolCall は system instruction、user message、tool schema を受けて名前と引数の tool call、または null を返す。required、型、追加引数許可を明示でき、tool / argument の空名称と重複引数を拒否する。tool の業務実行や durable task state は caller の責務で、共通契約は所有しない。

## 確認と追加実装

[AiTextInferenceTest](../../../core/ai-inference/src/test/kotlin/dev/terashima/yomitorirss/core/aiinference/AiTextInferenceTest.kt) と AiStructuredTextInferenceTest が実行境界と入力制約の確認入口になる。provider を追加する場合は契約を継承し、背景実行の検証を維持して生成部分を差し替える。progress や cache variant を変更するときは、それを使う summary / knowledge / library の再利用判定も調べる。

仕様・設計の正本: [architecture/ai-runtime.md](../../../docs/architecture/ai-runtime.md)、[spec/13-background-execution.md](../../../docs/spec/13-background-execution.md)。
