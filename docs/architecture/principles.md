# Architecture Principles

この文書は、Accepted ADR 群から現在有効なアーキテクチャ規則を抽出した実装ガイドである。判断理由や代替案は各 ADR を参照する。

## Layer responsibilities

基本の依存方向は次とする。

```text
Compose Screen / Route
        |
        v
ViewModel / UI state
        |
        v
UseCase / Domain service（必要な場合）
        |
        v
Repository contract
        ^
        |
Data implementation
        |
        v
Local / Remote / Android platform
```

- UI は SQLite、HTTP client、WorkManager 等の concrete implementation を直接操作しない。
- Domain は Android、DB、HTTP の実装型へ依存しない。
- Repository の単一メソッドを転送するだけの UseCase は作らない。
- 複数 Repository の orchestration、再試行、並列処理、複数画面から再利用される業務ルール等に UseCase / Application Service を利用する。
- consumer が Repository の一部の用途しか必要としない場合は、Reader / Writer / application capability 等の narrow interface を優先する。
- feature 固有 UI state は owning feature が所有し、`:app` は app shell、navigation、composition、platform wiring に限定する。

## Module ownership

- アプリケーション固有コードは `:feature:<name>:<layer>` を基本とする。
- `feature` は画面単位だけでなく、Article のような独立した共有概念の ownership namespace としても使う。
- `core` は database、network、design system、AI runtime 等の横断的技術 capability に限定する。
- `:core:data`、`:core:domain`、`:common`、`:util` のような責務の曖昧な集約先を作らない。
- module の公開 API は小さく保ち、Data source、DB entity、HTTP DTO 等は必要がない限り `internal` とする。
- 小さな責務を分けるだけのために Gradle module を増やさず、package / `internal` で十分なら同一 module に残す。

禁止または原則回避する依存は次とする。

```text
core   -> feature             禁止
domain -> ui / data           禁止
ui     -> concrete data       禁止
Gradle circular dependency    禁止
```

feature 間依存そのものは、ownership と layer rule に反しない限り許容する。

## Domain boundaries

Gradle module、Bounded Context、Aggregate は同一概念ではない。

```text
Gradle feature/module
  ownership / build boundary

Bounded Context
  ubiquitous language と model の境界

Aggregate
  transactional consistency と invariant の境界
```

module 名を Domain 名へ機械的に合わせるための rename は行わない。Domain model が安定してから module restructuring を判断する。

## Cross-context operations

他 Context の都合で低レベル CRUD を公開しない。目的を表す契約を owner が公開する。

- 単一 Context / Aggregate の command: owner の Domain API / Repository contract
- 複数 Context / Aggregate の command orchestration: Application Service
- 永続状態を所有せず複数 Aggregate の情報から domain rule を解決: Domain Service
- 大量の cross-context read で API 合成に実測上の問題がある場合: named read-only Projection

Application Service の呼び出し元は業務判定を再実装せず、framework entry point や composition root は入力変換・依存の組み立て・結果の presentation に限定する。

Projection は read-only とし、参照 Context/table を明示し、generic な `cross-feature` module を作らない。

## Persistence ownership

- durable table の直接 SELECT / INSERT / UPDATE / DELETE は owner data module が行う。
- 他 Context は owner の Domain API または named Query API を利用する。
- foreign table write は禁止する。
- cross-context の最適化 read は明示された read-only Projection に限定する。
- 同じ SQLite database を共有していることや foreign key の存在は共同 ownership の根拠にならない。
- feature schema は owner の `DatabaseSchemaContribution` / explicit initializer を正本とし、Repository の read method や `snapshot()` の副作用を schema 初期化として利用しない。
- durable table はすべて `config/architecture/table-ownership.tsv` に owner を登録する。
- 移行中の例外は `config/architecture/foreign-table-access-allowlist.tsv` に path、table、ADR に基づく理由を明示し、不要になったら削除する。現在の allowlist に例外 entry はない。

## Composition and framework boundaries

