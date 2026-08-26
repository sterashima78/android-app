# Android Platform Baseline

このドキュメントは、現在の Android platform / SDK 前提を現在形で示す。

## Supported runtime

- アプリの最小対応 OS は Android 14 / API 34 とする。
- Android 13 / API 33 以下は新しい APK の対応対象外とする。
- Android 17 / API 37 は現行の実行環境としてサポート対象に含める。
- すべての Android application/library module は `minSdk = 34` 以上を宣言する。
- OS バージョン分岐は、API 34 未満だけを支える目的では追加しない。
- ただし、DB migration、backup format、永続化された background job の class identity など、アプリ内データ・ジョブ互換性は OS 互換性とは別契約として維持する。

## Build SDK

現在の build baseline は次の通り。

- `compileSdk`: API 36
- `targetSdk`: API 36
- `minSdk`: API 34

Android 17 / API 37 SDK は現在利用可能であり、preview SDK とは扱わない。ただし Mosaic は SMB と LAN Web Server によりローカルネットワーク通信を行うため、`targetSdk = 37` は単純な version bump として実施しない。Android 17 を target するアプリでは `ACCESS_LOCAL_NETWORK` runtime permission が必要になるため、permission UX と SMB / LAN Web Server の動作確認を含む独立した platform migration として採用する。

現時点では current build を API 36 に維持しつつ、Android 17 端末上の all-app behavior changes は現在の互換性対象として検証する。特に app memory limits は `targetSdkVersion` に関係なく Android 17 上で適用されるため、local AI / native memory の診断と process isolation の前提に含める。

app だけを API 34 にして library module に古い baseline を残すと、到達不能な compatibility branch が module 内へ残りやすい。このため current architecture では Android application/library module の `minSdk` を API 34 以上へ統一し、architecture verification で API 33 以下への退行を禁止する。

## Android 17 target migration

`targetSdk = 37` へ移行する変更では、少なくとも次を同じ PR で確認する。

- `ACCESS_LOCAL_NETWORK` permission を manifest へ宣言し、SMB / LAN Web Server を利用する直前に必要な runtime permission UX を提供する。
- permission 未付与・拒否時に、SMB 接続、表紙先読み、LAN Web Server が不明な通信失敗ではなく利用不可理由を表示する。
- SMB server discovery / connection、cover prefetch、LAN Web Server の integration / 実端末確認を行う。
- Android 17 target-specific behavior changes を確認する。
- 大画面では orientation / resizability / aspect ratio 制約が無視される契約を前提に、現在の portrait 指定に依存した UI が破綻しないことを確認する。

`compileSdk = 37` のみを先行採用する場合も、CI の SDK install、全 Android module の compileSdk、architecture document を同じ変更で更新する。

## Implementation guidance

API 34 以上で常に成立する framework 契約は直接表現する。代表例は次の通り。

- `POST_NOTIFICATIONS` の runtime permission は API 34 実行を前提として扱う。
- Widget の fill-in intent 用 `PendingIntent` は mutability を明示する。
- API 34 の foreground service type を利用する background worker では、API 34 未満へフォールバックする `SDK_INT` 分岐を持たない。
- API 34 から利用できる User-Initiated Data Transfer Job 等を current runtime の正規経路として利用する場合、API 33 以下専用 fallback を併設しない。
- Health Connect の capability は API level だけで利用可能性を決めない。`READ_HEALTH_DATA_HISTORY` など extension / provider 更新に依存する機能は `HealthConnectFeatures` で feature status を確認してから対応 API や権限を利用する。

新しい platform API を導入する際は API 34 以上で利用可能かを確認する。API 35/36/37 以降の差分を扱う `SDK_INT` / extension feature 判定は、実際に現在の対応範囲内で動作が変わるため維持してよい。

## App-wide authentication boundary

