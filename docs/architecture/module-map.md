# Module Map

この文書は Gradle module の物理構成と ownership の読み方を示す。module 一覧そのものの正本は [`settings.gradle.kts`](../../settings.gradle.kts) とする。

## Top-level structure

```text
/
├── app/        application shell / app-only platform adapters / composition module
├── feature/    application-specific ownership
├── core/       cross-cutting technical capabilities
├── config/     machine-readable architecture configuration
├── docs/       specification / architecture / ADR
└── gradle/     build configuration and architecture verification support
```

共通 external dependency version は、移行済み dependency について [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) を正本とする。module-local `build.gradle.kts` は generated `libs` accessor を利用する。Android platform baseline は別契約であり、各 Android module の `minSdk = 34` 明示と architecture verification を維持する。

## App

```text
:app
:app:composition
```

`:app` は Application / Activity entry point、navigation graph、app-shell UI、Android permission / Activity Result、外部 Intent 等の application/platform boundary を担当する。feature 固有 business logic、durable persistence implementation、feature 固有 UI state の恒久的な所有場所にはしない。`:app` は `:feature:*:data` へ直接依存せず、concrete feature implementation の application-scope graph は `:app:composition` を介して利用する。

`:app:composition` は application-scope の高 fan-in composition boundary である。`AppContainer`、責務別 `App*RuntimeDependencies`、Route dependency composition、DB schema aggregation、WorkerFactory、application startup の background observer wiring、feature-specific provider adapter を所有し、必要な `:feature:*:domain` / `:feature:*:data` / `:feature:*:ui` と `:core:*` を接続する。これは新しい Domain ownership ではなく、`:app` の compile classpath から concrete Data implementation を除外するための build/dependency boundary である。

feature 内で ViewModel / Screen 接続まで完結できる root Route は owning `:feature:<name>:ui` が所有する。複数 feature を利用する presentation でも、独立した変更理由と名前を持つ feature responsibility であれば owning feature が state/action mapping を所有する。`:app` に残す adapter は Android permission / Activity Result、外部 Intent、app-shell navigation など executable shell の責務に限定する。`AppSection` / `MainTab` / `AppViewModel` のような app shell navigation state も同じ app UI ownership に置く。active tab ごとの app-shell presentation capability は `AppNavigationSpec` に集約し、各 composition host が独自の `MainTab` policy を重複して持たない。

`Integrated` はこの原則の代表例であり、RSS / Reddit / YouTube / Mail の state projection、target dispatch、item action、Integrated Route を `:feature:integrated:ui` が所有する。`:app` は source ViewModel の wiring、Mail tab への遷移、Android 外部 URL 起動などの callback だけを接続する。詳細は ADR-0188 を参照する。

`Settings` も同じ ownership 原則を適用する。Models / ChatGPT Debug / AI Execution Settings に加え、Summary Prompt / AI Task Queue / Drive Backup を Settings から開くための overlay selection と presentation policy は `:feature:settings:ui` が所有する。各 sibling feature は再利用可能 UI と task semantics を所有し続け、`:app` の `SettingsRoute` は Android Activity Result、backup restore 後の app-shell navigation、feature dependency wiring、platform callback の接続だけを担当する。詳細は ADR-0192 を参照する。

`app/src/main/.../feature` は feature implementation の配置場所として使わず、production Kotlin source を置かない。新しい feature Route / Screen / adapter や app shell state をこの path へ追加しない。

`AppContainer` は `:app:composition` に置く application-scope graph の公開 facade とし、concrete feature graph の構築は責務別の `App*RuntimeDependencies` に分割する。これは repository lifetime を変えるための分割ではなく、composition boundary 内の可読性と変更局所性を保つための構造である。DI framework や route-level service locator は導入しない。

Route composition も同じ原則で分割する。`AppRouteDependencies` は既存 caller contract を維持する薄い façade とし、content-facing な factory/capability construction は `AppContentRouteDependencies`、supporting/device-facing な construction は `AppSupportingRouteDependencies` が担当する。この grouping は Bounded Context を新設するものではなく、application composition 内部の責務分割である。

## Core capabilities

```text
:core:background
:core:database
:core:network
:core:web-collector
:core:ai-inference
:core:ai-cloud-openai
:core:ai-runtime
:core:designsystem
```

`core` は複数 feature が共有する技術 capability を提供し、アプリ固有 Domain concept や feature-specific use case を所有しない。

`:core:ai-inference` は provider 非依存の単発テキスト推論 contract とモデル能力・進捗を所有する。`:core:ai-runtime` は Gemma / LiteRT-LM、tokenizer、Engine lifecycle、benchmark、Vision / Conversation などローカル実装固有の capability を所有し、`LocalAiTextInference` から共通 contract へ投影する。Summary / Knowledge / Library 等の prompt や生成ポリシーは owning feature に残す。

`:core:ai-cloud-openai` は ChatGPT OAuth、credential refresh、ChatGPT account identity、Codex model catalog、Codex Responses transport、native Web search request / response mapping など OpenAI/ChatGPT 固有の cloud protocol adapter を所有する。endpoint、OAuth field、model catalog field、stream event type、Web search wire format はこの module に隔離し、feature は provider protocol を直接扱わない。Summary は provider 選択、要約 prompt、metadata policy を所有し、cloud adapter はそれらの task semantics を持たない。

