# ADR-0175: Knowledge Wiki の Local / ChatGPT 実行先を明示選択する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0056](0056-feature-owned-local-ai-policies.md), [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0071](0071-prioritized-background-ai-task-scheduling.md), [ADR-0104](0104-ai-task-queue-feature-ownership.md), [ADR-0109](0109-generated-knowledge-wiki.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md), [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md)

## Context

Knowledge は保存済みブックマークの要約を根拠に自動Wikiの再構築、新規ページ生成、既存ページのLLM編集を行う。ADR-0165 により生成処理は provider 非依存の `AiTextInference` を利用しているが、production composition は local LiteRT-LM 実装だけを接続していた。

Summary では ADR-0171 / ADR-0172 により Local と ChatGPT / Codex の実行先をユーザーが明示選択でき、Local AI と Cloud AI の background control も分離された。Knowledge でも同じ cloud provider を利用し、公開記事の要約やWiki生成をクラウドへオフロードしながら端末上のLocal AIを別タスクへ利用できるようにする。

Knowledge の入力は保存済み要約、記事タイトル・URL、既存Wiki本文、ユーザーの作成・編集指示を含む。今回の要件では入力内容を分類してクラウド可否を自動判断せず、実行先の決定はユーザーの明示設定だけを正本とする。

## Decision

### 1. Knowledge feature が Local / ChatGPT routing setting を所有する

`KnowledgeExecutionProvider` は `LOCAL` と `CHATGPT` の2値とし、`KnowledgeExecutionSettings` を Knowledge domain の capability とする。永続化は Knowledge data layer が所有し、初期値は既存挙動を維持する `LOCAL` とする。

Settings の `AI実行設定` はこの設定を表示・変更する presentation surface とし、routing decision 自体は Settings へ移さない。ChatGPTを選択できる条件は provider へログイン済みで利用モデルが選択されていることだけとする。

入力ソース、タグ、フォルダ、記事URL、Wiki本文等を見て `LOCAL` / `CHATGPT` を自動判定しない。`AUTO`、cloud eligibility、sensitivity classification は導入しない。

### 2. Wikiの自動再構築・新規生成・編集を同じproviderへ送る

Knowledge の次のAI処理はすべて現在選択中のproviderを利用する。

- 自動Wiki再構築
- ユーザー依頼による新規Wikiページ生成
- 既存WikiページのLLM編集
- 既存ページからの派生ページ生成

Local path は従来の `AiTextInference` / LiteRT-LM を利用する。ChatGPT path は既存の ChatGPT OAuth / Codex Responses adapter を利用し、Knowledgeの既存promptを通常のテキスト推論として送信する。Summaryと異なり、Knowledge側でWeb検索を要求せず、Knowledge featureが選択済みの保存要約とWiki本文を入力として渡す。

provider固有のendpoint、OAuth field、Responses wire formatは引き続き `:core:ai-cloud-openai` に隔離する。Knowledge featureは `ChatGptOpenAiClient` に依存しない。

### 3. クラウド送信内容をUIで明示し、自動的なLocal fallbackは行わない

ChatGPTを選択した場合、Wiki生成に必要な保存済み記事要約、記事タイトル・URL、既存Wiki本文、ユーザーの作成・編集指示がクラウドへ送信され得ることを `AI実行設定` に表示する。

クラウド利用中に認証失効、request rejection、provider failure等が発生しても、同じタスクを自動的にLocalへ切り替えない。どのproviderで処理されるかをユーザー設定と一致させる。

ChatGPTからログアウトした場合は、Summaryと同様にKnowledgeの実行先も `LOCAL` へ戻す。

### 4. Background Wiki build はenqueue時のproviderを固定する

自動Wiki再構築のWorkManager requestにはenqueue時の `KnowledgeExecutionProvider` を保存する。Workerはそのsnapshotでbuildを最後まで実行する。

設定変更時に未完了のKnowledge buildが存在する場合はunique workを新providerで置き換える。これにより、Local gateを取得した後に設定だけChatGPTへ変わる、またはcloud用network constraintのないworkがChatGPT推論を開始するといった不一致を避ける。

旧バージョンから残ったprovider情報のないworkは、従来挙動を維持するため `LOCAL` として扱う。

### 5. Local / Cloud background control をproviderごとに適用する

`LOCAL` の自動Wiki buildだけ `LocalAiBackgroundTaskGate` を取得する。Local AI pause と充電時自動再開もLocal pathだけに適用する。

