# ADR-0020: ローカルAIをGemma 4 / LiteRT-LMへ統一する

- Status: Accepted
- Date: 2026-08-11
- Amended: 2026-08-15

## Context

ローカルAIは当初、MediaPipe Tasks GenAIとLiteRT-LMを併用し、Qwen2.5、Qwen3、Gemma 4をモデル候補としていた。複数runtimeを維持するため、プロンプト形式、Engine lifecycle、Thinking制御、モデル能力の差をアプリ側で吸収する必要があった。

AIチャットをAgentとして利用する要件ではGemma 4のFunction CallingとLiteRT-LMの `ConversationConfig.tools` / `OpenApiTool` を利用できる。独自テキストTool Callを維持しながら、Tool Callingを持たない別runtimeも同時にサポートすることは、チャットの実装を複雑にし、モデルごとに異なる挙動を生む。

利用者は現行最新版のみを利用しており、モデル選択肢をGemma 4へ統一してよい。

## Decision

ローカルAIのモデルcatalogをGemma 4へ限定し、実行runtimeをLiteRT-LMへ統一する。

### モデルcatalog

提供するモデルは次の2つとする。

- Gemma 4 E2B Instruct: 既定・推奨モデル。8 GB級端末向け
- Gemma 4 E4B Instruct: 品質優先。12 GB級端末向け

Qwen2.5 0.5B、Qwen2.5 1.5B、Qwen3 4Bはcatalogから削除する。既に端末へ保存されている旧Qwenモデルファイル、一時ファイル、runtime cacheは起動時に削除する。選択モデルが旧Qwenの場合は選択状態も解除し、利用可能なGemma 4があれば通常の選択解決へ移る。

MediaPipe Tasks GenAI依存は削除し、LiteRT-LMだけをローカルAI runtimeとして利用する。

### 実行バックエンド

ローカルAIの実行バックエンドはCPU/GPUから選択可能とする。

- 初期値はCPU
- CPUは `Backend.CPU()`、GPUは `Backend.GPU()` へ対応付ける
- LiteRT-LMのコンパイルcacheはCPU/GPUでディレクトリを分離する
- GPUが利用できない端末やモデルでは自動的にCPUへ切り替えずエラーを返す
- Android Manifestには既存方針どおりOpenCL関連native libraryを `required=false` で宣言する

### 推論Engineのライフサイクル

選択モデル、実行backend、モデルファイルの状態が同一である間、初期化済み `Engine` を `LocalModelManager` 内で1つだけ保持して再利用する。

- Chat、Summary等の推論リクエストごとにEngineを再初期化しない
- 各推論またはChat requestでは軽量な `Conversation` を作り、処理終了時に閉じる
- 選択モデル、backend、モデルファイルのサイズまたは更新時刻が変わった場合は保持中Engineを閉じ、新しい条件で初期化する
- モデル削除時、推論失敗時、`LocalModelManager.close()` 時は保持中Engineを閉じる
- 同時に保持するEngineは1つだけとする

### 汎用Conversation capability

`core:ai-runtime` は単発テキスト生成に加えて、LiteRT-LMの構造化Conversationを利用する汎用APIを提供する。

汎用Conversation requestは次を受け取る。

- system instruction
- user/modelの初期メッセージ
- 現在のuserメッセージ
- OpenAPI schemaへ変換可能なTool定義と実行callback

runtimeはこれらを `ConversationConfig`、`Message`、`OpenApiTool` へ変換する。feature固有のTool名、Tool利用方針、system promptの意味は保持しない。

ChatでのAgent Skill接続方法はADR-0005で定義する。

### feature と runtime の責務境界

`core:ai-runtime` は次を担当する。

- Gemma 4モデルcatalogとモデルファイル管理
- CPU/GPU設定
- LiteRT-LM Engineのlifecycleとcache
- モデルの入力上限等の技術能力
- 汎用的な単発生成
- 汎用的なConversation / Tool Calling transport
- raw streamingと `LocalInferenceProgress`

Chat/Summary固有のsystem prompt、会話履歴の選択、Skill、要約アルゴリズム、結果整形は保持しない。

この責務境界はADR-0056を維持する。

### Thinking

従来のQwen3向け `/think` / `/no_think` 切り替えは廃止する。現行のGemma 4 catalogではアプリ設定としてThinking切り替えを公開しない。

将来Gemma 4のThinking設定をLiteRT-LMの構造化 `ThinkingConfig` として公開する場合は、Qwen用テキスト制御を復活させず、LiteRT-LMのnative capabilityとして別途判断する。

## Consequences

モデルとruntimeの組み合わせがGemma 4 / LiteRT-LMへ統一され、Chat、Summary、モデル管理で異なる推論基盤を扱う分岐を減らせる。

ChatではGemma 4のFunction CallingとLiteRT-LMのTool Calling loopを利用でき、通常テキストに独自Tool Call形式を書かせる必要がなくなる。

一方、低メモリ端末向けのQwen2.5軽量モデルは利用できなくなる。Gemma 4 E2Bは8 GB級メモリを推奨するため、対象端末要件は実質的に高くなる。

初期化済みEngineとモデル重みは `LocalModelManager` の寿命中メモリに残る。保持数を1つに制限し、モデル変更・削除・推論失敗・manager終了時に明示的に解放することで速度とメモリ使用量のバランスを取る。

旧Qwenモデルを起動時に削除するため、アップデート直後に端末ストレージが回収される。旧モデルへのロールバック互換性は提供しない。

## Superseded decisions

本改定により、同ADRの過去版で決定していた次の項目を廃止する。

- MediaPipe Tasks GenAIとLiteRT-LMの併用
- Qwen2.5モデル候補
- Qwen3 4Bモデル候補
- Qwen3の `/think` / `/no_think` によるThinking切り替え
- runtimeごとのChatML/plain prompt分岐を機能側で扱う方針

## References

- ADR-0005: チャットに読み取り専用Agent Skillハーネスを導入する
- ADR-0003: マルチモジュールアーキテクチャ
- ADR-0056: ローカルAIの機能固有ポリシーをfeatureへ分離する
- LiteRT-LM Kotlin API: `ConversationConfig`, `OpenApiTool`
- Gemma 4 Function Calling guide
