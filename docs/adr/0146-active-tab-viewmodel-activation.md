# ADR-0146: app composition は選択中タブに必要な ViewModel だけ起動する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0116](0116-route-owned-root-viewmodel-wiring.md), [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md)

## Context

ADR-0116 により feature ViewModel の factory 組み立ては `AppRouteDependencies` に集約され、`MainActivity` は feature ViewModel を直接所有しなくなった。一方、ViewModel の取得位置は app composition の共通 host に残っていた。

具体的には `AppFeatureContent`、`AppTopBarRoute`、`FeatureMessageEffects`、Summary / Bookmark overlay が、現在表示しているタブに関係なく複数 feature の `viewModel(factory = ...)` を評価していた。

Activity の `ViewModelStore` を利用するため一度作成した ViewModel の共有自体は意図した挙動だが、未使用 feature まで起動時に生成すると、その ViewModel の `init` にある repository read、cleanup、Flow 購読等も開始される。feature が増えるほど、表示していない機能の初期化・I/O・常駐 state が app shell の起動コストへ累積する。

本変更では Navigation Compose や destination-scoped `ViewModelStore` は導入せず、ADR-0116 の Activity-scoped sharing を維持したまま、ViewModel の「初回生成時点」だけを必要な presentation へ近づける。

## Decision

### 1. selected tab を確定してから feature ViewModel を取得する

`AppFeatureContent` は `selectedTab` の分岐より前に feature ViewModel を取得しない。

各 branch は、その画面が表示に必要とする ViewModel だけを `viewModel(factory = ...)` で取得する。例えば AI Chat を開いていない状態で `ChatViewModel` を生成せず、Settings を開いていない状態で Backup / AI Settings ViewModel を生成しない。

### 2. TopBar は presentation input だけを受け取る

`AppTopBar` 自体は RSS / Reddit / Feed ViewModel を受け取らず、title、refresh progress、未読有無、action callback のみを受け取る。

`AppTopBarRoute` が現在のタブを見て必要な ViewModel だけを取得する。RSS 以外のタブ表示中に RSS / Feed ViewModel を TopBar のためだけに生成しない。

### 3. global host は capability が必要なタブだけ mount する

Summary overlay と Bookmark edit overlay は、対応 action を提供するタブだけで mount する。

feature message の snackbar bridge も現在のタブに関連する ViewModel だけを購読する。Summary overlay は summary dialog が実際に表示されるまで AI Settings ViewModel を取得せず、推論進捗表示のためだけに常時起動しない。

### 4. ViewModel identity と lifetime は変更しない

本変更でも `viewModel()` の owner は Activity の `ViewModelStoreOwner` であり、同じ ViewModel class / default key は一度生成された後は既存 host 間で同じ instance を共有する。

目的は destination lifetime の変更ではなく、未使用 feature の eager activation を避けることである。将来 destination-scoped lifecycle を導入する場合は別 ADR で state sharing を再設計する。

### 5. source-level regression を検査する

app composition の主要 host では `selectedTab` の dispatch より前に `viewModel(...)` を評価しないことを source architecture test で固定する。

## Consequences

### Positive

- 起動時や無関係なタブ表示時に不要な repository read / Flow subscription を開始しない。
- feature 追加時に app shell の常時 activation cost が暗黙に増えにくい。
- TopBar と global host の presentation dependency が明確になる。
- ADR-0116 の ViewModel sharing semantics は維持できる。

### Negative

- `when (selectedTab)` branch ごとの wiring は明示的になり、同じ ViewModel factory の記述が複数 branch に現れる。
- 初めて feature を開いた時に ViewModel 初期化コストが発生する。
- Activity-scoped lifetime 自体は維持するため、一度開いた feature ViewModel は Activity が終了するまで保持される。

## Verification

- `AppNavigationSpecTest` で Summary / Bookmark overlay の active-tab policy を固定する。
- `AppCompositionSourceArchitectureTest` で `AppFeatureContent`、`AppTopBarRoute`、`FeatureMessageEffects` が selected-tab dispatch 前に feature ViewModel を生成しないことを検査する。
- 既存 unit tests、release lint、architecture verification を実行する。

## Documentation

- `docs/architecture/principles.md` に inactive feature を app shell から eager activation しない方針を反映する。
- `docs/architecture/testing.md` に active-tab ViewModel activation の regression test を反映する。

## Public repository review

本 ADR と実装は composition source と人工的な test data のみを扱う。credential、token、OAuth secret、実ユーザー URL / メール、蔵書・健康情報、database / backup artifact は追加しない。
