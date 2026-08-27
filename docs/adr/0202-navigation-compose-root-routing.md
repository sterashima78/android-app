# ADR-0202: root routing を Navigation Compose と feature-owned destination contract へ移行する

- Status: Accepted
- Date: 2026-08-27
- Supersedes: [ADR-0150](0150-app-shell-navigation-ui-ownership.md) Decision 1, Decision 3, Decision 4
- Refines: [ADR-0103](0103-app-route-composition-and-navigation-spec.md), [ADR-0116](0116-route-owned-root-viewmodel-wiring.md), [ADR-0147](0147-active-tab-viewmodel-activation.md), [ADR-0156](0156-active-tab-message-capability-policy.md), [ADR-0200](0200-app-composition-module-boundary.md)

## Context

root navigation は `MainTab` を `AppViewModel` の `selectedTab` として保持し、`AppFeatureContent` の `when (selectedTab)` で Route を切り替えていた。drawer / bottom bar / external Intent / feature callback はすべて `MainTab` へ変換して同じ state を更新していた。

この方式は単純だったが、root destination の増加に伴って次の問題が顕在化した。

- app shell がすべての destination を `MainTab` enum と巨大な `when` で二重に列挙する
- navigation history がなく、Android Back を app shell 独自動作だけで処理する
- `AppSection`、feature tab、`MainTab` の相互変換が navigation metadata のためだけに増える
- ViewModel owner が Activity のままで、destination の lifetime と一致しない
- widget / share Intent などの external navigation も app-specific selected-tab state に依存する

ADR-0116 では将来 NavHost / destination-scoped ViewModelStore を導入する場合は Route 側を destination owner に載せ替える方針を既に定めていた。ADR-0200 では `:app` を executable shell として app-shell navigation / presentation の owner に残し、concrete Data composition を `:app:composition` へ分離している。

## Decision

### 1. `NavController` を root navigation の source of truth とする

`:app` に Navigation Compose の `NavHost` を置き、現在 destination と back stack を `NavController` が所有する。

`MainTab`、selected-tab state を持つ `AppViewModel`、`AppFeatureContent` の manual dispatch は削除する。drawer、bottom bar、feature callback、external Intent は route を `NavController` へ渡して遷移する。

`AppSection` は drawer の presentation grouping として残し、各 section の既定 route だけを `AppNavigationSpec` が定義する。

`NavController` は `MainActivity.setContent` の app root composition で `rememberNavController()` により生成し、app-lock の conditional dispatch より上に保持する。これは Activity property として navigation state を戻すものではなく、Compose の app-shell owner を一段上へ置くためである。生体認証ロックにより `MainContent` / `YomitoriApp` が一時的に composition から外れても同じ controller を再利用し、現在 route、back stack、destination-scoped ViewModelStore を失わない。

### 2. destination identity は owning feature UI module が公開する

各 `:feature:<name>:ui` は `NavigationDestination.kt` で route contract を公開する。RSS / Reddit / Bookmark のように複数 root destination を持つ feature は、それぞれの route を同じ UI module が所有する。

`:app` は route string を独自に再定義せず、feature contract を利用して root `NavHost` を composition する。

ただし root graph の composition、drawer / bottom bar、cross-feature callback wiring は executable shell の責務なので `:app` に残す。feature module が `NavController` や他 feature の graph を所有する構造にはしない。

### 3. root graph composition は薄い `AppNavHost` と責務別 registration に分割する

`AppNavHost` は `NavHost` の生成、start destination、責務別 graph registration の composition だけを所有する。

実際の `composable(route)` 登録は `:app` 内の app-owned registration へ分割する。

- Home / Integrated
- RSS
- Reddit
- Bookmark
- 単一 root destination を持つその他 feature

これは feature module に app graph ownership を移す分割ではない。各 registration は feature-owned route contract と `AppRouteDependencies` を利用し、app-level callback を接続する executable shell adapter である。

ViewModel Factory や platform callback は引き続き `AppRouteDependencies` と app route adapter から渡し、Navigation Compose を concrete dependency composition の代替にはしない。

### 4. feature ViewModel は destination-scoped lifetime を基本とする

root graph registration の destination 内で `viewModel(factory = ...)` を取得し、`NavBackStackEntry` を `ViewModelStoreOwner` とする。

TopBar、Summary overlay、Bookmark edit overlay、feature message effect は NavHost の外側にあるため、active `NavBackStackEntry` を明示的な `ViewModelStoreOwner` として受け取る。これにより本文と cross-feature host が同じ destination-scoped ViewModel instance を共有する。

app-lock 表示時も root `NavController` 自体は composition に保持するため、temporary lock UI swap を destination 終了として扱わない。実際の navigation pop / replacement または Activity 終了まで destination-scoped state を保持する。

Activity property へ feature ViewModel を戻さない。

### 5. top-level navigation は single-top と state save/restore を使う

Drawer / BottomBar / app-level callback の遷移は共通 helper から行い、次を適用する。

