# ADR-0205: app-shell presentation を専用 Gradle module へ分離する

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0150](0150-app-shell-navigation-ui-ownership.md), [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md), [ADR-0196](0196-app-boundary-ownership-cleanup.md), [ADR-0200](0200-app-composition-module-boundary.md), [ADR-0202](0202-navigation-compose-root-routing.md), [ADR-0204](0204-app-composition-internal-package-ownership.md)

## Context

ADR-0200 により concrete feature Data implementation は `:app:composition` へ隔離され、executable `:app` から feature Data の高 fan-in dependency を除去した。一方、`:app` は root navigation / app-shell chrome / app-owned Route と、ほぼすべての `:feature:*:ui` への direct dependency を引き続き所有していた。

この状態では Application / Activity、app lock、diagnostics、external Intent、Custom Tab 等の executable-only concern が、feature UI の広い compile classpath と同じ Gradle module に残る。source-level rule で feature UI state や concrete Data の混入を検査できても、executable shell と app-shell presentation の変更理由・dependency fan-out は build boundary として分離されていない。

ADR-0193 は小さな責務のためだけに Gradle module を増やさない方針を採用した。本件は file/package 整理ではなく、`:app` から feature UI dependency を compile-time に除去し、executable lifecycle/platform ownership と cross-feature presentation ownership の依存方向を固定する目的があるため、専用 module とする価値がある。

## Decision

### 1. `:app:presentation` を追加する

`:app:presentation` は app-shell presentation の build boundary として次を所有する。

- `YomitoriApp` / application theme
- root `AppNavHost` と navigation graph registration
- `AppSection` / `AppNavigationSpec`
- drawer / top bar / overlay / feature message capability の app-wide chrome
- feature ViewModel factory と feature-owned Route/Screen の app-shell composition
- app-owned `LibraryRoute` / `MailRouteHost` / `CalendarRoute` / `SettingsRoute` 等の presentation adapter
- Composable lifecycle に結び付く Activity Result launcher / permission / document picker wiring

source package は既存 caller API を維持するため `dev.terashima.yomitorirss.ui` を維持し、物理 source root を `app/presentation/src/main/kotlin` とする。

### 2. executable `:app` は Android entry point と executable-only concern に限定する

`:app` は次を所有する。

- `YomitoriApplication` / `MainActivity` 等の Android component entry point
- app lock / secure-window transition
- startup / memory diagnostics
- share / widget 等の external Intent routing
- Custom Tab
- LAN Web Server notification permission / dialog 等、Activity/component lifecycle や executable-only state に結び付く platform host
- framework-generated component と narrow provider contract の接続

`:app` は `:app:presentation` と `:app:composition` を利用し、必要な feature Domain contract は直接利用できる。一方、`:feature:*:ui` / `:feature:*:data` へ直接依存しない。

Custom Tab のように presentation から起動要求が発生する executable-only platform action は `MainActivity` から callback として `:app:presentation` へ渡す。presentation source は executable `:app` の platform/security implementation を直接 import しない。これにより app-lock external transition tracking 等の executable lifecycle policy を `:app` に維持する。

### 3. root `NavController` は `MainActivity` に残す

ADR-0202 の app-lock navigation lifetime を維持する。

`rememberNavController()` は `MainActivity.setContent` の Compose root で app-lock / crash conditional UI より上に置き、`:app:presentation` の `YomitoriApp` へ渡す。これにより presentation source を module 分離しても、app lock 表示中の back stack / destination-scoped ViewModelStore lifetime は変えない。

`AppNavHost` と destination registration は `:app:presentation` が所有し、Activity は navigation graph の feature policy を実装しない。

### 4. Activity Result ownership を executable lifecycle と Composable presentation に分ける

ADR-0193 / ADR-0196 / ADR-0200 では Activity Result host を executable `:app` と説明していたが、実装上は feature authorization / Calendar permission / backup document picker 等が app-owned Route の Composable lifecycle に結び付いている。

このため current ownership を次に精密化する。

- Composable Route 内で feature UI と一体に lifecycle を持つ Activity Result launcher: `:app:presentation`
- Activity/component lifecycle、app lock transition、executable-only state と一体の platform host: `:app`
- Gmail / Google Books の concrete Data authorization manager と Activity Result contract を接続する dependency bridge: `:app:composition/platform/authorization`

これにより `:app:presentation -> :app` の逆依存を導入せず、authorization manager の concrete implementation も presentation へ漏らさない。

### 5. presentation dependency を Domain/UI contract に限定する

`:app:presentation` は次へ依存できる。

- `:app:composition` の narrow facade / route dependencies
- `:feature:*:domain`
- `:feature:*:ui`
- presentation 実装に必要な AndroidX / Compose library

次は禁止する。

