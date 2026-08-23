# ADR-0160: Worker runtime ownership と Android 17 baseline を現行実装へ収束させる

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0059](0059-current-version-compatibility-baseline.md), [ADR-0060](0060-converge-to-current-persisted-data-formats.md), [ADR-0126](0126-android-platform-baseline.md), [ADR-0146](0146-workmanager-worker-factory-injection.md), [ADR-0152](0152-library-route-and-route-runtime-ownership-cleanup.md)

## Context

ADR-0146 では WorkManager Worker の application-scope dependency を owning feature の `WorkerFactory` から constructor injection し、Worker 自身が Application provider lookup や parallel database / Repository graph を構築しない方針を採用した。

その後の current main を再レビューすると、Backup / Knowledge / Mail / Summary の一部では WorkerFactory 化が進んでいた一方、次の過去実装が残っていた。

- Library の SMB 表紙先読み、SMB 書誌正規化、蔵書 AI 整理 Worker が `YomitoriDatabase.create()` や concrete Repository / Scheduler を Worker 内で構築していた。
- Summary の inference / content fetch / task log cleanup Worker が application graph と別の `YomitoriDatabase` を開いていた。
- `UnreadWidgetRefreshWorker` が `:feature:widget:ui` に置かれ、helper 経由で Application の `WidgetRepositoryProvider` を参照していた。
- Worker の provider cast だけを検査する source regression では、helper を経由した provider lookup や Worker 内の parallel graph construction を検出できなかった。
- SMB 表紙先読みには Wi-Fi 制約導入時の一度限りの `wifi_constraint_v1` migration flag が残っていた。
- `AppRouteDependencies` が Gmail data layer の authorization outcome を直接認識していた。

また `docs/architecture/platform.md` は Android 17 / API 37 を preview と記述していたが、Android 17 SDK は現在 API 37 として利用可能である。一方、このアプリは SMB と LAN Web Server によりローカルネットワーク通信を行う。Android 17 を target するアプリでは `ACCESS_LOCAL_NETWORK` の runtime permission が必要になるため、targetSdk 37 への移行は単なる version bump ではない。

このアプリは現在配布中の最新版を利用しているため、ADR-0059/0060 に従い、すでに役目を終えた一度限り migration を恒久的に維持する必要はない。

## Decision

### 1. Library / Summary / Widget Worker を application-scope graph へ統一する

Library、Summary、Widget の WorkManager Worker は owning feature の `WorkerFactory` から依存を constructor injection する。

- Library は `LibraryWorkerFactory` を持ち、application-scope `DatabaseConnection`、Library Repository、Scheduler、AI Suggester を再利用する。
- Summary は `SummaryWorkerFactory` から application-scope `YomitoriDatabase` を inference / content fetch / cleanup Worker へ渡す。
- Widget は data layer が `WidgetWorkerFactory` と `WorkManagerWidgetRefreshScheduler` を所有し、refresh Worker へ `WidgetRepository` を注入する。

Worker 自身は次を行わない。

- `YomitoriDatabase.create()` による parallel database graph の作成
- `DatabaseConnection(...)` の再構築
- application-scope concrete Repository / Scheduler の再構築
- Application Provider からの dependency lookup

既存 WorkRequest が永続化している Worker class name を壊さないため、既存 Worker の FQCN は変更しない。Widget refresh Worker は物理的には `:feature:widget:data` へ移すが package / FQCN は維持する。

### 2. Android が直接生成する Widget entry point だけ narrow Provider を利用する

`AppWidgetProvider` / `RemoteViewsService` は constructor injection を差し込めないため、既存の `WidgetRepositoryProvider` と新しい `WidgetRefreshSchedulerProvider` を監査済み framework boundary として利用できる。

WorkManager Worker はこの例外に含めない。Widget Provider は refresh implementation を直接知るのではなく `WidgetRefreshScheduler` capability を取得して enqueue する。

### 3. Worker architecture regression を強化する

`FrameworkProviderBoundaryTest` は provider allowlist の一致に加えて、次を固定する。

