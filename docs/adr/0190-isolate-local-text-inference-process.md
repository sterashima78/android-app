# ADR-0190: 単発 Local text inference を短寿命の専用プロセスへ隔離する

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0079](0079-process-wide-local-ai-inference-sessions.md), [ADR-0159](0159-isolate-smb-vision-inference-process.md), [ADR-0161](0161-android17-main-process-memory-diagnostics.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0171](0171-summary-local-chatgpt-routing-and-web-fetch.md), [ADR-0175](0175-knowledge-local-chatgpt-routing.md)

## Context

ADR-0161 では Android 17 の `MemoryLimiter:AnonSwap` による main process 終了について、原因を推測して text inference を直ちに別 process へ移すのではなく、background local-AI task と PSS / RSS / native heap / Java heap を相関する診断を先に導入した。

その後の実機 report では、`LibraryOrganizationBatchWorker` が `main-active-background-ai` の owner である間に、約13分で native heap が約1.4 GiB、PSS が約1.2 GiB増加した一方、Java heap は数十 MiB程度に留まった。最終的に main process は Android 17 の `MemoryLimiter:AnonSwap` で終了した。

Library organization は書籍を直列処理しており、単純な並列 Worker 増殖ではない。`DefaultLibraryOrganizationSuggester` は provider-neutral `AiTextInference.generate()` を1回、validation repair が必要な場合は追加で1回呼び出す。main process の `LocalModelManager` は ADR-0079 に従って Engine を process-wide に再利用するため、連続した単発 generation で native allocation が process lifetime に残留する場合、main process 自体の memory baseline が上昇し続ける。

LiteRT-LM upstream には Android OpenCL GPU delegate で conversation ごとの memory が解放されず RSS が段階的に増える報告がある。ただし今回の実機 report には実際に選択されていた backend が含まれていないため、GPU/OpenCL 固有問題だったとは断定しない。今回の判断根拠は backend の推測ではなく、ADR-0161 が求めた実測条件として「active task 中の native/PSS 継続増加」と「小さい Java heap」が確認されたことである。

SMB vision inference では ADR-0159 により、native resource の完全解放を `Engine.close()` や GC だけへ依存せず、短寿命 app subprocess の終了を最終 reclamation boundary とする方式を既に採用している。単発 text generation にも同じ安全境界を適用できる。

一方、Android の `SharedPreferences` は複数 process 間の同期ストアとして扱えない。main process と inference subprocess が同じ `local_summary_models` preference を直接利用すると、model/backend/context 設定変更中の generation で stale な値を読むだけでなく、child 側の stage-duration 更新が main の新しい設定を巻き戻す危険がある。そのため process isolation と同時に設定の ownership も明示する必要がある。

## Decision

### 1. provider-neutral Local text generation を `:local_ai_text` process で実行する

Summary、Knowledge、Library organization が利用する application-scope Local `AiTextInference` は、`generate(prompt)` の実処理だけを `:core:ai-runtime` 所有の非公開 bound Service `LocalTextInferenceService` へ委譲する。

- Service は `android:exported="false"` とする。
- Service は `android:process=":local_ai_text"` で main process から分離する。
- `isolatedProcess` は利用しない。選択済み model artifact と app-private cache を同一 UID の app-private storage から読む必要があるためである。
- main process は `AiTextInference.selectedModel()` と `countTokens()` を従来どおり application-scope `LocalModelManager` へ委譲する。重い LiteRT-LM generation Engine の生成と conversation execution だけを subprocess へ移す。
- Chat の streaming / tool-capable conversation は `AiTextInference` の単発生成経路ではなく `LocalModelManager` の conversation API を直接利用するため、本 ADR では main process のまま維持する。

### 2. generation ごとに immutable execution snapshot を main process で確定する

main process は、その generation が remote request の直列化順序を取得した直後に、利用する設定を1つの immutable snapshot として確定し Binder へ渡す。呼び出し時点ではなく request mutex 取得後に snapshot を解決することで、先行 generation の待機中に user が設定を変更した場合、後続 generation が stale な snapshot を先取りしないようにする。

snapshot に含めるのは次の inference execution metadata に限定する。

- selected model id
- 保存済み model artifact の revision metadata（少なくとも selected model を含む全既知 revision）
- backend
- speculative decoding enabled
- main process で解決済みの effective context token count
- model ごとの `PREPARING_MODEL` / `GENERATING_RESPONSE` 推定時間

