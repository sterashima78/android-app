# ADR-0080: ローカルAIのコンテキスト長を端末実測で調整する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0027ではGemma 4 E2B / E4Bのアプリ内実行コンテキストを8,192 tokensへ固定した。これはLiteRT-LMモデルが対応する最大32kをそのまま使わず、モバイル端末上のKV cacheによるメモリ増加を抑えるための安全側の設定だった。

一方、要約機能は実SentencePiece token数で入力予算を計算し、コンテキストへ収まらない記事を階層要約する。コンテキスト長を大きくできる端末では、16Kまたは32Kを利用することでチャンク数とLLM推論回数を減らし、記事1件の総処理時間と電力消費を下げられる可能性がある。

端末RAM容量だけでは安全なコンテキスト長を決められない。実際のメモリ消費はモデル、CPU/GPU backend、Speculative decoding、LiteRT-LM、OSの状態によって変化する。またJava heapだけではmmap、native、graphics/runtime bufferを含む実プロセス負荷を評価できない。

## Decision

### Context size mode

ローカルAI設定に次の実行コンテキストを追加する。

- Auto
- 4K (4,096 tokens)
- 8K (8,192 tokens)
- 16K (16,384 tokens)
- 32K (32,768 tokens)

Gemma 4 E2B / E4Bのアプリ内モデル能力上限は32Kとする。手動指定はユーザーの明示的な上書きとして扱う。

Autoは次の順で実行コンテキストを決める。

1. 選択モデル、backend、Speculative decoding有無が一致する保存済みベンチマークの推奨値を使う。
2. 対応するベンチマークがない場合は従来値の8Kを使う。
3. モデル能力上限を超える値は使用しない。

コンテキスト長はLiteRT-LM `EngineConfig.maxNumTokens`へ渡す。Engine再利用キーにもcontext tokensを含め、設定変更後に異なるKV cache容量のEngineを誤って再利用しない。

### Context benchmark

モデル設定画面から明示的にコンテキストベンチマークを実行できるようにする。ベンチマークは4Kから昇順で行い、各サイズでコンテキストのおよそ75%を同じモデル内蔵tokenizerで作成した入力でprefillし、短い応答を生成する。

計測中は125ms間隔でAndroidのプロセスメモリ情報を取得し、次を記録する。

- baseline PSS
- peak total PSS
- peak native PSS
- peak graphics PSS
- minimum available system memory
- Engine初期化時間
- prefill + generation時間
- 実際の入力token数

Java heapだけを安全判定には使用しない。

### Safety rule

各contextを安全圏と判定する条件は次のすべてを満たすこととする。

- Androidがlow-memory状態を報告していない
- 計測中の最低空きメモリが `max(1 GiB, 端末RAMの15%)` 以上
- peak process PSSが端末RAMの70%以下

Autoは安全圏だった最大contextを推奨する。安全圏が一つもない場合は、成功した8K以下の最大値へフォールバックする。

安全でない、または推論に失敗したcontextが見つかった時点で、それより大きいcontextは試さない。さらに次のcontextを試す前に、直前計測の最低空きメモリが安全余白の2倍未満ならそこで終了する。これはベンチマーク自体が端末をOOMへ追い込むことを避けるためである。

ベンチマーク結果はモデル、backend、Speculative decoding有無ごとに端末内へ保存する。設定の組み合わせが変わった場合は別の結果として扱う。

### Summary integration

`LocalModelStatus.contextTokens` はモデルファイルの理論最大値ではなく、現在の実効contextを返す。既存の階層要約はこの値を利用しているため、Summary側に端末判定ロジックを追加せず、自動的に4K/8K/16K/32Kの入力予算へ追従する。

要約cache keyへ渡すruntime variantには実効contextを含める。context変更によってチャンク境界や推論回数が変わるため、異なるcontextで生成した結果を同一runtime条件として扱わない。

### Benchmark and retained Engine

ベンチマーク開始前は通常推論用に保持しているEngineだけを解放する。`LocalModelManager.close()`でセッショントラッカーまで閉じる処理は使わない。ベンチマーク後も共有Managerは通常のSummary/Chat推論に再利用できる状態を維持する。

## Consequences

十分なメモリ余裕がある端末では16K/32Kを利用して階層要約の推論回数を減らせる。余裕の少ない端末では4K/8Kへ留められるため、全端末へ一律に大きなKV cacheを確保する必要がない。

PSSとsystem available memoryは瞬間値であり、端末温度、バックグラウンドアプリ、OSキャッシュの影響を受ける。したがって結果は絶対的なハードウェア能力ではなく、その構成で安全余白を持って動かすための端末ローカルな推奨値として扱う。

ベンチマークでは実際に大きなprefillを行うため、実行中は電力消費と発熱が増える。ユーザー操作でのみ開始し、バックグラウンドAIタスクとの同時実行を避ける。

手動32KはAutoの安全判定を迂回できる。これは検証・比較用途を残すための意図的な設計であり、UIにはAutoを推奨として示す。

## Supersedes

ADR-0027の「Gemma 4 E2B / E4Bのアプリ内実行コンテキストは8,192 tokensに固定する」という決定を、本ADRの端末実測 + 手動上書き方式で置き換える。ADR-0027の階層要約、実token budget、output/runtime reserve、責務境界は維持する。

## References

- ADR-0020: ローカルAIをGemma 4 / LiteRT-LMへ統一する
- ADR-0027: 長文記事は階層的に分割要約する
- ADR-0073: LiteRT-LMモデル内蔵SentencePieceで実トークン数を計測する
- ADR-0079: ローカルAI Engineを推論セッション間で再利用する
- `core/ai-runtime/.../LocalModelManager.kt`
- `core/ai-runtime/.../LocalContextBenchmark.kt`
- `feature/settings/.../ModelManagerDialog.kt`
