# ADR-0152: Library Route と route runtime ownership を整理する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0116](0116-route-owned-root-viewmodel-wiring.md), [ADR-0142](0142-app-route-and-task-widget-ownership-cleanup.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md)
- Related: [ADR-0143](0143-web-library-source-and-bookmark-transfer.md), [ADR-0146](0146-workmanager-worker-factory-injection.md)

## Context

app route ownership と application-scope runtime group の整理後も、Library 周辺には過去の composition 形態が残っていた。

- `app/ui/LibraryRoute.kt` が Google Books の Activity Result だけでなく、Web Library の action UI、SMB Book Reader の Dialog と開閉 state、Library state の購読まで所有していた。
- `AppRouteDependencies` が `GoogleBooksAuthorizationManager`、SMB cover scheduler、書誌正規化 prompt repository、Library organization suggester、Book Reader の default implementation、X CSS repository を直接構築していた。
- `docs/spec.md` は WorkManager Worker も Provider lookup の対象になり得るように読める記述が残っており、ADR-0146 の WorkerFactory constructor injection と一致していなかった。

これらは即時の機能不具合ではないが、`:app` を composition と platform adapter に限定し、concrete feature graph を runtime group へ集約する現在の方針を曖昧にする。

## Decision

### 1. Library 固有 presentation は `:feature:library:ui` が所有する

`app/ui/LibraryRoute.kt` に残す責務は、Google Books 認証の Activity Result launcher と、app composition から受け取った callback / dependency を Library feature route へ接続することに限定する。

次は `LibraryFeatureRoute` が所有する。

- Web Library action UI と Library state からの Web book 選択
- Web book 追加・Bookmark への移動後の refresh / error presentation
- SMB Book Reader の選択中 book state
- SMB Book Reader Dialog の表示と閉じた後の Library refresh

Bookmark と Web Library の移動そのものは引き続き app application service が調停し、feature UI は callback として受け取る。foreign persistence ownership は変更しない。

### 2. route dependency wiring で feature concrete implementation を構築しない

`AppRouteDependencies` は ViewModel factory、Domain contract、application callback の wiring に集中する。

Library / Book Reader の application-scope または route 間で共有する concrete implementation は `AppFeatureRuntimeDependencies` の Library runtime group が構築する。X CSS repository は supporting runtime group が構築する。

この変更で repository / scheduler / reader setting の lifetime を短くせず、既存の application-scope graph を維持する。

### 3. WorkManager の仕様記述を WorkerFactory 方針へ合わせる

Android framework が直接生成し constructor injection を差し込めない監査済み entry point では Provider contract を利用できる。

WorkManager Worker はこの例外に含めず、owning feature の WorkerFactory が constructor injection し、`:app` が WorkerFactory composition を application graph へ接続する。`docs/spec.md` を ADR-0146 と同じ現在形へ修正する。

## Verification

`AppCompositionSourceArchitectureTest` で次を固定する。

- `AppRouteDependencies` に Library / Book Reader / X の concrete data implementation import / constructor を戻さない。
- app `LibraryRoute` に Web Library action、SMB reader Dialog、Library-specific mutable state を戻さない。
- `LibraryFeatureRoute` が Web Library action と SMB reader presentation を所有する。

既存の unit test、architecture verification、release lint、public repository verification も継続する。

## Consequences

### Positive

- app route が OS Activity Result と composition adapter に集中する。
- Library presentation の変更が `:feature:library:ui` 内へ局所化される。
- route wiring に concrete feature construction が再集積しにくくなる。
- WorkManager の仕様書と実装・ADR が一致する。

### Negative

- `LibraryFeatureRoute` の引数は Book Reader contract と cross-context callback を含むため増える。
- Library runtime group は model manager を必要とする organization suggester も構築するため、app runtime group 間の composition dependency が明示的に増える。

## Public repository review

変更対象は application composition source、Library UI、architecture regression test、仕様・ADRのみである。credential、token、OAuth secret、実ユーザー URL、メールアドレス、蔵書情報、SMB接続情報、database、backup、private artifact は追加しない。
