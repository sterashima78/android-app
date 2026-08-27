# ADR-0189: 単発 Local text inference を短寿命の専用プロセスへ隔離する

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0079](0079-process-wide-local-ai-inference-sessions.md), [ADR-0159](0159-isolate-smb-vision-inference-process.md), [ADR-0161](0161-android17-main-process-memory-diagnostics.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md), [ADR-0175](0175-knowledge-local-chatgpt-routing.md)

## Context

ADR-0161 では Android 17 の `MemoryLimiter:AnonSwap` による main process 終了について、原因を推測して text inference を直ちに別 process へ移すのではなく、background local-AI task と PSS / RSS / native heap / Java heap を相関する診断を先に導入した。

その後の実機 report では、`LibraryOrganizationBatchWorker` が `main-active-background-ai` の owner である間に、約13分で native heap が約1.4 GiB、PSS が約1.2 GiB増加した一方、Java heap は数十 MiB程度に留まった。最終的に main process は Android 17 の `MemoryLimiter:AnonSwap` で終了した。

Library organization は書籍を直列処理しており、単純な並列 Worker 増殖ではない。`DefaultLibraryOrganizationSuggester` は provider-neutral `AiTextInference.generate()` を1回、validation repair が必要な場合は追加で1回呼び出す。main process の `LocalModelManager` は ADR-0079 に従って Engine を process-wide に再利用するため、連続した単発 generation で native allocation が process lifetime に残留する場合、main process 自体の memory baseline が上昇し続ける。

LiteRT-LM upstream には Android OpenCL GPU delegate で conversation ごとの memory が解放されず RSS が段階的に増える報告がある。ただし今回の実機 report には実際に選択されていた backend が含まれていないため、GPU/OpenCL 固有問題だったとは断定しない。今回の判断根拠は backend の推測ではなく、ADR-0161 が求めた実測条件として「active task 中の native/PSS 継続増加」と「小さい Java heap」が確認されたことである。

SMB vision inference では ADR-0159 により、native resource の完全解放を `Engine.close()` や GC だけへ依存せず、短寿命 app subprocess の終了を最終 reclamation boundary とする方式を既に採用している。単発 text generation にも同じ安全境界を適用できる。

## Decision

### 1. provider-neutral Local text generation を `:local_ai_text` process で実行する

Summary、Knowledge、Library organization が利用する application-scope Local `AiTextInference` は、`generate(prompt)` の実処理だけを `:core:ai-runtime` 所有の非公開 bound Service `LocalTextInferenceService` へ委譲する。

- Service は `android:exported="false"` とする。
- Service は `android:process=":local_ai_text"` で main process から分離する。
- `isolatedProcess` は利用しない。選択済み model artifact、app-private cache、Local AI preference を同一 UID の app-private storage から読む必要があるためである。
- main process は `AiTextInference.selectedModel()` と `countTokens()` を従来どおり application-scope `LocalModelManager` へ委譲する。重い LiteRT-LM generation Engine の生成と conversation execution だけを subprocess へ移す。
- Chat の streaming / tool-capable conversation は `AiTextInference` の単発生成経路ではなく `LocalModelManager` の conversation API を直接利用するため、本 ADR では main process のまま維持する。

### 2. main process は durable task ownership を維持する

process isolation は inference execution boundary の変更だけとし、feature ownership を移動しない。

- WorkManager Worker
- durable queue / claim / retry
- DB read/write と transaction
- Summary / Knowledge / Library の feature policy
- `LocalAiBackgroundTaskGate` の priority / permit
- foreground notification
- result validation と persistence

これらは従来どおり main process と owning feature が保持する。child process は provider-neutral な prompt を受け取り text generation を返すだけで、DB、Repository、WorkManager graph を構築しない。

### 3. subprocess は最大2 generation で必ず世代交代する

`:local_ai_text` process 内では `LocalModelManager.shared(applicationContext)` を再利用するが、1 process generation あたりの完了済み generation は最大2回に制限する。

2回目の成功または失敗 response を返した後、main process は Service を unbind し、Service は `LocalModelManager.close()` を試みたうえで process 自体を終了する。次の generation は前 process の Binder death を確認してから新しい process を bind する。

1回だけ generation して後続 request がない場合も native Engine を無期限に保持しないよう、30秒 idle で main process が Service を unbind して process を終了する。validation repair のような直後の2回目 generation は同じ process を再利用できる。

