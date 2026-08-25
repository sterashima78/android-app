# ADR-0172: AI provider 設定・task routing・runtime control を分離する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0056](0056-feature-owned-local-ai-policies.md), [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0071](0071-prioritized-background-ai-task-scheduling.md), [ADR-0104](0104-ai-task-queue-feature-ownership.md), [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md), [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md)

## Context

ADR-0171 で Summary は Local / ChatGPT の実行先を選択できるようになった。しかし初期実装では、task routing の選択 UI を ChatGPT / Codex provider 設定へ置き、既存の local AI 一時停止状態を Summary cloud path にも流用していた。また cloud 要約は Android 側で本文を取得しないにもかかわらず、task queue へ `fetching_article` を保存していたため「記事本文を取得中」と表示された。

cloud provider を今後 Summary 以外にも利用する場合、provider account/model 設定、各 task の routing policy、実行中 queue の runtime control を同じ状態へまとめると責務が曖昧になる。Local AI の一時停止は端末資源・充電運用を制御するための機能であり、network 上の cloud execution と同一の停止条件ではない。

## Decision

### 1. Provider configuration と task routing を別 UI にする

`ChatGPT / Codex` 画面は provider configuration surface とし、次だけを扱う。

- login / logout
- 利用可能 model catalog の取得
- provider model の選択
- 接続テスト

どの application task がどの provider を使うかは、新しい `AI実行設定` 画面で扱う。最初は「記事要約・タグ付け」の `LOCAL / CHATGPT` を表示するが、将来 Library / Knowledge 等が cloud eligibility を持つ場合も同じ画面へ task 単位で追加する。

routing decision の ownership は引き続き owning feature に置く。Settings は feature setting を表示・変更する presentation surface に留める。

### 2. Local AI と Cloud AI の pause state を独立させる

background runtime control は次の2状態に分ける。

- Local AI pause: `LocalAiBackgroundExecutionPreferences`
- Cloud AI pause: `CloudAiBackgroundExecutionPreferences`

Local pause は local inference を使う Summary / Library / SMB metadata / Knowledge 等へ適用する。Cloud pause は cloud provider を使う task にだけ適用する。現在 cloud execution を持つのは Summary の ChatGPT path であり、後続 cloud adapter はこの control へ参加できる。

片方を停止してももう片方は継続できる。provider を切り替えた場合は新しい provider 側の pause state を尊重して queue を kick する。

### 3. 充電時自動再開は Local AI に限定する

充電時再開は、端末の電力・推論資源を理由に Local AI を一時停止する運用を支援する機能である。Cloud AI pause には適用しない。

AI task queue UI は次を個別に操作できるようにする。

- ローカルAIを一時停止
- クラウドAIを一時停止
- 充電時にローカルAIを自動再開

### 4. Progress stage は実際の execution semantics を表す

Cloud Summary は local `SummaryContentFetchWorker` を通らないため、`FETCHING_ARTICLE` を流用しない。cloud path は専用 stage を保存する。

- `CLOUD_GENERATING_SUMMARY`: URL を provider へ渡し、Web access を含む記事要約を実行中
- `CLOUD_GENERATING_METADATA`: summary と候補 metadata からタグ・folder を生成中

AI task queue はそれぞれ「クラウドで記事を要約中」「クラウドでタグ・フォルダ候補を生成中」と表示する。progress label は実装内部の古い local pipeline stage ではなく、ユーザーが現在の処理を理解できる semantic state とする。

### 5. Queue state と task state は分離したままにする

Local / Cloud pause は execution policy であり、durable task 自体を cancelled / failed へ変更しない。実行中 worker を停止する必要がある場合は interrupted task を queued へ戻し、対応 provider が再開されたときに再度 claim する。

個別 task の stop / cancel / retry semantics は既存 feature ownership を維持する。

### 6. Cloud provider の一時障害は durable task failure と分離する

Cloud transport / provider の状態は application task の失敗とは別に扱う。

