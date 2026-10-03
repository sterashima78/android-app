# ADR-0277: 対話型ローカルAI推論を短寿命サブプロセスへ隔離する

- Status: Accepted
- Date: 2026-10-03
- Refines: [ADR-0190](0190-isolate-local-text-inference-process.md), [ADR-0219](0219-local-ai-subprocess-exit-diagnostics-and-recovery.md), [ADR-0266](0266-local-text-inference-watchdog.md)

## Context

単発のローカル text inference は ADR-0190 により短寿命の `:local_ai_text` subprocess へ隔離されている。一方、AI Chat の streaming / tool-capable conversation は main process の `LocalModelManager.generateConversation()` を直接利用し、重い model Engine の初期化と conversation execution を foreground main process 内で行っている。

実機で AI Chat の model preparation 中に foreground main process が `ApplicationExitInfo.REASON_LOW_MEMORY` で終了した。これは background の通常 process reclaim ではなく、対話操作中の main process 自体が memory pressure の対象になったことを示す。

AI Chat は owner Context の read-only tool を main process で実行する必要がある。DB / Repository / feature graph を inference subprocess へ移すと既存 ownership と trust boundary を崩すため、Engine と conversation だけを subprocess へ移し、tool execution は main process に残す必要がある。

## Decision

### 1. AI Chat の Engine と Conversation を既存 `:local_ai_text` process へ移す

AI Chat は process-isolated conversation capability を利用し、model Engine の初期化、conversation 作成、streaming generation を main process で実行しない。

新しい inference process は追加せず、単発 text inference と同じ `:local_ai_text` process / execution snapshot / recycle policy を再利用する。

main process は引き続き次を所有する。

- Chat session / message persistence
- retrieval policy
- AgentSkill / AgentTool の実装
- owner Repository / read-only Query API
- streaming UI state
- model selection と inference setting の正本

### 2. Tool execution は IPC bridge で main process に残す

subprocess へ渡すのは tool の schema と conversation input だけとする。model が tool call を生成した場合、subprocess は tool name と正規化済み arguments を main process へ返す。

main process は現在の `AgentSkill` に対応する tool を実行し、result text だけを subprocess へ返す。subprocess はその result を同一 conversation に投入して generation を継続する。

DB、Repository、credential、arbitrary code execution capability を subprocess へ渡さない。tool result は既存 Chat 上限を維持し、prompt / tool arguments / tool result / model output を diagnostics へ保存しない。

### 3. Streaming と progress を既存 Binder session で返す

conversation の partial output は subprocess から main process へ streaming event として返す。

model preparation / response generation progress も既存 text inference と同じ transport を利用し、Chat UI の表示契約を維持する。

### 4. subprocess death は main process failure にしない

model preparation または generation 中に subprocess が終了した場合、Binder death recovery を1回だけ行う。再試行後も終了する場合は Chat request を失敗として UI へ返し、foreground main process は継続する。

無制限 retry や context size の暗黙変更は行わない。memory safety のために user-selected model/backend/context setting を silently override しない。

### 5. diagnostics は chat mode を識別する

`:local_ai_text` の process state summary と bounded memory sample に `mode=chat` を追加する。

記録対象は mode / phase / backend / effective context token count / speculative decoding flag と memory sample に限定し、会話本文、tool name、tool arguments、tool result、model output は含めない。

## Consequences

### Positive

- foreground main process から数GB級になり得る inference Engine を除外できる。
- model loading の memory pressure で inference subprocess が終了しても Chat UI process を維持できる。
- 単発 text inference と同じ process lifecycle / snapshot / watchdog / diagnostics を再利用できる。
- AgentSkill と owner Repository は main process に残り、既存の persistence / trust boundary を維持できる。

### Negative

- tool call ごとに Binder round-trip が増える。
- streaming chunk と tool bridge の IPC protocol が `:core:ai-runtime` に追加される。
- package-wide memory pressure 自体は subprocess 化だけで消えるとは限らず、端末条件によっては subprocess が再度終了する可能性がある。
- process recycle 後の最初の Chat turn は Engine 再初期化 latency を伴う。

## Verification

- conversation request が model Engine を main process で生成しないことを architecture/source test で固定する。
- conversation input / tool schema の IPC payload upper bound を unit test する。
- tool schema encode/decode、tool call argument/result bridge を unit test する。
- streaming chunk が main process callback へ転送されることを test する。
- diagnostics が `mode=chat` を表現できることを test する。
- existing text / structured inference test、Chat test、architecture verification、public repository verification、Android lint を実行する。

## Public repository review

diagnostics、tests、documents に実会話、実URL、account identifier、端末固有 identifier、credential、private file path を追加しない。回帰 test は synthetic input のみを使用する。
