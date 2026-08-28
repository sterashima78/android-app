# ADR-0166: LAN Web と Route composition の責務を分割する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0139](0139-app-entrypoint-and-worker-runtime-baseline.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md), [ADR-0162](0162-current-architecture-cleanup-guardrails.md)

## Context

P1 の owner boundary 整理後も、module ownership 自体には反しないが変更局所性を下げる責務集中と、過去 layout の残存があった。

- `feature:web:data` の `LanWebServer` が socket transport、client/auth validation、Repository read、RSS/Reddit projection、HTML rendering を単一 class で所有していた。
- Web data の一部 test source は package 宣言と物理 directory が一致していなかった。
- `AppRouteDependencies` が多数 feature の factory / capability construction を単一 class に集約していた。
- Android 17 の診断で生成され得る heap/profile artifact を public repository へ誤追加しないための `.gitignore` が public repository verifier より狭かった。

これらは Domain ownership や外部 API を変えず、現行 architecture の範囲内で整理できる。

## Decision

### 1. LAN Web を transport / read model / renderer に分離する

`feature:web:data` の ownership は維持し、責務を次へ分ける。

- `LanWebServer`: TCP socket、HTTP request parsing、network/client validation、token/cookie authentication、route dispatch、security response headers
- `LanWebReadModel`: `ArticleRepository` / `BookmarkRepository` / `FeedRepository` を利用した read-only page model の構築と RSS/Reddit presentation classification
- `LanWebRenderer`: typed page model からの HTML rendering と escaping

`LanWebServer` の外部 constructor、port、認証方式、read-only HTTP route は変更しない。新しい durable state や別 Context の table ownershipは導入しない。

### 2. Web data test source の package と物理 path を一致させる

`dev.terashima.yomitorirss.feature.web.data` package の test は同じ package path 配下へ配置する。source regression でこの一致を固定する。

### 3. AppRouteDependencies を薄い façade にする

既存 caller contract は維持し、composition を次へ分割する。

- `AppContentRouteDependencies`: RSS、Reddit、Bookmark、Mail、Summary、Chat、Knowledge、Library、YouTube 等
- `AppSupportingRouteDependencies`: Backup、Settings、AI task queue、Asset、Health、Task、Calendar、Workout、X 等

`AppRouteDependencies` は両 group を保持し、既存 property/function を forward するだけの façade とする。この grouping は Bounded Context を新設せず、application composition 内部の変更局所性のためだけに使う。

#### 2026-08-27 refinement: entrypoint capability を Route graph から取得しない

`MainActivity` の外部 Intent 処理のような app entrypoint 固有処理は、同じ feature capability が Route graph に存在していても `AppRouteDependencies` の内部構造を経由して取得しない。画面 composition 用 Route graph と entrypoint capability の変更理由を分離する。

共有 URL を Library へ追加する処理では、`MainActivityDependencies` が `addSharedWebBook` capability を直接受け取る。`YomitoriApplication` は `libraryRuntime.webLibraryMutator` と `backupChangeScheduler` から `NotifyingWebLibraryMutator` を composition し、その narrow capability を渡す。`YomitoriApp` の画面描画については従来どおり `routeDependencies` を利用する。

これにより外部 Intent 処理が `routeDependencies.library.addWebBook` へ到達せず、Route graph の内部 grouping や Library Route contract の変更から entrypoint 処理を独立させる。Library mutation 後の backup scheduling semantics は維持する。

#### 2026-08-28 refinement: MainActivity dependency façade を変更理由で分割する

ADR-0205 により app-shell presentation が `:app:presentation` へ分離された後も、executable `:app` の `MainActivityDependencies` は次の異なる変更理由を1つの class に保持していた。

- `AppRouteDependencies` と `LanWebServerController` を `MainActivity` の Compose presentation へ接続する責務
- share / external Intent から Bookmark / Library mutation capability を呼び出す責務

これらは同じ framework-created `MainActivity` から利用されるが、presentation と external entry routing は独立して変更される。そのため単一の provider lookup は維持したまま、取得する façade を次へ分割する。

- `MainActivityPresentationDependencies`: `AppRouteDependencies` と `LanWebServerController` のみを公開する
- `entry.IncomingIntentDependencies`: `SaveSharedBookmarkUseCase` と `addSharedWebBook` の narrow capability のみを保持する
- `MainActivityDependenciesProvider`: Android が生成する `MainActivity` 向けの監査済み provider contract として、上記2 façade を公開する

