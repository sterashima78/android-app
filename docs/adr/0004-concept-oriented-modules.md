# ADR-0004: 安定した共有概念は concept-oriented module として分離する

- Status: Accepted
- Date: 2026-08-08
- Updated: 2026-08-08
- Amends: ADR-0003
- Amended by: ADR-0106

## Context

ADR-0003 では変更単位との一致を重視し、feature-first の Gradle module 構成を基本とした。一方、Article のように RSS、Bookmark、Summary、Web、Widget など複数の機能から同じ意味で参照される中心概念が存在する。

このような概念を `:core:domain` に集約すると、無関係な共有モデルまで同じ module に集まり、`core` が責務の曖昧な共有領域になる可能性がある。また、Article に関する Repository contract や Data implementation を特定の従属 feature に所有させることも変更理由と一致しない。

## Decision

Gradle module の第一の分割軸は「変更理由と ownership」とする。

複数 feature から同じ意味で利用され、独立した変更理由を持つ安定した概念は concept-oriented module として独立した ownership を与える。ただし、リポジトリと Gradle project path 上では他のアプリケーション固有 module と同じ `:feature` namespace に配置する。

```text
:feature:article:domain
:feature:article:data
:feature:article:ui
```

ここで `article` は `rss` や `bookmark` の下位 feature ではない。

### Article の ownership

Article は RSS だけの概念ではなく、Bookmark、Summary、Chat、Web、Widget でも利用される。そのため Article の基本モデルと Repository contract は `:feature:article:domain` が所有し、永続化実装は `:feature:article:data` が所有する。

Article の基本モデルは記事そのものの属性を表し、Bookmark feature が所有するタグ・フォルダ等の整理状態は直接所有しない。複数 feature の情報を同時に必要とする場合は、利用側が projection / view state として合成する。

`:core:domain` を Article の置き場として利用しない。`core` は database、network、design system、AI runtime のような横断 capability を扱い、特定の名前を持つアプリ固有のドメイン概念はその概念自身の module に所有させる。

### Dependency rules

concept-oriented module も ADR-0003 の layer ルールに従う。

他 feature は必要な Article module に通常の feature 間依存として依存できる。

```text
:feature:rss:domain      -> :feature:article:domain
:feature:bookmark:ui     -> :feature:article:domain
:feature:summary:data    -> :feature:article:data
```

feature 間依存そのものは制限しない。Domain -> UI / Data、UI -> concrete Data、core -> feature、循環依存など ADR-0003 が禁止する layer 違反は引き続き避ける。

### Package naming

concept-oriented module も `feature` ownership を Kotlin package に含める。

```text
Gradle:  :feature:article:domain
Package: dev.terashima.yomitorirss.feature.article

Gradle:  :feature:article:data
Package: dev.terashima.yomitorirss.feature.article.data
```

Android namespace は `dev.terashima.yomitorirss.feature.article.<layer>` を基本とする。

## Consequences

- `core` が共有ドメインモデルの寄せ集めになることを防げる
- RSS や Bookmark のどちらにも属さない Article の ownership が明確になる
- Article 自体と Bookmark の整理状態を分離できる
- concept 単位で Repository、Data source、UI を発展させられる
- feature-oriented と concept-oriented の module を `feature/` 配下にまとめることで、リポジトリルートを小さく保てる
- feature と concept のどちらが適切な ownership かを変更理由に基づいて判断する必要がある

## Relationship to ADR-0003

ADR-0003 の feature-first は、アプリケーション固有 module を `:feature` namespace に集約する構造として維持する。

本 ADR は、その namespace 内部の ownership が必ずユーザー向け機能単位であるという制約を緩和する。Article のような安定した共有概念には独立した concept-oriented ownership を与える。