- `:app:presentation -> :app`
- `:app:presentation -> :feature:*:data`
- concrete database / WorkManager infrastructure の import / construction
- `YomitoriApplication` / `MainActivity` / `MainActivityDependencies` 等の executable implementation type への依存
- authorization bridge を除く executable platform/security implementation の direct import

feature Data implementation が必要な instance wiring は `:app:composition` が継続して所有する。

### 6. feature UI ownership は変更しない

`:app:presentation` は generic presentation feature ではない。

- feature 固有 UI state / reducer / Screen / dialog / feature Route は owning `:feature:<name>:ui` に残す
- Integrated の cross-feature projection/action ownership は ADR-0188 を維持する
- Settings overlay/presentation policy は ADR-0192 を維持する
- app presentation は app-wide navigation/chrome、factory/callback wiring、platform presentation adapter に限定する
- feature route contract は owning feature UI の `NavigationDestination.kt` を正本とし、app presentation が route string を再定義しない

### 7. application-scope lifetime と concrete composition は変更しない

- `AppContainer` は `:app:composition` に残す
- `AppRouteDependencies` の application-scope lifetime を維持する
- process-wide `HttpClient` / database / WorkerFactory ownership を変更しない
- feature provider policy / persistence ownership を変更しない
- Application / Activity FQCN、application id、database schema/version、Worker FQCN を変更しない

## Consequences

### Positive

- executable `:app` の compile classpath から `:feature:*:ui` を除去できる。
- Android component/lifecycle concern と app-shell presentation の変更理由を build boundary で分離できる。
- `:app:presentation` は feature Data を参照できないため、Route/Host への concrete repository wiring drift を dependency graph で遮断できる。
- feature UI の high fan-out dependency は presentation boundary に局所化される。
- navigation / presentation unit test を owning module に置ける。
- `:app -> :app:presentation -> :app:composition / feature UI` という一方向の app boundary を明示できる。

### Negative

- Android library module が1つ増え、Gradle configuration graph は複雑になる。
- `MainActivity` が利用する `YomitoriApp` / `YomitoriTheme` 等は module boundary を跨ぐ public API として維持する必要がある。
- app-owned Route の Activity Result wiring は executable module ではなく presentation module に存在するため、platform ownership の説明は lifecycle の種類を区別する必要がある。
- app-shell を横断して追う場合、`:app` / `:app:presentation` / `:app:composition` の3 module を確認する必要がある。

## Verification

- `settings.gradle.kts` が `:app:presentation` を含むこと。
- `:app` が `:app:presentation` に依存し、`:feature:*:ui` / `:feature:*:data` へ直接依存しないこと。
- `:app:presentation` が `:app` / `:feature:*:data` へ依存しないこと。
- `app/src/main/.../ui` に production Kotlin source が残らず、app-shell UI の正本が `app/presentation/src/main/kotlin/.../ui` であること。
- `YomitoriApp` が feature ViewModel state や Activity Result launcher を所有しないこと。
- app-owned Route が concrete feature Data / database / WorkManager infrastructure を import / construct しないこと。
- presentation が executable platform/security implementation を直接 import せず、Custom Tab 等を callback で受け取ること。
- `MainActivity` が root `NavController` を app-lock conditional UI より上に保持すること。
- feature destination identity / feature presentation state の既存 ownership test を新 source root に追従させること。
- presentation unit tests、existing app unit tests、`verifyArchitecture`、lint、public repository verifier を通すこと。

## Documentation

- `docs/architecture/module-map.md` に `:app:presentation` と新しい app dependency direction を追加する。
- `docs/architecture/code-organization.md` の app source tree を executable / presentation / composition に分ける。
- `docs/architecture/principles.md` の app-shell ownership / dependency rules を同期する。
- ADR-0193 / ADR-0200 の「`:app` が app-shell UI を所有する」という記述は本 ADR で refine される。履歴として本文は保持し、current architecture は本 ADR と current architecture docs を正本とする。

## Public repository review

本変更は Gradle dependency、app-shell presentation source の module 移動、synthetic architecture/unit test、current architecture documentation のみを変更する。credential、OAuth token、account identifier、private endpoint、実ユーザー URL / title / mail / health data、diagnostic artifact を追加しない。

## References

- [ADR-0003](0003-multi-module-architecture.md)
- [ADR-0136](0136-public-repository-content-verification.md)
- [ADR-0150](0150-app-shell-navigation-ui-ownership.md)
- [ADR-0188](0188-integrated-feature-owns-cross-feature-presentation.md)
- [ADR-0192](0192-settings-feature-owns-cross-feature-presentation.md)
- [ADR-0193](0193-within-module-responsibility-and-app-package-structure.md)
- [ADR-0196](0196-app-boundary-ownership-cleanup.md)
- [ADR-0200](0200-app-composition-module-boundary.md)
- [ADR-0202](0202-navigation-compose-root-routing.md)
- [ADR-0203](0203-feature-owned-provider-policy-adapters.md)
- [ADR-0204](0204-app-composition-internal-package-ownership.md)