- `:app` は composition root として feature implementation を組み立てる。
- application scope で複数の adapter / route / framework entry point から利用する concrete runtime は `AppContainer` が一度だけ構築して lifetime を所有し、同じ instance / graph を再利用する。並行した repository / scheduler graph を route や Worker ごとに再構築しない。
- Screen、`:app` の Route、`:app` の `ui` composition（`*Host.kt` 等を含む）で concrete Repository、database connection、WorkManager dependency を生成・import しない。
- app shell navigation state（`AppSection` / `MainTab` / `AppViewModel`）は `app/.../ui` が所有し、`app/.../feature` に production source を置かない。
- app shell は選択中の navigation state を確定してから、その presentation に必要な feature ViewModel だけを取得する。inactive feature の ViewModel を global host の都合で eager activation しない。
- Activity-scoped ViewModel sharing を利用する場合でも、Summary / Bookmark overlay、TopBar、message bridge 等の共通 host は capability が必要なタブだけ mount / observe する。
- `MainActivity` は Android lifecycle、external Intent、OS permission、app-level navigation、crash diagnostics 等の platform entry point に限定し、feature ViewModel を所有しない。app shell の `ui.AppViewModel` は Activity-scoped state として所有してよい。
- `MainActivity` が feature runtime を操作する場合は `MainActivityDependencies` 等から渡された narrow contract を利用し、`feature.*.data.*` implementation を直接 import しない。
- Application / container の service locator lookup は通常の Route、Screen、ViewModel、Application Service、Data object では行わない。
- Android が直接生成する Activity、Service、AppWidgetProvider 等で constructor injection を差し込めない entry point に限り、監査済みの narrow Provider contract を利用できる。
- WorkManager Worker が application-scope dependency を必要とする場合は、owning feature data module の `WorkerFactory` から constructor injection する。Worker 自身は `Application as? ...Provider` lookup を行わない。
- feature-owned `WorkerFactory` は dependency の concrete implementation を構築せず、`:app` の application worker factory が `AppContainer` の既存 graph と接続する。
- framework entry point 用 Provider は既存 application scope graph への接続に限定し、任意の dependency を取得する service locator として拡張しない。
- `YomitoriApplication` implementation type への直接 cast は行わない。

LAN Web Server では `MainActivity` は notification permission と dialog presentation を所有する一方、起動・停止・状態取得は `LanWebServerController` 契約を利用する。mutable server state と concrete Android Service は `feature:web:data` が所有する。

Mail Worker は `MailWorkerFactory` から application scope の `MailRepository` を constructor injection される。Worker 内で database / Repository graph を別構築しない。

## Background runtime ownership

feature 固有の Worker、WorkerFactory、scheduler/controller、queue-state interpretation は owning feature の data/runtime 側に置く。`:app` には feature 固有 background business logic や compatibility Worker を置かず、application worker factory では feature factory と application-scope dependency graph の接続だけを行う。

現在の互換性基準は最新版アプリからの更新であり、過去の app package FQCN を参照する WorkManager request のための shim は維持しない。将来 Worker class を移動し互換性対応が必要になった場合は、対象期間と終了条件を ADR で明示する。依存注入方式だけを変更する場合も enqueue 済み request が参照する Worker FQCN は維持する。

## Compatibility baseline

- 更新互換性の基準は現在配布中の最新版から次版への更新とする。
- 一度限りの preference / artifact / schema compatibility migration は、現行形式への収束が確認できた後は runtime に恒久保持しない。
- local AI model artifact は catalog の expected artifact と exact revision marker の一致を現行 validity contract とし、marker のない旧 artifact を file size だけで current revision とみなさない。
- framework identity、application id、database file 名等、最新版から次版への継続性に必要な契約は別途 ADR で明示して維持する。

## Android platform baseline

- 全 Android application/library module は `minSdk = 34` 以上を宣言する。
- API 34 未満だけを支える `SDK_INT` fallback は持たない。
- API 35/36/37 や extension capability など、現在の supported runtime 内で実際に差がある判定は維持する。
- Android 17 / API 37 は現行の実行環境として扱う。現在の build baseline は `compileSdk = 36` / `targetSdk = 36` とし、`targetSdk = 37` は SMB / LAN Web Server の `ACCESS_LOCAL_NETWORK` runtime permission UX と integration test を含む独立した platform migration として行う。

