# ADR-0156: active tab の message capability policy を navigation spec に集約する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0147](0147-active-tab-viewmodel-activation.md), [ADR-0150](0150-app-shell-navigation-ui-ownership.md)

## Context

ADR-0147 により app composition は選択中タブに必要な ViewModel だけを起動する方針になっている。一方 `FeatureMessageEffects` には `MainTab` ごとの `when` があり、どの feature の message を監視するかという navigation capability policy を composable 自身が重複して保持していた。

タブ構成や capability を変更する際、navigation spec と message effect の分類を別々に更新する必要があり、不要 ViewModel の eager activation や message の取りこぼしを再発させる余地があった。

## Decision

- `AppNavigationSpec` に `FeatureMessageSource` と `MainTab.featureMessageSources()` を置き、active tab ごとの message capability を一か所で宣言する。
- `FeatureMessageEffects` は `selectedTab` を直接分類せず、この capability set を消費する。
- ViewModel は capability が含まれる場合だけ composition し、ADR-0147 の active-tab activation を維持する。
- capability metadata は app shell/navigation ownership の一部であり、feature Domain model へ移さない。

## Consequences

### Positive

- タブと feature message source の対応関係が navigation spec に集約される。
- message effect 側で二重の `MainTab` policy を保守する必要がなくなる。
- active tab 以外の ViewModel を起動しない性質を維持しやすい。

### Negative

- `AppNavigationSpec` が画面タイトルや section mapping に加えて presentation capability metadata も持つ。
- message source の追加時は enum と mapping、effect 実装の双方が必要だが、タブ分類そのものは一か所になる。

## Verification

- 全 `MainTab` の expected message source set を unit test で固定する。
- `FeatureMessageEffects` が `selectedTab.featureMessageSources()` を利用し、独自の `when (selectedTab)` を持たないことを source architecture test で固定する。
- ViewModel 解決が capability dispatch より前に行われないことを既存 architecture test の考え方で継続検査する。

## Public repository review

UI composition policy のみを扱い、個人データ、credential、外部 endpoint、端末固有情報は追加しない。
