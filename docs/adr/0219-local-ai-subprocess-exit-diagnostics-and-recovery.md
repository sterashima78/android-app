# ADR-0219: Local AI subprocess の終了診断と Binder death recovery を揃える

- Status: Accepted
- Date: 2026-08-30
- Refines: [ADR-0161](0161-android17-main-process-memory-diagnostics.md), [ADR-0190](0190-isolate-local-text-inference-process.md), [ADR-0199](0199-library-organization-structured-tool-output.md)

## Context

ADR-0190 / ADR-0199 により、単発 Local text inference と structured text inference は `:local_ai_text` subprocess へ隔離し、native allocation が main process に累積しないよう process death を最終 reclamation boundary としている。

Android 17 実機では `dev.terashima.yomitorirss:local_ai_text` が `ApplicationExitInfo.REASON_LOW_MEMORY`、`importance=IMPORTANCE_SERVICE` で終了した。ただし既存 report には終了直前に通常 text / structured のどちらを実行していたか、model preparation / generation / recycle のどの phase だったか、subprocess の PSS / RSS / native heap が終了前に増加していたかが残らない。

既存 `LocalAiMemoryDiagnostics` は main process と vision process の診断を SharedPreferences に保存する。Android の SharedPreferences は複数 process 間の同期ストアとして保証されず、ADR-0190 では inference 設定について main / child の preference ownership を明確に分離している。`:local_ai_text` の死亡診断を同じ SharedPreferences へ追加すると、その境界を診断経路から再び曖昧にする。

また、通常 text adapter は `DeadObjectException` / `RemoteException` を検出して新しい subprocess で1回だけ generation を再試行する一方、structured adapter は Binder death を `IllegalStateException` に変換して終了していた。Library organization は feature 側に validation repair policy を持つため、transport recovery と model-output repair を混同しない形で subprocess death recovery を揃える必要がある。

Android 17 の app memory limiter は端末 / platform build により、従来観測していた `REASON_OTHER + MemoryLimiter:AnonSwap` に加えて API 37 の `REASON_MEMORY_LIMITER` として表現され得る。現行 compileSdk / targetSdk の更新をこの診断変更へ混在させず、API 37 documented reason value を runtime-compatible に扱う必要がある。

## Decision

### 1. `:local_ai_text` は `setProcessStateSummary()` へ安全な実行状態を登録する

API 30 以降では subprocess 自身が `ActivityManager.setProcessStateSummary()` を利用し、`ApplicationExitInfo.processStateSummary` から死亡後に取得できる短い状態を登録する。

状態には次だけを含める。

- mode: `text` / `structured`
- phase: `bound` / `request` / `prepare` / `generate` / `complete` / `recycle`
- backend
- effective context token count
- speculative decoding enabled

128 bytes以下の ASCII とし、model id、prompt、model output、tool arguments、書名、著者、URL、file path、account identifier 等は含めない。

### 2. subprocess memory sample は PID 単位の app-private ring log に保存する

`:local_ai_text` process は10秒間隔と主要 phase 遷移時に次を採取する。

- timestamp / pid / process name
- mode / phase
- PSS / RSS
- native heap / Java heap
- backend / context token count / speculative decoding

保存先は app-private `files/diagnostics/local-ai-text-<pid>.log` とし、1 PID あたり最大72 sample、最大6ファイルに制限する。child process 起動時に同一 PID file を初期化し、PID reuse による古い sample の混入を避ける。

main process は `ApplicationExitInfo.pid` と終了 timestamp を使い、終了前10分の sample だけを共有可能 report に添付する。SharedPreferences は subprocess telemetry の transport として利用しない。

### 3. Android 17 memory limiter reason の新旧表現を両方診断対象にする

startup process-exit classification は以下を memory-related とする。

- `ApplicationExitInfo.REASON_LOW_MEMORY`
- API 37 `REASON_MEMORY_LIMITER` の documented value `17`
- description に `MemoryLimiter` を含む既存 `REASON_OTHER` 表現

現行 compileSdk をこの変更だけで更新しないため、`17` は Android 17 compatibility constant として保持する。将来 compileSdk が API 37 以上になった時点で framework constant への置換を検討する。

