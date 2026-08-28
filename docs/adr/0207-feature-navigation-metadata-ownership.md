# ADR-0207: multi-route feature の navigation metadata を owning feature UI に置く

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0150](0150-app-shell-navigation-ui-ownership.md), [ADR-0202](0202-navigation-compose-root-routing.md), [ADR-0205](0205-app-presentation-module-boundary.md)

## Context

ADR-0202 / ADR-0205 により root navigation graph と app-wide chrome は `:app:presentation` が所有し、destination identity の route string は owning `:feature:<name>:ui` の `NavigationDestination.kt` を正本とする構成になった。

一方 `AppNavigationSpec.kt` には RSS / Reddit / Bookmark について、route string 自体は feature から import しつつ、次の feature-local metadata が重複して残っていた。

- route から `RssTab` / `RedditTab` / `BookmarkTab` への変換
- tab から route への逆変換
- 各 feature destination の画面 title

これらは root graph registration や drawer grouping のような app-shell policy ではなく、owning feature の destination contract と tab presentation が同時に変わる metadata である。app presentation に残すと destination を追加・名称変更する際に feature UI と app-shell の双方を同期する必要がある。

## Decision

### 1. RSS / Reddit / Bookmark の feature-local navigation metadata を owning feature UI が所有する

各 `:feature:<name>:ui` の `NavigationDestination.kt` は既存 route constant に加えて次を公開する。

- route -> feature tab の変換
- feature tab -> route の変換
- feature destination の画面 title

対象は複数 destination と bottom tab を持つ RSS / Reddit / Bookmark とする。

`AppNavigationChrome` は feature contract を利用して active tab と遷移先 route を解決し、`AppNavigationSpec.screenTitle()` は feature-local title を feature contract から取得する。

### 2. app-shell policy は `:app:presentation` に残す

次は引き続き app ownership とする。

- root `NavHost` と `composable(route)` registration
- `allAppRoutes`
- `AppSection` grouping と section default route
- global top bar の有無
- feature message source / Summary overlay / Bookmark edit overlay の app-wide capability policy
- drawer / top bar / bottom bar の icon と Compose chrome
- semantic `AppNavigationTarget` から destination への root navigation mapping

feature module は app-level `NavController`、他 feature の graph、app-wide capability policy を所有しない。

### 3. route identity と runtime lifetime は変更しない

既存 route string、Tab enum、ViewModel factory、NavBackStackEntry / ViewModelStore lifetime は変更しない。新しい Gradle module や dependency direction も追加しない。

本変更は feature UI がすでに公開している route / tab contract の ownership を揃える source-level refinement であり、ADR-0193 の Gradle module を増やす条件には該当しない。

### 4. 2026-08-28 refinement: single-route feature の画面 title も owning feature UI が所有する

multi-route feature の整理後も、Integrated / Library / Knowledge / Asset / Mail / YouTube / X / Task / Calendar / Game / Health / Workout / Chat / Settings の画面 title は `AppNavigationSpec.screenTitle()` に literal として残っていた。

これらの title も destination identity と同時に変更される feature-local metadata であり、single-route であることを理由に app-shell ownership とする必要はない。各 feature UI の `NavigationDestination.kt` に既存 route constant と対応する `*_TITLE` constant を置き、`AppNavigationSpec` はその owner contract を参照する。

表示文字列、route string、`AppSection` grouping、default route、global capability policy、root graph registration は変更しない。RSS / Reddit / Bookmark の関数型 title contract も既存のまま維持する。

## Consequences

### Positive

- multi-route / single-route を問わず feature destination の名称変更を owning feature UI に局所化できる。
- `AppNavigationSpec` が app-wide navigation capability / grouping に集中する。
- app chrome は feature-local mapping / title を再定義せず public feature contract を利用する。
- route identity の正本という ADR-0205 の説明と実装が一致する。

### Negative

- feature UI の public contract に navigation metadata function / title constant が増える。
- app-shell の title 表示は feature metadata contract に依存するため、その互換性を integration test で確認する必要がある。
- app-wide capability policy は feature-local metadata へ移さないため、route が capability を追加する場合は引き続き `AppNavigationSpec` の更新が必要になる。

## Verification

- `FeatureNavigationMetadataSourceArchitectureTest` で app presentation に旧 tab mapping が戻らないことを検証する。
- 同 test で RSS / Reddit / Bookmark の `NavigationDestination.kt` が tab mapping と title contract を公開することを検証する。
- 同 test で single-route feature の `NavigationDestination.kt` が対応する `*_TITLE` を所有し、`AppNavigationSpec` が owner title constant を利用することを検証する。
- `AppNavigationSpecTest` で各 feature tab の route round-trip、全 registered route の title 解決、single-route feature の既存表示 title が不変であることを確認する。
- `verifyArchitecture`、unit tests、lint、public repository verifier を実行する。

## Documentation

- `docs/architecture/module-map.md` はすでに destination identity の正本を owning `:feature:<name>:ui/NavigationDestination.kt` と定義しており、本変更はその既存規定を tab mapping / local title metadata まで具体化する refinement とする。Gradle module ownership の記述変更は不要である。
- ADR-0205 の app-wide `AppNavigationSpec` ownership は維持するが、feature-local metadata まで app ownership と解釈しないよう本 ADR で refine する。

## Public repository review

本変更は navigation contract、app-shell integration、synthetic test、architecture documentation のみを変更する。credential、token、OAuth secret、account identifier、private endpoint、実ユーザー URL / title / mail / health data、diagnostic artifact、backup/database artifact を追加しない。
