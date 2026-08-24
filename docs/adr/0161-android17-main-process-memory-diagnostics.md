# ADR-0161: Android 17 の main-process memory limit を実行元と相関できる診断にする

- Status: Accepted
- Date: 2026-08-24
- Refines: [ADR-0079](0079-process-wide-local-ai-inference-sessions.md), [ADR-0145](0145-bound-vision-inference-memory-lifetime.md), [ADR-0149](0149-sanitize-shareable-crash-diagnostics.md), [ADR-0159](0159-isolate-smb-vision-inference-process.md), [ADR-0160](0160-worker-runtime-and-android-17-baseline-cleanup.md)

## Context

Android 17 / API 37 では app ごとの memory limit が導入され、制限で終了された process は `ApplicationExitInfo` の reason が `REASON_OTHER`、description が `MemoryLimiter:AnonSwap` として観測される。

SMB の vision inference は ADR-0159 により `:local_ai_vision` process へ隔離し、vision engine の初期化・推論・close 前後を pid/process 単位で採取している。一方、Android 17 実機では main process `dev.terashima.yomitorirss` が foreground service 実行中に RSS 約 3.4 GiB まで増加して `MemoryLimiter:AnonSwap` で終了した。終了対象は `:local_ai_vision` ではないため、vision process の短寿命化だけでは説明できない。

main process では Summary、Knowledge、Library organization 等の background local-AI task が process-wide `LocalModelManager` を共有する。終了時の `importance=125` だけではどの Worker が permit を保持していたか、推論終了後も native / anonymous memory が残留していたかを判別できない。

Android 17 は `ProfilingManager` の `TRIGGER_TYPE_ANOMALY` により memory limit 到達時の profiling artifact を system-triggered に取得できる。現行 build baseline は ADR-0160 により `compileSdk = 36` / `targetSdk = 36` を維持しており、target 37 移行をこの診断変更へ混在させない。

## Decision

### 1. Background local-AI gate が diagnostics-only owner を公開する

`LocalAiBackgroundTaskGate` は permit を保持している task の diagnostics-only label を公開する。

- 公開 `withPermit` の引数には diagnostic label を追加せず、permit 取得時の stack trace から最初の app package 内の feature/app caller class を抽出する。
- `$` 以下の coroutine / lambda suffix を除外し、英数字と `._:-` だけに制限する。
- article title、URL、prompt、model output、file name 等の user data は label に含めない。
- priority、durable queue state、business behavior は label を参照しない。
- 待機 task に permit が移ると label も同じ critical section で切り替え、idle では `null` に戻す。

### 2. main process は local-AI 実行中と終了後5分だけ memory を定期採取する

main process の Application runtime に `AppLocalAiMemoryMonitor` を置き、10秒間隔で次を採取する。

- `Debug.getPss()`
- `/proc/self/status` の `VmRSS`
- `Debug.getNativeHeapAllocatedSize()`
- Java heap 使用量
- pid / process name
- background local-AI diagnostic label

AI permit 保持中は `main-active-background-ai`、permit 解放後は直前 task label を保持して最大5分だけ `main-retained-after-background-ai` として採取する。

これにより、次回 MemoryLimiter kill で「特定 task 実行中に増え続けた」「task 終了後も RSS/native memory が下がらなかった」を区別できる。常時 sampler にはせず、local-AI と無関係な通常利用時の overhead を避ける。

既存の vision memory sample と main-process sample は同じ report store に統合し、process exit timestamp より前かつ同一 pid/process の行だけを crash report に時系列で添付する。旧 `recent_vision_memory_samples` は読み取り互換だけ維持し、新規書き込みは統合 key へ行う。

### 3. Android 17 では anomaly profiling trigger を登録する

API 37 runtime でのみ `ProfilingManager` の anomaly trigger を登録する。

現行 compileSdk 36 には `ProfilingTrigger` / `addProfilingTriggers` は存在するが `TRIGGER_TYPE_ANOMALY` constant は API 37 追加であるため、API 37 の documented constant value `8` を SDK 37 guard の内側だけで使用する。API 37 framework 型を参照する実装も別 object に隔離し、API 34-36 runtime ではロードしない。これにより compileSdk / targetSdk を変更せず、実機 Android 17 の診断能力だけを利用する。

system-generated profiling result は app private `files/profiling` 以下に残し、自動 upload や repository への保存は行わない。MemoryLimiter exit report には終了前10分以内の artifact file name を最大3件だけ記録し、heap dump 内容や private path は共有テキストへ含めない。

### 4. text inference の process isolation は診断結果を根拠に別判断する

今回の変更だけを根拠に Summary / Knowledge 等の text inference を別 process へ移さない。

次回の実機 report で以下を確認してから、ADR-0159 と同様の短寿命 process を text inference に適用するか判断する。

- active task 中に PSS/RSS/native heap が task 単位で単調増加するか
- permit 解放後5分でも RSS/native memory が高止まりするか
- anomaly profiling の Java heap dump が managed heap leak を示すか
- Java heap が小さいまま RSS が大きい場合、LiteRT-LM backend/native allocation の process lifetime retention と整合するか

推論 engine の強制 release や task ごとの process recycle は性能コストが大きいため、diagnostics で原因を切り分けた後の変更とする。

## Consequences

### Positive

- main process の MemoryLimiter kill を background AI 実行元と相関できる。
- Java heap / native heap / RSS / PSS を同じ時系列で比較できる。
- AI task 終了後の memory retention を5分追跡できる。
- Android 17 の system anomaly heap dump を compileSdk/targetSdk 変更なしで取得できる。
- shareable crash report に user content や heap dump 本体を含めない。

### Negative

- background AI 実行中とその後5分は10秒ごとに `Debug.getPss()` と SharedPreferences commit を行うため、小さな診断 overhead がある。
- API 37 constant を compileSdk 36 から数値で参照するため、Android 17 API documentation との一致をレビューで維持する必要がある。
- Java heap dump だけでは GPU/OpenCL/native runtime retention を直接説明できない場合がある。
- 本変更は原因特定を改善するもので、text inference の native memory retention 自体を直ちに解消するものではない。

## Verification

- `LocalAiBackgroundTaskGateTest`: permit 保持中の label、待機 task への切り替え、idle clear
- `LocalAiMemoryDiagnosticsTest`: main-process sample field、sanitization、pid/process/time filtering、時系列化
- `AppLocalAiMemoryMonitorTest`: active / retained / expiration window
- `Android17MemoryAnomalyProfilerTest`: exit 前の安全な artifact name のみ抽出
- app unit test
- core background / AI runtime unit test
- release lint
- `verifyArchitecture`
- public repository verifier

実機 Android 17 では次回 `MemoryLimiter:AnonSwap` report に `main-active-background-ai` または `main-retained-after-background-ai` が含まれることと、system が artifact を生成した場合に `profilingArtifacts=` が付くことを確認する。

## Documentation

- ADR index に本 ADR を追加する。
- Android 17 memory limit の診断方法として本 ADR を current platform documentation の根拠にする。

## Public repository review

診断 label は実装 class name のみを許可し、prompt、URL、article/book title、file path、account、health data、credential 等を保存しない。profiling artifact 本体は app private storage にのみ置き、shareable report には安全な file name だけを追加する。test data は synthetic name のみを使う。

## References

- Android Developers: Android 17 behavior changes for all apps — App memory limits
- Android Developers: Android 17 features and APIs — Profiling trigger for app anomalies
- Android Developers: `ProfilingManager`
- Android Developers: `ProfilingTrigger.TRIGGER_TYPE_ANOMALY`
- Android Developers: Retrieve and analyze profiling data