process death を最終 reclamation boundary とし、LiteRT-LM / backend が process 内 allocator や driver allocation を保持しても main process の memory baseline へ累積させない。

### 4. Binder death は1回だけ新 process で再試行する

OS memory pressure、service process crash、明示的 recycle と request が競合した場合、main process adapter は `DeadObjectException` / `RemoteException` を検出して active session を破棄し、新しい process で1回だけ generation を再試行する。

無制限 retry は行わない。durable task 全体の retry policy は Summary / Knowledge / Library の owning feature に残す。

呼び出し coroutine が cancellation された場合は active Service を unbind し、推論中 child process も終了させる。キャンセル済み generation を background で保持し続けない。

### 5. provider-neutral progress を child process から転送する

`LocalModelManager.inferenceProgress` の `PREPARING_MODEL` / `GENERATING_RESPONSE`、model name、推定 stage duration は Messenger 経由で main process の `AiTextInference.progress` へ写像する。

prompt、model output、article/book title、URL、file path 等を progress message、diagnostics、log へ追加しない。

### 6. IPC payload を小さい text に限定する

Binder へ渡すのは prompt と生成結果、progress metadata、成功/失敗状態だけとする。

- prompt / output はそれぞれ最大128 Ki charactersに制限する。現行 Local model の prompt budget より十分大きく、Binder transaction budget に対して余裕を残す。
- model file bytes、tokenizer artifact、画像、DB entity 等を Binder で転送しない。
- error message は最大500 charactersへ制限する。
- prompt / output を log、crash report、SharedPreferences、repository fixture へ保存しない。

### 7. secondary Application runtime は起動しない

Android は `:local_ai_text` process にも application class を生成するが、既存の `shouldInitializeMainProcessRuntime()` は package name と完全一致する main process だけを許可する。

そのため Activity tracking、startup crash diagnostics、main-process memory monitor、widget observer、backfill scheduling、WorkManager application graph 等を `:local_ai_text` では初期化しない。この契約を app unit test で固定する。

## Consequences

### Positive

- Library organization の連続 Local generation で native memory が増加しても main process の PSS/native baseline へ累積しない。
- Summary / Knowledge の Local 単発 generation も同じ execution boundary を共有し、同種の native retention に対して予防的に保護される。
- process death が allocator / driver / LiteRT-LM internal cache を含む最終的な resource reclamation boundary になる。
- provider-neutral `AiTextInference`、feature-owned durable queue、DB ownership は変更しない。
- Chat の対話・streaming 経路には process IPC を導入しない。
- prompt と output を diagnostics へ持ち込まず、public repository / shareable crash report の privacy boundary を維持する。

### Negative

- 最大2 generation ごとに model Engine を再初期化するため、process-wide Engine reuse より latency と消費電力が増える。
- Binder / Service lifecycle と process death retry の実装複雑性が増える。
- `countTokens()` は synchronous contract のため main process tokenizer を引き続き利用する。今回観測した generation Engine の native retention 対策とは分離する。
- backend 固有の upstream leak が修正されても、実端末で連続 generation の memory baseline が安定することを確認するまでは process isolation を維持する。

## Verification

- `ProcessIsolatedLocalAiTextInferenceTest`
  - 2 generation で recycle する batch policy
  - batch size validation
  - IPC text upper bound
- `ApplicationProcessPolicyTest`
  - `:local_ai_text` process で main Application runtime を初期化しない
- existing Local `AiTextInference` model/progress mapping tests
- Summary / Knowledge / Library unit tests
- app unit tests
- release lint
- `verifyArchitecture`
- public repository verifier

実機 Android 17 では Library organization の長時間連続処理を再実行し、main process の `main-active-background-ai` sample で native heap / PSS が従来のように単調増加しないことを確認する。`:local_ai_text` process が generation 世代ごとに終了することも process diagnostics で確認する。

## Public repository review

この ADR には実ユーザーの prompt、書籍名、URL、file path、account、credential、exact process id、private profiling artifact を記録しない。実機診断値は設計判断に必要な丸めた memory 増加量と task class 名だけを記載する。実装も prompt / output を log または shareable diagnostics へ保存しない。

## References

- Android Developers: Android 17 behavior changes for all apps — App memory limits
  - https://developer.android.com/about/versions/17/behavior-changes-all
- LiteRT-LM issue #2699: Android OpenCL GPU delegate: per-conversation memory not reclaimed across create→send→delete — RSS ratchets to OOM
  - https://github.com/google-ai-edge/LiteRT-LM/issues/2699
