# ADR-0185: ChatGPT provider failure を core adapter で正規化する

- Status: Accepted
- Date: 2026-08-26
- Refines: [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md), [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md), [ADR-0175](0175-knowledge-local-chatgpt-routing.md)

## Context

Summary と Knowledge はどちらも ChatGPT / Codex を利用するが、provider の HTTP status、OAuth refresh failure、transport failure をそれぞれの app adapter で個別に解釈していた。

そのため `429` / `5xx` / authentication failure の分類規則が重複し、provider protocol の message format や raw error text を複数 adapter が知る状態になっていた。機能ごとの retry policy や user-facing message は各 feature の契約に属する一方、provider response を安定した failure taxonomy へ変換する責務は OpenAI adapter 側に置く方が境界が明確になる。

また provider response body や prompt 断片を含む raw exception message は、feature adapter や durable task error へ伝播させないことが既存 ADR の安全性方針である。

## Decision

`core:ai-cloud-openai` に inference 用の `ChatGptInferenceClient` を追加する。

- `ChatGptInferenceClient` は既存 `ChatGptOpenAiClient` を delegate として利用する。
- network I/O failure と provider の `IllegalStateException` を core 内で捕捉し、`ChatGptProviderException` へ正規化する。
- failure kind は `TRANSIENT`、`RATE_LIMITED`、`AUTHENTICATION`、`REQUEST_REJECTED`、`NOT_CONNECTED`、`WEB_TARGET_NOT_OPENED`、`UNKNOWN` とする。
- `408`、`429`、`5xx`、`401` / `403`、OAuth refresh の `4xx` 等の provider-specific 判定は core adapter 内だけで行う。
- typed failure には kind、retryable、HTTP status のみを保持し、provider response body、prompt、token、account id、対象 URL 等の raw detail を保持しない。
- `CancellationException` は failure へ変換せずそのまま伝播する。

Summary / Knowledge の app adapter は typed failure を各 feature の `SummaryCloudFailureKind` / `KnowledgeCloudFailureKind` と安全な user-facing message へ写像する。feature 固有の retry / durable queue policy は従来どおり owning feature が所有する。

Settings の login / model catalog / debug 用経路は既存 `ChatGptOpenAiClient` を利用し続ける。今回の境界は background inference で provider failure を feature へ渡す経路に限定する。

## Consequences

### Positive

- provider HTTP / OAuth の分類規則が Summary と Knowledge で重複しない。
- provider protocol の exception message format を app feature adapter が解析しなくなる。
- provider response body や入力断片が feature failure へ漏れる可能性を core boundary で抑えられる。
- 新しい cloud inference consumer も同じ typed taxonomy を再利用できる。

### Negative

- raw provider detail は inference caller から参照できないため、詳細診断が必要な場合は core adapter 内で別の安全な診断経路を設計する必要がある。
- Settings/debug 経路と inference 経路で利用する client facade が2段になる。

## Verification

- core unit test で `429` / `5xx` / authentication / non-retryable `4xx` / transport / not-connected / Web target failure の分類を固定する。
- synthetic provider error に private prompt や URL を含めても `ChatGptProviderException.message` へ残らないことを test する。
- Summary / Knowledge adapter が `ChatGptInferenceClient` を利用し、HTTP status regex や OAuth refresh message を解析しないことを architecture test で固定する。
- Summary / Knowledge feature module が `core:ai-cloud-openai` へ直接依存しない既存制約を維持する。
- Architecture / unit tests / lint / public repository verification を CI で実行する。

## Public repository review

テストには `example.com` と架空の prompt / token 文字列だけを使用する。credential、実 token、実 account id、実ユーザー URL、provider response の実データを repository へ保存しない。