## Architecture enforcement

機械的に検査できる規則はレビューだけに依存しない。

- Gradle dependency / source ownership: `verifyArchitecture`
- durable table ownership / created-table registration / app UI composition / Android platform baseline: `gradle/table-ownership.gradle.kts`
- durable table manifest: `config/architecture/table-ownership.tsv`
- transitional foreign access: `config/architecture/foreign-table-access-allowlist.tsv`
- Android 直生成 entry point の framework provider exception: `config/architecture/framework-provider-lookups.tsv`
- ADR identifier/link integrity: `scripts/verify_adr_integrity.py`
- public repository の高確度な credential / private artifact: `scripts/verify_public_repository.py`

`verifyArchitecture` は Screen と `:app` の `*Route.kt` に加え、`MainActivity` の feature ViewModel / concrete feature data drift、`:app` production source の feature Worker を検査する。MainActivity の feature ViewModel import に app-shell-specific allowlist は設けない。Worker 判定では `CoroutineWorker` / `Worker` / `ListenableWorker` の Kotlin import alias も同じ基底 class として扱う。

`FrameworkProviderBoundaryTest` は監査 manifest と production provider lookup の完全一致、WorkManager Worker での provider lookup 禁止、feature Worker の data-layer ownership、Worker source での parallel database / Repository graph 再構築禁止、`Configuration.Provider` / application WorkerFactory / default WorkManager initializer removal の組み合わせを固定する。

Architecture job の init script は `:app` の `ui` composition をファイル名に依存せず検査し、`MailRouteHost.kt` のような Host に concrete data wiring が移ることも防ぐ。同時に全 Android module の API 34 baseline と、owner schema で作成される durable table の manifest 登録を検査する。

App composition の source ownership と active-tab ViewModel activation は `AppCompositionSourceArchitectureTest` で補完し、`app/.../feature` production source / historical `feature.navigation` package の再導入と、selected-tab dispatch より前の feature ViewModel eager activation を検出する。

検査で表現しにくい ownership、命名、API 粒度、Route の orchestration 肥大化、実ユーザー情報かどうかの意味判定等はレビュー対象とする。再発しやすい構造的パターンが見つかった場合は、可能なら fixture と verification rule を追加する。

## Sources

- [ADR-0001](../adr/0001-layered-architecture.md)
- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0004](../adr/0004-concept-oriented-modules.md)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0047](../adr/0047-feature-owned-database-schema-contributions.md)
- [ADR-0059](../adr/0059-current-version-compatibility-baseline.md)
- [ADR-0060](../adr/0060-converge-to-current-persisted-data-formats.md)
- [ADR-0101](../adr/0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0103](../adr/0103-app-route-composition-and-navigation-spec.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0122](../adr/0122-current-architecture-documentation.md)
- [ADR-0125](../adr/0125-application-service-and-capability-segregation.md)
- [ADR-0126](../adr/0126-android-platform-baseline.md)
- [ADR-0136](../adr/0136-public-repository-content-verification.md)
- [ADR-0138](../adr/0138-database-v27-compatibility-baseline.md)
- [ADR-0139](../adr/0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0146](../adr/0146-workmanager-worker-factory-injection.md)
- [ADR-0147](../adr/0147-active-tab-viewmodel-activation.md)
- [ADR-0148](../adr/0148-retire-local-model-revision-marker-migration.md)
- [ADR-0150](../adr/0150-app-shell-navigation-ui-ownership.md)
- [ADR-0152](../adr/0152-library-route-and-route-runtime-ownership-cleanup.md)
- [ADR-0155](../adr/0155-application-scope-http-transport.md)
- [ADR-0159](../adr/0159-isolate-smb-vision-inference-process.md)
- [ADR-0160](../adr/0160-worker-runtime-and-android-17-baseline-cleanup.md)
