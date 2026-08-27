# ADR-0150: app shell navigation state を app UI ownership へ収束する

- Status: Superseded
- Date: 2026-08-23
- Refines: [ADR-0116](0116-route-owned-root-viewmodel-wiring.md), [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md)
- Superseded by: [ADR-0202](0202-navigation-compose-root-routing.md)

## Context

ADR-0142 で feature 固有 Route / presentation を owning feature module へ移し、application composition adapter を `app/.../ui` へ集約した。一方、`AppSection`、`MainTab`、`AppViewModel` だけは歴史的な `app/.../feature/navigation` package に残し、source architecture test と MainActivity の ViewModel import rule に専用例外を設けていた。

この3型は Gradle feature module の概念ではなく、drawer / bottom bar / app-level selected tab / app-level message を表す app shell presentation state である。現在の Route / app composition ownership が安定したため、歴史的 package を維持する理由よりも `feature` namespace の意味を曖昧にするコストが大きくなった。

## Decision

### 1. app shell navigation state は `app/.../ui` が所有する

`AppSection`、`MainTab`、`AppUiState`、`AppViewModel` を `dev.terashima.yomitorirss.ui` package に置く。

これらは feature-owned UI state ではなく、application shell の navigation / presentation state として扱う。

### 2. `app/.../feature` の production source 例外を廃止する

`app/src/main/.../feature` に production Kotlin source を置かない。app-level platform adapter、cross-feature composition、navigation state は `app/.../ui` または app root の責務に応じた package へ置く。

source regression test は navigation の allowlist を持たず、`app/.../feature` に Kotlin production source が再導入された場合に失敗する。

### 3. MainActivity の feature ViewModel import 例外を廃止する

`AppViewModel` は feature namespace ではなくなるため、`verifyArchitecture` は MainActivity からの `dev.terashima.yomitorirss.feature.*ViewModel` import を例外なしで禁止する。

app shell の `ui.AppViewModel` は Activity が所有してよい。feature root ViewModel は引き続き Route composition が取得する。

### 4. state lifetime は変更しない

package / physical ownership の整理だけを行い、`AppViewModel` の Activity-scoped lifetime、`MainTab` の値、drawer / bottom bar semantics は変更しない。

## Consequences

### Positive

- `feature` namespace が Gradle feature ownership と一致しやすくなる。
- `app/.../feature` の唯一の歴史的例外と、そのための test / verifier allowlist を削除できる。
- app shell navigation state と `AppNavigationSpec` / app chrome の物理 ownership が揃う。
- MainActivity の feature ViewModel 禁止ルールが単純になる。

### Negative

- package 移動により app UI と関連テストの import 更新が必要になる。
- package 名を直接参照する外部コードが存在する場合は互換性がないが、これらは app-internal 型であり外部 API として公開していない。

## Verification

- `AppViewModelTest` を app `ui` package へ移し、selected tab / message semantics が維持されることを確認する。
- `AppNavigationSpecTest` で section / tab mapping を継続検証する。
- `AppCompositionSourceArchitectureTest` で `app/.../feature` に production Kotlin source が存在しないこと、historical `feature.navigation` package が production source に戻らないことを検査する。
- `verifyArchitectureRuleTests` で MainActivity の任意の feature ViewModel import を違反とし、`ui.AppViewModel` は許可する。
- unit test、release lint、architecture verification を実行する。

## Documentation

- `docs/architecture/module-map.md` の app ownership から navigation 例外を削除する。
- ADR-0142 の historical decision は残し、本 ADR を refinement として relationship を追加する。

## Public repository review

変更対象は app shell の型、架空の test fixture、architecture documentation のみである。credential、token、OAuth secret、実ユーザー URL・メールアドレス・個人データ、database / backup / private artifact は追加しない。

## Supersession note

ADR-0202 により Decision 1、3、4 の `MainTab` / `AppViewModel` selected-tab state と Activity-scoped navigation lifetime は廃止した。Decision 2 の `app/.../feature` production source を禁止する ownership rule は引き続き有効である。
