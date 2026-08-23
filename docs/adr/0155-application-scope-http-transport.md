# ADR-0155: HTTP transport を application scope で共有する

- Status: Accepted
- Date: 2026-08-23
- Refines: [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md), [ADR-0146](0146-workmanager-worker-factory-injection.md)
- Related: [ADR-0084](0084-mosaic-application-branding.md)

## Context

`:core:network` の `HttpClient` は共通 contract を提供していたが、複数の feature adapter が `HttpClient.create()` を default constructor から個別に呼び出していた。従来の `create()` は呼び出すたびに新しい `OkHttpClient` を生成するため、connection pool、dispatcher、socket resource が feature ごとに分散する状態だった。

また Summary の content fetch Worker は `ArticleContentClient` を Worker 内で直接構築しており、ADR-0146 で確定した WorkerFactory constructor injection の方向とも揃っていなかった。

## Decision

- `HttpClient.create()` は process-wide に共有される単一 transport を返す。
- `AppContainer` はその transport を application graph の dependency として所有する。
- app の content / supporting / feature runtime group は同じ `HttpClient` を受け取り、明示的に注入できる adapter にはその instance を渡す。
- 既存 adapter の default constructor は unit test や段階的移行のため残してよいが、`HttpClient.create()` 自体が共有 transport を返すため parallel OkHttp pool を生成しない。
- WorkManager が生成する Summary content fetch Worker の HTTP adapter は Worker 内で構築せず、`SummaryWorkerFactory` から constructor injection する。
- static HTTP と WebView rendering は別 capability とする。Web Library の ADR-0154 による isolated WebView fallback はこの変更の対象外であり、static metadata fetch だけ共有 `HttpClient` を利用する。

## Consequences

### Positive

- connection pool と dispatcher を process 内で共有できる。
- feature ごとの HTTP adapter が同一 timeout / redirect / User-Agent policy を利用しやすくなる。
- foreground と WorkManager background の content fetch が同じ transport lifetime に揃う。
- route や Worker を service locator にせず application composition から dependency を渡せる。

### Negative

- process-wide transport の設定変更は複数 feature に影響するため、`:core:network` の policy 変更は cross-cutting change としてレビューする必要がある。
- default constructor は互換のため残るので、application graph での明示注入を architecture regression test でも固定する必要がある。

## Verification

- `HttpClient.create()` が同じ instance を返す unit test を持つ。
- `AppContainer` が shared `HttpClient` を runtime group へ渡すことを source architecture test で固定する。
- `SummaryContentFetchWorker` が `ArticleContentClient()` を直接構築しないことを source architecture test で固定する。
- PR CI の unit test、architecture verification、lint、public repository verification を継続する。

## Public repository review

HTTP lifetime と公開 User-Agent のみを扱い、credential、token、実ユーザー URL、メールアドレス、SMB 接続情報、backup、private endpoint は追加しない。
