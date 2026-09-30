# ADR-0275: 自然言語仕様をQuintとAlloyの形式モデルで補完する

- Status: Accepted
- Date: 2026-09-30
- Refines: [ADR-0046](0046-automated-architecture-verification.md), [ADR-0122](0122-current-architecture-documentation.md), [ADR-0228](0228-human-architecture-control-plane.md)

## Context

現行仕様は `docs/spec/*.md` の自然言語を中心に管理している。これは利用者が期待する振る舞い、例外、非目標を把握するには適している一方、状態遷移、順序、排他、cardinality、到達不能な組合せなどは、文章だけでは変更時の抜け漏れを機械的に検出しにくい。

production code と unit / integration test は実装が仕様どおりかを検証するが、仕様そのものに矛盾や未定義状態がないことを直接検査するものではない。

## Decision

### 1. 自然言語仕様を維持し、形式モデルを機械検査可能なprojectionとして追加する

`docs/spec/*.md` は引き続きユーザーから見た現行仕様を完全に説明する人間向けの正本とする。

そのうえで、状態遷移・構造制約を形式化する価値がある仕様について、`spec-models/` 配下に Quint / Alloy model を追加する。形式モデルは自然言語仕様を置き換えず、選択した性質を機械検査可能にしたprojectionとする。

自然言語仕様と形式モデルが矛盾した場合は、どちらかを暗黙に優先せず、仕様不整合として変更を止めて同じPR内で解消する。

### 2. QuintとAlloyを役割分担する

Quintは主に次を扱う。

- state transition
- event / action ordering
- queue / scheduler / retry lifecycle
- safety invariant
- 必要に応じたtemporal property

Alloyは主に次を扱う。

- entity / relation structure
- cardinality / uniqueness
- ownershipや参照関係
- mutually exclusive configuration
- 到達してはいけない構造

同じ自然言語仕様を両言語へ全文複製することは要求しない。1つの性質に片方だけが適する場合は片方だけでよく、状態と構造の両面が重要な場合は両方を使う。

### 3. 自然言語仕様から対応modelを直接参照する

形式化した仕様節には、対応する `.qnt` / `.als` fileへの相対linkと、そのmodelが検査する性質を記載する。

model側にも対応する自然言語仕様fileをcommentで記載する。

これにより、仕様を読む人間とagentが自然言語・形式モデル・検査対象を双方向に追跡できる状態を維持する。

### 4. modelのscopeとabstractionを明示する

形式モデルはproduction implementationの逐語的な複製にしない。検査したい性質へ必要な状態だけを残し、Android framework、database API、UI implementation等の詳細は原則として抽象化する。

有限modelへ縮約する場合は、例えば「2つのslotで任意個の独立schedule時刻を代表する」等、どの性質を保存したabstractionかをmodel commentに記載する。

modelが検査していない性質を、検査済みであるかのように扱わない。

### 5. 変更時は自然言語仕様・model・検査を同期する

既に形式モデルが対応している仕様を変更する場合は、同じPRでmodelも確認し、必要なら更新する。

新しい仕様または既存仕様の変更が次に該当する場合は、形式モデル追加を原則として検討する。

- 複数状態を遷移する
- background execution、retry、queue、schedulerを持つ
- 重複、排他、exactly-one / at-most-one等の制約がある
- 複数Context / owner間の関係に重要なinvariantがある
- rollback / restore / migration等で到達可能状態が問題になる
- 自然言語だけでは境界条件を列挙しにくい

表示文言、単純なlayout、外部platformが決める値など、形式化の効果が低い事項へ無理にmodelを作らない。

既存仕様全体を一括で形式化することは要求せず、変更対象と高リスク領域から段階的にcoverageを増やす。

### 6. CIで形式モデルとtraceabilityを検査する

`scripts/verify_formal_models.sh` を形式仕様の共通検査entry pointとする。

検査は少なくとも次を含む。

- spec documentから参照されたmodel fileが存在する
- model fileが少なくとも1つのspec documentから参照される
- Quint modelのparse / typecheck
- Quint modelのfinite-state invariant verification
- Alloy model内のrun / check commandとexpectation
- tool versionの固定

PRではArchitecture checkの一部として実行し、形式モデルの不整合を通常のarchitecture driftと同様にmerge前に検出する。

### 7. 初期coverageはbackup scheduleから開始する

最初のmodelは、複数時刻を持つ自動backup scheduleを対象とする。

Quintではscheduleの追加・削除・発火・再予約に関する状態遷移を扱い、Alloyでは設定時刻とscheduled workの一意対応、およびautomatic jobが設定済みscheduleに由来する構造制約を扱う。

このmodelを運用例として、以後の仕様変更時にcoverageを拡張する。

## Consequences

- 自然言語仕様の可読性を維持したまま、重要なstate / relation invariantをCIで検査できる。
- modelとimplementationは別artifactなので、modelが存在するだけで実装準拠を証明したことにはならない。production testは引き続き必要である。
- 形式化対象を選ぶ判断とmodel abstractionの妥当性がreview対象になる。
- toolchain downloadとmodel checkingのCI costが増えるため、tool versionを固定しcache可能な構成にする。
