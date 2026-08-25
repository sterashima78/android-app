# ADR-0171: Summary の Local / ChatGPT routing と URL 起点の cloud Web 取得を分離する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0056](0056-feature-owned-local-ai-policies.md), [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0071](0071-prioritized-background-ai-task-scheduling.md), [ADR-0092](0092-separate-summary-and-bookmark-metadata-generation.md), [ADR-0105](0105-summary-content-preparation-pipeline.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md), [ADR-0170](0170-bounded-http-response-bodies.md)

## Context

ADR-0165 で Summary の単発テキスト推論は `AiTextInference` に依存する形へ整理し、ADR-0168 で ChatGPT OAuth と Codex Responses transport を production task から隔離して実端末接続を確認した。

次の段階では、公開 Web 記事の要約を cloud へオフロードし、端末のローカル推論資源を Library 等の別タスクへ残したい。一方、既存 Summary pipeline は本文を Android 側で取得・整形してから `LocalAiBackgroundTaskGate` 内で推論する前提であり、そのまま provider を差し替えるだけでは cloud task もローカル排他制御を占有する。

Codex の Responses transport は native `web_search` tool を持ち、指定 URL を `open_page` した結果を response item として返せる。また ChatGPT 認証時の Codex model catalog から、アカウントで利用可能な model metadata を取得できる。そのため cloud 要約では Android が記事本文を取得して upload するより、URL を渡して provider 側で対象ページを開かせる方が責務とデータフローを単純化できる。

## Decision

### 1. Summary feature が実行 provider を所有する

Summary は `SummaryExecutionProvider` として次の2値を持つ。

- `LOCAL`
- `CHATGPT`

既定値は `LOCAL` とし、既存ユーザーの実行先を自動で cloud へ変更しない。設定変更時は Summary queue を再度 kick し、待機中 task を新しい provider で処理できるようにする。すでに running の task は開始時に選択された provider のまま完了させる。

provider 選択は Summary の policy であり、`:core:ai-inference` に task kind や cloud routing enum を追加しない。

### 2. ChatGPT の model catalog を認証済みアカウントから取得する

ChatGPT 接続後、`:core:ai-cloud-openai` が Codex の model catalog endpoint を認証付きで取得する。UI は provider が返した model metadata を使い、固定の model ID 候補をアプリへ埋め込まない。

B2 の Summary cloud 利用では次を満たす model だけを選択候補にする。

- picker 表示対象
- API 利用対象
- native Web search 対応

現在の catalog が返す `web_search_tool_type` (`text` / `text_and_image`) を Web search capability の一次情報として扱い、旧 metadata の `supports_search_tool` は互換 fallback とする。

選択した model ID は provider setting として永続化し、Summary Worker に model ID を hardcode しない。catalog の再取得に失敗した場合は保存済み選択を勝手に変更せず、UI に取得失敗を表示する。保存済み model が現在の catalog に存在しない場合は別 model の再選択を要求する。

### 3. Cloud 要約は記事本文ではなく URL を入力にする

`CHATGPT` 経路では `SummaryContentFetchWorker` による HTML 取得、本文抽出、prepared content 保存を要約の前提にしない。

Summary Worker は記事 URL と要約 prompt を cloud adapter へ渡し、provider request に native `web_search` tool を含める。

- live Web access を要求する。
- target URL の host を allowed domain として制限する。
- tool 使用を必須にする。
- response の `web_search_call` を確認し、target と同じ host/path の `open_page` が発生した場合だけ生成結果を採用する。

対象 URL は HTTPS の公開 URL に限定し、localhost、`.local`、private / link-local IP literal、userinfo 付き URL は cloud 経路で拒否する。

指定ページを開けなかった場合、検索 snippet、別ページ、model の事前知識から推測した要約へ fallback しない。

### 4. Bookmark metadata も選択 provider で生成する

Summary 完了後の Bookmark metadata generation では記事 summary、記事 title、既存 tag 候補、既存 folder 候補を prompt に利用する。

`CHATGPT` が選択されている場合、これらの metadata を cloud provider へ送信してよいものとして同じ provider で tags / folder を生成する。既存の JSON parse、schema validation、既存 folder への一致検証は引き続き Summary feature が所有する。

`LOCAL` が選択されている場合は従来どおり `AiTextInference` を利用する。

### 5. Local gate は Local 推論にだけ適用する