`MainActivity` は presentation rendering では `MainActivityPresentationDependencies`、`IncomingIntentHandler` の生成では `IncomingIntentDependencies` だけを利用する。旧 `MainActivityDependencies` aggregate は廃止する。

この refinement は新しい Gradle module、service locator、runtime lifetime を導入しない。`YomitoriApplication` が既存 application graph から両 façade を一度だけ composition し、framework provider lookup の例外箇所も `MainActivity` 1箇所のまま維持する。

### 4. local diagnostic artifact を git 管理対象外にする

public repository verifier が拒否する profiling/heap artifact とローカル trace を `.gitignore` でも除外する。

- `*.hprof`
- `*.trace`
- `*.perfetto-trace`
- `*.perfetto-trace-unredacted`
- `*.heapprofile`
- `*.heapdump`
- `*.heapsnapshot`

共有可能な text report の sanitizer 方針は既存 ADR を維持し、binary profiling artifact は repository に保存しない。

### 5. regression test で責務分割を固定する

`ArchitectureCleanupSourceTest` で次を検査する。

- `LanWebServer` が Repository query と HTML document markup を再び直接所有しない。
- `LanWebReadModel` が Repository read、`LanWebRenderer` が markup を所有する。
- Web data test source の package/path が一致する。
- `AppRouteDependencies` が feature Factory/Repository construction を再び直接所有しない。
- diagnostic artifact ignore rule が維持される。
- P1 の Reddit owner boundary と Summary prompt ownership の検査先を新しい composition group に合わせる。

`MainActivityDependenciesSourceArchitectureTest` では、共有 Library 追加が `routeDependencies.library` を経由せず narrow capability として直接 composition され、backup scheduling 付き mutator が維持されることに加え、presentation と incoming Intent の dependency façade が再び統合されないことを検査する。

## Consequences

### Positive

- network/auth、read-model、HTML の変更が分離され、LAN Web のレビュー範囲が小さくなる。
- Route composition が application runtime graph と同様に責務別 group へ分かれる。
- app entrypoint 固有の共有処理が Route graph の内部構造から独立する。
- MainActivity の presentation wiring と external Intent mutation が別 façade になり、不要な capability が各 caller に見えなくなる。
- framework provider lookup の例外数を増やさず、変更理由だけを同一 module 内で分離できる。
- test source layout の過去残存を自動検出できる。
- profiling/heap artifact の accidental commit を二重に防止できる。

### Negative

- application composition と Web data の class 数は増える。
- Route composition group は Domain Context と1対1ではないため、Domain ownership の根拠には使えない。
- entrypoint と Route が同じ mutation capability を利用する場合でも、application composition 側で別の narrow wiring が必要になる。
- MainActivity provider は2つの façade property を持つため、framework entry point の composition wiring は1段増える。
- LAN Web は引き続き server-side HTML string rendering を使用する。

## Verification

- `LanWebServerBoundaryTest`: server constructor が Domain Repository contract を受け取り database implementation を受け取らないこと。
- `LanWebServerTest` / `LanWebServerRepositoryTest`: HTML escaping contract。
- `ArchitectureCleanupSourceTest`: LAN Web responsibility、Web test package/path、Route façade、diagnostic ignore、P1 boundary regression。
- `MainActivityDependenciesSourceArchitectureTest`: entrypoint の共有 Library 追加が Route graph を経由せず、presentation / incoming Intent façade が分離され、backup scheduling 付き capability を直接利用すること。
- existing `verifyArchitecture`、全 unit test、release lint、public repository verifier。

## Documentation

- `docs/architecture/module-map.md` の Route composition を同期する。
- `docs/architecture/platform.md` に LAN Web responsibility boundary を記録する。
- ADR index を ADR-0164〜0166 の現状へ同期する。
- 2026-08-27 refinement は module ownership や Route composition group 自体を変更しないため、module map の構造記述は変更しない。
- 2026-08-28 refinement は executable `:app` 内の façade 分割であり、Gradle module ownership は変更しない。current architecture docs の MainActivity dependency 名だけを同期する。

## Public repository review

本変更は source responsibility、test layout、ignore rule、architecture documentation の整理である。credential、token、OAuth secret、実ユーザー URL / メール、SMB 接続情報、実蔵書・健康データ、database、backup、heap dump / trace 本体を追加しない。

2026-08-27 refinement も application composition と source architecture test の変更だけであり、credential、実ユーザーデータ、接続先情報、診断 artifact を追加しない。

2026-08-28 refinement も executable app 内の dependency façade、application composition wiring、source architecture test、current architecture documentation の変更だけであり、credential、token、private endpoint、実ユーザーデータ、診断 artifact を追加しない。
