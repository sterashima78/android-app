# Architecture Principles

この文書は、Accepted ADR 群から現在有効なアーキテクチャ規則を抽出した実装ガイドである。判断理由や代替案は各 ADR を参照する。

## Layer responsibilities

基本の依存方向は次とする。

```text
Compose Screen / Route
        |
        v
ViewModel / UI state
        |
        v
UseCase / Domain service（必要な場合）
        |
        v
Repository contract
        ^
        |
Data implementation
        |
        v
Local / Remote / Android platform
```

- UI は SQLite、HTTP client、WorkManager 等の concrete implementation を直接操作しない。
- Domain は Android、DB、HTTP の実装型へ依存しない。
- Repository の単一メソッドを転送するだけの UseCase は作らない。
- 複数 Repository の orchestration、再試行、並列処理、複数画面から再利用される業務ルール等に UseCase / Application Service を利用する。
- feature 固有 UI state は owning feature が所有し、`:app` は app shell、navigation、composition、platform wiring に限定する。

## Module ownership

- アプリケーション固有コードは `:feature:<name>:<layer>` を基本とする。
- `feature` は画面単位だけでなく、Article のような独立した共有概念の ownership namespace としても使う。
- `core` は database、network、design system、AI runtime 等の横断的技術 capability に限定する。
- `:core:data`、`:core:domain`、`:common`、`:util` のような責務の曖昧な集約先を作らない。
- module の公開 API は小さく保ち、Data source、DB entity、HTTP DTO 等は必要がない限り `internal` とする。
- 小さな責務を分けるだけのために Gradle module を増やさず、package / `internal` で十分なら同一 module に残す。

禁止または原則回避する依存は次とする。

```text
core   -> feature             禁止
domain -> ui / data           禁止
ui     -> concrete data       禁止
Gradle circular dependency    禁止
```

feature 間依存そのものは、ownership と layer rule に反しない限り許容する。

## Domain boundaries

Gradle module、Bounded Context、Aggregate は同一概念ではない。

```text
Gradle feature/module
  ownership / build boundary

Bounded Context
  ubiquitous language と model の境界

Aggregate
  transactional consistency と invariant の境界
```

module 名を Domain 名へ機械的に合わせるための rename は行わない。Domain model が安定してから module restructuring を判断する。

## Cross-context operations

他 Context の都合で低レベル CRUD を公開しない。目的を表す契約を owner が公開する。

- 単一 Context / Aggregate の command: owner の Domain API / Repository contract
- 複数 Context / Aggregate の command orchestration: Application Service
- 永続状態を所有せず複数 Aggregate の情報から domain rule を解決: Domain Service
- 大量の cross-context read で API 合成に実測上の問題がある場合: named read-only Projection

Projection は read-only とし、参照 Context/table を明示し、generic な `cross-feature` module を作らない。

## Persistence ownership

- durable table の直接 SELECT / INSERT / UPDATE / DELETE は owner data module が行う。
- 他 Context は owner の Domain API または named Query API を利用する。
- foreign table write は禁止する。
- cross-context の最適化 read は明示された read-only Projection に限定する。
- 同じ SQLite database を共有していることや foreign key の存在は共同 ownership の根拠にならない。
- 移行中の例外は `config/architecture/foreign-table-access-allowlist.tsv` に path、table、ADR に基づく理由を明示し、不要になったら削除する。

## Composition and framework boundaries

- `:app` は composition root として feature implementation を組み立てる。
- Screen で concrete Repository、database connection、WorkManager dependency を生成しない。
- Application / container の service locator lookup は通常の Route、Screen、ViewModel、Application Service、Data object では行わない。
- Android / WorkManager が constructor を所有する Activity、Worker、Service、AppWidgetProvider 等の framework entry point だけ、明示された Provider contract を利用できる。
- `YomitoriApplication` implementation type への直接 cast は行わない。

## Background runtime ownership

feature 固有の Worker、scheduler/controller、queue-state interpretation は owning feature の data/runtime 側に置く。`:app` には feature 固有 background business logic を置かない。

Android framework が永続化する class name 等の互換性が必要な場合は、ADR を根拠とする明示的 compatibility shim に限定する。

## Architecture enforcement

機械的に検査できる規則はレビューだけに依存しない。

- Gradle dependency / source ownership: `verifyArchitecture`
- durable table ownership: `gradle/table-ownership.gradle.kts` と `config/architecture/table-ownership.tsv`
- transitional foreign access: `config/architecture/foreign-table-access-allowlist.tsv`
- framework provider exception: `config/architecture/framework-provider-lookups.tsv`
- ADR identifier/link integrity: `scripts/verify_adr_integrity.py`

検査で表現しにくい ownership、命名、API 粒度、Route の orchestration 肥大化等はレビュー対象とする。再発しやすい構造的パターンが見つかった場合は、可能なら fixture と verification rule を追加する。

## Sources

- [ADR-0001](../adr/0001-layered-architecture.md)
- [ADR-0003](../adr/0003-multi-module-architecture.md)
- [ADR-0004](../adr/0004-concept-oriented-modules.md)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0047](../adr/0047-feature-owned-database-schema-contributions.md)
- [ADR-0101](../adr/0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
- [ADR-0121](../adr/0121-current-architecture-documentation.md)
