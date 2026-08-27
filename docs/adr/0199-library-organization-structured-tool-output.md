# ADR-0199: 蔵書整理のAI出力を構造化ツール呼び出しにする

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0108](0108-library-organization-and-ai-suggestions.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0190](0190-isolate-local-text-inference-process.md)

## Context

蔵書の「まとめて整理」は `DefaultLibraryOrganizationSuggester` が書誌情報と既存分類からタグ・コレクション候補を生成し、Library 所有の検証後に自動適用する。従来はモデルへ JSON Schema を提示して「JSON オブジェクトだけを返す」と指示し、通常テキスト応答を Kotlin 側で JSON parse していた。

実機では、モデルが説明文、Markdown、配列でない値等を返して JSON Schema 検証に失敗するケースが多く、1回の repair generation を行っても整理に失敗することがあった。SMB 書誌正規化では既に LiteRT-LM の tool calling を使い、モデルの通常テキストではなく tool arguments を構造化結果として受け取る方式を採用している。

一方、ADR-0165 では単発自由形式テキスト生成を provider-neutral `AiTextInference` として分離し、ADR-0190 では Library organization を含む Local text generation を `:local_ai_text` subprocess へ隔離した。蔵書整理だけを tool calling のために main-process `LocalModelManager` へ戻すと、ADR-0190 の native-memory safety boundary を破る。

## Decision

### 1. 蔵書整理結果は `submit_library_organization` tool call で提出する

Library organization のモデルには JSON 本文を通常テキストとして返させない。判断完了時に `submit_library_organization` を1回だけ呼び出させ、次の引数を構造化結果とする。

- `tags`: 文字列配列、0〜5件
- `collections`: 文字列配列、0〜2件
- `reason`: 文字列、最大240文字

feature 側は tool arguments をそのまま信用せず、件数、型、空文字列、長さ、重複、想定外フィールドを従来同様 Kotlin で検証する。検証に失敗した場合は、検証理由を限定的に返して1回だけ再生成する。

### 2. 自由形式 text inference と structured output capability を分離する

`:core:ai-inference` に `AiStructuredTextInference` と provider-neutral な tool schema / tool call 型を追加する。

`AiTextInference` 自体には tool calling を追加しない。Summary / Knowledge 等の自由形式テキスト利用側へ不要な capability を強制せず、structured output が必要な feature だけが明示的に sibling capability を要求する。

Library organization の prompt、分類ポリシー、tool schema、validation、repair policy は引き続き `:feature:library:data` が所有する。`:core:ai-inference` は Library、LiteRT-LM、OpenAI、Android を知らない。

### 3. Local structured inference も短寿命 subprocess で実行する

Local 実装は `ProcessIsolatedLocalAiStructuredTextInference` とし、`LocalStructuredTextInferenceService` を `android:process=":local_ai_text"` / `android:exported="false"` で起動する。

main process は selected model、backend、speculative decoding、effective context token count、model revision を immutable snapshot として child へ渡す。child は main process の `SharedPreferences` を同期ストアとして直接利用せず、structured inference 専用の process-local preference に snapshot を適用する。

structured request は1回の bound-service lifetime で完結させ、unbind 後は child process を終了して LiteRT-LM / backend の native resource を回収する。Library の Worker、durable queue、DB、auto-apply、`LocalAiBackgroundTaskGate` は main process と owning feature に残す。

通常の `AiTextInference.generate()` 用 `LocalTextInferenceService` と structured service は同じ private process 名を利用する。Local AI background task は既存 gate で直列化され、structured request 完了時の process recycle が次の通常 generation と競合した場合も、通常 text adapter の既存 Binder recovery 境界を利用する。

### 4. tool calling の transport detail は runtime に閉じる

Local adapter は provider-neutral tool schema を既存 `LocalInferenceTool` / LiteRT-LM OpenAPI tool definition へ変換する。feature は `ConversationConfig`、LiteRT-LM `tool(...)`、tool-call JSON parser 等を直接参照しない。

model の通常テキスト最終応答は Library organization の結果として採用しない。tool executor が受け取った arguments のみを main process へ返す。

### 5. privacy boundary を維持する

書名、著者、description、既存分類、prompt、tool arguments、model の通常応答は log、diagnostics、SharedPreferences、repository fixture へ保存しない。IPC は app-private process 間に限定し、service は exported にしない。

public repository には synthetic test data だけを追加し、実蔵書、account identifier、token、secret、file path を追加しない。

## Consequences

### Positive

- モデルに JSON 構文そのものを生成させる必要がなくなり、説明文や Markdown 混入による失敗を減らせる。
- tags / collections の配列型を tool schema としてモデルへ提示できる。
- Library-owned validation と bounded repair は維持され、tool calling を schema validation の代替にはしない。
- ADR-0190 の process-isolation / native-memory reclamation boundary を維持したまま structured output を使える。
- 自由形式生成だけを使う feature は tool calling capability に依存しない。

### Negative

- provider-neutral inference contract が自由形式 text と structured output の2 capability になる。
- Local structured output 用 Binder / Service / schema mapping が追加される。
- structured request ごとに process を recycleするため Engine 初期化コストがある。
- 将来 cloud provider でも Library organization を実行する場合、同じ `AiStructuredTextInference` contract の provider adapter が必要になる。

## Verification

- Library organization parser が tool arguments の配列型、件数、文字列制約を検証する unit test
- validation failure 後の1回だけの repair tool call test
- Library organization が free-form `AiTextInference.generate()` を呼ばず structured capability を使う unit test
- provider-neutral structured tool schema invariant test
- app composition が process-isolated structured adapter を注入する source / compile verification
- `LocalStructuredTextInferenceService` が non-exported `:local_ai_text` service として manifest merge されること
- `verifyArchitecture`
- ADR integrity verification
- public repository verification
- PR unit tests / lint

## References

- [ADR-0108](0108-library-organization-and-ai-suggestions.md)
- [ADR-0134](0134-smb-multimodal-metadata-normalization.md)
- [ADR-0165](0165-provider-neutral-text-inference-contract.md)
- [ADR-0190](0190-isolate-local-text-inference-process.md)