prompt 自体も同じ request で渡すが、設定 snapshot と prompt/output は log、diagnostics、永続 task state へ保存しない。

user が generation 実行中に model/backend/context 設定を変更しても、実行中 generation は開始時 snapshot を最後まで利用する。待機中の次 generation は自分の request 順序を取得した後に main process から新しい snapshot を取得する。これにより token counting と generation の設定境界を generation 単位で明確にする。

### 3. child process は main process の SharedPreferences を直接利用しない

`:local_ai_text` 内の `LocalModelManager` には、`local_summary_models` と `local_context_benchmarks` を child 専用 preference 名へリダイレクトする process-local `ContextWrapper` を渡す。

- child は main process の `local_summary_models` / `local_context_benchmarks` を読み書きしない。
- request ごとに Binder で受け取った snapshot を child 専用 preference へ同期してから generation を開始する。
- `AUTO` context の benchmark 判定結果は main process で effective token count まで解決し、child では対応する固定 context mode として適用する。child が stale な benchmark preference を再評価しないようにする。
- 保存済み model artifact の全既知 revision metadata を snapshot から child 専用 preference へ与える。`LocalModelManager` の起動時 cleanup が revision 不明の別 model を誤って削除しない状態で、既存の artifact validation 規則を再利用する。
- child で更新された stage-duration 値だけは response metadata として main へ返し、main process 自身が main preference へ保存する。child が main preference editor を保持することはない。
- child 専用 preference は正常な Service 終了時に削除する。異常終了で残っても次 request snapshot で全内容を上書きし、main process の設定正本としては利用しない。

### 4. main process は durable task ownership を維持する

process isolation は inference execution boundary の変更だけとし、feature ownership を移動しない。

- WorkManager Worker
- durable queue / claim / retry
- DB read/write と transaction
- Summary / Knowledge / Library の feature policy
- `LocalAiBackgroundTaskGate` の priority / permit
- foreground notification
- result validation と persistence

これらは従来どおり main process と owning feature が保持する。child process は provider-neutral な prompt と execution snapshot を受け取り text generation を返すだけで、DB、Repository、WorkManager graph を構築しない。

### 5. subprocess は最大2 generation で必ず世代交代する

`:local_ai_text` process 内では child 専用設定を利用する `LocalModelManager.shared(...)` を再利用するが、1 process generation あたりの完了済み generation は最大2回に制限する。

2回目の成功または失敗 response を返した後、main process は Service を unbind し、Service は `LocalModelManager.close()` を試みたうえで process 自体を終了する。次の generation は前 process の Binder death を待ってから新しい process を bind する。

1回だけ generation して後続 request がない場合も native Engine を無期限に保持しないよう、30秒 idle で main process が Service を unbind して process を終了する。validation repair のような直後の2回目 generation は同じ process を再利用できる。

process death を最終 reclamation boundary とし、LiteRT-LM / backend が process 内 allocator や driver allocation を保持しても main process の memory baseline へ累積させない。

### 6. Binder death は1回だけ新 process で再試行する

OS memory pressure、service process crash、明示的 recycle と request が競合した場合、main process adapter は `DeadObjectException` / `RemoteException` を検出して active session を破棄し、新しい process で1回だけ generation を再試行する。

無制限 retry は行わない。durable task 全体の retry policy は Summary / Knowledge / Library の owning feature に残す。

呼び出し coroutine が cancellation された場合は active Service を unbind し、推論中 child process も終了させる。キャンセル済み generation を background で保持し続けない。

### 7. provider-neutral progress を child process から転送する

`LocalModelManager.inferenceProgress` の `PREPARING_MODEL` / `GENERATING_RESPONSE`、model name、推定 stage duration は Messenger 経由で main process の `AiTextInference.progress` へ写像する。

prompt、model output、article/book title、URL、file path 等を progress message、diagnostics、log へ追加しない。

### 8. IPC payload を小さい text と primitive metadata に限定する

Binder へ渡すのは prompt、execution snapshot、生成結果、progress metadata、成功/失敗状態、stage-duration metadata だけとする。