- `401` は provider adapter が credential refresh を1回だけ試し、同じ request を1回だけ再送する。
- network I/O failure、`408`、`429`、`5xx` は retryable cloud failure として分類する。
- retryable failure では running Summary task を `queued` へ戻し、WorkManager の `Result.retry()` と exponential backoff に委譲する。
- ChatGPT Summary worker は network connectivity constraint を持ち、offline 中に cloud request を開始しない。
- refresh 後も失敗する `401` / `403` とその他の非一時的 `4xx` は自動再試行せず、ユーザー操作が必要な durable failure とする。
- `CancellationException` は failure に変換せず、そのまま worker cancellation として伝播させる。

OpenAI / ChatGPT 固有の status code や transport exception は Summary domain へ直接公開せず、cloud adapter が retryability と安全な user-facing message へ正規化する。provider response body、prompt、URL、token、account id を task error や log へコピーしない。

AI task queue の Summary 行には現在選択されている実行先を `Local` / `ChatGPT` として表示し、pause state と progress に加えて「どこで実行されるタスクか」を確認できるようにする。これは routing の観測表示であり、provider credential details は表示しない。

## Consequences

### Positive

- ChatGPT connection/model 設定と application task routing の意味が分かれる。
- 将来別 feature を cloud provider 対応するとき、provider 設定 UI に feature-specific option を増やさずに済む。
- Local AI を止めたまま cloud Summary を処理でき、逆も可能になる。
- 充電時再開が cloud request を意図せず開始することがなくなる。
- cloud Summary の進捗表示が実際の pipeline と一致する。
- rate limit、provider outage、通信断でSummary taskが直ちに「失敗」へ落ちず、WorkManagerのbackoffで自動回復できる。
- provider response bodyをdurable task errorへ保存しないため、クラウド側が入力断片をerrorへ含めても端末ログ/UIへ再露出しにくい。

### Negative

- execution state が1つから Local / Cloud の2つになるため、queue repository と UI state が少し複雑になる。
- 現時点では cloud pause の対象が Summary だけなので、汎用 control の利用者は少ない。
- Summary queue の実行先表示は現在の routing setting を表し、provider 切替前に失敗した履歴の「実際に失敗したprovider」を永続化する監査ログではない。
- provider-specific concurrency や Retry-After を使った厳密な待機時間制御はこの判断では扱わず、WorkManager exponential backoff を利用する。

## Verification

- Local pause が Library 等の local task を停止し、Cloud pause state を変更しないことを test する。
- Cloud pause が Summary cloud execution を停止し、Library scheduler を停止しないことを test する。
- Local / Cloud pause preference が独立して永続化されることを test する。
- charging resume が Local preference だけへ作用することを test する。
- Cloud Summary が `cloud_generating_summary` / `cloud_generating_metadata` を記録し、UI が provider-aware label へ変換することを test する。
- `401` が credential refresh 後に1回だけ再送されることを transport test で固定する。
- `429` / `5xx` を retryable、認証失効や非一時的 `4xx` を non-retryable として分類することを test する。
- retryable cloud failure で running task が `failed` ではなく `queued` へ戻ることを persistence test する。
- provider error body の synthetic secret / prompt 断片が user-facing failure message に残らないことを test する。
- AI task queue が Summary の `Local` / `ChatGPT` 実行先ラベルを表示できることを test する。
- ChatGPT / Codex UI が task routing control を持たず、AI実行設定が Summary routing を表示することを source/architecture review で確認する。
- Architecture / Test / Lint / public repository verification を実行する。

## Documentation

- ADR index に ADR-0172 を追加する。
- module map の Summary / Settings / AI task queue ownership を更新する。
- cloud retry / failure classification / queue observability の判断は ADR-0172 の refinement として本ADRへ追記する。

## References

- [ADR-0056](0056-feature-owned-local-ai-policies.md)
- [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md)
- [ADR-0071](0071-prioritized-background-ai-task-scheduling.md)
- [ADR-0104](0104-ai-task-queue-feature-ownership.md)
- [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md)
