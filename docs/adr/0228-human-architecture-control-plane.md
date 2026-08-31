# ADR-0228: AI主導開発に人間向け Architecture Control Plane を置く

- Status: Accepted
- Date: 2026-08-31

## Context

Mosaic は実装の大部分を AI agent に委譲して開発している。既存の `docs/architecture/` は layer、module、Context、persistence、AI runtime、background runtime 等の現在形を詳細に記録し、ADR は判断理由を追跡できる状態になっている。一方で、詳細文書が増えるほど、人間が短時間で次の問いへ答えることは難しくなる。

- アプリ全体はどの capability と Context で構成されているか
- 主要なデータはどこから入り、どこに保存され、どこへ流れるか
- 変更時に絶対に守るべき invariant は何か
- 変更すると特に影響が広がりやすい boundary はどこか
- 新機能を追加する前に、既存機能の再利用、代替案、複雑性増加をどう判断するか

AI agent が production code、test、ADR、architecture document を個別に生成できても、人間がコード差分を全行読むことだけに依存して全体像を維持するのは現実的ではない。逆に、人間向けの要約を実装より上位の唯一の正本にすると、コードや機械可読な architecture metadata から乖離した別の architecture を作る危険がある。

## Decision

既存の source of truth を維持したまま、人間が全体像を把握し変更判断を行うための Architecture Control Plane を `docs/architecture/` に追加する。

Architecture Control Plane は次の2文書を中心とする。

- `system-overview.md`: capability、runtime topology、主要 data flow、invariant、decision-sensitive boundary を短時間で把握する入口
- `change-impact-review.md`: 実装前の調査、Impact Brief、人間の判断、実装後の system diff、独立レビューを行う手順

### System overview の位置付け

`system-overview.md` は詳細仕様の新しい正本にはしない。現在形を人間が把握するための index / projection とし、各記述には詳細 architecture document、machine-readable manifest、production code 等の evidence path を示す。

値や一覧がコードから一意に決まる場合は複製を避け、`settings.gradle.kts`、`config/architecture/*`、schema contribution、CI 等を参照する。overview と実装が異なる場合は、既存の current architecture documentation policy に従い、Accepted ADR の有無を確認して drift か documentation lag かを判断する。

### Change Impact Brief

次のいずれかに該当する変更では、production code を変更する前に `change-impact-review.md` の Impact Brief を作る。

- 複数 Context / feature にまたがる
- durable data、schema、backup、credential boundary を変える
- background task、Worker identity、application-scope runtime を変える
- Local / Cloud AI の provider、data egress、model runtime boundary を変える
- Android platform permission、component、external Intent、WebView、network boundary を変える
- module dependency、ownership、layer boundary、公開 capability を変える
- 既存機能と役割が重複する可能性がある
- 将来の削除・移行コストを増やす新しい durable state や abstraction を追加する

Impact Brief は少なくとも次を含む。

1. 目的とユーザー価値
2. 現在の実現方法と evidence
3. 影響する capability / Context / module / persistence / runtime
4. 変更しない boundary
5. 選択肢と不採用案
6. 推奨案と理由
7. 新しく増える概念、状態、依存、外部通信
8. migration / rollback / compatibility
9. 必要な test と documentation update
10. ADR の要否

軽微で局所的な修正は Impact Brief を省略できる。ただし architecture 上の判断を含むか不明な場合は、実装前に brief を作る側へ倒す。

### Human decision boundary

AI agent は調査と推奨案作成を行えるが、次の判断を暗黙に production code へ埋め込まない。

- 新しい system-wide abstraction を導入する
- ownership / Context boundary を変更する
- durable state を増やす
- cloud data egress を新規追加する
- compatibility baseline を変える
- 既存機能と重複する新しい capability を追加する

これらは Impact Brief と ADR を通して判断を明示する。

### Post-change system diff

実装後、PR の独立レビューでは line diff だけでなく次の system diff を確認する。

- 新しく増えた / 削除された概念
- capability / Context / module boundary の変化
- data flow、persistence、external communication の変化
- invariant の変化
- architecture document / ADR の更新漏れ
- public repository に出してはいけない情報の混入
- 変更範囲に対して test 方法が十分か

## Consequences

### Positive

- 人間が詳細コードを全行読まずに、全体像と判断上重要な boundary を維持できる
- agent が実装前に既存設計を調査するため、重複機能や局所最適な abstraction を作りにくくなる
- proposal、decision、implementation、review の責務が分離される
- system overview から詳細 architecture document、ADR、production evidence へ段階的に降りられる
- PR review が「テストが通ったか」だけでなく「システムとして何が変わったか」を扱える

### Negative

- architecture に関わる変更では実装前の調査コストが増える
- overview と実装の同期を保つ maintenance が必要になる
- Impact Brief が形式だけになった場合、判断品質は向上しない
- brief をすべての小変更へ適用すると開発速度を不必要に落とすため、適用範囲の判断が必要になる

## Relationship to existing ADRs

- ADR-0046 の architecture verification を置き換えず、人間向け判断層を追加する。
- ADR-0055 の ADR 番号管理規則に従う。
- ADR-0122 の current architecture documentation policy を拡張し、`system-overview.md` を詳細文書への projection として位置付ける。
- ADR-0136 の public repository verification を Impact Brief / independent review の必須観点として維持する。
- ADR-0193 等の ownership / package structure の既存判断を overview から参照し、overview 自体で新しい ownership rule を定義しない。

## Sources

- [Architecture Documentation](../architecture/README.md)
- [Architecture Principles](../architecture/principles.md)
- [Domain Context Map](../architecture/context-map.md)
- [Module Map](../architecture/module-map.md)
- [Testing Strategy](../architecture/testing.md)
- [ADR-0046](0046-automated-architecture-verification.md)
- [ADR-0055](0055-adr-numbering-policy.md)
- [ADR-0122](0122-current-architecture-documentation.md)
- [ADR-0136](0136-public-repository-content-verification.md)
