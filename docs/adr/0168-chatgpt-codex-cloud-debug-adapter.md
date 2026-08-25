# ADR-0168: ChatGPT OAuth と Codex Responses を隔離した cloud debug adapter として導入する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0056](0056-feature-owned-local-ai-policies.md), [ADR-0069](0069-unified-ai-model-settings-and-task-queue.md), [ADR-0136](0136-public-repository-content-verification.md), [ADR-0155](0155-application-scope-http-transport.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md)

## Context

ADR-0165 で Summary / Knowledge / Library の単発テキスト生成は provider 非依存の `AiTextInference` contract へ移し、Gemma / LiteRT-LM 固有処理を `:core:ai-runtime` に残した。次段階では、公開 Web 記事等をクラウドへオフロードし、端末上のローカル推論と並行実行できる基盤が必要になる。

B1 では実タスクの routing を変更せず、ChatGPT subscription の認証と Codex Responses への最小推論が Android 上で成立するかを独立して検証できる状態を作る。参照実装は OpenClaw と OpenAI Codex の公開ソースに限定し、Codex app-server や agent runtime は組み込まない。

## Decision

### 1. OpenAI/ChatGPT 固有 protocol を `:core:ai-cloud-openai` へ隔離する

新しい core module `:core:ai-cloud-openai` を追加する。この module は ChatGPT OAuth、credential refresh、ChatGPT account id 解決、Codex Responses request/response 変換だけを所有する。

feature や `:app` に endpoint、OAuth field、Authorization header、stream event type を分散させない。将来 upstream protocol が変化した場合はこの adapter 内で追随する。

`:core:ai-inference` の provider 非依存 contract は変更しない。B1 では cloud adapter を production AI task へ接続せず、B2 で routing と cloud eligibility を別判断として追加する。

### 2. ChatGPT login は device-code flow を利用する

Android では localhost callback server を持たず、Codex が提供する device-code flow を利用する。

- アプリが device code と verification URL を取得する。
- ユーザーは外部ブラウザで ChatGPT にログインして code を承認する。
- アプリが承認状態を poll し、authorization code を token endpoint で交換する。
- access token の期限前、または Responses が 401 を返した場合に refresh token で更新する。
- refresh は process 内で直列化し、refresh token rotation の競合を避ける。

Device-code authorization が ChatGPT account / workspace 側で無効な場合は、設定側で失敗を確認できるようにする。

OAuth client id は OpenAI Codex が公開ソースで利用している public client identifier を使用する。client secret は存在せず、credential として扱わない。

### 3. OAuth credential は Keystore で保護し backup 対象外にする

access token、refresh token、account id、expiry は Android Keystore の AES/GCM key で暗号化した SharedPreferences に保存する。

保存ファイルは Android Auto Backup と device transfer の双方から明示的に除外する。UIには access / refresh token と完全な account id を渡さず、接続確認には account id の末尾だけを表示する。

ログや例外文にも credential、Authorization header、token response body を含めない。

### 4. B1 の Responses transport は単発デバッグ用 SSE とする

ChatGPT/Codex Responses には application-scope の共有 `HttpClient` を使う。request は `store=false` とし、単一 user text input の streaming response を SSE として受ける。

現在の `:core:network` は response body を完了時にまとめて返すため、B1 は SSE event を受信完了後に解析する。token-by-token UI streaming、WebSocket transport、tool calling、thread/session continuation は B1 の対象外とする。

成功 body とエラー表示には上限を設け、未知・不正な stream は fail closed にする。

### 5. Settings に debug-only surface を追加する

Settings の AI section に `ChatGPT / Codex デバッグ` を追加し、次を端末上で確認できるようにする。

- 未接続 / 接続済み状態
- device-code login とブラウザ起動
- token expiry
- logout
- model id の一時入力
- test prompt の一時入力
- 推論結果と所要時間

model id の既定値は実装時点の upstream catalog に合わせるが、account entitlement は固定値から保証できないため編集可能にする。B1 の model id と prompt は durable user setting にしない。

### 6. production task routing は変更しない

Summary / Knowledge / Library / Chat / Vision の既存実行先は変更しない。`LocalAiBackgroundTaskGate` の利用条件も変更しない。

B1 は認証・通信・credential lifecycle の技術検証だけを提供する。公開情報を cloud eligible とする規則、task ごとの Local / Cloud 選択、cloud executor の並列度、retry policy は B2 以降で owning feature と task queue の責務を確認して決める。

## Consequences

### Positive

- ChatGPT subscription を使った cloud inference の成立性を production task から独立して端末デバッグできる。
- undocumented / change-prone な provider protocol を1 moduleへ閉じ込められる。
- credential が画面、通常ログ、Android backupへ漏れる経路を狭められる。
- B2 は認証実装ではなく routing / data sensitivity / concurrency に集中できる。

### Negative

- ChatGPT/Codex subscription backend は OpenAI Platform の安定した公開 API contract と同一ではなく、upstream 変更で adapter が壊れる可能性がある。
- device-code authorization が account / workspace 設定で無効な場合がある。
- B1 の `HttpClient` は body-buffering transport のため、UIで token 単位の streaming 表示はできない。
- debug UI と credential store を保守する必要がある。

## Verification

- OAuth device-code request、authorization-code exchange、refresh token rotation を fake `HttpClient` で unit test する。
- Codex Responses request の endpoint / auth header / account header / `store=false` と SSE text extraction を unit test する。
- source regression test で provider endpoint が `:core:ai-cloud-openai` 外へ漏れないことを確認する。
- source regression test で OAuth preference が Android backup / device transfer から除外されることを確認する。
- source regression test で Settings debug UI が token field / Authorization header を描画しないことを確認する。
- Architecture / Test / Lint / public repository verification を実行する。
- 実端末では Settings から login → test inference → refresh可能状態確認 → logout までをデバッグできることを確認する。

## Documentation

- `docs/architecture/module-map.md` に `:core:ai-cloud-openai` の ownership を追加する。
- ADR index を ADR-0168 まで同期する。

## Public repository review

実 credential、token、完全な account id、ユーザー固有 URL、実推論結果は repository に保存しない。テストは synthetic token / account id と fake endpoint のみを使う。

OAuth client id は upstream の OpenAI Codex 公開ソースに含まれる public identifier であり secret ではない。provider endpoint と protocol constant は adapter 実装に必要な値として `:core:ai-cloud-openai` に限定し、ADR や feature code へ複製しない。
