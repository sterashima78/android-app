# Testing Strategy

この文書は、変更の責務に応じてどの種類のテスト・architecture verification を適用するかを示す。個別機能の詳細な test case は各 feature の実装と ADR を参照する。

## Principle

テストは実装詳細の階層ではなく、守りたい契約・rule の所有場所に合わせる。

```text
Pure Domain rule
  -> unit test

UseCase / Application Service
  -> fake / stub port を使う unit test

Repository / Data adapter
  -> contract / integration test

Cross-context Query / Projection
  -> integration test

Module / source / table ownership
  -> architecture verification

Public repository safety
  -> high-confidence repository scan + semantic review

Android framework integration
  -> framework-aware test only where needed
```

## Domain tests

Domain test は Android、SQLite、HTTP 等から独立させる。

対象例:

- value / policy の invariant
- Content Classification の precedence
- Content Retention の cutoff / protected state
- task priority / state transition
- parser 後の純粋な transformation

Domain module に Android framework dependency を持ち込まないため、可能な限り通常の JVM unit test とする。

## Application Service / UseCase tests

複数 Repository / Context の orchestration は Application Service / UseCase 自体をテストする。

- port / Repository は fake を利用する
- call ordering が意味を持つ場合はその順序を検証する
- added / duplicate / skipped 等の集計規則を検証する
- failure / retry / partial result の意味が Domain/Application rule なら明示的にテストする

例として Bookmark import は Android document I/O や SQLite を UseCase へ持ち込まず、parse 済み entry と port を使う unit test で workflow を検証する。

## Repository and adapter tests

Data implementation は owner contract と persistence / remote semantics を検証する。

- Repository contract を満たすこと
- schema / migration の変更で既存 semantics を壊さないこと
- external API / parser adapter の edge case
- transaction が必要な operation の atomicity
- query port adapter が owner schema の意味を正しく公開すること
- 明示的 schema initializer を持つ場合、read method の副作用に依存せず必要 table を作成できること

他 Context の実 table schema がなくても成立すべき Repository は boundary test でそれを固定する。例えば Content Repository は RSS/Summary の table schema を直接必要としないことを検証する。

Library catalog の単体 lookup は空の test schema から catalog initializer を通じて必要 table を作成し、全 Library snapshot を構築せず対象書籍を取得できることを固定する。

mutable runtime state の transition は、その mutable state を所有する runtime/data module でテストする。presentation は公開された immutable state から表示を導出する部分だけを UI test で固定する。

## Projection tests

cross-context Projection を導入する場合は integration test を必須とする。

最低限次を確認する。

- read-only であること
- 宣言した owner table だけを参照していること
- schema change で破壊される場合に test が検出できること
- owner Repository/API の通常合成より Projection が必要な理由が ADR または計測で説明されていること

Integrated のように application composition で複数 feature の state を表示モデルへ写像する場合、pure projection と Android/Compose host を分離し、projection semantics は通常の JVM unit test で固定する。platform action は app adapter に残す。

## Architecture verification

### `verifyArchitecture`

Gradle dependency と production source の構造的 guardrail を検査する。

現在の主な対象:

- `core -> feature` 禁止
- `domain -> ui/data` 禁止
- `ui -> concrete data` 禁止
- circular Gradle dependency 禁止
- package / physical path consistency
- Domain から Android import 禁止
- root app shell への feature UI ownership drift
- Screen / `:app` Route での concrete dependency construction / import 禁止
- `MainActivity` での feature ViewModel ownership と concrete feature data import 禁止。app shell の `ui.AppViewModel` は feature ViewModel ではないため許可する
- `:app` production source での feature Worker 禁止。`CoroutineWorker` / `Worker` / `ListenableWorker` の import alias も検出対象
- feature data から app implementation への依存禁止

rule 自体の regression は `verifyArchitectureRuleTests` fixture で検証する。Route fixture に加え、MainActivity の concrete feature data / feature ViewModel、WorkManager Worker の import alias も違反として固定し、`ui.AppViewModel` は app shell state として許可する。

app composition の回帰は `AppCompositionSourceArchitectureTest` でも固定する。`app/src/main/.../feature` に production Kotlin source を置かないこと、historical `feature.navigation` package を再導入しないこと、`AppContainer` へ concrete feature data construction を戻さないこと、Integrated の pure projection が Android/Compose framework dependency を持たないことに加え、`AppFeatureContent`、`AppTopBarRoute`、`FeatureMessageEffects` が selected-tab dispatch より前に feature ViewModel を eager activation しないことを検査する。

Summary / Bookmark の global overlay は `AppNavigationSpecTest` で capability を提供するタブ集合を固定し、無関係なタブ表示だけで feature ViewModel が生成される範囲を増やさない。

