# ADR-0056: ローカルAIの機能固有ポリシーをfeatureへ分離する

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-15
- Amended by: ADR-0060

## Context

ADR-0003では `core` を横断的な技術capabilityの提供場所とし、feature固有の意味やポリシーは各featureが所有する方針としている。

一方、`core:ai-runtime` にはモデル実行基盤だけでなく、次のChat/Summary固有仕様も存在していた。

- Chatの会話モデル、system prompt、履歴の切り詰め規則
- Chatのストリーミング応答整形
- 要約プロンプトの既定値、検証、render、cache key
- 長文記事の階層要約アルゴリズム、進捗段階、中間要約用prompt
- Chat/Summary固有のprogressと実行entry point

この状態ではChatやSummaryの仕様変更が共通runtimeへ入り込み、別featureから見た `core:ai-runtime` の責務が不明瞭になる。また、`feature:chat:domain` に既に存在する `ChatRole` / `ChatTurn` / `ChatContextBlock` とcore側の型が重複していた。

2026-08-15にGemma 4 / LiteRT-LMへruntimeを統一し、ChatでLiteRT-LMの構造化ConversationとTool Callingを利用することになった。system instruction、履歴、ToolをLiteRT-LM型へ変換するtransport capabilityはcoreに必要だが、Chat固有の意味をcoreへ戻さない境界を維持する必要がある。

## Decision

`core:ai-runtime` はローカル推論を実行するための技術capabilityに限定し、Chat/Summary固有ポリシーを各featureへ置く。

### core:ai-runtime

次を所有する。

- Gemma 4ローカルモデルcatalogとモデルファイル管理
- モデル選択、ダウンロード、端末要件判定
- CPU/GPU backend設定
- LiteRT-LM Engineのcacheとlifecycle
- モデルの入力上限など技術的能力の公開
- featureに依存しない単発 `generate` API
- LiteRT-LMへsystem instruction、user/model履歴、user message、汎用Tool定義を渡すConversation API
- 汎用Tool定義からLiteRT-LM `OpenApiTool` / `ConversationConfig.tools` への変換
- raw streamingと汎用 `LocalInferenceProgress`

`core:ai-runtime` は「記事」「要約」「ブックマーク」「タスク」「Agent Skill」といったfeature固有概念を知らない。Toolの名前や「いつ使うか」という意味も所有しない。

### feature:chat

- `feature:chat:domain` が `ChatRole`、`ChatTurn`、`ChatContextBlock`、`AgentSkill`、`AgentTool` などChat/Agentの意味を持つモデルを所有する。
- `feature:chat:data` がsystem instruction、参照情報、会話履歴のbudgetと選択規則を所有する。
- `feature:chat:data` が `AgentSkill` / `AgentTool` をruntimeの汎用Tool定義へ変換し、Skill説明と利用指示をTool descriptionへ与える。
- `feature:chat:data` がthinking/control tokenやassistant prefix等の表示用応答整形を所有する。
- `feature:chat:data` がruntimeのgeneric progress/raw streamingをChat用 `ChatProgress` / `streamingReply` へ変換する。

ChatはLiteRT-LMのprompt template制御トークンを手書きしない。system/user/model/toolの構造化はruntime transportへ渡し、Chat側はその内容と選択規則だけを決める。

### feature:summary

- `feature:summary:domain` が要約promptの既定値、placeholder、入力検証、render、cache keyを所有する。
- `feature:summary:data` がpromptの永続化を所有する。
- `feature:summary:data` が階層要約、chunk/reduction/final progress、中間要約prompt、要約結果整形を所有する。
- `feature:summary:data` がruntimeのgeneric progressをSummaryのtask progressへ変換する。

SummaryはAgent Toolを必要としないため、汎用単発 `generate` を利用する。

### 設定との接続

AI設定画面は既存の `feature:settings` に残す。`feature:settings:data` はSummary promptの保存・読取を `feature:summary:data` へ委譲する。

ADR-0003でDataから他featureのDataへの依存は許可されているため、この依存方向を採用する。Summaryのprompt policy自体をSettingsへ移動しないことで、設定UIの有無に関係なくSummary featureが自身の規則を所有する。

### 既存データの互換性

当初はユーザーが保存済みの要約promptを失わないよう、`SummaryPromptStore` が従来 `LocalModelManager` のSharedPreferences名 `local_summary_models` とkey `summary_prompt` を引き継ぎ、その後 ADR-0060 で `feature:summary:data` 専用の `summary_preferences` へ一度限りの自動移行を導入した。

現行保存先への移行完了後、ADR-0060 の方針に従ってこのruntime migrationも削除した。現在の `SummaryPromptStore` は `summary_preferences` の `summary_prompt` だけを読み書きし、旧 `local_summary_models` の要約promptを参照しない。

モデルファイルやbackend設定の保存場所は変更しないため、`LocalModelManager` 自体が利用する `local_summary_models` SharedPreferencesは継続して存在する。

`YomitoriApp` に残っていた旧 `SummaryProgress` helperとSummary domain側のsource compatibility shimは、ADR-0060 により予定どおり削除した。進捗表示はfeature固有の型だけを利用する。

## Consequences

ChatとSummaryの仕様変更を各feature内で完結させやすくなり、`core:ai-runtime` は新しいAI利用featureからも再利用しやすい技術基盤になる。

LiteRT-LMの構造化ConversationとTool Callingをcoreの汎用transportとして共有できる一方、feature側でruntimeの汎用型へ変換するadapterが必要になる。これはfeature固有ポリシーを明示するための意図的な境界である。

ChatのSkillやTool利用指示をcoreへ置かないことで、AI runtimeの更新がアプリ固有のAgent権限設計へ直接波及しにくくなる。

## References

- ADR-0003: マルチモジュールアーキテクチャ
- ADR-0004: 概念指向モジュール
- ADR-0005: チャットに読み取り専用Agent Skillハーネスを導入する
- ADR-0020: ローカルAIをGemma 4 / LiteRT-LMへ統一する
- ADR-0027: 長文記事は階層的に分割要約する
- ADR-0060: 現行データ形式へ互換処理を収束させる
