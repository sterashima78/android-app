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

## Consequences

### Positive

- network/auth、read-model、HTML の変更が分離され、LAN Web のレビュー範囲が小さくなる。
- Route composition が application runtime graph と同様に責務別 group へ分かれる。
- test source layout の過去残存を自動検出できる。
- profiling/heap artifact の accidental commit を二重に防止できる。

### Negative

- application composition と Web data の class 数は増える。
- Route composition group は Domain Context と1対1ではないため、Domain ownership の根拠には使えない。
- LAN Web は引き続き server-side HTML string rendering を使用する。

## Verification

- `LanWebServerBoundaryTest`: server constructor が Domain Repository contract を受け取り database implementation を受け取らないこと。
- `LanWebServerTest` / `LanWebServerRepositoryTest`: HTML escaping contract。
- `ArchitectureCleanupSourceTest`: LAN Web responsibility、Web test package/path、Route façade、diagnostic ignore、P1 boundary regression。
- existing `verifyArchitecture`、全 unit test、release lint、public repository verifier。

## Documentation

- `docs/architecture/module-map.md` の Route composition を同期する。
- `docs/architecture/platform.md` に LAN Web responsibility boundary を記録する。
- ADR index を ADR-0164〜0166 の現状へ同期する。

## Public repository review

本変更は source responsibility、test layout、ignore rule、architecture documentation の整理である。credential、token、OAuth secret、実ユーザー URL / メール、SMB 接続情報、実蔵書・健康データ、database、backup、heap dump / trace 本体を追加しない。