- アプリ全体のロックは既定で無効とし、設定画面から明示的に有効化する。
- ロック有効化時は `BIOMETRIC_STRONG` が登録済みであることを確認し、framework `BiometricPrompt` による認証成功後に設定を保存する。
- 通常の解除では `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` を許可し、生体認証が利用できない場合も端末 PIN / パターン / パスワードで回復可能にする。
- ロック設定の表示は `:feature:settings:ui` が所有し、認証 lifecycle と app shell の gate は `:app` が所有する。
- ロック中は feature content と startup crash diagnostics を描画せず、共有・widget Intent の処理も認証成功後まで遅延する。
- Activity が通常のバックグラウンドへ移動した後は次回表示時に再認証を要求する。configuration change と認証 prompt 自身による lifecycle 遷移では不要な再ロックを行わない。
- アプリ自身が `openWebContentInCustomTab` で Custom Tabs を起動した直後の `onStop` は、一時的な外部コンテンツ表示として一度だけ再ロック対象外にする。marker は process 内のみ、起動開始から 10 秒以内の one-shot とし、起動失敗・古い marker・消費後の stop は通常どおり再ロックする。
- Custom Tabs 遷移を含め、ロック有効時に Activity が非表示になる際は `FLAG_SECURE` を適用し、最近使ったアプリの preview 等へ直前の内容を残さない。
- 永続化するのはロック有効フラグだけとし、生体情報、認証済み状態、Custom Tabs の遷移 marker は保存しない。

## LAN Web Server boundary

LAN Web Server は `:feature:web:data` が所有し、read-only HTTP contract を維持する。単一 class に transport、Repository read、HTML rendering を集約せず、次の責務へ分ける。

- `LanWebServer`: socket transport、HTTP request parsing、LAN/client validation、token/cookie authentication、route dispatch、security response headers
- `LanWebReadModel`: owner Repository contract からの read-only page model 構築と RSS/Reddit presentation classification
- `LanWebRenderer`: typed page model からの HTML rendering と escaping

認証は service 起動単位の bootstrap / session token とする。bootstrap token は永続化せず、初回 URL の query parameter で一度だけ使用する。認証成功時は同じ同期処理で session token を生成して `HttpOnly; SameSite=Strict` Cookie に設定し、token を除去した URL へ redirect する。LAN IPv4 address が変化した場合は bootstrap token をローテーションし、新しい origin から再認証できるようにする。server 停止時は bootstrap / session token の双方を失効する。

HTTP は暗号化されないため、LAN Web は信頼できる LAN でのみ利用する。別 Context の database implementation を server へ直接注入せず、Domain Repository contract を利用する。

## Process boundaries and local AI

- 通常の UI、WorkManager、DB、widget、startup diagnostics は application の main process が所有する。
- WorkManager Worker の application-scope database / Repository / Scheduler は owning feature の WorkerFactory から constructor injection し、Worker 内で parallel graph を構築しない。
- SMB 書誌正規化の GPU vision inference は `:local_ai_vision` の非公開 bound Service に隔離し、main process は durable queue、foreground execution、DB transaction、local AI task priority を保持する。
- vision process へは大きな画像 bytes を Binder で渡さず、app-private cache の表紙 path と小さい metadata/prompt だけを渡す。Service 側でも canonical path と入力サイズを再検証する。
- vision Engine は専用 process 内で最大2冊だけ再利用し、バッチ終了時に Service を破棄して process 自体を終了する。次バッチは前 process の終了を確認してから起動する。
- secondary process でも `Application` は生成されるため、main-process 専用の Activity tracking、startup diagnostics、widget observer、backfill scheduling は process name が package name と一致する場合だけ開始する。
- この process isolation は LiteRT-LM の Android GPU/OpenCL memory retention 問題が upstream で解決し、実端末で連続推論時の memory baseline が安定するまでの安全境界とする。
- main process の background local-AI task は global task gate の permit owner を diagnostics-only class label として公開する。priority、queue state、feature behavior はこの label に依存しない。
- main process は background local-AI permit 保持中と permit 解放後5分に限り、10秒間隔で PSS、RSS、native heap、Java heap を採取する。終了後の retained sample は直前 task label と関連付け、engine/native memory が task 終了後も残るケースを判別できるようにする。

## WebView renderer lifecycle

