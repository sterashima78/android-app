# Android Platform Baseline

このドキュメントは、現在の Android platform / SDK 前提を現在形で示す。

## Supported runtime

- アプリの最小対応 OS は Android 14 / API 34 とする。
- Android 13 / API 33 以下は新しい APK の対応対象外とする。
- すべての Android application/library module は `minSdk = 34` 以上を宣言する。
- OS バージョン分岐は、API 34 未満だけを支える目的では追加しない。
- ただし、DB migration、backup format、永続化された background job の class identity など、アプリ内データ・ジョブ互換性は OS 互換性とは別契約として維持する。

## Build SDK

- `compileSdk`: API 36
- `targetSdk`: API 36
- `minSdk`: API 34

`compileSdk` と `targetSdk` は stable API 36 を維持する。Android 17 / API 37 は現在 preview SDK なので、通常 CI に preview channel を要求せず、SDK の提供状態と target-specific behavior change を確認する別変更で採用を判断する。

app だけを API 34 にして library module に古い baseline を残すと、到達不能な compatibility branch が module 内へ残りやすい。このため current architecture では Android application/library module の `minSdk` を API 34 以上へ統一し、architecture verification で API 33 以下への退行を禁止する。

## Implementation guidance

API 34 以上で常に成立する framework 契約は直接表現する。代表例は次の通り。

- `POST_NOTIFICATIONS` の runtime permission は API 34 実行を前提として扱う。
- Widget の fill-in intent 用 `PendingIntent` は mutability を明示する。
- API 34 の foreground service type を利用する background worker では、API 34 未満へフォールバックする `SDK_INT` 分岐を持たない。
- API 34 から利用できる User-Initiated Data Transfer Job 等を current runtime の正規経路として利用する場合、API 33 以下専用 fallback を併設しない。
- Health Connect の capability は API level だけで利用可能性を決めない。`READ_HEALTH_DATA_HISTORY` など extension / provider 更新に依存する機能は `HealthConnectFeatures` で feature status を確認してから対応 API や権限を利用する。

新しい platform API を導入する際は API 34 以上で利用可能かを確認する。API 35/36 以降の差分を扱う `SDK_INT` / extension feature 判定は、実際に現在の対応範囲内で動作が変わるため維持してよい。

## Process exit and crash diagnostics

- uncaught exception は app entry point で起動時診断用の report として保持し、次回起動時に表示・コピーできる。
- Android が process を終了したケースは `ApplicationExitInfo` から未確認の終了理由を取得し、low-memory / MemoryLimiter 系の終了を起動時診断へ取り込む。
- local AI の画像推論では memory diagnostics を process-exit report に補足できるが、raw user content や画像 payload を診断へ保存しない。
- ユーザーが共有できる crash / process-exit report は最終 report 全体を保存前にサニタイズする。HTTP(S) URL の path/query、メールアドレス、credential-like value、Bearer token、Android private path 等を伏せ、version、commit、SDK、device、PSS/RSS 等の高レベル診断値は維持する。
- diagnostic sanitizer は共有を安全にするための defense-in-depth であり、高機密情報を exception message や diagnostic section に意図的に含めてよい根拠にはしない。

## CI

CI は stable Android API 36 platform を利用し、少なくとも次を検証する。

- architecture verification
- 全 Android application/library module の `minSdk >= 34`
- unit tests
- release lint
- main branch の signed release APK build

Android platform baseline は `gradle/table-ownership.gradle.kts` を併用する Architecture job で検査する。module を追加した場合も `minSdk` が 34 未満または未宣言なら CI を失敗させる。

Android 17 / API 37 を採用する際は、SDK の提供状態と behavior change を確認するテストを追加または更新してから行う。

## Sources

- [ADR-0126](../adr/0126-android-platform-baseline.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
- [ADR-0139](../adr/0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0145](../adr/0145-bound-vision-inference-memory-lifetime.md)
- [ADR-0149](../adr/0149-sanitize-shareable-crash-diagnostics.md)
