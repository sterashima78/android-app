# Code Organization

この文書は、Gradle module の ownership を変えずに module 内の実装責務を整理する現在の規則を示す。

## Module boundary と file boundary

Gradle module は ownership、依存方向、build boundary を表す。file / class / package は、同一 module 内の変更理由と可読性を局所化するために使う。

単一 file / class が複数の独立した変更理由を持つ場合は、別 module の新設を最初の選択肢にせず、同一 module 内で次を優先する。

- cohesive な class / function を別 file に分ける
- module 内 helper は必要がない限り `internal` にする
- 同じ変更理由を持つ実装を意味のある package にまとめる
- `common` / `util` のような汎用置き場を作らない
- file size の固定値ではなく、変更理由、依存、lifecycle、テスト境界を分割判断に使う

小さな責務を分けるためだけに Gradle module を増やさない。一方、同じ module に属することを理由に複数責務を1 fileへ集約しない。

## `:app` package layout

executable `:app` の root package `dev.terashima.yomitorirss` は Application / Activity entry point を中心にする。application-scope graph の narrow facade は同じ package namespace を使う `:app:composition` に置き、Gradle module boundary で executable shell と concrete composition を分ける。

app ownership のまま独立した実装責務を持つ code は、意味のある subpackage へ配置する。

```text
dev.terashima.yomitorirss
├── MainActivity / YomitoriApplication
├── entry/        external Intent、share、widget launch routing
├── security/     app lock、認証 session、secure-window transition
├── diagnostics/  startup crash、memory diagnostics、diagnostic presentation
├── platform/     Custom Tab、OS permission、platform dialog host
└── ui/           app-shell navigation / presentation
```

`:app:composition` は application graph の facade と composition-only implementation を分ける。`AppContainer`、`AppRouteDependencies`、application database schema、WorkerFactory creation など app shell が参照する narrow API は module root package に残す。一方、startup/background wiring のように独立した変更理由を持つ internal implementation は `composition/` 以下の責務 package に配置する。

```text
dev.terashima.yomitorirss
├── AppContainer / AppRouteDependencies / application composition API
├── composition/
│   └── background/  startup observer / one-shot scheduler wiring
└── platform/
    └── authorization/  Gmail / Google Books の feature Data manager と Activity Result host を接続する bridge
```

Activity Result launcher / callback host は executable `:app` が所有し、feature Data の authorization manager との接続に必要な dependency bridge は `:app:composition/platform/authorization` が所有する。

package 分割は Gradle module の追加を意味しない。application graph の ownership と lifecycle が同じで、package で責務を局所化できる場合は `:app:composition` 内に留める。

この一覧は closed set ではない。新しい package は独立した責務名を持つ場合に追加する。

feature policy や provider technical implementation は app root package の整理対象にせず、owning feature / core module へ配置する。たとえば Workout の provider routing / prompt budget policy は `:feature:workout:data`、provider-neutral contract への ChatGPT adapter は `:core:ai-cloud-openai`、Summary / Knowledge 固有の provider failure mapping と prompt/cache policy は各 feature の Data layer が所有する。`:app:composition` はこれらの instance wiring のみを担当する。

OpenAI provider client と process-wide HTTP transport の concrete construction は `:app:composition` の責務であり、executable `:app` は `:core:ai-cloud-openai` / `:core:network` へ直接依存しない。app-owned diagnostics が直接利用する `:core:ai-runtime` / `:core:background` のように、executable shell 自身に明確な利用理由がある core capability はこの限りではない。

## MainActivity

`MainActivity` は Android lifecycle と app-shell wiring の entry point とする。

具体的な state machine や workflow は専用の class / file に委譲する。現在は次の責務を分離している。

- app lock: `security.AppLockCoordinator`
- incoming Intent: `entry.IncomingIntentHandler`
- crash diagnostics UI: `diagnostics.CrashDiagnosticsContent`
- LAN Web Server permission / dialog: `platform.LanWebServerDialogHost`
- Custom Tab: `platform.WebContentLauncher`

feature business logic は引き続き `MainActivityDependencies` 等の narrow contract 経由で利用する。

## Review rule

feature / core / app のいずれでも、変更時に既存 file の責務が増える場合は次を確認する。

1. 新しい処理は既存 class と同じ変更理由か。
2. lifecycle、IO、presentation、routing、state machine 等の別責務が混在していないか。
3. 別 file / package に分けることで public API を増やさず変更を局所化できるか。
4. module ownership 自体を変える必要があるか。それとも同一 module 内の分割で十分か。

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