- WebView renderer は Mosaic main process とは別の sandbox process で動作する。system が memory pressure により renderer を終了したこと自体を Mosaic main process の memory failure とみなさない。
- custom `WebViewClient` を持つ production WebView は `onRenderProcessGone` を実装し、終了済み renderer に結び付いた `WebView` を再利用しない。
- `RenderProcessGoneDetail.didCrash() == false` の system low-memory kill では、画面型 WebView は新しい instance へ切り替えて継続する。headless WebView adapter は現在の取得単位を retryable failure として終了する。
- `didCrash() == true` の renderer crash では、同じ remote page を無条件に即時再読込しない。X viewer / Web collector は安全な開始 URL からの明示 retry に戻し、headless adapter は失敗として呼び出し側へ返す。
- Mail HTML は network load を禁止した local document rendering なので、system kill では同じ document を新 instance で再描画し、renderer crash では表示失敗を示して画面再表示時の再生成に委ねる。
- renderer termination 後は通常 dispose 用の navigation / loading 操作を続行せず、参照を切り替えて `destroy` する。

## Process exit and crash diagnostics

- uncaught exception は app entry point で起動時診断用の report として保持し、次回起動時に表示・コピーできる。
- Android が process を終了したケースは `ApplicationExitInfo` から未確認の終了理由を取得し、low-memory / MemoryLimiter 系の終了を起動時診断へ取り込む。短寿命 vision process の正常終了で障害記録が押し出されないよう、固定件数ではなく Android が保持する履歴全体を確認する。
- process-exit report の障害候補は application package name と一致する main process、または `applicationPackageName:` で始まる Mosaic 所有 subprocess に限定する。WebView sandbox renderer 等の別 package process の low-memory exit は Mosaic の process-exit report として表示しない。
- Android 17 の app memory limits は targetSdk に関係なく実行環境の制約として扱い、app-owned process の `MemoryLimiter` を含む終了はこの診断経路で追跡する。
- Android 17 / API 37 では `ProfilingManager` の anomaly trigger を登録し、memory limit 到達時に system profiling artifact を app-private storage へ残せるようにする。現行 compileSdk 36 / targetSdk 36 は変更せず、API 37 runtime guard 内だけで anomaly trigger を有効にする。
- process-exit report は対象 exit の pid と process name を記録する。local AI memory diagnostics は同じ pid・process name かつ exit timestamp 以下のサンプルだけを補足し、別 process generation や終了後のサンプルを混在させない。
- MemoryLimiter exit 前10分以内に system profiling artifact が生成されている場合、共有 report には安全な artifact file name を最大3件だけ記録する。heap dump 本体、app-private path、heap 内容は report へコピーしない。
- local AI の診断には raw user content、画像 payload、表紙 path、prompt、AI 出力を保存しない。
- ユーザーが共有できる crash / process-exit report は最終 report 全体を保存前にサニタイズする。HTTP(S) URL は authority/path/query/fragment を伏せて scheme だけを残し、メールアドレス、credential-like value、Bearer token、Android private path 等も伏せる。version、commit、SDK、device、PSS/RSS、pid、process name 等の高レベル診断値は維持する。
- diagnostic sanitizer は共有を安全にするための defense-in-depth であり、高機密情報を exception message や diagnostic section に意図的に含めてよい根拠にはしない。

## CI

CI は現在の build baseline である Android API 36 platform を利用し、少なくとも次を検証する。

- architecture verification
- 全 Android application/library module の `minSdk >= 34`
- unit tests
- release lint
- main branch の signed release APK build

Android platform baseline は `gradle/table-ownership.gradle.kts` を併用する Architecture job で検査する。module を追加した場合も `minSdk` が 34 未満または未宣言なら CI を失敗させる。

API 37 を compile / target baseline に採用する際は、SDK install と behavior change の検証を追加または更新してから行う。

## Sources

- [ADR-0126](../adr/0126-android-platform-baseline.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
- [ADR-0139](../adr/0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0145](../adr/0145-bound-vision-inference-memory-lifetime.md)
- [ADR-0146](../adr/0146-workmanager-worker-factory-injection.md)
- [ADR-0149](../adr/0149-sanitize-shareable-crash-diagnostics.md)
- [ADR-0159](../adr/0159-isolate-smb-vision-inference-process.md)
- [ADR-0160](../adr/0160-worker-runtime-and-android-17-baseline-cleanup.md)
- [ADR-0161](../adr/0161-android17-main-process-memory-diagnostics.md)
- [ADR-0163](../adr/0163-webview-renderer-exit-recovery.md)
- [ADR-0166](../adr/0166-lan-web-and-route-composition-responsibility-split.md)
- [ADR-0169](../adr/0169-lan-web-bootstrap-session-authentication.md)
- [ADR-0187](../adr/0187-biometric-app-lock.md)
