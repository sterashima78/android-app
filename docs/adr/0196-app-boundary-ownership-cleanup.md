# ADR-0196: app 例外を feature / provider / platform ownership へ整理する

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0185](0185-normalize-chatgpt-provider-failures-in-core.md), [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md), [ADR-0194](0194-workout-ai-advisor.md), [ADR-0195](0195-trigger-backup-from-persistence-commit-boundary.md)

## Context

ADR-0193 により `:app` 内の app-only responsibility は `entry` / `security` / `diagnostics` / `platform` / `ui` へ整理した。一方、root package には application composition ではなく feature policy や provider technical adapter を実装する型が残っていた。

特に `AppWorkoutAiAdvisor` は Local / ChatGPT の provider 選択と `promptBudgetChars` に基づく Workout prompt 省略規則を実装していた。これは ADR-0194 が Workout ownership とした policy であり、単なる app wiring ではない。

また `ChatGptTextInference` は `AiTextInference` を ChatGPT / Codex へ投影する provider technical adapter で、feature semantics を持たない。`AppAuthorizationDependencies` は Gmail / Google Books の Activity Result boundary であり app ownership は正しいが、root package に置く理由はない。

前回レビュー時に候補だった `NotifyingWebLibraryMutator` は、その後の ADR-0195 / PR #328 で persistence commit notification へ置き換えられ、main には存在しない。backup scheduling を UI/feature mutator decorator に戻さない。

## Decision

### 1. Workout AI advisor implementation は `:feature:workout:data` が所有する

`AppWorkoutAiAdvisor` を廃止し、`DefaultWorkoutAiAdvisor` を `:feature:workout:data` に配置する。

`DefaultWorkoutAiAdvisor` は次を所有する。

- `WorkoutAiProvider.LOCAL` / `CHATGPT` の明示 routing
- 選択された `AiTextInference` の prompt budget 適用
- budget 超過時に安全制約側の冒頭と現在依頼側の末尾を残す Workout 固有の省略規則

`:app` は Local / Cloud の `AiTextInference` instance を構築し、Workout-owned advisor へ注入するだけとする。これにより ADR-0194 の feature ownership と source ownership を一致させる。

### 2. provider-neutral ChatGPT text inference adapter は `:core:ai-cloud-openai` が所有する

`ChatGptTextInference` を `:app` から `:core:ai-cloud-openai` へ移し、`:core:ai-cloud-openai` から `:core:ai-inference` へ依存する。

この adapter は OpenAI/ChatGPT 固有 client と model preference を provider-neutral `AiTextInference` contract へ投影する technical capability であり、feature policy を持たない。

application composition は引き続き provider adapter の選択点であり、`:app` の `AppAiCoreRuntimeDependencies` が `ChatGptTextInference` を構築する。

Summary / Knowledge の feature-specific cloud adapter は今回移動しない。これらは provider failure を feature-specific failure semantics へ変換するため ADR-0185 の app composition adapter 例外を維持する。

### 3. Activity Result authorization boundary は `:app/platform/authorization` に置く

Gmail / Google Books の authorization dependency、authorized account、resolution outcome は Android `Intent` / `PendingIntent` を含む application/platform boundary であるため Gradle ownership は `:app` のまま維持する。

物理配置だけを `dev.terashima.yomitorirss.platform.authorization` に移し、root package には置かない。Route host と runtime composition はこの platform package の型を利用する。

### 4. `NotifyingWebLibraryMutator` は再導入しない

ADR-0195 に従い、automatic backup scheduling は durable persistence commit notification を起点とする。Library mutator や UI refresh callback に backup side effect を付与する decorator は app cleanup のために再導入しない。

### 5. 今回は新しい Gradle module を追加しない

今回の変更は既存 ownership と source placement の不一致を修正するものに限定する。`:app:composition` や provider-specific feature module の新設は、依存境界として独立した価値を再評価する別 ADR とする。

## Consequences

### Positive

- Workout provider routing と prompt budget policy の source ownership が Workout feature と一致する。
- provider-neutral ChatGPT adapter が OpenAI technical capability と同じ module に集約される。
- `:app` root package から feature policy / provider implementation / platform helper が減る。
- Activity Result boundary は app ownership を維持しつつ source tree から platform responsibility を判別できる。
- architecture test で各 ownership を regression rule として固定できる。

### Negative

- `:feature:workout:data` から `:core:ai-inference` への dependency が増える。
- `:core:ai-cloud-openai` から `:core:ai-inference` への dependency が増える。
- authorization 型の package 変更に伴い app 内 import が増える。

## Verification

- `DefaultWorkoutAiAdvisorTest` を Workout data module で実行し、provider routing と prompt budget 省略規則を確認する。
- app source に `AppWorkoutAiAdvisor.kt` が存在しないことを architecture test で固定する。
- `ChatGptTextInference` が `:core:ai-cloud-openai` に存在し、app root に存在しないことを architecture test で固定する。
- `:core:ai-cloud-openai` と `:feature:workout:data` が `:core:ai-inference` へ依存することを architecture test で固定する。
- authorization boundary が `platform.authorization` に存在し、app root の旧 file が存在しないことを architecture test で固定する。
- existing Architecture / Test / Lint / public repository verification を実行する。

## Public repository review

本変更は source ownership、Gradle dependency、synthetic unit test、architecture test、ADR のみを変更する。credential、OAuth token、account identifier、実ユーザー prompt、Workout 実データ、private endpoint、diagnostic artifact を repository に追加しない。

## References

- [ADR-0003](0003-multi-module-architecture.md)
- [ADR-0136](0136-public-repository-content-verification.md)
- [ADR-0165](0165-provider-neutral-text-inference-contract.md)
- [ADR-0185](0185-normalize-chatgpt-provider-failures-in-core.md)
- [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md)
- [ADR-0194](0194-workout-ai-advisor.md)
- [ADR-0195](0195-trigger-backup-from-persistence-commit-boundary.md)