- current destination と同じ route には遷移しない
- `launchSingleTop = true`
- `restoreState = true`
- start destination まで `popUpTo` し、`saveState = true`

これにより top-level destination を重複積載せず、切替時の saved navigation state を復元できる形にする。

### 6. Back は navigation history を優先し、root の既存 drawer semantics を維持する

Drawer が閉じていて back stack に戻り先がある場合は `popBackStack()` する。

root で戻り先がない場合は従来どおり Back で Drawer を開く。Drawer が開いている状態で Back を押した場合は app を終了する既存 semantics を維持する。

### 7. external Intent は buffered navigation request として app shell へ渡す

`IncomingIntentHandler` は `AppViewModel` を操作せず、feature-owned route string を navigation request callback へ渡す。

Activity は buffered channel を bridge として持ち、Compose が active になった後 `YomitoriApp` が request Flow を収集して `NavController` へ適用する。share 保存処理や widget action 自体の ownership は変更しない。

### 8. Navigation dependency は 2.9.8 を採用する

2026-08-26 公開の Navigation 2.10.0 は Compose artifact の compileSdk baseline が API 37 に上がっている。現在の app は compileSdk 36 であるため、本変更では SDK baseline migration を混在させず `androidx.navigation:navigation-compose:2.9.8` を採用する。

compileSdk 37 / Navigation 2.10 への更新は platform baseline の別変更として扱う。

## Consequences

### Positive

- root destination の source of truth が `NavController` / navigation graph に一本化される。
- `MainTab`、`AppViewModel.selectedTab`、`AppFeatureContent` の manual routing を削除できる。
- Android Back が navigation history を利用できる。
- feature ViewModel lifetime を destination back stack に合わせられる。
- destination identity が owning feature UI module に置かれ、app shell の enum へ全 feature を再列挙する必要がなくなる。
- widget / share / feature callback の遷移が同じ navigation path に合流する。
- `AppNavHost` 自体を destination 一覧の巨大な集中点にしない。
- 生体認証ロックの一時表示で navigation state や destination-scoped ViewModel を失わない。

### Negative

- `:app` に Navigation Compose dependency が増える。
- app shell の TopBar / overlay が active `NavBackStackEntry` owner を明示的に受け渡す必要がある。
- root Compose composition が app-lock UI より長寿命の `NavController` を保持する必要がある。
- top-level navigation に back stack が生まれるため、以前の selected-tab replacement と Back semantics が変わる。
- feature UI module に route contract file が増える。
- app-owned graph registration file が増える。
- Navigation 2.10 の採用は compileSdk 37 migration まで保留する。

## Verification

- `MainTab.kt`、`AppViewModel.kt`、`AppFeatureContent.kt` が production source に存在しないこと。
- `MainActivity` の root composition が `rememberNavController()` を app-lock dispatch より前に生成し、同じ controller を `MainContent` / `YomitoriApp` へ渡すこと。
- `YomitoriApp` が `NavHostController` を受け取り、独自に `rememberNavController()` を再生成せず selected-tab state も持たないこと。
- `AppNavHost` が root `NavHost` と graph composition だけを所有し、直接 `composable(route)` を列挙しないこと。
- app-owned destination registration が責務別ファイルへ分割され、destination dispatch 前に feature ViewModel を eager resolve しないこと。
- destination route contract が owning feature UI module に存在すること。
- section / RSS / Reddit / Bookmark route mapping、message capability、overlay capability を unit test すること。
- Back action を navigation history / root drawer / drawer-open の3状態で unit test すること。
- task widget launch が task route へ解決されることを unit test すること。
- active route の TopBar / overlay / message host が active `NavBackStackEntry` を ViewModel owner として使うこと。
- `verifyArchitecture`、unit tests、release lint、public repository verifier を通すこと。

## Documentation

- `docs/architecture/module-map.md` の app navigation ownership を `NavController` / `AppNavHost` / app-owned graph registration / feature destination contract に更新する。
- ADR-0150 の selected-tab state decision を supersede する。
- ADR-0156 の selected-tab message capability decision を supersede し、active route capability へ接続する。
- ADR-0116 に destination-scoped ViewModelStore が現行決定であることを追記する。
- app-lock UI より長寿命の root navigation owner を current architecture doc に明記する。

## Public repository review

変更対象は navigation route identifier、UI composition、architecture test、ADR と current architecture documentation である。route identifier は固定のアプリ内部識別子だけを含み、credential、token、OAuth secret、signing material、個人メールアドレス、実ユーザー URL/title、private endpoint、端末固有データを追加しない。

## References

- Android Developers: Navigation Compose / Navigation release notes
- [ADR-0103](0103-app-route-composition-and-navigation-spec.md)
- [ADR-0116](0116-route-owned-root-viewmodel-wiring.md)
- [ADR-0147](0147-active-tab-viewmodel-activation.md)
- [ADR-0150](0150-app-shell-navigation-ui-ownership.md)
- [ADR-0156](0156-active-tab-message-capability-policy.md)
- [ADR-0200](0200-app-composition-module-boundary.md)