`CHATGPT` の自動Wiki buildはLocal gateを取得せず、Cloud AI pauseを適用する。WorkManagerにはnetwork connectivity constraintを設定し、一時的なnetwork I/O failure、`408`、`429`、`5xx` はretryable cloud failureとしてexponential backoffへ委譲する。

retryable failureでは、provider adapterが正規化した安全なuser-facing messageをKnowledge queue stateへ直前エラーとして保持する。WorkManagerが再試行待ちになっている間、AI task queueはそのmessageを「直前の失敗・自動再試行待ち」として表示する。新規生成要求、手動再開、完了では直前エラーを消去する。

refresh後も解消しない認証エラーや非一時的な`4xx`は自動再試行せず、Knowledge taskを失敗状態としてユーザー操作を要求する。provider response body、prompt、token、account idはdurable task errorへ保存しない。

AI task queueのKnowledge行には現在の実行先を `Local` / `ChatGPT` として表示する。Local AI pauseはChatGPT Wikiを停止せず、Cloud AI pauseはLocal Wikiを停止しない。

### 6. Cloud Wikiのprompt budgetは保守的な上限を使う

Knowledgeの既存prompt builderは `AiTextInferenceModel.promptBudgetChars` を利用するため、ChatGPT adapterは初期実装では16,000文字の保守的なprompt budgetを公開する。provider model catalogから正確なcontext budgetを安定して取得・変換できるようになった場合はadapter内部で改善できる。

この値はrouting判断には使用せず、既存Wiki編集で入力を途中切断しない安全策を維持するためだけに使う。

## Consequences

### Positive

- Wiki生成をChatGPTへオフロードしながらLocal AIを蔵書整理等へ利用できる。
- SummaryとKnowledgeで同じChatGPT login/model設定を共有できる。
- ユーザーが選択したproviderと実際の実行先が一致し、自動判定の説明可能性を持ち込まない。
- Knowledge固有prompt・引用・source selectionはfeatureに残り、OpenAI protocolはcore adapterへ隔離される。
- Local / Cloud pauseの分離というADR-0172のruntime policyをKnowledgeにも拡張できる。
- provider変更中のbackground workでもgate、network constraint、実際の推論先が一致する。
- retryable cloud failureでWorkManagerの待機状態へ戻った後も、直前の失敗理由をAI task queueから確認できる。

### Negative

- ChatGPTを選択した場合、Knowledgeへ取り込んだ要約や既存Wiki本文がユーザーの明示選択に従ってクラウドへ送信される。
- 入力内容による自動保護は行わないため、実行先選択の責任はユーザー設定にある。
- Cloud Wikiは初期実装で保守的な16,000文字budgetを使うため、モデルの実contextより少ない入力しか利用しない場合がある。
- Local / Cloud provider対応によりKnowledge background controllerとAI task queueの状態管理が増える。
- 再試行待ちに保持する診断情報は安全化済みの直前messageだけで、provider response bodyなどの詳細監査情報は保持しない。

## Verification

- Knowledge execution settingの初期値がLocalであり、SettingsからLocal / ChatGPTを明示切替できることを確認する。
- ChatGPT未ログインまたはモデル未選択時はChatGPT選択を受け付けないことを確認する。
- ChatGPT logoutでKnowledge routingがLocalへ戻ることをunit testする。
- Knowledge feature sourceが `core:ai-cloud-openai` / `ChatGptOpenAiClient` に直接依存しないことをarchitecture testで固定する。
- `429` / `5xx` / transport failureをretryable、認証失効・非一時的`4xx`をnon-retryableとして分類し、provider bodyをuser-facing errorへ残さないことをtestする。
- retryable cloud failureでKnowledge queue stateへ安全化済みmessageを保持し、AI task queueのqueued行へ「直前の失敗・自動再試行待ち」として表示することを確認する。
- Local WikiだけがLocal AI global pause / charging resumeの対象であり、ChatGPT WikiはCloud AI pauseの対象になることを確認する。
- AI task queueにKnowledgeのLocal / ChatGPT labelが表示されることを確認する。
- Architecture / Test / Lint / public repository verificationを実行する。

## Documentation

- ADR indexへADR-0175を追加する。
- `docs/architecture/module-map.md` のKnowledge / Settings / AI task queue ownershipを更新する。

## References

- [ADR-0056](0056-feature-owned-local-ai-policies.md)
- [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md)
- [ADR-0071](0071-prioritized-background-ai-task-scheduling.md)
- [ADR-0104](0104-ai-task-queue-feature-ownership.md)
- [ADR-0109](0109-generated-knowledge-wiki.md)
- [ADR-0165](0165-provider-neutral-text-inference-contract.md)
- [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md)
