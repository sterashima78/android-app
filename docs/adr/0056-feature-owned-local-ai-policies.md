# ADR-0056: ローカルAIの機能固有ポリシーをfeatureへ分離する

- Status: Accepted
- Date: 2026-08-14

## Context

ADR-0003では `core` を横断的な技術capabilityの提供場所とし、feature固有の意味やポリシーは各featureが所有する方針としている。

一方、`core:ai-runtime` にはモデル実行基盤だけでなく、次のChat/Summary固有仕様も存在していた。

- Chatの会話モデル、system prompt、履歴の切り詰め規則
- Chatのストリーミング応答整形
- 要約プロンプトの既定値、検証、render、cache key
- 長文記事の階層要約アルゴリズム、進捗段階、中間要約用prompt
- Chat/Summary固有のprogressと実行entry point

この状態ではChatやSummaryの仕様変更が共通runtimeへ入り込み、別featureから見た `core:ai-runtime` の責務が不明瞭になる。また、`feature:chat:domain` に既に存在する `ChatRole` / `ChatTurn` / `ChatContextBlock` とcore側の型が重複していた。

## Decision

`core:ai-runtime` はローカル推論を実行するための技術capabilityに限定し、Chat/Summary固有ポリシーを各featureへ移す。

### core:ai-runtime

次を所有する。

- ローカルモデルcatalogとモデルファイル管理
- モデル選択、ダウンロード、端末要件判定
- CPU/GPU backend設定
- Thinking対応モデルへのruntime-level mode適用
- LiteRT-LM Engineのcacheとlifecycle
- モデルの入力上限、prompt formatなど技術的能力の公開
- featureに依存しない `generate` API
- raw streamingと汎用 `LocalInferenceProgress`

`core:ai-runtime` は「記事」「要約」「会話履歴」「アシスタント回答」といったfeature固有概念を知らない。

### feature:chat

- `feature:chat:domain` が `ChatRole`、`ChatTurn`、`ChatContextBlock` など会話モデルを所有する。
- `feature:chat:data` がsystem prompt、参照情報、会話履歴のbudget、ChatML/plain形式への変換を所有する。
- `feature:chat:data` がthinking/control tokenやassistant prefixを除去する表示用応答整形を所有する。
- `feature:chat:data` がruntimeのgeneric progress/raw streamingをChat用 `ChatProgress` / `streamingReply` へ変換する。

### feature:summary

- `feature:summary:domain` が要約promptの既定値、placeholder、入力検証、render、cache keyを所有する。
- `feature:summary:data` がpromptの永続化を所有する。
- `feature:summary:data` が階層要約、chunk/reduction/final progress、中間要約prompt、要約結果整形を所有する。
- `feature:summary:data` がruntimeのgeneric progressをSummaryのtask progressへ変換する。

### 設定との接続

AI設定画面は既存の `feature:settings` に残す。`feature:settings:data` はSummary promptの保存・読取を `feature:summary:data` へ委譲する。

ADR-0003でDataから他featureのDataへの依存は許可されているため、この依存方向を採用する。Summaryのprompt policy自体をSettingsへ移動しないことで、設定UIの有無に関係なくSummary featureが自身の規則を所有する。

### 既存データの互換性

ユーザーが保存済みの要約promptを失わないよう、`SummaryPromptStore` は従来 `LocalModelManager` が使用していたSharedPreferences名 `local_summary_models` とkey `summary_prompt` を引き継ぐ。保存形式のmigrationは不要とする。

モデルファイルやbackend/Thinking設定の保存場所も変更しない。

`YomitoriApp` に残る旧 `SummaryProgress` 型名の未使用helperについては、core runtimeへ型を戻さず、Summary domain側にdeprecatedなsource compatibility shimを一時的に置く。app shellの整理時にhelperとshimを同時に削除する。新規コードからshimを利用しない。

## Consequences

ChatとSummaryの仕様変更を各feature内で完結させやすくなり、`core:ai-runtime` は新しいAI利用featureからも再利用しやすい技術基盤になる。

一方、各featureはモデル能力・generic progress・raw responseを自身の意味へ変換するadapterを持つ必要がある。これはfeature固有ポリシーを明示するための意図的な重複である。

`LocalModelManager` の公開APIはfeature固有の `chat` / `summarize` からgeneric `generate` へ変わるため、runtime単体テストと各featureのprompt/response policyテストを分離する。

## References

- ADR-0003: マルチモジュールアーキテクチャ
- ADR-0004: 概念指向モジュール
- ADR-0020: ローカルAIの実行バックエンドとThinkingをユーザー設定にする
- ADR-0027: 長文記事は階層的に分割要約する
