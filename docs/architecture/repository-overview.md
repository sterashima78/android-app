# Repository Architecture Overview

この文書は `android-app` の現在の構造を、Archify の architecture diagram と同じ一次経路で読むための入口である。モジュール一覧や個別ルールの正本を置き換えるものではなく、詳細は既存 architecture docs / ADR を参照する。

- [Interactive architecture diagram](repository-overview.html)
- [Archify source specification](repository-overview.architecture.json)
- [Module map](module-map.md)
- [Architecture principles](principles.md)
- [Testing strategy](testing.md)
- [ADR index](../adr/README.md)

## Primary path

```text
User
  |
  v
:app
  | app-shell navigation / composition / platform wiring
  v
:feature:<name>:ui
  | domain API
  v
:feature:<name>:domain
  ^
  | implements
:feature:<name>:data
```

`:app` は Android の entry point、app-shell navigation、feature wiring、application-scope dependency graph を所有する。feature 固有の UI state、business rule、永続化実装を恒久的に所有しない。

feature UI は Route / Screen / ViewModel / UI state を所有し、Domain の契約を利用する。Domain は Android、SQLite、HTTP、WorkManager の具体型から独立し、Repository 等の contract を定義する。Data はその contract を実装し、必要に応じて shared core capability と Android / external service の境界へ接続する。

## Supporting paths

`core` は feature 間で共有する技術 capability だけを所有する。現在は database、network、background execution、web collector、design system、AI inference/runtime/cloud adapter などが独立した module として定義されている。generic な `common` / `util` module に application-specific ownership を集約しない。

Data layer は永続化、HTTP、background runtime、AI runtime などの concrete integration を所有する。durable table の直接アクセスは owning feature data module に限定し、他 Context は owner が公開する Domain API / named read API を利用する。

Android が直接生成する Activity / Service / Worker / AppWidgetProvider 等は framework boundary として扱う。`:app` は application-wide wiring を行うが、feature 固有 Worker や runtime business logic は owning feature に置く。

## Dependency guardrails

許容される基本方向は次の通り。

```text
UI     -> own/other Domain
Data   -> own/other Domain / Data / core (ownership requires it)
app    -> feature Domain / Data / UI (composition)
```

禁止される方向は次の通り。

```text
core   -> feature
Domain -> UI / Data
UI     -> concrete Data implementation
circular Gradle dependency
```

これらのうち機械化可能な規則は `verifyArchitecture`、module map verification、table ownership verification、public repository verification などで CI 検査される。

## Feature and core inventory

module 一覧の正本は [`settings.gradle.kts`](../../settings.gradle.kts) であり、対応表は [module-map.md](module-map.md) が自動検査対象として保持する。全 feature に `domain / data / ui` の3層を強制せず、独立した責務・依存・build boundary として価値がある layer だけを module 化する。

## Reading order

1. この overview と interactive diagram で全体の一次経路を確認する。
2. [module-map.md](module-map.md) で Gradle module と ownership を確認する。
3. [principles.md](principles.md) で現在有効な依存・composition・persistence 規則を確認する。
4. Domain 境界は [context-map.md](context-map.md) / [persistence.md](persistence.md) を確認する。
5. 判断理由や例外は [ADR index](../adr/README.md) から該当 ADR を確認する。

## Source revision

この diagram は `main` の `d9afcd4bd8742bbae54916e5865f43ac1484a6e8` を基準に作成した。構造変更時は JSON の `meta.repository.revision` と本節を更新し、diagram と既存 architecture docs の整合を再確認する。
