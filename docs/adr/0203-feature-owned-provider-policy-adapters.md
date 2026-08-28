# ADR-0203: feature 固有 provider policy adapter は owning feature Data が所有する

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0185](0185-normalize-chatgpt-provider-failures-in-core.md), [ADR-0196](0196-app-boundary-ownership-cleanup.md), [ADR-0200](0200-app-composition-module-boundary.md)

## Context

ADR-0185 では OpenAI/ChatGPT protocol failure の正規化を `:core:ai-cloud-openai` に置き、Summary / Knowledge 固有の failure mapping は app adapter に残した。その後 ADR-0196 はこの app composition adapter を明示的な例外として維持し、ADR-0200 で application composition を `:app:composition` に分離した結果、`ChatGptKnowledgeTextInference` と `ChatGptSummaryCloudInference` も composition module に移った。

しかし両 adapter は単なる graph wiring ではない。Knowledge の prompt budget / cache variant / safe message / retry classification、Summary の URL failure message / feature failure taxonomy mapping など、feature semantics の変更理由を持つ。これを high fan-in composition boundary が所有すると `:app:composition` が第二の business-logic module になる。

## Decision

provider protocol と feature policy の境界を次のように分ける。

- `:core:ai-cloud-openai` は OAuth、transport、model catalog、provider response、`ChatGptProviderFailureKind` への正規化を所有する。
- `:feature:knowledge:data` は normalized provider capability を Knowledge の `AiTextInference` semantics、prompt budget、cache variant、`KnowledgeCloudFailureKind`、安全な user-facing message へ変換する adapter を所有する。
- `:feature:summary:data` は normalized provider capability を `SummaryCloudInference`、`SummaryCloudFailureKind`、Summary 固有の URL failure semantics と安全な user-facing messageへ変換する adapter を所有する。
- `:app:composition` は provider client と feature adapter の instance wiring のみを行い、feature 固有の mapping/policy を実装しない。
- feature Domain / UI は `:core:ai-cloud-openai` に依存しない。provider-specific dependency は infrastructure implementation である Data layer に限定する。
- provider-specific adapter のためだけに追加 Gradle layer/module は作らない。既存 Data layer が infrastructure ownership と test boundary を十分表現できるためである。

ADR-0185 の「Summary / Knowledge feature module が `core:ai-cloud-openai` へ直接依存しない」という verification は、本 ADR により「Summary / Knowledge の Domain / UI は直接依存せず、Data の provider adapter だけが依存できる」へ置き換える。ADR-0196 の Summary / Knowledge app composition adapter 例外も本 ADR により終了する。

## Consequences

### Positive

- feature 固有 policy の変更が owning feature 内に閉じる。
- `:app:composition` は graph construction に集中し、第二の business-logic module 化を抑制できる。
- provider protocol normalization は引き続き core に一元化される。
- adapter test が owning feature と同じ module に置かれる。

### Negative

- Summary / Knowledge Data は provider-specific core module への依存を持つ。
- 将来 provider が増え、Data layer の provider adapter が大きくなる場合は独立 integration module の価値を再評価する必要がある。

## Verification

- Knowledge / Summary Data の adapter unit test で normalized failure から feature failure への mapping を固定する。
- `:app:composition` に `ChatGpt*Knowledge*` / `ChatGpt*Summary*` policy implementation を置かない。
- Domain / UI module が `:core:ai-cloud-openai` に依存しないことを architecture verification で固定する。
- `:core:ai-cloud-openai` は feature module に依存しない。
- Architecture tests、unit tests、lint、public repository verification を実行する。

## Public repository review

この変更は module ownership と synthetic failure mapping のみを扱う。credential、OAuth token、account identifier、private endpoint、実ユーザー URL、prompt、provider response body、diagnostic artifact を追加しない。
