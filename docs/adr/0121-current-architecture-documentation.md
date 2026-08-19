# ADR-0121: ADR を根拠とする current architecture documentation を維持する

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0046, ADR-0055, ADR-0106

## Context

ADR が増え、layer、module ownership、Domain Context、persistence ownership、background runtime、architecture verification 等の現在有効なルールが複数 ADR に分散している。

ADR は個々の意思決定とその理由、代替案、移行経緯を残す用途には適している。一方、実装やレビューのたびに多数の ADR を時系列に読み、後続 ADR の `Amends` / `Refines` / `Supersedes` 関係を再構成して「現在のルール」を得る方法は、変更量の増加に対して読み取りコストが高い。

ADR-0106 に基づく `docs/domain-context-map.md` は、ADR を根拠としながら現在の Context Map を独立文書として示す先行例になっている。この方式を architecture 全体へ広げる。

一方で、ADR と current documentation の両方に同じ説明を複製すると整合性が崩れやすい。また、module 一覧や table ownership のように既に `settings.gradle.kts` や `config/architecture/*` に機械可読な正本がある情報まで prose で独立管理すると、別の重複 source of truth を作ることになる。

## Decision

### 1. ADR と Architecture Documentation の責務を分ける

`docs/adr/` は Decision Log として、設計判断の時点での Context / Decision / Consequences / relationship を保存する。

`docs/architecture/` は複数 ADR の結果として現在有効な構造・ルール・用語を現在形で説明する。

```text
ADR
  why / decision history
       |
       v
Architecture Documentation
  current model / rules
       |
       v
Executable configuration / verification / implementation
```

現在の architecture を理解する入口は `docs/architecture/README.md` とする。

### 2. Current Architecture Documentation を責務別に分ける

次の文書を維持する。

- `README.md`: architecture 全体の入口と source-of-truth policy
- `principles.md`: layer、dependency、ownership、cross-context operation 等の実装原則
- `module-map.md`: Gradle module と物理 ownership
- `context-map.md`: Bounded Context と Context 間関係
- `persistence.md`: schema、migration、table access ownership
- `testing.md`: test boundary と architecture verification
- `glossary.md`: ubiquitous language と architecture terminology

`docs/adr/README.md` は Decision Log の読み方と current architecture に重要な ADR の索引を提供する。この README は ADR 自体ではないため ADR 番号を持たず、`scripts/verify_adr_integrity.py` はこのファイルだけを ADR filename/header 検査対象から除外する。それ以外の `docs/adr/*.md` は ADR-0055 の命名規則に従う。

### 3. Architecture Documentation は根拠 ADR を明示する

各 architecture document の規範的な内容は `Sources` から根拠 ADR へ辿れるようにする。

Architecture Documentation には ADR の Context、比較した代替案、詳細な rationale を複製しない。それらは ADR に残し、current document は現在の rule と relationship を短く説明する。

### 4. 機械可読な構成値を prose の第二の正本にしない

次のような情報には既存の専用 source of truth を利用する。

- Gradle module 一覧: `settings.gradle.kts`
- architecture manifest: `config/architecture/*`
- table ownership enforcement: `config/architecture/table-ownership.tsv` と `gradle/table-ownership.gradle.kts`
- production architecture rule: `verifyArchitecture`
- ADR identifier/link integrity: `scripts/verify_adr_integrity.py`

Architecture Documentation は値を説明・要約してよいが、矛盾した場合に prose 側を独立した構成値として扱わない。

ただし implementation/configuration が Accepted ADR と矛盾している場合、implementation を暗黙の設計変更とは扱わない。設計変更を示す新しい ADR がない限り architecture drift として修正する。

### 5. Architecture-changing PR では current docs への影響を確認する

新規 ADR または既存 ADR の意味を変更する PR では、同じ変更内で次を確認する。

- `docs/architecture/` の現在形が変わるか
- module / manifest / verification rule の更新が必要か
- older ADR の relationship metadata を更新すべきか
- glossary の ubiquitous language が変わるか

Architecture Documentation の文章一致を自動生成・同期することはしない。prose の意味的整合性はレビュー対象とし、機械判定できる制約は既存の architecture verification / manifest に寄せる。

### 6. 既存 Context Map の URL は互換入口として残す

`docs/domain-context-map.md` の本体は `docs/architecture/context-map.md` へ移し、旧 path は新 path への案内だけを残す。

これにより既存 link を破壊せず、current architecture document の置き場所を一貫させる。

### 7. Public repository safety を current docs にも適用する

Architecture Documentation、ADR、manifest、test fixture には credential、token、OAuth secret、実ユーザー URL、メールアドレス、個人データ、公開を意図しない endpoint を含めない。

## Consequences

### Positive

- 実装者・レビュー担当者は多数の ADR を再構成せず現在の architecture を把握できる。
- ADR は判断履歴として安定して残り、current docs は現在形に集中できる。
- Context Map、module map、persistence ownership の責務が分離される。
- machine-readable source を既に持つ情報の二重管理を最小化できる。
- architecture-changing PR で「ADR 更新」と「current docs 更新」を別の責務として確認できる。
- AI/agent に architecture context を与える場合も、まず少数の current docs を渡し、必要な rationale だけ ADR へ遡れる。
- ADR index を追加しても ADR numbering/integrity checker の規則を曖昧にしない。

### Negative

- ADR と current docs の2種類の文書を維持する必要がある。
- prose の意味的整合性は完全には自動検証できない。
- architecture-changing PR で documentation update の確認項目が増える。
- current docs の要約が古くなる可能性は残るため、review と executable verification の両方が必要になる。
- ADR checker に `README.md` という単一の非 ADR 例外を持つ。

## Testing and verification

この変更は documentation structure の変更であり production behavior は変更しないため、新しい functional test は追加しない。

次を確認する。

- `scripts/verify_adr_integrity.py` で ADR-0121 の番号・見出し・参照整合性が成立すること
- `docs/adr/README.md` だけが non-ADR index として許可され、その他の不正 filename は引き続き失敗すること
- architecture documents の相対 link と source ADR が存在すること
- `docs/domain-context-map.md` の旧 path が新 Context Map へ案内すること
- public repository に公開してはいけない credential / personal data が追加されていないこと

既存 PR CI の `verifyArchitecture`、unit test、lint は通常どおり実行する。

## Relationship to existing ADRs

ADR-0046 の executable architecture verification は維持する。本 ADR は prose documentation へ置き換えるものではなく、machine-verifiable constraint と current architecture explanation の責務を分ける。

ADR-0055 の ADR numbering / integrity policy は維持しつつ、`docs/adr/README.md` だけを非 ADR index として明示的に除外する。その他の `docs/adr/*.md` の命名・番号・参照 integrity は従来どおり検査する。

ADR-0106 の Domain Context / Aggregate / persistence ownership の決定は変更しない。既存 `docs/domain-context-map.md` を `docs/architecture/context-map.md` 配下へ整理する。
