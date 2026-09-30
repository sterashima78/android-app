# Formal Specification Workflow

この文書は、自然言語仕様を Quint / Alloy の形式モデルで補完するときの運用ルールの正本である。

設計判断は [ADR-0275](adr/0275-formal-specification-with-quint-and-alloy.md) を参照する。

## 1. 仕様artifactの役割

このrepositoryでは、仕様を次の3層で扱う。

```text
docs/spec/*.md
  人間が読む完全なユーザー仕様
       |
       | direct link
       v
spec-models/quint/*.qnt
  状態遷移・順序・safety / temporal property
       |
       +-------------------+
                           |
spec-models/alloy/*.als    |
  構造・関係・cardinality |
                           v
scripts/verify_formal_models.sh
  model + traceability verification
```

自然言語仕様は、形式化していないdetailも含めてユーザーから見た振る舞いを説明する。Quint / Alloyは、その中から機械検査する価値がある性質をprojectionする。

形式モデルだけを読めば仕様全体が分かる、という状態は目指さない。

## 2. どちらのmodelを使うか

### Quint

次のような時間方向の振る舞いを優先して記述する。

- state transition
- command / event ordering
- queue、scheduler、retry、resume
- concurrencyで壊してはいけないsafety invariant
- 必要に応じたliveness / temporal property

### Alloy

次のような関係・構造を優先して記述する。

- entity間のrelation
- one / lone / some等のcardinality
- uniqueness
- owner / reference relation
- mutually exclusiveなconfiguration
- 存在してはいけない構造

### 両方

状態遷移と構造制約の両方が重要な仕様では、QuintとAlloyを併用する。

ただし同じ文章を2言語へ翻訳するのではなく、それぞれが得意な性質へ分割する。

## 3. spec documentからmodelへlinkする

形式化した仕様節には、対応modelへの相対linkとcoverageを記載する。

例:

```markdown
形式モデル:

- [Quint: backup_schedule.qnt](../../spec-models/quint/backup_schedule.qnt)
  - schedule追加・削除・発火・再予約の状態遷移
- [Alloy: backup_schedule.als](../../spec-models/alloy/backup_schedule.als)
  - 設定時刻とscheduled workの一意対応
```

model側にも先頭commentで対応する自然言語仕様fileを記載する。

1つのmodelが複数spec fileに対応してもよい。逆に、1つのspec sectionを複数modelへ分割してもよい。

## 4. modelの書き方

### 4.1 production codeを写経しない

modelはimplementation modelではなくspecification modelとする。

class名、framework API、database schema、UI state holder等をそのまま再現するのではなく、検査対象となるstate / relationだけを残す。

### 4.2 abstractionをcommentへ残す

有限化や縮約を行う場合、なぜそのabstractionで検査対象の性質を保てるかをcommentへ書く。

例として、任意個の独立した時刻scheduleの状態遷移を2つの代表slotで検査する場合、slot数そのものではなく「各slotが独立に追加・削除・発火できること」を検査していると明記する。

### 4.3 model外の性質を主張しない

model checkerがpassしても、modelへ含めていないproduction behaviorまで証明されたことにはしない。

UI、platform integration、persistence adapter、実際のWorker実装等は従来どおり適切なunit / integration / instrumented testで検証する。

## 5. repository convention

### Quint

- path: `spec-models/quint/*.qnt`
- main moduleは原則としてfile stemと同名にする
- executable modelは `init` と `step` actionを公開する
- CIで検査するsafety propertyは `safety` という `bool` definitionへ集約する
- finite-state modelを優先し、TLCで全状態を列挙可能なscopeに保つ
- 大きなmodelでbounded verificationが必要になった場合は、この共通conventionを拡張するADRまたは運用変更を先に行う

### Alloy

- path: `spec-models/alloy/*.als`
- CIで検査するassertionには `check ... expect 0` を記載する
- modelが意図せず空集合だけで成立していないことを確認するため、意味のあるinstanceには `run ... expect 1` を少なくとも1つ記載する
- scopeは検査する構造を十分に表現できる最小値を選ぶ

## 6. 変更時の運用

### 6.1 既にmodelがある仕様を変更する場合

1. 対象の `docs/spec/*.md` を読む。
2. 仕様節からlinkされたQuint / Alloy modelを読む。
3. 自然言語変更がmodelのabstractionに影響するか判断する。
4. 影響する場合は同じPRでmodelを変更する。
5. 影響しない場合も、modelが依然として同じ性質を表していることをreviewする。
6. `scripts/verify_formal_models.sh` を実行する。

自然言語だけ、またはmodelだけを更新して意味がずれた状態でmergeしない。

### 6.2 新しい仕様を追加する場合

次のいずれかがある場合は形式化を優先して検討する。

- lifecycle / state machine
- background work、queue、scheduler、retry
- ordering / concurrency
- duplicate prevention
- uniqueness / cardinality
- cross-context relation
- restore / migration / rollback
- 境界条件が多く、例示だけでは抜けを見つけにくいrule

形式化する場合、仕様節からmodelへ直接linkする。

形式化しない場合に各仕様へ「modelなし」と定型記載する必要はない。ただし、上記の高リスクな性質を持つのに形式化しない判断をした場合は、Change Impact BriefまたはPR reviewで理由を確認する。

### 6.3 既存仕様のmigration

既存仕様を一括で形式化しない。

次の順でcoverageを増やす。

1. 変更対象になったstateful / relational specification
2. background / persistence / restore / migration等の高リスク領域
3. 複数Contextにまたがるinvariant
4. 過去にregressionや解釈揺れがあった仕様

初期coverageはbackup scheduleとする。

## 7. 検査

共通entry point:

```bash
scripts/verify_formal_models.sh
```

このscriptは次を行う。

1. specとmodelのlink整合性検査
2. pinned Quintの取得とchecksum検証
3. Quint typecheck
4. Quintのfinite-state safety verification
5. pinned Alloy Analyzerの取得とchecksum検証
6. Alloyのrun / check expectation実行

toolはrepositoryへbinaryとしてcommitせず、cache directoryへ取得する。

CIではArchitecture checkの一部として同じscriptを実行する。

## 8. tool version

現在のbaseline:

- Quint 0.33.0
- Alloy 6.2.0
- formal verification用JVM: 21

versionは `scripts/verify_formal_models.sh` で固定する。更新時はrelease noteを確認し、model syntax / solver behavior / required runtimeの変更を確認したうえでchecksumとこの文書を同時に更新する。

## 9. review checklist

形式仕様を含む変更では次を確認する。

- 自然言語仕様は単独でも意味が分かるか
- specから対応modelへ直接辿れるか
- model commentから元specへ戻れるか
- Quint / Alloyの役割分担が適切か
- modelがimplementation detailを過剰に複製していないか
- abstractionが検査対象の性質を失っていないか
- assertion / invariantが「常に真になるだけ」の弱い条件になっていないか
- satisfiable exampleもあり、model自体が過剰制約で空になっていないか
- model checkerのpassをimplementation testの代替にしていないか
