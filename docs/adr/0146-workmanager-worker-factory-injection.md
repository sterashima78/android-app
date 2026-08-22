# ADR-0146: WorkManager Worker の依存解決を WorkerFactory constructor injection へ移す

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0101](0101-feature-route-and-background-runtime-ownership.md), [ADR-0120](0120-bookmark-application-service-and-framework-provider-boundary.md), [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0139](0139-app-entrypoint-and-worker-runtime-baseline.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md)

## Context

ADR-0139 では、Android / WorkManager が constructor を所有する framework entry point に限り、`Application` から narrow Provider contract を取得して application-scope graph へ接続することを許可した。この例外は `config/architecture/framework-provider-lookups.tsv` で監査していた。

P1/P2 の composition cleanup 後にこの例外を再評価すると、Android が直接生成する Activity / Service / AppWidgetProvider と WorkManager Worker では事情が異なる。

- Activity / Service / AppWidgetProvider は Android framework が直接生成し、通常の constructor injection を差し込む application-level factory を持たないため、narrow Provider lookup を残す合理性がある。
- WorkManager は `Configuration.Builder.setWorkerFactory()` を提供しており、Worker の生成時に application-scope dependency を明示的に constructor injection できる。
- Backup / Knowledge / Mail / Summary Worker がそれぞれ `Application as? ...Provider` を行っており、Provider interface と allowlist entry が feature ごとに増えていた。
- Worker 自身が Application を service locator として扱う形は、依存が constructor に現れず、通常の Data class より dependency boundary が読みにくい。

一方、既に enqueue 済みの WorkRequest は Worker class name を永続化しているため、Worker class の rename / FQCN 移動は行わない必要がある。

## Decision

### 1. WorkManager Worker は Application Provider lookup を使用しない

WorkManager が生成する Worker の application-scope dependency は `WorkerFactory` から constructor injection する。

対象は次の Worker runtime である。

- Backup: `GoogleDriveBackupWorker`
- Knowledge: `KnowledgeBuildWorker`
- Mail: `MailSyncWorker`
- Summary: `SummaryWorker`, `SummaryContentFetchWorker`, `BookmarkAutoEnrichmentBackfillWorker`

Worker class の FQCN は変更しない。既存 WorkRequest が保持する class name を feature-owned WorkerFactory が同じ Worker implementation へ解決する。

### 2. WorkerFactory は owning feature data module が所有する

各 feature の data module が、その feature の Worker class と constructor dependency の対応を知る WorkerFactory を公開する。

- `BackupWorkerFactory`
- `KnowledgeWorkerFactory`
- `MailWorkerFactory`
- `SummaryWorkerFactory`

WorkerFactory は Repository / Application Service の concrete implementation を構築しない。app composition から渡された domain contract または capability provider lambda を Worker constructor へ接続する。

`:app` は `AppWorkerFactory.kt` で `DelegatingWorkerFactory` を構成し、feature WorkerFactory を application-scope `AppContainer` graph へ接続する。`:app` が feature Worker business logic を所有したり、Worker class 自体を再実装したりしない。

### 3. WorkManager は Application の custom Configuration を on-demand で利用する

`YomitoriApplication` は `Configuration.Provider` を実装し、app worker factory を `Configuration.Builder.setWorkerFactory()` へ設定する。

WorkManager の default AndroidX Startup initializer は manifest merge rule で削除する。`WorkManager.getInstance(context)` が必要になった時点で Application の custom Configuration を利用して初期化する。

既存 scheduler/controller は引き続き Context 付き `WorkManager.getInstance(context)` を利用する。

### 4. WorkManager 用 Provider contract と allowlist entry を削除する

次の WorkManager 専用 Provider contract / lookup は廃止する。

- `BackupRepositoryProvider`
- `KnowledgeRepositoryProvider`
- `MailRepositoryProvider`
- `SummaryRuntimeDependenciesProvider`
- `BookmarkAutoEnrichmentBackfillProvider`

`framework-provider-lookups.tsv` から WorkManager Worker の entry を削除する。

Android が直接生成する次の entry point の narrow Provider は継続する。

- `MainActivityDependenciesProvider`
- `LanWebRepositoryProvider`
- Widget / Task widget repository Provider
- `DatabaseSchemaProvider`

`DatabaseSchemaProvider` は `YomitoriDatabase.create(Context)` が app の schema contribution 集約へ接続する core database capability であり、任意の feature dependency を取得する service locator としては扱わない。

### 5. architecture regression で Worker provider lookup の再導入を禁止する

`FrameworkProviderBoundaryTest` は既存 manifest と production lookup の完全一致に加え、WorkManager Worker source に `Application as? ...Provider` lookup が存在しないことを検査する。

同 test で次も固定する。

- `YomitoriApplication` が `Configuration.Provider` を実装すること
- WorkManager Configuration に application WorkerFactory を設定すること
- default `WorkManagerInitializer` を manifest で削除すること

## Consequences

### Positive

- Worker の依存が constructor に現れ、Application service locator への暗黙依存がなくなる。
- WorkManager 専用 Provider interface / Application implementation / allowlist entry を削減できる。
- application-scope Repository / UseCase instance は引き続き AppContainer graph を再利用し、Worker 内で parallel graph を構築しない。
- feature-owned WorkerFactory により Worker implementation ownership は owning feature data module に残る。
- Worker FQCN を維持するため、現在 enqueue 済みの WorkRequest class name と互換である。
- custom WorkManager configuration が必要なことを architecture test で固定できる。

### Negative

- injected dependency を持つ Worker は default reflective WorkerFactory だけでは生成できず、app WorkerFactory の登録が必須になる。
- feature に Worker を追加した際、constructor injection が必要なら owning feature WorkerFactory と app factory composition の両方を更新する必要がある。
- Application の custom WorkManager configuration と manifest initializer removal を対で維持する必要がある。

## Verification

- `FrameworkProviderBoundaryTest`: provider allowlist と production lookup の一致、Worker provider lookup 禁止、custom WorkManager configuration / initializer removal を確認する。
- `StartupSmokeTest`: `YomitoriApplication` 起動時に WorkManager を利用する backfill scheduling を含む startup path が成立することを確認する。
- existing Worker / Repository unit tests、`verifyArchitecture`、release lint、public repository verifier を PR CI で実行する。

## Documentation

- `docs/architecture/principles.md` の framework boundary を WorkerFactory constructor injection 前提へ更新する。
- `docs/architecture/testing.md` の framework provider boundary / architecture verification を更新する。
- `config/architecture/framework-provider-lookups.tsv` は Android 直生成 entry point の例外だけを保持する。

## Public repository review

本変更は Worker dependency wiring、architecture manifest/test、ADR/current architecture documentation を変更する。credential、token、OAuth secret、実ユーザー URL / メール、実蔵書・健康データ、database、backup、private artifact を追加しない。
