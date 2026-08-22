# ADR-0144: composition runtime group と module map 機械検証を導入する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0046](0046-automated-architecture-verification.md), [ADR-0062](0062-extract-integrated-ui-from-app.md), [ADR-0122](0122-current-architecture-documentation.md), [ADR-0139](0139-app-entrypoint-and-worker-runtime-baseline.md), [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md)

## Context

P1 cleanup 後、`:app` の Route ownership は整理されたが、application composition 自体には次の保守上の課題が残っていた。

- `AppContainer` が Content/Curation、AI、Task/Calendar/Mail、Knowledge、cross-feature service の concrete construction を1 class に集約しており、application scope の正しさとは別に変更局所性が低かった。
- `IntegratedRoute` が Compose host、複数 feature の state projection、補助 action 構築を同一ファイルで持ち、ADR-0062 が許容した cross-feature mapping と UI host の境界を読み取りにくかった。
- `docs/architecture/module-map.md` の feature module 表は `settings.gradle.kts` と手動同期しており、正本を宣言していても stale documentation を機械的に防げなかった。
- PR では unit test / lint / architecture verification を実行している一方、main の signed APK build 前に同じ unit test / lint を再実行する必要はない。main build は release packaging と release 前の repository / architecture safety check に集中させたい。

## Decision

### 1. AppContainer は application-scope facade とする

`AppContainer` の公開 contract と application-scope lifetime は維持する。一方、concrete graph construction は責務別の app-internal runtime group に分割する。

- `AppAiCoreRuntimeDependencies`: model manager、AI model settings、Summary の独立 AI primitive
- `AppContentRuntimeDependencies`: Content / Curation / RSS / Reddit / YouTube / widget の graph
- `AppSupportingRuntimeDependencies`: Asset、Task、Workout、Calendar、Mail、Backup、LAN Web Server 等の supporting graph
- `AppKnowledgeRuntimeDependencies`: Knowledge persistence / generation graph
- `AppCrossFeatureRuntimeDependencies`: Chat skill graph、bookmark enrichment backfill、Summary task queue、composite AI task queue
- 既存 `AppFeatureRuntimeDependencies`: Health、Library、Knowledge WorkManager 等の application-scope runtime を継続

`AppContainer` はこれらの instance を lazy に保持し、既存 caller へ contract を facade として公開する。

この分割は DI framework 導入ではない。repository / scheduler の lifetime を変更せず、Route から `AppContainer` を lookup する service locator も導入しない。

### 2. Integrated の projection と item action 構築を Compose host から分離する

`IntegratedRoute` は次に集中する。

- root ViewModel state の購読
- tab と Mail mailbox の composition
- refresh / mutation command の dispatch
- Android Intent 等の platform transition
- `IntegratedScreen` への接続

複数 feature state から `IntegratedItem` / original target へ写像する pure logic は `IntegratedProjection.kt` へ分離する。Reddit/Hatena 等の補助 item action 構築は `IntegratedItemActions.kt` へ分離する。

`IntegratedProjection.kt` は Android / Compose framework import を持たず、既存の `IntegratedRouteAdapterTest` で mapping semantics を継続検証する。

### 3. Module map は settings.gradle.kts と機械検証する

`settings.gradle.kts` を module list の唯一の正本とする方針は維持する。

`docs/architecture/module-map.md` の feature/layer 表に marker を設け、`scripts/verify_module_map.py` が次を比較する。

- feature の追加 / 削除
- layer の追加 / 削除
- stale row
- duplicate row

verifier 自身は `scripts/test_verify_module_map.py` で fixture test を持つ。PR の Architecture job では verifier test、実 repository consistency、既存 `verifyArchitecture` を連続実行する。

### 4. main APK build 前に unit test / lint を重複実行しない

PR quality gate で次を並列実行する方針を維持する。

- Architecture
- Unit test
- Release lint
- Public repository verification

main push の signed APK build では unit test / lint は再実行しない。release keystore 復元前に public repository verification を行い、module map consistency と architecture verification を確認してから signed APK を build / signature verify する。

この判断は direct push を品質保証経路として推奨するものではなく、PR quality gate を通した merge を通常経路とする前提で重複計算を避けるものとする。

## Consequences

### Positive

- AppContainer の公開 API と application scope を維持したまま concrete graph の変更範囲を局所化できる。
- feature data implementation の追加・差し替えで巨大な AppContainer import / constructor block を編集し続けずに済む。
- Integrated mapping は framework-free な pure code として読みやすく、既存 test の対象も明確になる。
- module 追加時の documentation 更新忘れを Architecture CI が検出する。
- main APK build で PR と同一の unit test / lint を重複実行しないため、release build の無駄な lead time を増やさない。

### Negative

- app composition 内の class 数は増える。
- runtime group は Bounded Context と1対1ではなく、application graph の構築順序・変更局所性のための grouping であるため、Domain ownership の資料として誤読しない必要がある。
- module-map 表自体は人間向けに残るため、module 変更時は table 更新が必要。ただし更新漏れは CI failure になる。
- PR を経由しない main push では unit test / lint が実行されない。通常の変更経路は PR とする。

## Verification

- `IntegratedRouteAdapterTest`: integrated projection / action semantics を継続確認する。
- `AppCompositionSourceArchitectureTest`: `AppContainer` へ feature data implementation import を戻さないこと、Integrated projection が Android / Compose に依存しないことを確認する。
- `scripts.test_verify_module_map`: parser、missing/stale/layer mismatch、duplicate row を確認する。
- `scripts/verify_module_map.py`: current `settings.gradle.kts` と module map が一致することを確認する。
- existing `verifyArchitecture`, unit tests, release lint, public repository verifier を PR CI で実行する。

## Documentation

- `docs/architecture/module-map.md` に AppContainer runtime grouping と module table verification を反映する。
- `docs/architecture/testing.md` に module-map verifier と PR/main CI の役割分担を反映する。

## Public repository review

本変更は application composition source、architecture verification script/test、architecture documentation を変更する。credential、token、OAuth secret、実ユーザー URL / メール、実蔵書・健康データ、database、backup、private artifact を追加しない。
