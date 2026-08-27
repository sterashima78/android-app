# AI Runtime Boundaries

この文書は、Local AI と ChatGPT / Codex を利用する生成処理について、現在有効な runtime / provider boundary をまとめる。

## Provider-neutral feature contracts

Summary、Knowledge、Library organization 等の feature は provider protocol を直接扱わず、用途に応じた inference capability を利用する。

- 単発テキスト生成の共通 contract は `:core:ai-inference` に置く。
- Local 実装は `:core:ai-runtime` の local model runtime へ接続する。
- ChatGPT / Codex の HTTP、OAuth、Responses protocol は `:core:ai-cloud-openai` に閉じる。
- Summary / Knowledge feature module は `ChatGptOpenAiClient` や OpenAI endpoint を直接参照しない。
- app composition は provider adapter と feature contract を接続するが、feature 固有 prompt、task lifecycle、retry policy を再実装しない。

## Execution routing

Summary と Knowledge は Local / ChatGPT の実行先を明示的に選択する。provider 設定と task runtime control は別責務とする。

- ChatGPT の login、model selection 等は provider 設定として管理する。
- Summary / Knowledge の Local / ChatGPT 選択は各 feature の execution setting とする。
- Local AI pause と Cloud AI pause は独立した runtime control とし、片方の停止が他方の task を止めない。
- provider が変わった場合、background task の gate、network constraint、実際の inference 先が同じ設定を参照する。
- AI task queue は provider-specific domain type へ依存せず、表示用 execution provider label と provider-neutral progress / failure state を扱う。

## Local one-shot text inference process boundary

Summary、Knowledge、Library organization が利用する Local `AiTextInference.generate()` は main process で LiteRT-LM Engine を実行せず、`:core:ai-runtime` の非公開 bound Service を `:local_ai_text` process で起動して実行する。

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
- `PREPARING_MODEL` / `GENERATING_RESPONSE` の provider-neutral progress は child process から main process へ転送する。
- prompt と output は diagnostics や log へ保存せず、IPC payload は128 Ki characters以下の text と必要最小限の primitive metadata に制限する。
- `selectedModel()` と `countTokens()` は main process の application-scope `LocalModelManager` を利用する。重い generation Engine の lifecycle だけを subprocess へ分離する。
- Chat の streaming / tool-capable conversation は `LocalModelManager` の conversation API を使う別経路であり、本境界では main process のままとする。

この境界は Android 17 の main-process memory diagnostics で、Library organization 実行中に Java heap が小さいまま native heap / PSS が継続増加し `MemoryLimiter:AnonSwap` に至った実測を根拠とする。backend は診断 report から確定できないため、GPU/OpenCL 固有の不具合とは断定せず、process lifetime に残留する native allocation 全般に対する safety boundary として扱う。

Android の `SharedPreferences` は複数 process 間の整合性保証を持たないため、process isolation を導入する際は設定の正本も main process に固定する。child process は Binder snapshot を execution input とし、main process の model/backend/context preference を直接同期ストアとして扱わない。

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

Summary / Knowledge の app adapter は typed failure を各 feature の failure kind と user-facing message へ写像する。WorkManager retry、durable queue state、再試行待ち表示等の application policy は owning feature が引き続き所有する。

Settings の login / model catalog / debug 操作は inference failure contract とは用途が異なるため、既存の provider client を利用する。

## Application-scope runtime ownership

Local model manager、ChatGPT client、inference adapter、feature repository 等の application-scope instance は `AppContainer` 配下の runtime dependency group が一度だけ構築し再利用する。

runtime group は construction detail であり、Route、Screen、Worker へ group 型そのものを渡さない。consumer には ViewModel factory、Repository contract、scheduler、inference capability 等の narrow dependency へ投影してから渡す。Worker は owning feature の WorkerFactory を通じて application-scope graph へ接続し、parallel graph を再構築しない。

application-scope で再利用するのは adapter / manager ownership であり、重い Local text generation Engine を main process に常駐させることを意味しない。`ProcessIsolatedLocalAiTextInference` は application-scope capability として共有しつつ、実 Engine は短寿命 `:local_ai_text` process generation ごとに構築・破棄する。

## Sources

- [ADR-0161](../adr/0161-android17-main-process-memory-diagnostics.md)
- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
- [ADR-0168](../adr/0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171](../adr/0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172](../adr/0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175](../adr/0175-knowledge-local-chatgpt-routing.md)
- [ADR-0185](../adr/0185-normalize-chatgpt-provider-failures-in-core.md)
- [ADR-0190](../adr/0190-isolate-local-text-inference-process.md)