- prompt / output はそれぞれ最大128 Ki charactersに制限する。現行 Local model の prompt budget より十分大きく、Binder transaction budget に対して余裕を残す。
- model file bytes、tokenizer artifact、画像、DB entity 等を Binder で転送しない。
- error message は最大500 charactersへ制限する。
- prompt / output を log、crash report、SharedPreferences、repository fixture へ保存しない。

### 9. secondary Application runtime は起動しない

Android は `:local_ai_text` process にも application class を生成するが、既存の `shouldInitializeMainProcessRuntime()` は package name と完全一致する main process だけを許可する。

そのため Activity tracking、startup crash diagnostics、main-process memory monitor、widget observer、backfill scheduling、WorkManager application graph 等を `:local_ai_text` では初期化しない。この契約を app unit test で固定する。

## Consequences

### Positive

- Library organization の連続 Local generation で native memory が増加しても main process の PSS/native baseline へ累積しない。
- Summary / Knowledge の Local 単発 generation も同じ execution boundary を共有し、同種の native retention に対して予防的に保護される。
- process death が allocator / driver / LiteRT-LM internal cache を含む最終的な resource reclamation boundary になる。
- provider-neutral `AiTextInference`、feature-owned durable queue、DB ownership は変更しない。
- model/backend/context 設定は main process が正本のままで、child process から stale `SharedPreferences` を書き戻さない。
- Chat の対話・streaming 経路には process IPC を導入しない。
- prompt と output を diagnostics へ持ち込まず、public repository / shareable crash report の privacy boundary を維持する。

### Negative

- 最大2 generation ごとに model Engine を再初期化するため、process-wide Engine reuse より latency と消費電力が増える。
- Binder / Service lifecycle、execution snapshot、process-local preference bridge、process death retry の実装複雑性が増える。
- `countTokens()` は synchronous contract のため main process tokenizer を引き続き利用する。今回観測した generation Engine の native retention 対策とは分離する。
- `LocalModelManager` の既存 preference schema を child snapshot bridge でも利用するため、同 module 内で schema key の変更時は bridge とテストを同期する必要がある。
- backend 固有の upstream leak が修正されても、実端末で連続 generation の memory baseline が安定することを確認するまでは process isolation を維持する。

## Verification

- `ProcessIsolatedLocalAiTextInferenceTest`
  - 2 generation で recycle する batch policy
  - batch size validation
  - IPC text upper bound
  - child preference 名が main preference と分離されていること
  - main で解決済み effective context token count を固定 mode へ変換すること
- `ApplicationProcessPolicyTest`
  - `:local_ai_text` process で main Application runtime を初期化しない
- `ArchitectureCleanupSourceTest`
  - app composition が process-isolated Local `AiTextInference` adapter を構築すること
- existing Local `AiTextInference` model/progress mapping tests
- Summary / Knowledge / Library unit tests
- app unit tests
- release lint
- `verifyArchitecture`
- public repository verifier

実機 Android 17 では Library organization の長時間連続処理を再実行し、main process の `main-active-background-ai` sample で native heap / PSS が従来のように単調増加しないことを確認する。`:local_ai_text` process が generation 世代ごとに終了することも process diagnostics で確認する。

設定変更の実機確認では、generation 実行中に model/backend/context 設定を変更しても、実行中 generation が開始時 snapshot で完了し、待機中の次 generation が request 順序を取得した後に新設定を snapshot 化して利用することを確認する。child process 終了後に main process の model/backend/context preference が旧値へ戻らないことも確認する。

## Public repository review

この ADR には実ユーザーの prompt、書籍名、URL、file path、account、credential、exact process id、private profiling artifact を記録しない。実機診断値は設計判断に必要な丸めた memory 増加量と task class 名だけを記載する。実装も prompt / output を log または shareable diagnostics へ保存しない。

child 専用 preference に保存するのは model id/revision、backend、speculative decoding flag、固定 context mode、stage-duration metadata のみであり、prompt/output や feature content は含めない。

## References

- Android Developers: Android 17 behavior changes for all apps — App memory limits
  - https://developer.android.com/about/versions/17/behavior-changes-all
- Android Developers: `SharedPreferences`
  - https://developer.android.com/reference/android/content/SharedPreferences
- LiteRT-LM issue #2699: Android OpenCL GPU delegate: per-conversation memory not reclaimed across create→send→delete — RSS ratchets to OOM
  - https://github.com/google-ai-edge/LiteRT-LM/issues/2699