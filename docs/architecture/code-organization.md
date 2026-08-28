# Code Organization

この文書は、Gradle module の ownership と module 内の実装責務を整理する現在の規則を示す。

## Module boundary と file boundary

Gradle module は ownership、依存方向、build boundary を表す。file / class / package は、同一 module 内の変更理由と可読性を局所化するために使う。

単一 file / class が複数の独立した変更理由を持つ場合は、別 module の新設を最初の選択肢にせず、同一 module 内で次を優先する。

- cohesive な class / function を別 file に分ける
- module 内 helper は必要がない限り `internal` にする
- 同じ変更理由を持つ実装を意味のある package にまとめる
- `common` / `util` のような汎用置き場を作らない
- file size の固定値ではなく、変更理由、依存、lifecycle、テスト境界を分割判断に使う

小さな責務を分けるためだけに Gradle module を増やさない。一方、compile classpath や依存方向を分離する価値がある場合は build boundary として module を設ける。

## App boundary layout

app ownership は executable、presentation、composition の3つの Gradle boundary に分ける。

```text
:app
├── MainActivity / YomitoriApplication
├── entry/        external Intent、share、widget launch routing
├── security/     app lock、認証 session、secure-window transition
├── diagnostics/  startup crash、memory diagnostics、diagnostic presentation
└── platform/     Custom Tab、component/lifecycle-level OS permission / dialog host

:app:presentation
└── dev.terashima.yomitorirss.ui/
    ├── YomitoriApp / Theme
    ├── AppNavHost / navigation graph registration
    ├── AppSection / AppNavigationSpec / navigation chrome
    └── app-owned Route / feature UI / Composable Activity Result composition

:app:composition
├── AppContainer.kt
├── AppRouteDependencies.kt
├── AppDatabaseSchema.kt
├── AppWorkerFactory.kt
├── composition/
│   ├── ai/           local / cloud AI primitive composition
│   ├── background/   startup observer / one-shot scheduler wiring
│   ├── content/      Article / Bookmark / RSS / Reddit / YouTube graph
│   ├── crossfeature/ owner repository 構築後の cross-feature service composition
│   ├── health/       Health Connect repository composition
│   ├── knowledge/    Knowledge persistence/generation と task runtime composition
│   ├── library/      Library / SMB / book reader / authorization composition
│   ├── route/        content / supporting route dependency construction
│   └── supporting/   independent/supporting repository と platform adapter composition
└── platform/
    └── authorization/  Gmail / Google Books の feature Data manager と Activity Result host を接続する bridge
```

executable `:app` の root package `dev.terashima.yomitorirss` は Application / Activity entry point を中心にする。app-shell production UI は `app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui` を正本とし、`app/src/main/.../ui` へ戻さない。

`:app:presentation` は feature UI の composition と app-wide navigation/chrome を所有するが、feature 固有 state/policy の owner にはならない。feature 内で完結する Screen / Route / projection は owning `:feature:<name>:ui` に残し、app presentation は callback、ViewModel factory、navigation、Composable Activity Result 等の app-wide wiring に限定する。

Composable の lifecycle 内で feature authorization、calendar permission、document picker 等を接続する Activity Result launcher は `:app:presentation` に置ける。一方、LAN Web Server notification permission/dialog、Custom Tab、app lock transition のように Activity/component lifecycle や executable-only state を扱う host は `:app` に残す。

`:app:presentation` は `:app` へ依存せず、`:feature:*:data` や database / WorkManager infrastructure を参照しない。runtime capability は `:app:composition` の narrow facade と feature Domain/UI contract から受け取る。

`:app:composition` は application graph の facade と composition-only implementation を分ける。module root package には executable/presentation boundary が直接参照する narrow API だけを残し、concrete graph construction は `composition/` 以下の責務 package に配置する。

root package に `App*RuntimeDependencies` の generic implementation を追加しない。複数 feature の concrete wiring が増えた場合も、まず変更理由を表す責務 package を選び、異なる lifecycle / dependency boundary が必要になるまでは `:app:composition` module 内に留める。

旧 `AppFeatureRuntimeDependencies` のように Health / Knowledge background task / Library runtime を1つに束ねる generic group は使わない。現在は `composition/health`、`composition/knowledge`、`composition/library` がそれぞれの application-scope construction を所有する。

feature Data の authorization manager との接続に必要な dependency bridge は `:app:composition/platform/authorization` が所有し、Composable launcher/callback host は `:app:presentation` が利用する。

この一覧は closed set ではない。新しい package は独立した責務名を持つ場合に追加する。

feature policy や provider technical implementation は app boundary の整理対象にせず、owning feature / core module へ配置する。たとえば Workout の provider routing / prompt budget policy は `:feature:workout:data`、provider-neutral contract への ChatGPT adapter は `:core:ai-cloud-openai`、Summary / Knowledge 固有の provider failure mapping と prompt/cache policy は各 feature の Data layer が所有する。`:app:composition` はこれらの instance wiring のみを担当する。

OpenAI provider client と process-wide HTTP transport の concrete construction は `:app:composition` の責務であり、`:app` / `:app:presentation` は `:core:ai-cloud-openai` / `:core:network` の concrete integration を直接参照しない。app-owned diagnostics が直接利用する `:core:ai-runtime` / `:core:background` のように、executable shell 自身に明確な利用理由がある core capability はこの限りではない。

## MainActivity

`MainActivity` は Android lifecycle と top-level app-shell wiring の entry point とする。

- root `NavController` を app-lock conditional UI より上で保持する
- `:app:presentation` の `YomitoriTheme` / `YomitoriApp` へ navigation と callback を渡す
- app lock: `security.AppLockCoordinator`
- incoming Intent: `entry.IncomingIntentHandler`
- crash diagnostics UI: `diagnostics.CrashDiagnosticsContent`
- LAN Web Server permission / dialog: `platform.LanWebServerDialogHost`
- Custom Tab: `platform.WebContentLauncher`

feature business logic は引き続き `MainActivityDependencies` 等の narrow contract 経由で利用する。`MainActivity` 自身は feature ViewModel や feature UI を直接所有しない。

## Review rule

feature / core / app のいずれでも、変更時に既存 file の責務が増える場合は次を確認する。

1. 新しい処理は既存 class と同じ変更理由か。
2. lifecycle、IO、presentation、routing、state machine 等の別責務が混在していないか。
3. 別 file / package に分けることで public API を増やさず変更を局所化できるか。
4. module ownership 自体を変える必要があるか。それとも同一 module 内の分割で十分か。
5. compile classpath を分離することで禁止依存を構造的に不可視化できるか。

再発しやすく機械検査できる drift が見つかった場合だけ architecture verification を追加する。単純な行数制限は設けない。

## Sources

- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0101](../adr/0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0122](../adr/0122-current-architecture-documentation.md)
- [ADR-0166](../adr/0166-lan-web-and-route-composition-responsibility-split.md)
- [ADR-0193](../adr/0193-within-module-responsibility-and-app-package-structure.md)
- [ADR-0196](../adr/0196-app-boundary-ownership-cleanup.md)
- [ADR-0200](../adr/0200-app-composition-module-boundary.md)
- [ADR-0203](../adr/0203-feature-owned-provider-policy-adapters.md)
- [ADR-0204](../adr/0204-app-composition-internal-package-ownership.md)
- [ADR-0205](../adr/0205-app-presentation-module-boundary.md)
