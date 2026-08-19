# ADR-0077: 端末上でローカルAIの標準/Speculative性能を比較する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0112でGemma 4のSpeculative Decodingをユーザー設定として追加したが、その効果は端末、CPU/GPU backend、モデル、処理内容によって異なる。特にモバイルGPUではSpeculative Decodingが常に高速になるとは限らないため、固定の既定値だけでは端末ごとの最適設定を判断できない。

また、通常の要約時間だけを計測すると、ネットワーク取得、本文抽出、モデル初期化、prefill、decodeなど複数の要因が混ざり、Speculative Decoding自体の効果を判断しにくい。

LiteRT-LM 0.14.0には `benchmark()` APIがあり、次の指標を取得できる。

- Engine/Conversation初期化時間
- Time to First Token (TTFT)
- prefill token count / tokens per second
- decode token count / tokens per second

## Decision

### 選択中モデルとbackendを端末上で計測する

AIモデル管理画面から、現在選択中のモデルとCPU/GPU backendに対してベンチマークを実行できるようにする。

比較対象は次の2条件とする。

1. Speculative Decoding OFF（標準）
2. Speculative Decoding ON

Speculative Decoding非対応モデルでは標準のみを計測する。Speculative側だけが失敗した場合は標準結果を保持し、Speculative側のエラーを表示する。

### LiteRT-LMの公式benchmark APIを利用する

独自にストリーミング時間から推定するのではなく、LiteRT-LM 0.14.0の `benchmark()` / `BenchmarkInfo` を利用する。

さらにAPI呼び出し全体を `SystemClock.elapsedRealtime()` で囲み、初期化を含む総実行時間も記録する。

### 要約用途に近い固定トークン量で比較する

端末間・実行方式間で比較可能にするため、ベンチマーク条件は固定する。

- prefill: 2048 tokens
- decode: 128 tokens
- prompt: 日本語要約を想定した固定プロンプト

LiteRT-LM benchmark APIは、プロンプトがprefill token数より短い場合に残りをpaddingするため、ここでの値は「実記事の品質評価」ではなく、要約規模を想定した推論スループット比較として扱う。

### ベンチマーク中は他のローカルAI処理と競合させない

`feature:settings:data` から既存の `LocalAiBackgroundTaskGate` を `HIGH` priorityで取得し、要約、蔵書整理、ナレッジ構築などのバックグラウンドAI処理と同時実行しない。

さらにベンチマーク開始前に共有 `LocalModelManager` の保持Engineを解放する。`close()` は実行中の同Manager推論がある場合、その推論ロックの解放まで待つため、対話側の推論終了後にベンチマークEngineを作成する。

これにより、モデルを二重保持することによるピークメモリ増加を抑え、計測値への他AI処理の影響も小さくする。

### ベンチマークは設定値を変更しない

ベンチマークは標準/Speculativeを一時的なEngineで比較し、ユーザーが設定したSpeculative DecodingのON/OFFは変更しない。

結果はモデル管理ダイアログを開いている間だけ保持する。CPU/GPU backendまたは選択モデルを変更した場合は、条件が変わるため既存結果を破棄する。

### 表示する指標

標準/Speculativeそれぞれについて次を表示する。

- Decode tokens/sec
- Prefill tokens/sec
- TTFT
- Engine/Conversation初期化時間
- 総実行時間

両条件が成功した場合はさらに次の倍率を表示する。

- Decode速度倍率 = Speculative decode tok/s / 標準 decode tok/s
- 総時間倍率 = 標準総時間 / Speculative総時間

どちらも `1.0x` より大きければSpeculative側が高速である。

## Consequences

- ユーザーはPixelなど実端末上でSpeculative Decodingの有効性を判断できる。
- CPU/GPU backendの比較にも同じ機能を利用できるが、backend切替後は再計測が必要になる。
- ベンチマーク中はバックグラウンドAI処理が待機するため、その間のタスク処理は進まない。
- 端末温度、OSスケジューリング、キャッシュ状態などで結果は変動するため、単一計測だけで自動設定を変更しない。
- ベンチマーク結果の履歴保存や自動backend選択は本ADRの範囲外とする。

## References

- ADR-0112: Gemma artifact revisionとSpeculative Decoding設定
- ADR-0076: ブックマークの要約・タグ・フォルダ分類を最終推論へ統合する
- LiteRT-LM 0.14.0 `Benchmark.kt`
- `LocalAiBackgroundTaskGate`
