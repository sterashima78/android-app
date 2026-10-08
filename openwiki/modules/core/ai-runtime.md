---
type: module
title: 端末内モデルと推論 subprocess
description: LiteRT-LM の端末内モデル管理、生成 session、token 計数と分離 process の lifetime を扱う。
tags: [ai, local-ai, runtime, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T14:01:52.039Z
sources:
  - id: openwiki-source-5e9c18fa6175359b48c8d714
    resource: repo://core/ai-runtime/src/main/AndroidManifest.xml
  - id: openwiki-source-bd1d3e5466a81c988b4181b1
    resource: repo://core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt
  - id: openwiki-source-0f5c54634ad8069b69a5984b
    resource: repo://core/ai-runtime/src/test/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInferenceTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# 端末内モデルと推論 subprocess

## モデルと生成の所有

`:core:ai-runtime` は LiteRT-LM Android engine を利用し、モデル管理、context 設定、tokenizer、session とローカル生成を所有する。feature は [ai-inference](ai-inference.md) の単発契約を通じて使い、app composition が adapter を選ぶ。LocalAiTextInference は manager の progress と model metadata を共通型へ変換し、生成を IO dispatcher 上で委譲する。

## process 境界と設定 snapshot

[ProcessIsolatedLocalAiTextInference](../../../core/ai-runtime/src/main/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInference.kt) はモデル情報と token counting を main process に置き、生成だけを Binder 経由で子 process に送る。[Manifest](../../../core/ai-runtime/src/main/AndroidManifest.xml) に定義した text / structured service は exported=false で `:local_ai_text` に配置される。選択モデル、backend、context tokens、revision などを実行 snapshot として送り、子側は main と別の preferences を使う。conversation 経路では履歴・tool schema・streaming・tool 呼び出し応答も IPC に載る。

text 入力は空白のみと容量上限超過を拒否し、progress は成功・失敗とも finally でクリアする。短命 process は batch policy と idle retirement により再生成される。接続待ちと生成 watchdog は別々に制限し、生成 timeout は実測 stage 時間へ余裕を付けて上下限に収める。process death と binding 失敗は pending request の失敗へ伝える。子 process へ主 Application runtime を再初期化しないことも [app](../application/app.md) 側の契約である。

## 検証・変更の入口

[ProcessIsolatedLocalAiTextInferenceTest](../../../core/ai-runtime/src/test/kotlin/dev/terashima/yomitorirss/core/airuntime/ProcessIsolatedLocalAiTextInferenceTest.kt) は 2 回完了での retirement、IPC budget、分離 preferences、context 対応値、watchdog と tool parse failure を検証する。engine session は LocalInferenceSessionTest、会話は LocalInferenceConversationTest、token 計数は SentencePieceBpeTokenCounterTest を読む。IPC や process lifetime の変更では単なる文字列 test だけでなく Android service 境界の動作確認も必要になる。

仕様・設計の正本: [architecture/ai-runtime.md](../../../docs/architecture/ai-runtime.md)、[spec/05-on-device-ai.md](../../../docs/spec/05-on-device-ai.md)。