current-version compatibility cleanup のように Android-heavy runtime の一度限り migration を削除する変更では、適切な integration fixture がない場合、production source に退役済み migration が戻らないことと current validity contract が残ることを source regression test で固定してよい。local model revision marker は `CurrentCompatibilityBaselineSourceTest` でこれを検査する。

共有可能な診断情報の sanitizer は pure transformation として人工的な URL / credential-like value / path を使う JVM unit test で検証し、実ユーザー情報を fixture に利用しない。

### Module map consistency

`settings.gradle.kts` を Gradle module 一覧の正本とし、`docs/architecture/module-map.md` の feature/layer 表は機械検証する。

```bash
python3 -m unittest scripts.test_verify_module_map
python3 scripts/verify_module_map.py
```

表は `feature-modules:start/end` marker 内だけを比較対象とし、feature の追加・削除、layer の追加・削除、stale row を Architecture CI で検出する。これにより日付付きの手動スナップショットとして扱わない。

### Architecture ownership init script

CI の Architecture job は `gradle/table-ownership.gradle.kts` を init script として併用する。

```bash
./gradlew --no-daemon -I gradle/table-ownership.gradle.kts verifyArchitecture
```

この init script は table ownership に加えて、現在次も検査する。

- owner data source の `CREATE TABLE IF NOT EXISTS` が `table-ownership.tsv` に登録されていること
- table creator と registered owner が一致すること
- owner 以外から durable table を参照していないこと
- `foreign-table-access-allowlist.tsv` の unknown / stale entry がないこと
- `:app` の `ui` composition 配下（`*Host.kt` 等を含む）が concrete feature data、database、WorkManager implementation を import / construct しないこと
- 全 Android application/library module が `minSdk = 34` 以上を宣言すること

`MailRouteHost.kt` 相当の concrete data import と API 29 module を fixture として持ち、guardrail 自体が退行しないことも固定する。

### Framework provider / WorkerFactory boundary

Application への Provider lookup は Android が直接生成し constructor injection を差し込めない entry point に限定し、`config/architecture/framework-provider-lookups.tsv` と production lookup の集合を `FrameworkProviderBoundaryTest` で一致させる。不要になった manifest entry は stale として削除する。

LAN Web Server の Android Service は `LanWebRepositoryProvider` を利用するが、Activity から Service implementation を直接参照しない。Activity は injected `LanWebServerController` contract を利用する。

WorkManager Worker は Provider lookup の例外に含めない。application-scope dependency は owning feature data module の `WorkerFactory` から constructor injection し、`:app` の `DelegatingWorkerFactory` が feature factory を `AppContainer` graph へ接続する。Worker 内に parallel database / Repository graph を作らず、`Application as? ...Provider` lookup も行わない。

`FrameworkProviderBoundaryTest` は次を固定する。

- Android 直生成 entry point の Provider lookup と監査 manifest が完全一致すること
- WorkManager Worker source に Provider cast がないこと
- `YomitoriApplication` が `Configuration.Provider` を実装すること
- application WorkManager configuration が custom WorkerFactory を登録すること
- default `WorkManagerInitializer` が manifest merge で削除されること

Application startup smoke test は `YomitoriApplication.onCreate()` から WorkManager を使う backfill schedule を通すため、custom WorkManager configuration の初期化経路も回帰検査する。

### ADR integrity

ADR 自身の identifier / link integrity は次で検査する。

```bash
python3 -m unittest scripts.test_verify_adr_integrity
python3 scripts/verify_adr_integrity.py
```

新しい ADR は現在存在する最大番号より大きい一意な番号を使用し、見出し・ファイル名・参照先を一致させる。

current architecture document の compatibility redirect を削除する場合は、repository 内参照を canonical `docs/architecture/` path へ移したことを意味的レビューで確認し、更新した ADR / current docs の local link は ADR integrity と通常の link review で検証する。

### Public repository verification

tracked source へ高確度な credential / private artifact を追加していないことを次で検査する。

```bash
python3 scripts/test_verify_public_repository.py
python3 scripts/verify_public_repository.py
```

verifier は private key、代表的 credential literal、keystore / OAuth secret file、tracked database、backup/export archive 等を検出する。matching secret value 自体は CI log へ出力しない。

実ユーザーのメールアドレス・URL・書籍情報・健康データ等は文字列形式だけでは private かを判定できないため、自動検査を通過しても PR 作成前の意味的な公開情報レビューを必須とする。

## CI baseline

Pull Request の品質 gate は `.github/workflows/check.yml` が所有し、次の4 checkを独立して並列実行する。

- `Public repository`: public repository verifier の unit test と tracked content scan
- `Architecture`: module map verifier、ADR integrity verifier、Gradle `verifyArchitecture`
- `Test`: `./gradlew --no-daemon test`
- `Lint`: `./gradlew --no-daemon :app:lintRelease`

