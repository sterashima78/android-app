# AI Runtime Boundaries

この文書は、Local AI と ChatGPT / Codex を利用する生成処理について、現在有効な runtime / provider boundary をまとめる。

## Provider-neutral feature contracts

Summary、Knowledge、Library organization 等の feature は provider protocol を直接扱わず、用途に応じた inference capability を利用する。

- 単発の自由形式テキスト生成は `:core:ai-inference` の `AiTextInference` を利用する。
- tool call を構造化出力として要求する単発生成は、同 module の sibling capability `AiStructuredTextInference` を利用する。自由形式生成しか必要としない consumer に tool calling を強制しない。
- Local 実装は `:core:ai-runtime` の local model runtime へ接続する。
- ChatGPT / Codex の HTTP、OAuth、Responses protocol と provider-neutral `ChatGptTextInference` implementation は `:core:ai-cloud-openai` に閉じる。
- Summary / Knowledge feature module は `ChatGptOpenAiClient` や OpenAI endpoint を直接参照しない。
- app composition は provider adapter instance と feature contract を接続するが、provider technical adapter や feature 固有 prompt、task lifecycle、retry policy を再実装しない。

Library organization は `AiTextInference.selectedModel()` から model / prompt budget を取得し、実際の分類結果は `AiStructuredTextInference` の `submit_library_organization` tool call で受け取る。分類 prompt、tool schema、引数 validation、bounded repair は Library feature が所有する。

## Execution routing

Summary と Knowledge は Local / ChatGPT の実行先を明示的に選択する。provider 設定と task runtime control は別責務とする。

- ChatGPT の login、model selection 等は provider 設定として管理する。
- Summary / Knowledge の Local / ChatGPT 選択は各 feature の execution setting とする。
- Local AI pause と Cloud AI pause は独立した runtime control とし、片方の停止が他方の task を止めない。
- provider が変わった場合、background task の gate、network constraint、実際の inference 先が同じ設定を参照する。
- AI task queue は provider-specific domain type へ依存せず、表示用 execution provider label と provider-neutral progress / failure state を扱う。

## Local one-shot text inference process boundary

Summary、Knowledge 等が利用する Local `AiTextInference.generate()` は main process で LiteRT-LM Engine を実行せず、`:core:ai-runtime` の非公開 bound Service を `:local_ai_text` process で起動して実行する。

- main process は WorkManager、durable queue、DB、feature policy、`LocalAiBackgroundTaskGate` を所有し続ける。
- child process は prompt と immutable execution snapshot を受け取り単発 text generation を返すだけで、Repository や Worker graph を構築しない。
- generation request が直列化順序を取得した直後に main process が selected model id、全既知 model revision、backend、speculative decoding、effective context token count、stage-duration estimate を snapshot として確定する。
- user が generation 中に設定を変更しても実行中 generation は開始時 snapshot を使い、待機中の次 generation は request 順序を取得した後に新設定を snapshot 化する。
- child process の `LocalModelManager` は main の `local_summary_models` / `local_context_benchmarks` を直接読まず、process-local `ContextWrapper` により child 専用 preference を利用する。
- `AUTO` context は main process で effective token count まで解決し、child では対応する固定 context mode として再現する。
- child には保存済み model artifact の全既知 revision metadata を渡し、起動時 cleanup が revision 不明の別 model を誤って削除しないようにする。
- child が学習した stage-duration 値だけを response metadata で main に戻し、main process 自身が main preference へ保存する。child から main の preference editor は利用しない。
- process 内の `LocalModelManager` は最大2 generation だけ再利用し、その後は unbind と process exit で native resource を回収する。
- 1 generation だけで後続 request がない場合も30秒 idle で process を終了する。
- process death / Binder failure は新しい process で1回だけ再試行し、durable task 全体の retry policy は owning feature に残す。
- child は `ActivityManager.setProcessStateSummary()` に text / structured、prepare / generate / recycle 等の安全な runtime state を128 bytes以下で登録し、PID単位の app-private ring log に PSS / RSS / native heap / Java heap を保存する。prompt、output、model id、書誌情報、URL、file path 等の user content は保存しない。
- Android 17 の memory-related exit report は `REASON_LOW_MEMORY`、API 37 の `REASON_MEMORY_LIMITER`、既存の `MemoryLimiter` description を扱い、`ApplicationExitInfo.pid` と終了時刻で child の ring log を相関する。
- `PREPARING_MODEL` / `GENERATING_RESPONSE` の provider-neutral progress は child process から main process へ転送する。
- prompt と output は diagnostics や log へ保存せず、IPC payload は128 Ki characters以下の text と必要最小限の primitive metadata に制限する。
- `selectedModel()` と `countTokens()` は main process の application-scope `LocalModelManager` を利用する。重い generation Engine の lifecycle だけを subprocess へ分離する。
- Chat の streaming / tool-capable conversation は `LocalModelManager` の conversation API を使う別経路であり、本境界では main process のままとする。

この境界は Android 17 の main-process memory diagnostics で、Library organization 実行中に Java heap が小さいまま native heap / PSS が継続増加し `MemoryLimiter:AnonSwap` に至った実測を根拠とする。backend は診断 report から確定できないため、GPU/OpenCL 固有の不具合とは断定せず、process lifetime に残留する native allocation 全般に対する safety boundary として扱う。