report には numeric value に加えて `reasonName` と `importanceName` を付け、`processStateSummary` が存在すれば `processState=` として出力する。

ADR-0161 の方針どおり、通常の cached `REASON_LOW_MEMORY` は診断対象外とする。一方 `REASON_MEMORY_LIMITER` と `MemoryLimiter` description は cached importance だけを理由に除外しない。

### 4. structured inference も Binder death を新 process で1回だけ再試行する

`ProcessIsolatedLocalAiStructuredTextInference` は request ごとに immutable execution snapshot を1回確定する。その後 Binder transport が `RemoteException` / `DeadObjectException` で失敗した場合だけ、新しい `LocalStructuredTextInferenceService` へ bind して同じ snapshot で1回だけ再試行する。

- model / backend / context 設定を retry 間で再 snapshot しない。
- service が正常に返した model / tool / validation error は transport retry しない。
- Library feature が所有する validation repair 1回とは別の recovery boundary とする。
- 無制限 retry は行わない。

これにより通常 text と structured text の subprocess-death semantics を揃える。

### 5. subprocess の importance は引き上げない

`BIND_IMPORTANT` や foreground service 化は行わない。

process isolation の目的は、native allocation が大きくなった Local AI process を必要に応じて OS が回収できる状態のまま、main process の memory baseline と lifecycle を保護することである。診断結果なしに subprocess を foreground 相当へ保護すると、memory pressure を main process や他 application へ転嫁する可能性がある。

## Consequences

### Positive

- 次回の subprocess death で通常 text / structured と phase を `ApplicationExitInfo` だけから判別できる。
- process が突然 kill されても、終了前の PSS / RSS / native heap / Java heap の推移を PID 単位で確認できる。
- diagnostics は multi-process SharedPreferences へ依存しない。
- Android 17 の memory limiter reason 表現の差を吸収できる。
- structured inference が transient Binder death から通常 text と同じ範囲で回復できる。
- prompt や user content を診断へ保存しない。

### Negative

- Local AI subprocess 実行中は10秒ごとに PSS / RSS 取得と小さい app-private file write が発生する。
- process state summary と ring log の phase update が Service lifecycle に追加される。
- API 37 reason value を compileSdk 36 から数値 compatibility constant として維持する必要がある。
- structured request は Binder death 時に最大2回 inference engine を初期化する可能性がある。

## Verification

- `LocalAiTextProcessDiagnosticsTest`
  - process state summary が128 bytes以内で user content field を持たない
  - PID / timestamp window filtering
- `StartupProcessExitClassificationTest`
  - Android 17 memory limiter reason `17`
  - cached memory limiter を診断対象に維持
  - symbolic reason / importance name
- `ProcessIsolatedLocalAiStructuredTextInferenceTest`
  - structured transport attempt budget が初回 + 1 retry で固定される
  - immutable snapshot を retry loop 前に確定し、`RemoteException` のみを transport retry とする source contract
- existing `StartupCrashStoreTest`
- existing `ProcessIsolatedLocalAiTextInferenceTest`
- `:core:ai-runtime:test`
- app unit tests
- release lint
- `verifyArchitecture`
- public repository verifier

実機 Android 17 では、次回 `:local_ai_text` の memory-related exit report に `processState=` と `localAiTextProcessDiagnostics:` が含まれることを確認する。memory sample が大きく増えていれば LiteRT-LM/native allocation の subprocess lifetime retention として追跡し、sample が小さいままなら device-wide memory pressure の可能性を優先する。

## Public repository review

永続化する diagnostics は process/runtime metadata のみとする。model id、prompt、model output、tool argument、book/article metadata、URL、file path、account data、credential は保存しない。共有 report は既存 `sanitizeCrashDetails()` を通す。

テストデータは synthetic PID / process name / memory value のみを利用する。

## References

- [ADR-0161](0161-android17-main-process-memory-diagnostics.md)
- [ADR-0190](0190-isolate-local-text-inference-process.md)
- [ADR-0199](0199-library-organization-structured-tool-output.md)
- Android Developers: `ActivityManager.setProcessStateSummary`
- Android Developers: `ApplicationExitInfo.getProcessStateSummary`
- Android Developers: Android 17 app memory limits
