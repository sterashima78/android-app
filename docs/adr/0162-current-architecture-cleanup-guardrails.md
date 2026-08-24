# ADR-0162: current architecture cleanup の残存境界を guardrail 化する

- Status: Accepted
- Date: 2026-08-24
- Refines: [ADR-0009](0009-separate-reddit-feature.md), [ADR-0122](0122-current-architecture-documentation.md), [ADR-0136](0136-public-repository-content-verification.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md), [ADR-0155](0155-application-scope-http-transport.md), [ADR-0157](0157-mosaic-external-and-compatibility-identifiers.md)

## Context

大規模な ownership cleanup 後も、現在形ドキュメントの ADR link、source-specific classification、application composition の runtime graph、外部 version identifier、Android profiling artifact の公開防止には review 依存の残差があった。

具体的には次が確認された。

- `docs/architecture/context-map.md` に ADR renumber 後の古い link target が残っていたが、ADR integrity verifier は `docs/adr` 内しか検査していなかった。
- RSS presentation から Reddit を除外する判定が `AppRouteDependencies` に条件式として残り、Reddit-owned URL classification を composition root が再構成していた。
- `AppRouteDependencies` と Worker wiring が `AppFeatureRuntimeDependencies` という generic runtime graph を直接参照できた。
- app version が Gradle `versionName` のほか、HTTP User-Agent と APK artifact filename に重複していた。
- Android 17 の `ProfilingManager` が app-private profiling artifact を生成する一方、public repository verifier は profiling / heap artifact suffix を拒否していなかった。

これらは直ちに機能障害を起こすものではないが、次回の変更時に current architecture から再び逸脱しやすい境界である。

## Decision

### 1. current architecture document も ADR reference integrity の対象にする

`scripts/verify_adr_integrity.py` は ADR file 自身に加え、次の現在形文書からの ADR reference / ADR link target を検査する。

- `docs/adr/README.md`
- `docs/architecture/*.md`
- `docs/spec.md`

`.github/workflows/adr-integrity.yml` もこれらの current document 変更で起動する。

全 Markdown link を generic link checker として扱うのではなく、ADR identifier と ADR link target の整合性という既存 verifier の責務を拡張する。

### 2. Reddit source classification は Reddit-owned boundary から公開する

Reddit URL / Article classification の低レベル関数を application composition が組み合わせない。

`:feature:reddit:domain` が `RedditSourceBoundary` を公開し、RSS Route composition は次の named policy を利用する。

- generic RSS article に含めるか
- generic RSS feed presentation に含めるか
- RSS subscription input として受け付けるか

これにより Reddit URL semantics の変更時に app shell へ同じ条件式を複製しない。

### 3. generic feature runtime graph を Route / Worker へ公開しない

`AppFeatureRuntimeDependencies` は `AppContainer` 内部の concrete construction group とし、Route、WorkerFactory、cross-feature runtime は直接取得しない。

`AppContainer` は caller ごとに必要な narrow application-scope capability だけを公開する。現時点では Health repository、Library runtime、Library Worker runtime、Knowledge build scheduler 等である。

新しい DI framework や追加 Gradle module は導入しない。これは lifetime を変更する設計ではなく、既存 application-scope graph の exposure surface を狭める整理である。

### 4. app version の正本を Android build version にする

`app/build.gradle.kts` の `versionName` を app version の正本とする。

- production HTTP User-Agent は `BuildConfig.VERSION_NAME` から version を組み立てる。
- `core:network` は app version を知らず、User-Agent string を application composition から受け取る。
- OkHttp connection pool は process-wide に共有し、User-Agent ごとの wrapper は同じ transport を利用する。
- CI の APK filename は build 後の `output-metadata.json` に記録された `versionName` から導出する。

これにより version bump 時に User-Agent や artifact filename の別ハードコードを更新しない。

### 5. ProfilingManager artifact を public repository verifier で拒否する

Android `ProfilingManager` の result は app-private storage に保持する診断データであり、source repository へ追加しない。

public repository verifier は少なくとも次の profile / heap artifact suffix を high-confidence private artifact として拒否する。

- `.perfetto-trace`
- `.perfetto-trace-unredacted`
- `.hprof`
- `.heapprofile`
- `.heapdump`
- `.heapsnapshot`

binary file の内容を secret pattern scan できないことを path-based rejection で補完する。

## Consequences

### Positive

- ADR renumber や file rename 後の current architecture 文書の stale link を CI で検出できる。
- RSS / Reddit の product boundary が source owner に集約される。
- app composition の generic runtime graph が service locator 的に広がりにくくなる。
- version bump の更新箇所を減らせる。
- memory investigation artifact を誤って public repository へ commit するリスクを下げられる。

### Negative

- ADR integrity workflow の起動対象が増え、architecture/spec 文書変更でも verifier が実行される。
- `HttpClient.create()` は User-Agent wrapper を返すため、wrapper identity と connection-pool identity は別概念になる。
- AppContainer に narrow capability accessor が増えるが、generic graph を公開するより caller dependency が明確になる。

## Verification

- `scripts.test_verify_adr_integrity` で current architecture / spec の stale ADR reference を fixture 化する。
- `RedditSourceBoundaryTest` で Reddit community / thread / feed と通常 RSS の分類を固定する。
- app source regression test で Route の Reddit low-level classification import と generic `featureRuntimeDependencies` access の再導入を禁止する。
- core network test で同一 User-Agent wrapper の再利用を固定する。
- app source regression test で User-Agent が `BuildConfig.VERSION_NAME` 由来であり、core network / APK workflow に旧 version literal がないことを固定する。
- public repository verifier test で profiling / heap artifact path を拒否する。
- PR では通常の Architecture / Test / Lint / public repository verification を実行する。

## Public repository review

この変更で追加する test data は架空の Reddit URL、version、profiling filename のみとする。実ユーザー URL、メールアドレス、書籍情報、健康データ、credential、profiling artifact 本体は含めない。
