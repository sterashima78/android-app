# Module Map

この文書は Gradle module の物理構成と ownership の読み方を示す。module 一覧そのものの正本は [`settings.gradle.kts`](../../settings.gradle.kts) とする。

## Top-level structure

```text
/
├── app/        application entry point / composition root / navigation
├── feature/    application-specific ownership
├── core/       cross-cutting technical capabilities
├── config/     machine-readable architecture configuration
├── docs/       specification / architecture / ADR
└── gradle/     build and architecture verification support
```

## App

```text
:app
```

`:app` は Application / Activity entry point、navigation graph、feature wiring、application-level configuration を担当する。feature 固有 business logic、durable persistence implementation、feature 固有 UI state の恒久的な所有場所にはしない。

feature 内で ViewModel / Screen 接続まで完結できる root Route は owning `:feature:<name>:ui` が所有する。Android permission / Activity Result、外部 Intent、複数 feature の state/action mapping など application composition が必要な adapter は `app/src/main/.../ui` に置く。`AppSection` / `MainTab` / `AppViewModel` のような app shell navigation state も同じ app UI ownership に置く。active tab ごとの app-shell presentation capability は `AppNavigationSpec` に集約し、各 composition host が独自の `MainTab` policy を重複して持たない。

`app/src/main/.../feature` は feature implementation の配置場所として使わず、production Kotlin source を置かない。新しい feature Route / Screen / adapter や app shell state をこの path へ追加しない。

`AppContainer` は application-scope graph の公開 facade とし、concrete feature graph の構築は責務別の `App*RuntimeDependencies` に分割する。これは repository lifetime を変えるための分割ではなく、composition root 内の可読性と変更局所性を保つための構造である。DI framework や route-level service locator は導入しない。

## Core capabilities

```text
:core:background
:core:database
:core:network
:core:web-collector
:core:ai-inference
:core:ai-runtime
:core:designsystem
```

`core` は複数 feature が共有する技術 capability を提供し、アプリ固有 Domain concept や feature-specific use case を所有しない。

`:core:ai-inference` は provider 非依存の単発テキスト推論 contract とモデル能力・進捗を所有する。`:core:ai-runtime` は Gemma / LiteRT-LM、tokenizer、Engine lifecycle、benchmark、Vision / Conversation などローカル実装固有の capability を所有し、`LocalAiTextInference` から共通 contract へ投影する。Summary / Knowledge / Library 等の prompt や生成ポリシーは owning feature に残す。

`:core:network` の HTTP transport は process-wide に共有し、`:app` の application graph が同じ `HttpClient` instance を runtime group と WorkerFactory へ渡す。feature 側は HTTP adapter の testability のため default constructor を持てるが、production composition で feature ごとの OkHttp connection pool を作らない。

## Feature modules

この表は `settings.gradle.kts` を正本とし、`scripts/verify_module_map.py` が feature / layer の一致を検査する。表を更新し忘れた場合は Architecture CI を失敗させる。

<!-- feature-modules:start -->
| Feature | Layers |
| --- | --- |
| ai-task-queue | domain / data / ui |
| backup | domain / data / ui |
| bookmark | domain / data / ui |
| article | domain / data / ui |
| asset | domain / data / ui |
| book-reader | domain / data / ui |
| calendar | domain / data / ui |
| chat | domain / data / ui |
| game | domain / ui |
| health | domain / data / ui |
| integrated | ui |
| library | domain / data / ui |
| knowledge | domain / data / ui |
| mail | domain / data / ui |
| reddit | domain / data / ui |
| rss | domain / data / ui |
| summary | domain / data / ui |
| settings | domain / data / ui |
| task | domain / data / ui |
| web | domain / data / ui |
| widget | domain / data / ui |
| workout | domain / data / ui |
| youtube | domain / data / ui |
| x | domain / data / ui |
<!-- feature-modules:end -->

全 feature に3 layer を強制しない。独立した責務・依存・ビルド境界として価値がある layer だけを module 化する。

## Layer relationship

同一 feature 内では次を基本とする。

```text
:feature:<name>:ui
        |
        v
:feature:<name>:domain
        ^
        |
:feature:<name>:data
        |
        v
:core:<capability>
```

Domain が Repository 等の contract を定義し、Data が実装する。`:app` が必要な implementation を composition する。

feature 間依存は許容するが、次を守る。

```text
Domain -> other feature Domain                  allowed
Data   -> other feature Domain / Data / core   allowed when ownership requires it
UI     -> other feature Domain / UI            allowed when presentation requires it
app    -> feature Domain / Data / UI            allowed for composition

core   -> feature                               forbidden
domain -> UI / Data                             forbidden
UI     -> concrete Data implementation          forbidden
circular Gradle dependency                      forbidden
```

Data -> other feature Data は物理 dependency として許容される場合があるが、別 Context の persistence ownership を越える根拠にはならない。cross-context access は [persistence.md](persistence.md) と [context-map.md](context-map.md) の規則を優先する。

## Module boundary and Domain boundary

`:feature:<name>` は ownership / build boundary であり、Bounded Context や Aggregate と 1 対 1 ではない。

現在の代表例では `:feature:article` は実装名として維持されているが、Domain 上は RSS 記事だけに限定されない Content Context / `ContentItem` に近い。module rename は ubiquitous language が安定してから別の設計判断として扱う。

## How to update

- module の追加・削除・layer 構成変更: `settings.gradle.kts` と本表を同じ PR で更新する。Architecture CI が両者の不一致を検出する。
- dependency rule を変更する: ADR を追加または更新し、`verifyArchitecture` と [principles.md](principles.md) を更新する。
- module 名と Domain Context の関係が変わる: [context-map.md](context-map.md) と必要な ADR を更新する。
- app composition adapter / app shell navigation の配置や feature UI ownership を変更する: ADR と本 `App` 節を同期し、app source layout の regression test を更新する。
- AppContainer の runtime group 分割を変更する: application scope / caller contract を維持し、必要なら ADR と本 `App` 節を更新する。
- shared core runtime の lifetime を変更する: application composition と background entry point の両方を確認し、ADR と regression test を更新する。

## Sources

- [`settings.gradle.kts`](../../settings.gradle.kts)
- [`scripts/verify_module_map.py`](../../scripts/verify_module_map.py)
- [ADR-0001](../adr/0001-layered-architecture.md)
- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0004](../adr/0004-concept-oriented-modules.md)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
- [ADR-0128](../adr/0128-calendar-read-model-and-android-calendar-provider.md)
- [ADR-0142](../adr/0142-app-route-and-task-widget-ownership-cleanup.md)
- [ADR-0144](../adr/0144-composition-runtime-groups-and-module-map-verification.md)
- [ADR-0150](../adr/0150-app-shell-navigation-ui-ownership.md)
- [ADR-0155](../adr/0155-application-scope-http-transport.md)
- [ADR-0156](../adr/0156-active-tab-message-capability-policy.md)
- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