Android の `SharedPreferences` は複数 process 間の整合性保証を持たないため、process isolation を導入する際は設定の正本も main process に固定する。child process は Binder snapshot を execution input とし、main process の model/backend/context preference を直接同期ストアとして扱わない。subprocess の memory telemetry も SharedPreferences には保存せず、PID単位の app-private file を child process 自身が所有する。

## Local structured text inference boundary

Library organization の構造化結果は通常テキストの JSON として生成せず、`AiStructuredTextInference` を通じて tool call arguments として受け取る。

- Local adapter は `ProcessIsolatedLocalAiStructuredTextInference` とし、非公開 `LocalStructuredTextInferenceService` を同じ `:local_ai_text` process で起動する。
- main process で selected model、backend、speculative decoding、effective context token count、model revision を snapshot 化し、child 専用 preference へ適用する。main process の preference を child の同期ストアにはしない。
- provider-neutral `AiStructuredTool` を runtime 内で LiteRT-LM の `LocalInferenceTool` / OpenAPI tool definition へ変換する。feature は LiteRT-LM API を直接参照しない。
- Library organization では `submit_library_organization(tags, collections, reason)` の tool arguments だけを分類結果として利用し、通常の model text は採用しない。
- tool schema で型を限定した後も Library feature が件数、長さ、空値、重複等を再検証する。検証失敗時の repair は1回だけとする。
- structured request は1 bound-service lifetime で完結させ、終了時に child process を recycle して native resource を回収する。
- structured request の Binder transport が `RemoteException` / `DeadObjectException` で失敗した場合だけ、同じ immutable execution snapshot を使って新しい process で1回再試行する。model / tool / validation error は transport retry せず、Library feature の bounded repair と分離する。
- service は `android:exported="false"` とし、prompt、書誌データ、tool arguments、model output を log / diagnostics / SharedPreferences へ保存しない。

この capability は Chat の対話的 tool calling とは別である。Chat は会話履歴、streaming、複数 tool execution を扱うため、引き続き Chat 用 runtime boundary を利用する。

## ChatGPT inference failure boundary

ChatGPT / Codex の inference 経路では、provider の raw failure を feature へ直接渡さない。

`ChatGptInferenceClient` が `ChatGptOpenAiClient` を wrap し、network / HTTP / OAuth failure を次の安定した taxonomy へ正規化する。

- transient
- rate limited
- authentication
- request rejected
- not connected
- requested Web target not opened
- unknown

HTTP status の解釈、OAuth refresh failure の判定、provider exception message format の解析は `:core:ai-cloud-openai` 内だけで行う。typed failure には kind、retryable、必要な場合の HTTP status だけを残し、provider response body、prompt、token、account id、対象 URL 等の raw detail を保持しない。

`ChatGptTextInference` も同じ `:core:ai-cloud-openai` が所有し、normalized client と model preferences を provider-neutral `AiTextInference` へ投影する。これは technical adapter であり Workout 等の feature-specific provider routing や prompt budget policy は持たない。

Summary / Knowledge の app adapter は typed failure を各 feature の failure kind と user-facing message へ写像する。WorkManager retry、durable queue state、再試行待ち表示等の application policy は owning feature が引き続き所有する。

Settings の login / model catalog / debug 操作は inference failure contract とは用途が異なるため、既存の provider client を利用する。

## Application-scope runtime ownership

Local model manager、ChatGPT client、inference adapter、feature repository 等の application-scope instance は `AppContainer` 配下の runtime dependency group が一度だけ構築し再利用する。

runtime group は construction detail であり、Route、Screen、Worker へ group 型そのものを渡さない。consumer には ViewModel factory、Repository contract、scheduler、inference capability 等の narrow dependency へ投影してから渡す。Worker は owning feature の WorkerFactory を通じて application-scope graph へ接続し、parallel graph を再構築しない。

application composition は Local / ChatGPT adapter の concrete instance を選択・共有するが、adapter implementation の source ownership は各 technical core / owning feature に置く。Workout の provider routing と prompt budget 処理は `:feature:workout:data` の `DefaultWorkoutAiAdvisor` が所有する。

application-scope で再利用するのは adapter / manager ownership であり、重い Local text generation Engine を main process に常駐させることを意味しない。`ProcessIsolatedLocalAiTextInference` と `ProcessIsolatedLocalAiStructuredTextInference` は application-scope capability として共有しつつ、実 Engine は短寿命 `:local_ai_text` process 内で構築・破棄する。

## Sources

- [ADR-0161](../adr/0161-android17-main-process-memory-diagnostics.md)
- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
- [ADR-0168](../adr/0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171](../adr/0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172](../adr/0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175](../adr/0175-knowledge-local-chatgpt-routing.md)
- [ADR-0185](../adr/0185-normalize-chatgpt-provider-failures-in-core.md)
- [ADR-0190](../adr/0190-isolate-local-text-inference-process.md)
- [ADR-0196](../adr/0196-app-boundary-ownership-cleanup.md)
- [ADR-0199](../adr/0199-library-organization-structured-tool-output.md)
- [ADR-0219](../adr/0219-local-ai-subprocess-exit-diagnostics-and-recovery.md)
