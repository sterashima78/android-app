---
type: module
title: 端末内モデルと推論 subprocess
description: LiteRT-LM の端末内モデル管理、生成 session、token 計数と分離 process の lifetime を扱う。
tags:
  - ai
  - local-ai
  - runtime
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-5e9c18fa6175359b48c8d714
    resource: repo://core/ai-runtime/src/main/AndroidManifest.xml
  - id: openwiki-source-22d1b8ee6f0d8bd0daa916b1
    resource: repo://core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalInferenceConversation.kt
  - id: openwiki-source-bd1d3e5466a81c988b4181b1
    resource: repo://core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt
  - id: openwiki-source-0f5c54634ad8069b69a5984b
    resource: repo://core/ai-runtime/src/test/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInferenceTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# 端末内モデルと推論 subprocess

## モデルと生成の所有

`:core:ai-runtime` は LiteRT-LM Android engine を利用し、モデル管理、context 設定、tokenizer、session とローカル生成を所有する。feature は [ai-inference](ai-inference.md) の単発契約を通じて使い、app composition が adapter を選ぶ。LocalAiTextInference は manager の progress と model metadata を共通型へ変換し、生成を IO dispatcher 上で委譲する。

## モデル管理・設定の主要構成要素

| 宣言・種類 | 責務・主要メソッド | 根拠 |
| --- | --- | --- |
| `LocalModelManager` class | `shared(context)` の application 共通 instance。`models` / `downloadProgress` / `inferenceProgress` / `inferenceSettings` を公開。`isSupported`、`refreshModels`、`selectedModel`、`selectModel`、`deleteModel`、`downloadModel`、`cancel` を持つ。 | [manager](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalModelManager.kt)。 |
| `LocalInferenceBackend` / `LocalPromptFormat` / `LocalInferenceStage` enum | backend、prompt format、準備/生成の区分。 | 同上。 |
| `LocalInferenceSettings` / `LocalModelStatus` / `ModelDownloadProgress` / `LocalInferenceProgress` data class | 設定、選択/取得済み/容量/能力、download bytes、推論段階と推定時間を渡す。 | 同上。 |
| `LocalContextSizeMode` enum / `LocalContextBenchmarkSample` / `LocalContextBenchmarkReport` data class | AUTO/固定容量、測定成功・安全性とモデル/backend 別推奨容量。 | [context 設定](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalContextConfiguration.kt)。 |
| `LocalContextBenchmarkRunner` / `LocalModelBenchmarkRunner` class | `runSelectedModelContexts` / `lastSelectedModelReport`、`runSelectedModelComparison` で容量/推測復号の測定を行う。 | [context 測定](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalContextBenchmark.kt)、[model 測定](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalModelBenchmark.kt)。 |
| `LocalModelBenchmarkSample` / `LocalModelBenchmarkComparison` data class | 初期化/初 token/生成速度と standard/speculative の比較結果。 | model 測定ファイル。 |

manager の `setInferenceBackend` / `setThinkingEnabled` / `setSpeculativeDecodingEnabled` / `setContextSizeMode` は preferences を更新して設定とモデル表示を更新する。`inferenceCacheVariant` はモデル revision と実効設定を含める。`countTokens` は tokenizer を使い、`generate` / `generateWithImage` / `generateImageToolCall` / `generateConversation` は engine に委譲する。これらは同期 API なので adapter/利用者が IO 上へ移す。`close` は保持 engine/tokenizer と idle release を解放する。

## 推論契約・process 接続の構成要素

| 宣言・種類 | API と関係 | 根拠 |
| --- | --- | --- |
| `LocalAiTextInference` class | 背景契約のモデル/進捗変換と countTokens、同一 process manager への生成委譲。 | [直接 adapter](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalAiTextInference.kt)。 |
| `ProcessIsolatedLocalAiTextInference` / `ProcessIsolatedLocalAiConversationInference` class | 前者は背景自由文、後者は `LocalConversationInference` を実装し model/progress と `generateConversation` を公開する。 | [text IPC](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt)。 |
| `ProcessIsolatedLocalAiStructuredTextInference` class | 共通 tool schema を IPC と local tool へ変換して単一 tool call を返す背景 adapter。 | [structured IPC](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiStructuredTextInference.kt)。 |
| `LocalTextInferenceService` / `LocalStructuredTextInferenceService` class | Android Service の `onCreate` / `onBind` / `onDestroy` が Messenger と子側 manager の lifetime を所有する。 | 上記 IPC ファイルと Manifest。 |
| `LocalConversationInference` interface / `LocalInferenceConversationRequest` data class | system instruction、履歴、user message、tools、streaming callback を受け、最終 String を返す。 | [会話契約](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalInferenceConversation.kt)。 |
| `LocalInferenceMessageRole` enum / `LocalInferenceMessage` data class | USER/MODEL の会話履歴。 | 同上。 |
| `LocalInferenceToolArgumentType` enum / `LocalInferenceToolArgument` / `LocalInferenceTool` / `LocalInferenceToolCall` data class | tool schema、suspend execute callback と呼出結果。空 tool 名/説明、重複名を拒否する。 | 同上。 |

[AppAiCoreRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/ai/AppAiCoreRuntimeDependencies.kt) は shared manager を text/structured/conversation の process adapter に渡す。会話は単発背景契約とは別の入口であり、背景 scope 検査を会話 API の要件と混同しない。

## 診断用公開 API

`LocalAiMemoryDiagnostics` object の `recordVisionInference` / `recordProcessSample` / `recentInferenceReport` は `LocalAiMemoryDiagnosticPhase` / `LocalAiProcessMemoryPhase` enum と共に memory 診断を記録・抽出する。[memory 診断](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalAiMemoryDiagnostics.kt)を参照。`LocalAiTextProcessDiagnostics.recentProcessReport` は process/session/phase の記録を条件で絞る。[process 診断](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/LocalAiTextProcessDiagnostics.kt)の internal session は start/mark/stop を service lifecycle に合わせる。診断情報は engine の制御や業務 task の正本にはしない。
## 内部処理・状態・失敗

manager は download/inference/tokenizer の lock と engine cache を分離する。private `LiteRtLmInference` が engine と生成 conversation を扱い、internal `LocalInferenceSessionTracker` / `LocalInferenceSession` が lease を参照計数する。新 session は idle release を取り消し、最後の lease が閉じた後に engine を解放する。`IdleReleaseScheduler` / `IdleReleaseHandle` が test 可能な時間境界である。

`LiteRtLmModelSections` は model container の section を読み、`LiteRtLmTokenizer` が tokenizer section と internal `SentencePieceBpeTokenCounter` を接続する。SentencePiece の model/normalizer/piece type 定義と BPE merge は token 計数用である。`ThinkingMode` は model の思考 marker を処理する。context resolver は設定・benchmark 推奨・model 上限から実効容量を決定し、internal `LocalContextBenchmarkStore` が model/backend/speculative 別に結果を保存する。

text IPC の private `RemoteLocalTextInferenceClient` は request mutex で直列化し、`RemoteTextInferenceSession` が binding と pending response を所有する。snapshot を確定 → service 接続 → Bundle を送信 → child manager で生成 → progress/chunk/tool execution を返信 → 最終結果と stage 時間を受信、の順で進む。private `ChildToolExecutionBridge` は子から親の tool callback を呼び結果を戻す。RemoteException は session を退役させ一度再接続する。cancel/timeout も session を退役させる。

structured 経路は request ごとに `StructuredTextInferenceSession` を作り、finally で close する。schema と引数を検査し、子側解析不能な tool call は安全なエラーへ変換する。両経路の snapshot Context は subprocess 専用 preferences を使い main の設定を書き換えない。

## process 境界と設定 snapshot

[ProcessIsolatedLocalAiTextInference](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt) はモデル情報と token counting を main process に置き、生成だけを Binder 経由で子 process に送る。[Manifest](../../../core/ai-runtime/src/main/AndroidManifest.xml) に定義した text / structured service は exported=false で `:local_ai_text` に配置される。選択モデル、backend、context tokens、revision などを実行 snapshot として送り、子側は main と別の preferences を使う。conversation 経路では履歴・tool schema・streaming・tool 呼び出し応答も IPC に載る。

text 入力は空白のみと容量上限超過を拒否し、progress は成功・失敗とも finally でクリアする。短命 process は batch policy と idle retirement により再生成される。接続待ちと生成 watchdog は別々に制限し、生成 timeout は実測 stage 時間へ余裕を付けて上下限に収める。process death と binding 失敗は pending request の失敗へ伝える。子 process へ主 Application runtime を再初期化しないことも [app](../application/app.md) 側の契約である。

## 検証・変更の入口

[ProcessIsolatedLocalAiTextInferenceTest](../../../core/ai-runtime/src/test/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInferenceTest.kt) は 2 回完了での retirement、IPC budget、分離 preferences、context 対応値、watchdog と tool parse failure を検証する。engine session は LocalInferenceSessionTest、会話は LocalInferenceConversationTest、token 計数は SentencePieceBpeTokenCounterTest を読む。IPC や process lifetime の変更では単なる文字列 test だけでなく Android service 境界の動作確認も必要になる。

仕様・設計の正本: [architecture/ai-runtime.md](../../../docs/architecture/ai-runtime.md)、[spec/05-on-device-ai.md](../../../docs/spec/05-on-device-ai.md)。
