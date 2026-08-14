# ADR-0020: ローカルAIの実行バックエンドとThinkingをユーザー設定にする

- Status: Accepted
- Date: 2026-08-11
- Amended: 2026-08-14

## Context

ローカルAIは MediaPipe Tasks GenAI と LiteRT-LM の双方を利用しているが、従来は推論バックエンドをCPUに固定していた。LiteRT-LMはAndroidでGPUをサポートし、Kotlin APIでは `Backend.GPU()` を選択できる。GPU利用時はAndroid ManifestでOpenCL関連のnative libraryを明示する必要がある。

また、Qwen3 4Bは1つのモデルでThinkingと非Thinkingを切り替えられる。Qwen公式のsoft switchとして `/think` と `/no_think` が提供されているため、Thinking専用モデルと非Thinking専用モデルを別々に保持する必要はない。

GPUでは生成自体は高速でも、LiteRT-LMの `Engine.initialize()` によるモデル読み込み・GPU初期化がCPUより目立つ場合がある。LiteRT-LMのAPIでは `Engine` がモデル重みを保持する重量級オブジェクト、`Conversation` がその上に作る軽量オブジェクトとして分離されているため、メッセージごとに `Engine` を破棄して再生成する構成は適切ではない。

## Decision

### 実行バックエンド

ローカルAIの実行バックエンドをユーザー設定としてCPU/GPUから選択可能にする。

- 初期値はCPUとし、既存利用者の挙動を維持する。
- MediaPipe Tasks GenAIでは `LlmInference.Backend.CPU/GPU` に対応付ける。
- LiteRT-LMでは `Backend.CPU()/GPU()` に対応付ける。
- LiteRT-LMのコンパイルキャッシュはCPU/GPUでディレクトリを分離し、異なるバックエンドの生成物を混在させない。
- GPUが利用できない端末やモデルでは推論開始時のエラーをユーザーへ返し、自動的にCPUへ切り替えない。性能特性をユーザーが明示的に選択した状態と実際の実行状態を一致させるためである。
- Android Manifestには `libvndksupport.so` と `libOpenCL.so` を `required=false` で宣言する。

### 推論エンジンのライフサイクル

LiteRT-LMでは選択モデル、実行バックエンド、モデルファイルの状態が同一である間、初期化済み `Engine` を `LocalModelManager` 内で1つだけ保持して再利用する。

- チャットや要約など各featureからの推論リクエストでは `Engine` を再初期化しない。
- 各リクエストで作る `Conversation` は処理終了時に閉じる。チャット履歴は `feature:chat:data` がプロンプトへ毎回明示的に与えるため、`Conversation` 自体は保持しない。
- 選択モデル、CPU/GPU、モデルファイルのサイズまたは更新時刻が変わった場合は、次の推論開始時に保持中の `Engine` を閉じて新しい条件で初期化する。
- モデル削除時と `LocalModelManager.close()` 時は保持中の `Engine` を閉じる。
- 推論処理が例外で失敗した場合は、その `Engine` が再利用可能であると仮定せず破棄し、次回に再初期化する。
- 同時に保持する `Engine` は1つだけとし、複数モデル分の重みを常駐させない。
- MediaPipe Tasks GenAI の既存モデルは挙動変更を避けるため従来どおりリクエスト単位で生成・破棄し、この常駐化はLiteRT-LMに限定する。

### feature と runtime の責務境界

`core:ai-runtime` はモデルの取得・選択、CPU/GPU設定、Thinking適用、Engineのライフサイクル、汎用的な単発/ストリーミング推論を担当する。Chat/Summary固有のプロンプト、会話モデル、応答整形、要約アルゴリズムは保持しない。

- `feature:chat:domain` が会話モデルを所有する。
- `feature:chat:data` がChatプロンプト生成と応答整形を所有し、完成したプロンプトを `LocalModelManager.generate` へ渡す。
- `feature:summary:domain` が要約プロンプトの既定値、検証、キャッシュキー規則を所有する。
- `feature:summary:data` が要約プロンプト保存、階層要約、要約結果整形を所有する。
- `LocalModelManager` はfeature名に依存しない `LocalInferenceProgress` を公開する。各featureが自身のprogress modelへ変換する。

この責務境界はADR-0056で明文化する。

### Thinking

モデル定義にThinking対応可否を持たせる。

- Thinking設定は対応モデルを選択している場合だけUIに表示する。
- 初期値は無効とし、従来の要約・チャットの応答時間を維持する。
- Qwen3 4Bではユーザー入力へ `/think` または `/no_think` を付加して切り替える。
- Thinking中に生成された `<think>...</think>` の表示上の除去は、回答の意味を所有する各feature側で行う。
- Thinking対応モデルではThinking状態を要約キャッシュキーへ含め、モード変更後に異なるモードのキャッシュを再利用しない。runtimeはThinking状態を表すcache variantだけを提供し、要約cache keyの構築はsummary featureが行う。

### Qwen3 4B

`litert-community/Qwen3-4B` の `qwen3_4b_mixed_int4.litertlm` をモデル候補へ追加する。

- 元モデル: `Qwen/Qwen3-4B`
- ライセンス: Apache-2.0
- 量子化: TorchAO mixed INT4
- コンテキスト: 2048
- 配布サイズ: 約2535.88 MiB
- 推奨端末メモリ: 8 GB以上
- Thinking対応: 有効

2507版はInstructとThinkingが別モデルとして提供されるため、今回の「1モデルでThinkingを切り替える」という要件には通常版Qwen3 4Bを採用する。

## Consequences

GPUを利用できる端末では推論時間の短縮が期待できる一方、GPUドライバやモデルとの組み合わせによって実行できない場合がある。CPUは引き続き互換性重視の既定値として残す。

LiteRT-LMの初回推論では引き続きモデル読み込みとGPU初期化が必要だが、同じモデルとバックエンドで続けてチャット・要約する場合は2回目以降の `Engine.initialize()` を省略できる。特にGPU利用時のメッセージ間待ち時間を削減できる。

一方、初期化済み `Engine` とモデル重みは `LocalModelManager` の寿命中メモリに残る。保持数を1つに制限し、モデル変更・削除・推論失敗・manager終了時には明示的に解放することで、速度とメモリ使用量のバランスを取る。

Thinkingは推論品質を高められるが、生成時間、電力消費、発熱が増える可能性がある。そのため対応モデルに限定した明示設定とする。

feature固有ポリシーをruntimeから分離することで、Chat/Summaryの仕様変更がAI実行基盤へ波及しにくくなる。一方、feature側でruntimeの汎用progressやモデル能力を自身の概念へ変換するadapterが必要になる。

## References

- ADR-0003: マルチモジュールアーキテクチャ
- ADR-0056: ローカルAIの機能固有ポリシーをfeatureへ分離する
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/get_started.md
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/cpp/conversation.md
- https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.14.0
- https://huggingface.co/Qwen/Qwen3-4B
- https://huggingface.co/litert-community/Qwen3-4B
