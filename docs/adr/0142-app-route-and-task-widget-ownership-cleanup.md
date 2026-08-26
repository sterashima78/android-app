# ADR-0142: app Route の presentation ownership と Task widget 更新境界を整理する

- Status: Accepted
- Date: 2026-08-23
- Amends: [ADR-0024](0024-task-home-screen-widget.md), [ADR-0062](0062-extract-integrated-ui-from-app.md), [ADR-0063](0063-feature-ui-ownership-cleanup.md), [ADR-0116](0116-route-owned-root-viewmodel-wiring.md)
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0139](0139-app-entrypoint-and-worker-runtime-baseline.md)
- Refined by: [ADR-0150](0150-app-shell-navigation-ui-ownership.md)
- Amended by: [ADR-0187](0187-integrated-feature-owns-cross-feature-presentation.md)

## Context

P0〜P2 の architecture cleanup により、`:app` は application entry point、navigation、dependency composition へかなり収束した。一方、追加レビューで次の残存責務が見つかった。

- SMB Book Reader のダウンロード進捗、エラー、再試行、準備中 UI が `:app` の Route に残っていた。
- YouTube の loading / snackbar / message consume が `:app` の Route に残っていた。
- Asset / Knowledge / Workout / Task / AI Task Queue の root Route が owning feature UI module ではなく `app/src/main/.../feature/*` に置かれていた。
- Calendar / Library / Integrated / Settings の app-level composition adapter も feature 実装と同じ物理 namespace に置かれ、Gradle feature module の実装と区別しにくかった。
- Task widget の更新が Task 画面で `state.tasks` を observe する `LaunchedEffect` に依存していた。そのため画面初期表示でも widget update が発火し、画面外の command と変更通知の関係が不明確だった。

ADR-0003 / ADR-0063 では feature 固有 UI state は owning feature が持ち、`:app` の adapter は navigation、platform integration、cross-feature composition に限定する方針を既に採用している。本 ADR は新しい feature 分割を導入するのではなく、この既存方針へ残存コードを収束させる。

## Decision

### 1. feature 内で完結する root Route は owning UI module が所有する

次の Route を `:app` から各 `:feature:<name>:ui` へ移す。

- AI Task Queue
- Asset
- Knowledge
- Task
- Workout
- YouTube

Route は ViewModel factory から root ViewModel を取得し、feature Screen へ state / action を接続する。feature 固有 loading、message、snackbar 等も owning UI module が扱う。

YouTube の外部 URL 起動だけは Android `Intent` を扱うため `:app` の `YouTubeRouteHost` に残し、feature-owned `YouTubeRoute` へ `onOpen` callback として渡す。

### 2. SMB Book Reader の preparation presentation は Library UI が所有する

`SmbBookReaderRoute` を `:feature:library:ui` へ移し、次を Library UI state として扱う。

- download progress
- prepared book
- preparation error
- retry state
- preparation / retry UI
- Book Reader source の lifecycle

app-level Library composition は Google Books の Activity Result、Library / Book Reader capability の接続、dialog navigation だけを担当する。Book Reader dependency は app 固有 container 型を feature UI へ渡さず、`BookPageSourceFactory` と `ReadingPositionStore` の contract を明示的に渡す。

### 3. app composition adapter は `app/.../ui` に配置する

Android permission / Activity Result、cross-feature state mapping、app navigation を必要とする次の adapter は `dev.terashima.yomitorirss.ui` 配下へ置く。

- Calendar Route
- Library Route
- Integrated Route
- Settings feature host
- YouTube external-intent host

これにより `app/src/main/.../feature` を Gradle feature module の実装置き場として誤認しにくくする。

既存の `feature/navigation` package は Gradle feature module ではなく app shell navigation state の歴史的 package であり、本 ADR 時点では名称変更による広範な churn を避けて維持した。ADR-0150 でこの暫定例外を終了し、app shell navigation state を `app/.../ui` へ移した。

ADR-0187 により、この一覧のうち Integrated Route は app adapter ではなく `:feature:integrated:ui` の ownership へ移した。Integrated 固有の cross-feature state/action mapping は named feature responsibility として feature 側が所有し、`:app` は navigation / platform callback / dependency wiring のみを接続する。

### 4. Task widget 更新は Task mutation 成功に接続する

Task 画面の `state.tasks` observation を widget 更新トリガーとして使わない。

Task Domain に `TaskChangeNotifyingRepository` を置き、delegate の command が成功した後だけ `onChanged` callback を呼ぶ。app composition は Task UI 用 Repository をこの decorator で包み、callback で `TaskWidgetUpdater.updateAll` を呼ぶ。

widget 自身からの完了操作は既存どおり Task Repository command の完了後に widget を更新する。現在の command 経路では、Task 画面を表示しているかどうかではなく command 成功が projection refresh の契機となる。

widget refresh が失敗しても Task command 自体を失敗扱いにしないよう、app composition の callback では widget update 例外を Task Repository へ伝播させない。

## Consequences

### Positive

- feature 固有 presentation state が owning UI module に集約される。
- app composition source と Gradle feature implementation の物理配置が明確になる。
- SMB Reader の準備 UI を Library UI 単独で変更・テストしやすくなる。
- YouTube の platform Intent と feature presentation が分離される。
- Task widget 更新が画面初期化や state reload ではなく実際の mutation 成功に対応する。
- `app/.../feature` への route drift を regression test で検出できる。

### Negative

- Library UI から Book Reader Domain/UI への明示的 dependency が増える。
- 本 ADR 時点では app shell navigation の歴史的 package を暫定的に残した。ADR-0150 でこの負債は解消済みである。
- Task widget は Task state の汎用 reactive stream ではなく現在の command composition による invalidation を利用する。将来別の Task command entry point を追加する場合は同じ notifying repository / change capability へ接続する必要がある。

## Verification

- `TaskChangeNotifyingRepositoryTest` で read では通知せず、各 mutation の成功後だけ通知し、mutation failure では通知しないことを確認する。
- `AppCompositionSourceArchitectureTest` で `app/src/main/.../feature` に production Kotlin source が存在しないことを確認する。ADR-0150 以降は navigation 例外を持たない。
- 既存 `IntegratedRouteAdapterTest` を app composition package へ移動し、物理移動で mapping semantics を変えないことを確認する。
- `verifyArchitecture` で feature UI -> concrete data dependency、package/source path、app Route の infrastructure dependency が増えていないことを確認する。
- 全 unit tests と release lint を実行する。
- YouTube / Library のユーザー操作意味、Task の CRUD semantics、永続 schema は変更しない。

## Documentation

- `docs/architecture/module-map.md` に app composition source の配置方針を反映する。navigation 暫定例外の終了は ADR-0150 を参照する。
- ADR-0024 の Task widget 機能要件は維持し、更新契機の ownership だけを本 ADR で変更する。

## Public repository review

本変更は production source、架空の unit test、architecture document のみを変更する。credential、token、OAuth secret、実ユーザーの URL・メールアドレス・蔵書名・健康情報、database / backup / private artifact を追加しない。
