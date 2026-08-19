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

## Core capabilities

```text
:core:background
:core:database
:core:network
:core:web-collector
:core:ai-runtime
:core:designsystem
```

`core` は複数 feature が共有する技術 capability を提供し、アプリ固有 Domain concept や feature-specific use case を所有しない。

## Feature modules

2026-08-19 時点の `settings.gradle.kts` では次の feature module が含まれる。

| Feature | Layers |
| --- | --- |
| ai-task-queue | domain / data / ui |
| backup | domain / data / ui |
| bookmark | domain / data / ui |
| article | domain / data / ui |
| asset | domain / data / ui |
| book-reader | domain / data / ui |
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

- module の追加・削除・layer 構成変更: `settings.gradle.kts` を更新し、本表も同じ PR で更新する。
- dependency rule を変更する: ADR を追加または更新し、`verifyArchitecture` と [principles.md](principles.md) を更新する。
- module 名と Domain Context の関係が変わる: [context-map.md](context-map.md) と必要な ADR を更新する。

## Sources

- [`settings.gradle.kts`](../../settings.gradle.kts)
- [ADR-0001](../adr/0001-layered-architecture.md)
- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0004](../adr/0004-concept-oriented-modules.md)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
