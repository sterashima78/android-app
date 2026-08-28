# ADR-0204: app composition の内部実装を責務 package へ分離する

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md), [ADR-0200](0200-app-composition-module-boundary.md), [ADR-0203](0203-feature-owned-provider-policy-adapters.md)

## Context

ADR-0200 により application-scope の concrete graph は `:app:composition` へ分離され、executable `:app` から feature Data implementation を compile-time に隔離できた。一方、module root package には `AppContainer` / `AppRouteDependencies` の facade と並んで、AI、content、Knowledge、Library、supporting feature、route composition など複数の内部 runtime group が配置されていた。

さらに `AppFeatureRuntimeDependencies` は Health Connect、Knowledge background task、Library / SMB / book reader / Google Books authorization を1 class に束ねていた。これらは application-scope lifetime を共有するため別 Gradle module を必要としないが、変更理由と依存は独立している。

ADR-0193 の「module boundary と file/package boundary を別に判断する」方針に従い、`:app:composition` の Gradle ownership と lifetime を維持したまま内部責務を package で局所化する。

## Decision

### 1. composition module root は narrow facade に限定する

`app/composition/src/main/java/dev/terashima/yomitorirss` 直下の production Kotlin file は次に限定する。

- `AppContainer.kt`
- `AppRouteDependencies.kt`
- `AppDatabaseSchema.kt`
- `AppWorkerFactory.kt`

composition-only runtime group や route builder を root package に追加しない。

### 2. concrete composition は変更理由を表す package に置く

現在の package ownership は次とする。

- `composition/ai`: local / cloud AI primitive composition
- `composition/background`: startup observer / scheduler wiring
- `composition/content`: Article / Bookmark / RSS / Reddit / YouTube の content graph
- `composition/crossfeature`: owner repository 構築後の cross-feature application service composition
- `composition/health`: Health Connect repository composition
- `composition/knowledge`: Knowledge persistence/generation と background task runtime composition
- `composition/library`: Library / SMB / book reader / Google Books authorization composition
- `composition/route`: content / supporting route dependency construction
- `composition/supporting`: independent/supporting repository と Android platform adapter composition
- `platform/authorization`: feature Data authorization manager と executable Activity Result host を接続する bridge

この一覧は closed set ではないが、`common` / `util` / generic `feature` のような catch-all package は追加しない。

### 3. `AppFeatureRuntimeDependencies` を廃止する

Health、Knowledge background task、Library runtime は次へ分割する。

- `AppHealthRuntimeDependencies`
- `AppKnowledgeTaskRuntimeDependencies`
- `AppLibraryRuntimeDependencies`

repository / scheduler / authorization の application-scope lifetime は維持し、`AppContainer` が各 group を lazy に構築する。Library runtime が AI inference provider を遅延取得する既存 semantics も維持する。

### 4. route composition builder を `composition/route` へ移す

`AppContentRouteDependencies` と `AppSupportingRouteDependencies` は `AppRouteDependencies` の内部 implementation であるため `composition/route` が所有する。

Library / Health / Workout の route dependency contract は同 package から executable `:app` へ公開できるが、constructor は引き続き `internal` とし、concrete graph construction は composition module 内に閉じる。

### 5. Gradle module と feature ownership は変更しない

- 新しい Gradle module は追加しない。
- feature business policy を composition package へ移さない。
- provider-specific Summary / Knowledge policy は ADR-0203 の owning feature Data ownership を維持する。
- process-wide HTTP transport、database、WorkerFactory、application-scope repository lifetime は変更しない。
- Activity Result host は executable `:app`、authorization bridge は `:app:composition/platform/authorization` という ADR-0196 / ADR-0200 の境界を維持する。

## Consequences

### Positive

- composition root を executable shell 向け API として読みやすく保てる。
- concrete graph の変更理由を source path から判別できる。
- Health / Knowledge task / Library が generic runtime group の同時変更対象にならない。
- Gradle module を増やさずにレビュー・テスト境界を局所化できる。
- root package への内部 runtime drift を architecture test で機械的に検出できる。

### Negative

- composition module 内の package と import が増える。
- route dependency contract の一部は `composition.route` package の public type となる。
- application graph 全体を追う場合は複数 package を横断する必要がある。

## Verification

- `AppCompositionPackageArchitectureTest` で composition root の file set を固定する。
- `AppCompositionPackageArchitectureTest` で責務 package の主要 implementation file を固定する。
- generic `AppFeatureRuntimeDependencies` が再導入されないことを固定する。
- existing `AppBoundaryOwnershipArchitectureTest` / `ArchitectureCleanupSourceTest` / `BackupSchedulingArchitectureTest` を新 path に追従させる。
- existing `verifyArchitecture`、unit tests、lint、public repository verifier を実行する。

## Documentation

`docs/architecture/code-organization.md` の `:app:composition` package tree を本 ADR に合わせて更新する。module ownership 自体は変わらないため `settings.gradle.kts` と feature module map の変更は行わない。

## Public repository review

本変更は source/package 構成、synthetic architecture test、current architecture documentation のみを変更する。credential、OAuth token、account identifier、private endpoint、実ユーザー URL / title / mail / health data、diagnostic artifact を追加しない。