`:core:background` は background execution の共有技術 policy を所有する。端末内推論を使う task の global pause / charging resume は `LocalAiBackgroundExecutionPreferences`、cloud provider を使う task の global pause は `CloudAiBackgroundExecutionPreferences` に分離する。Cloud pause に charging resume semantics は持たせない。

`:core:network` の HTTP transport は process-wide に共有し、`:app:composition` の application graph が同じ `HttpClient` instance を runtime group と WorkerFactory へ渡す。feature 側は HTTP adapter の testability のため default constructor を持てるが、production composition で feature ごとの OkHttp connection pool を作らない。

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

Summary の Local / ChatGPT provider 選択、URL 起点の cloud 要約可否、cloud metadata generation policy は `:feature:summary` が所有する。Local provider は prepared article content と `LocalAiBackgroundTaskGate` を利用し、ChatGPT provider は本文 prefetch を行わず URL と prompt を cloud capability へ渡す。Cloud path の task progress は local pipeline の `FETCHING_ARTICLE` を流用せず、cloud summary / metadata generation の semantic stage を記録する。

Knowledge の Local / ChatGPT provider 選択は `:feature:knowledge` が所有する。自動Wiki再構築、新規ページ生成、LLM編集はいずれもユーザーが明示選択したproviderを利用し、入力内容によるcloud eligibilityや自動routingは行わない。Local background buildだけ `LocalAiBackgroundTaskGate` とLocal pause / charging resumeを利用し、ChatGPT background buildはCloud pauseとnetwork constraintを利用する。enqueue済みbuildはprovider snapshotをWorkManager inputへ保持し、provider変更時は新providerのworkへ置き換える。

`:feature:settings` は provider connection/model setting と task routing setting を別 presentation surface として表示する。`ChatGPT / Codex` は login・model catalog・model選択・接続テストを扱い、`AI実行設定` は各 owning feature の provider routing settingを操作する。Settings 自身は routing decision を所有しない。Settings から起動する Summary Prompt / AI Task Queue / Drive Backup については、各 feature の public UI contract を利用しつつ、どの overlay を表示するかという Settings 固有の presentation state を `:feature:settings:ui` が所有する。

`:feature:ai-task-queue` は複数 feature の task read model と runtime execution control を集約する。Local AI pause と Cloud AI pause は独立して表示・変更し、充電時自動再開は Local AI にだけ適用する。SummaryとKnowledgeのprovider labelを表示するが、task固有の stop / cancel / retry state は引き続き owning feature が所有する。

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

Domain が Repository 等の contract を定義し、Data が実装する。`:app:composition` が必要な implementation を composition し、`:app` はその公開 facade/capability を利用する。

feature 間依存は許容するが、次を守る。

```text
Domain           -> other feature Domain                  allowed
Data             -> other feature Domain / Data / core   allowed when ownership requires it
UI               -> other feature Domain / UI            allowed when presentation requires it
app              -> feature Domain / UI                   allowed for executable shell
app              -> feature Data                          forbidden
app:composition  -> feature Domain / Data / UI            allowed for composition

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
- shared dependency version を catalog へ追加・変更する: `gradle/libs.versions.toml` を正本とし、対象 module の alias 利用と regression test を同じ変更で更新する。
- module 名と Domain Context の関係が変わる: [context-map.md](context-map.md) と必要な ADR を更新する。
- app composition adapter / app shell navigation の配置や feature UI ownership を変更する: ADR と本 `App` 節を同期し、app source layout の regression test を更新する。
- `:app:composition` の公開 facade / concrete feature dependency を変更する: `:app` に `:feature:*:data` dependency が漏れないことと、application scope lifetime を維持することを確認する。
- shared core runtime の lifetime を変更する: application composition と background entry point の両方を確認し、ADR と regression test を更新する。

## Sources

- [`settings.gradle.kts`](../../settings.gradle.kts)
- [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml)
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
- [ADR-0157](../adr/0157-mosaic-external-and-compatibility-identifiers.md)
- [ADR-0158](../adr/0158-bounded-book-page-geometry-cache.md)
- [ADR-0159](../adr/0159-isolate-smb-vision-inference-process.md)
- [ADR-0165](../adr/0165-provider-neutral-text-inference-contract.md)
- [ADR-0166](../adr/0166-lan-web-and-route-composition-responsibility-split.md)
- [ADR-0167](../adr/0167-gradle-version-catalog-baseline.md)
- [ADR-0168](../adr/0168-chatgpt-codex-cloud-debug-adapter.md)
- [ADR-0171](../adr/0171-summary-local-chatgpt-routing-and-web-fetch.md)
- [ADR-0172](../adr/0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0175](../adr/0175-knowledge-local-chatgpt-routing.md)
- [ADR-0188](../adr/0188-integrated-feature-owns-cross-feature-presentation.md)
- [ADR-0192](../adr/0192-settings-feature-owns-cross-feature-presentation.md)
- [ADR-0198](../adr/0198-app-composition-module-boundary.md)