`LOCAL` Summary task は従来どおり `LocalAiBackgroundTaskGate` を取得してから claim / inference する。

`CHATGPT` Summary task は `LocalAiBackgroundTaskGate` を取得せずに claim / cloud inference する。これにより cloud 要約中でも Library 等の local AI task が端末の推論 runtime を利用できる。

B2 では既存 Summary Worker 自体は1本のままとし、cloud Summary 同士は直列実行を維持する。複数 cloud task の並列度、rate limit、provider ごとの executor 分離は後続変更で扱う。

### 6. Cloud failure を暗黙に Local へ fallback しない

認証切れ、model 利用不可、Web page 取得失敗、provider error、network error が起きた場合、その task は通常の Summary failure として記録する。

同じ実行中 task を自動で Local へ切り替えない。cloud 障害時に大量の task が `LocalAiBackgroundTaskGate` へ流れ込むことを避け、実行先の意味を明確にする。

retry / backoff、429 や一時的 5xx の分類、cloud concurrency は B3 で追加する。

### 7. Cloud transport も bounded response policy を維持する

ADR-0170 に従い、OAuth / device login、model catalog、Responses の各 request は transport 読み込み上限を明示する。model catalog は 4 MiB、生成成功 response は 16 MiB、error / OAuth response は 16 KiB を上限とし、B2 の追加経路によって無制限読み込みへ戻さない。

## Consequences

### Positive

- Cloud Summary は Android 側で記事本文を download / parse / upload せず URL だけで開始できる。
- provider 自身の Web access で指定ページを開いたことを検証でき、別ページからの推測要約を fail closed にできる。
- cloud task が local gate を占有せず、別 feature のローカル推論と同時に進められる。
- model catalog を account から取得するため、model 世代や entitlement の変更へ固定リストより追随しやすい。
- Summary prompt、metadata validation、task persistence は引き続き owning feature に残る。

### Negative

- provider の model catalog / Web search contract は upstream 変更の影響を受けるため、`:core:ai-cloud-openai` adapter の保守が必要になる。
- Cloud 選択時は target site が Codex の Web access から取得可能である必要がある。
- B2 では cloud Summary も1件ずつ処理するため、cloud provider 自体の並列性能はまだ活用しない。
- 設定変更直前から running の task は途中で provider を切り替えない。

## Verification

- ChatGPT model catalog request の auth、endpoint、`web_search_tool_type` を含む主要 model field mapping を fake transport で検証する。
- picker 候補を Web search 対応かつ API 利用可能な visible model に限定する。
- OAuth / catalog / generation request が ADR-0170 の response-size 上限を維持することを検証する。
- Web Summary request が `web_search`、live access、allowed domain、required tool choice を含むことを検証する。
- target URL の `open_page` が response にない場合は生成結果を拒否する。
- cloud prompt が target URL を明示し、別 source から推測しない指示を含むことを検証する。
- source / worker tests で cloud path が `LocalAiBackgroundTaskGate` を取得せず、local path が既存 gate を維持することを確認する。
- `SummaryContentFetchWorker` が cloud provider 選択時に本文 prefetch を行わないことを確認する。
- architecture test で Summary feature が OpenAI/Codex protocol に直接依存せず、app composition adapter 経由で接続されることを確認する。
- Architecture / Test / Lint / public repository verification を実行する。

## Documentation

- ADR index に ADR-0171 を追加する。
- module map で `:core:ai-cloud-openai` の model discovery / native Web search ownership と Summary の routing ownership を明記する。

## Public repository review

本変更は provider protocol adapter、model metadata、task routing、設定 UI、synthetic test fixture と architecture documentation を変更する。access token、refresh token、完全な account id、実ユーザー URL、実記事本文、実 tag / folder、実推論結果は repository に保存しない。

## References

- [ADR-0056](0056-feature-owned-local-ai-policies.md)
- [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md)
- [ADR-0071](0071-prioritized-background-ai-task-scheduling.md)
- [ADR-0092](0092-separate-summary-and-bookmark-metadata-generation.md)
- [ADR-0105](0105-summary-content-preparation-pipeline.md)
- [ADR-0155](0155-application-scope-http-transport.md)
- [ADR-0165](0165-provider-neutral-text-inference-contract.md)
- [ADR-0168](0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0170](0170-bounded-http-response-bodies.md)
