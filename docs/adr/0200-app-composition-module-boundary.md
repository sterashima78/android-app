# ADR-0200: application composition を専用 Gradle module へ分離する

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md), [ADR-0155](0155-application-scope-http-transport.md), [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md), [ADR-0196](0196-app-boundary-ownership-cleanup.md)
- Refined by: [ADR-0203](0203-feature-owned-provider-policy-adapters.md)

## Context

`:app` の package / file 分割は ADR-0193 と ADR-0196 により整理され、app-only concern と feature/provider ownership の混在は減った。一方、Gradle dependency graph では `:app` がほぼすべての `:feature:*:data` に直接依存し続けていた。

application-scope graph の構築には concrete Data implementation が必要だが、Application / Activity、app-shell UI、Android permission / Activity Result、external Intent host が同じ compile classpath を共有する必要はない。source-level architecture test だけで concrete Data import を禁止すると、誤った dependency が Gradle 上は利用可能なままである。

ADR-0193 は小さな責務の分割だけを理由に Gradle module を増やさない方針を採用した。本件は file size の整理ではなく、executable shell から concrete Data implementation を compile-time に不可視化する dependency/build boundary として独立した価値があるため、別 module として扱う。

## Decision

### 1. `:app:composition` を application-scope composition boundary として追加する

`:app:composition` は次を所有する。

- `AppContainer`
- `App*RuntimeDependencies`
- `AppRouteDependencies` と内部 route composition group
- application DB schema contribution の集約
- application WorkerFactory
- application startup で開始する background observer / scheduler wiring
- provider client と feature-owned adapter の instance wiring

この module は新しい Domain / Bounded Context を表さない。application scope の high fan-in graph を隔離する build boundary である。feature-specific provider policy adapter の source ownership は ADR-0203 に従い owning feature Data とし、`:app:composition` は feature 固有 mapping / prompt / failure policy を実装しない。

### 2. `:app` から `:feature:*:data` への direct dependency を禁止する

`:app` は executable shell として次を所有する。

- `Application` / `Activity` entry point
- app-shell navigation / presentation
- Android permission / Activity Result
- external Intent routing
- security / diagnostics / platform host
- framework-generated component と narrow provider contract の接続

`:app` は feature Domain / UI contract と `:app:composition` の公開 facade/capability を利用できるが、`:feature:*:data` へ直接依存しない。

concrete feature Data implementation は `:app:composition` だけが application graph の構築目的で参照する。

### 3. composition module の公開面を限定する

module 越しに公開するのは executable shell が必要とする facade/capability に限定する。

- `AppContainer`
- `AppRouteDependencies`
- application DB schema
- application WorkerFactory creation

責務別 `App*RuntimeDependencies` は `internal` を維持し、Route / Screen / framework entry point へ generic runtime graph を公開しない。

### 4. Application startup wiring も composition boundary に移す

backup persistence observer、backup preference observer、widget refresh observer、bookmark enrichment backfill scheduler の concrete construction は `:app:composition` の `composition.background.AppBackgroundRuntime` に集約する。

`YomitoriApplication` は main process 判定と app-owned diagnostics を保持し、application graph の background runtime は `AppContainer.startBackgroundRuntime()` から開始する。これにより `YomitoriApplication` 自身から feature Data import を除去する。

### 5. app BuildConfig を composition module へ漏らさない

application User-Agent の version は `YomitoriApplication` が `BuildConfig.VERSION_NAME` を `AppContainer` constructor に値として渡す。`:app:composition` は application module の generated `BuildConfig` へ依存しない。

### 6. existing ownership と lifetime は変更しない

- feature business/data ownership は変更しない
- `AppContainer` の application-scope lifetime は維持する
- process-wide `HttpClient` は引き続き単一 instance を runtime group / WorkerFactory で共有する
- Worker FQCN、Application / Activity FQCN、database name/version は変更しない
- ADR-0193 の `entry` / `security` / `diagnostics` / `platform` / `ui` package 方針は維持する

## Consequences

### Positive

- executable `:app` から concrete feature Data implementation が compile classpath 上不可視になる。
- app shell / platform host が accidental に Data implementation を import する経路を dependency graph で遮断できる。
- high fan-in composition dependency が専用 module に集約され、`:app` の Gradle dependency list が ownership に近づく。
- composition runtime group の application-scope lifetime と既存 caller contract を維持したまま build boundary を強化できる。

### Negative

- Android library module が1つ増え、Gradle configuration/build graph はわずかに複雑になる。
- composition facade の型は module boundary を跨ぐため、必要な API の一部を `public` にする必要がある。
- concrete feature dependency の更新時は `:app:composition` の build dependency も確認する必要がある。

## Verification

- `settings.gradle.kts` が `:app:composition` を含むこと。
- `:app` の production dependency に `:feature:*:data` が存在しないこと。
- `:app` production source が `feature.*.data.*` を import しないこと。
- `:app:composition` が concrete feature Data dependency を所有すること。
- `AppContainer` / WorkerFactory / route composition の existing architecture regression test を新 module path に追従させること。
- backup startup observer の wiring が `composition.background.AppBackgroundRuntime` へ移っても ADR-0195 の persistence-triggered semantics を維持すること。
- Summary / Knowledge の provider-specific policy adapter が owning feature Data に残ることを ADR-0203 の verification で固定すること。
- application User-Agent が引き続き `BuildConfig.VERSION_NAME` を正本にすること。
- `verifyArchitecture`、unit tests、lint、public repository verifier を通すこと。

## Public repository review

本変更は Gradle dependency、application composition source、architecture test、current architecture documentation、ADR のみを変更する。公開対象外の認証情報、実ユーザー content、private endpoint、diagnostic artifact を追加しない。

## References

- [ADR-0003](0003-multi-module-architecture.md)
- [ADR-0136](0136-public-repository-content-verification.md)
- [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md)
- [ADR-0155](0155-application-scope-http-transport.md)
- [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md)
- [ADR-0195](0195-trigger-backup-from-persistence-commit-boundary.md)
- [ADR-0196](0196-app-boundary-ownership-cleanup.md)
- [ADR-0203](0203-feature-owned-provider-policy-adapters.md)
