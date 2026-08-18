# ADR-0103: app route の composition と navigation metadata の所有境界を明確化する

- Status: Accepted
- Date: 2026-08-19

## Context

ADR-0001、ADR-0003、ADR-0063、ADR-0101 では、`:app` を navigation、composition、platform wiring の境界として扱い、feature 固有の state や data implementation を画面へ漏らさない方針を定めている。

ADR-0101 の整理後も、app shell 周辺には次の曖昧さが残っていた。

- `MainActivity` が RSS の `UiState.unread` を監視し、未読 widget 更新を直接起動していた。Widget 更新の契機が Activity の表示ライフサイクルに依存していた
- Library、Integrated、YouTube、Asset、Knowledge、Task、Workout の app route が、それぞれ Compose 関数内で `YomitoriApplication` / `AppContainer` を取得し、ViewModel Factory や concrete data / WorkManager dependency を個別に組み立てていた
- `AppNavigationChrome.kt` が drawer / top bar / bottom bar の描画だけでなく、`MainTab` と `AppSection` の対応、既定タブ、画面タイトル、feature tab との相互変換まで同時に所有していた

いずれも app module の責務ではあるが、composition、runtime side effect、navigation metadata、navigation rendering の境界が曖昧になり、同じ種類の依存配線が複数箇所に増える原因になっていた。

## Decision

### 1. Widget 更新の監視は Widget feature が所有する

未読 widget の更新は `MainActivity` の RSS state 監視から切り離す。

`:feature:widget:ui` に `UnreadArticlesWidgetRefreshObserver` を置き、アプリ全体の data change signal を購読して `UnreadArticlesWidgetUpdater` を実行する。`YomitoriApplication` は composition root として signal と observer を接続し、observer の実行内容は Widget feature が所有する。

これにより Widget の更新は RSS 画面が表示・初期化されているかに依存しない。

### 2. app route の concrete dependency wiring は `AppRouteDependencies` に集約する

app module 内の route adapter は `YomitoriApplication` や `AppContainer` を Compose 関数内から探索しない。

`AppRouteDependencies` を app-level composition root とし、次を一か所で生成する。

- Asset / Knowledge / Task / Workout / YouTube の ViewModel Factory
- Library route が必要とする authorization、Library / Organization ViewModel Factory、SMB repository
- Task widget 更新 callback

各 route は Factory、repository、callback など必要な依存を引数で明示的に受け取る。Integrated と YouTube は同じ YouTube ViewModel Factory を利用する。

`AppContainer` は引き続き domain/data service の lifetime を所有し、`AppRouteDependencies` はそれらを presentation route へ接続する役割に限定する。

### 3. Navigation metadata と navigation chrome rendering を分離する

`AppNavigationChrome.kt` は drawer、top bar、bottom bar の Compose rendering に限定する。

次の純粋な navigation metadata / mapping は `AppNavigationSpec.kt` に置く。

- `MainTab -> AppSection`
- `AppSection -> default MainTab`
- `MainTab -> screen title`
- RSS / Reddit / Bookmark の feature tab と `MainTab` の相互変換
- global top bar の利用可否

この mapping は unit test で section の既定タブ整合性と feature tab の往復変換を検証する。

## Consequences

### Positive

- Activity が Widget feature 固有の更新 side effect を持たなくなる
- Widget 更新が画面表示状態に依存しなくなる
- app route ごとの dependency wiring 方法が一つに揃う
- Compose route から service locator 的な `YomitoriApplication` 参照を除去できる
- Library や Knowledge の concrete implementation 生成場所を追跡しやすくなる
- navigation の定義変更と Material UI の描画変更を別々に扱える
- navigation mapping を Android UI を起動せず unit test できる

### Negative

- `AppRouteDependencies` という app-level composition object が一つ増える
- route の引数に Factory / dependency holder が増える
- Widget 更新は generic data change signal を購読するため、未読件数に影響しない database change でも軽量な再描画要求が発生する場合がある

## Relationship to existing ADRs

- ADR-0001 の UI / Domain / Data 責務分離を app route の dependency construction に適用する
- ADR-0003 の app module を composition root とする方針を具体化する
- ADR-0012 の navigation drawer 方針は変更せず、navigation metadata と rendering の配置のみ整理する
- ADR-0063 の feature UI ownership cleanup を継続する
- ADR-0101 の `YomitoriApp` を app shell に限定する判断を、route dependency wiring と app runtime side effect まで拡張する