Android の3検証は matrix で `fail-fast: false` とし、1つが失敗しても他の結果を取得する。従来の `quality` 集約 job は置かず、GitHub repository ruleset から4 checkを直接 required status checks とする。ADR integrity は path filter 付きの独立 workflow にせず、常時実行される `Architecture` check に含める。

```bash
python3 scripts/test_verify_public_repository.py
python3 scripts/verify_public_repository.py
python3 -m unittest scripts.test_verify_module_map scripts.test_verify_adr_integrity
python3 scripts/verify_module_map.py
python3 scripts/verify_adr_integrity.py
./gradlew --no-daemon -I gradle/table-ownership.gradle.kts verifyArchitecture
./gradlew --no-daemon test
./gradlew --no-daemon :app:lintRelease
```

`main` push と手動実行の signed release APK は `.github/workflows/build.yml` が所有する。PR gate を通過した commit を ruleset により `main` へ取り込む前提とし、main build では Architecture / Test / Lint を重複実行しない。repository scan は release keystore を runner へ復元する前に再実行し、その後 APK build / signature verification / artifact upload / `apk/main` commit status publication を行う。

ruleset 移行中は workflow 分離が先行し、direct push の技術的な防止が一時的に弱くなることを ADR-0192 で明示的に受け入れている。最終状態では `main` への Pull Request、4 required checks、force push / deletion / bypass の禁止を repository ruleset で enforcement する。

CI workflow が変更された場合は、この文書のコマンドを正本とせず workflow を優先して本記述を更新する。

Sources: [ADR-0038](../adr/0038-android-test-layers-and-e2e.md), [ADR-0093](../adr/0093-main-apk-build-run-status.md), [ADR-0136](../adr/0136-public-repository-content-verification.md), [ADR-0192](../adr/0192-split-pr-checks-and-main-apk-build.md).

## Choosing tests for a change

変更範囲に対し「すべての種類のテストを追加する」のではなく、変更した契約を守る最小で適切な層を選ぶ。

| Change | Expected validation |
| --- | --- |
| pure Domain rule | unit test |
| multi-port orchestration | UseCase/Application Service unit test |
| SQL / migration | Repository/integration + migration-related test |
| explicit schema initializer | empty/minimal schema integration test + architecture table registration |
| backup compatibility baseline | current snapshot round-trip + unsupported schema rejection |
| parser / external adapter | adapter/parser test |
| cross-context read optimization | Projection integration test |
| mutable runtime state ownership | owner data/runtime unit test + presentation derivation test where needed |
| app composition projection split | pure projection unit test + source architecture regression |
| active-tab ViewModel activation | navigation policy unit test + app composition source regression |
| app shell navigation package relocation | app navigation unit tests + source ownership regression + `verifyArchitecture` fixture |
| retired one-time runtime migration | current validity contract + source regression when integration fixture is impractical |
| shareable diagnostic sanitizer | pure unit test with synthetic sensitive-looking data |
| module/source ownership rule | architecture fixture + `verifyArchitecture` |
| module map update | module-map verifier unit test + consistency verification |
| WorkManager dependency injection | WorkerFactory boundary test + startup smoke + affected Worker behavior tests |
| new table ownership rule | table ownership manifest/fixture + verification |
| Android platform baseline | architecture fixture + all module `minSdk` verification |
| framework Provider exception | boundary manifest/test |
| public repository verifier | verifier unit test + repository scan + semantic review |
| ADR-only change | ADR integrity; functional test追加は原則不要 |
| architecture docs only | link/source review; code behavior test追加は原則不要 |

PR review では test の「数」ではなく、変更した responsibility と failure mode を適切な test boundary で固定しているかを確認する。

## Sources

- [`.github/workflows/build-apk.yml`](../../.github/workflows/build-apk.yml)
- [`scripts/verify_module_map.py`](../../scripts/verify_module_map.py)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0047](../adr/0047-feature-owned-database-schema-contributions.md)
- [ADR-0055](../adr/0055-adr-numbering-policy.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0126](../adr/0126-android-platform-baseline.md)
- [ADR-0136](../adr/0136-public-repository-content-verification.md)
- [ADR-0138](../adr/0138-database-v27-compatibility-baseline.md)
- [ADR-0139](../adr/0139-app-entrypoint-and-worker-runtime-baseline.md)
- [ADR-0144](../adr/0144-composition-runtime-groups-and-module-map-verification.md)
- [ADR-0146](../adr/0146-workmanager-worker-factory-injection.md)
- [ADR-0147](../adr/0147-active-tab-viewmodel-activation.md)
- [ADR-0148](../adr/0148-retire-local-model-revision-marker-migration.md)
- [ADR-0149](../adr/0149-sanitize-shareable-crash-diagnostics.md)
- [ADR-0150](../adr/0150-app-shell-navigation-ui-ownership.md)
- [ADR-0151](../adr/0151-retire-current-architecture-compatibility-redirects.md)
