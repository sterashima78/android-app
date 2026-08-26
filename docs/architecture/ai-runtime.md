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

## Sources

- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
- [ADR-0168](../adr/0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171](../adr/0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172](../adr/0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175](../adr/0175-knowledge-local-chatgpt-routing.md)
- [ADR-0185](../adr/0185-normalize-chatgpt-provider-failures-in-core.md)