- feature Worker が `ui` / `domain` layer に置かれないこと
- Worker が `YomitoriDatabase.create()`、`DatabaseConnection(...)`、concrete Repository / WorkManager Scheduler construction、Repository provider helper を利用しないこと
- Worker の direct provider cast が存在しないこと

検査は `WorkerFactory` が `ListenableWorker` を import するだけで Worker 本体と誤認しないよう、Worker base class の継承宣言を基準に対象を識別する。

### 4. SMB 表紙先読みの Wi-Fi 制約 migration を終了する

現在配布中の最新版では Wi-Fi 制約付き WorkRequest への移行が完了していることを baseline とし、`wifi_constraint_v1` SharedPreferences flag と初回 `REPLACE` 分岐を削除する。

通常 enqueue は `APPEND_OR_REPLACE`、ユーザー操作等による明示的な reschedule だけ `REPLACE` とする。

### 5. authorization data type を Route wiring から分離する

Gmail / Google Books の data layer authorization outcome を route wiring の公開契約にしない。

Android Activity Result と feature data adapter の変換は app runtime composition で行い、`AppRouteDependencies` は app-owned authorization capability を受け渡すだけとする。

### 6. Android 17 は現行 runtime として扱うが targetSdk 37 は別変更とする

Android 17 / API 37 を preview とする記述は廃止する。Android 17 上での動作は現在のサポート対象として扱い、targetSdkVersion に依存せず適用される app memory limits 等を実端末検証対象に含める。

一方、本変更では current build baseline の `compileSdk = 36` / `targetSdk = 36` を維持する。

`targetSdk = 37` への移行は別の platform decision とし、少なくとも次を同じ変更で扱う。

- SMB / LAN Web Server 等のローカルネットワーク用途に対する `ACCESS_LOCAL_NETWORK` declaration / runtime permission UX
- permission 未付与時の明示的な状態と再要求導線
- SMB 接続、表紙先読み、LAN Web Server の Android 17 integration test / 実端末確認
- API 37 target-specific behavior changes と大画面 orientation / resizability contract の確認

この分離により、SDK の最新状態を正しく記録しつつ、ネットワーク権限を実装しないまま targetSdk だけを上げることを避ける。

## Consequences

### Positive

- UI と background entry point が同じ application-scope database / Repository graph を利用し、lifetime と ownership が一致する。
- Worker ごとの database helper 作成・close による競合や、将来の schema composition drift の余地を減らせる。
- Widget の WorkManager implementation が UI module から除去され、background ownership が data layer へ収束する。
- 一度限り migration と route/data type coupling を削減できる。
- architecture regression が今回見つかった逸脱パターンを再発時に検出できる。
- Android 17 の現在状態と target 37 移行に必要なローカルネットワーク権限を明示できる。

### Negative

- Library / Summary / Widget Worker は custom application WorkerFactory の登録を前提とし、default reflective construction では生成できない。
- application composition が Worker runtime dependency bundle を組み立てる責務を持つ。
- targetSdk 37 は本変更では採用しないため、API 37 target-specific behavior への opt-in は後続変更になる。

## Verification

- Library の既存 queue / scheduler / normalization / organization tests
- Summary の既存 Worker / queue tests
- Widget の existing UI / repository tests
- `FrameworkProviderBoundaryTest` の Worker ownership / graph reconstruction regression
- `StartupSmokeTest` による custom WorkManager configuration
- `verifyArchitecture`
- release lint
- public repository verifier

## Documentation

- `docs/architecture/platform.md` を Android 17 / API 37 の現在状態へ更新する。
- ADR index に本 ADR を追加する。
- current testing / module-map の既存 WorkerFactory / parallel graph 禁止方針は本変更後の実装と一致するため維持する。

## Public repository review

本変更は dependency wiring、Worker source、architecture test、current architecture documentation のみを変更する。credential、token、OAuth secret、実ユーザー URL / メール、SMB 接続情報、実蔵書・健康データ、database、backup、private artifact を追加しない。

## References

- Android Developers: Android 17 behavior changes for apps targeting API 37
- Android Developers: Android 17 behavior changes for all apps
- Android Developers: Set up the Android 17 SDK
