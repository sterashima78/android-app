# Change Impact Review

この文書は、AI agent が実装を始める前に現状を調査し、人間が「何を変えるか」を判断できる情報へ圧縮するための手順である。

目的は、生成コードを人間が全行レビューすることではなく、変更が system に与える意味を先に明示することにある。

## 1. When to use

次のいずれかに当てはまる場合、production code を変更する前に Impact Brief を作る。

- 複数 Context / feature / module にまたがる
- 新しい capability、shared concept、module、application-scope dependency を追加する
- ownership、layer、module dependency、public capability を変更する
- database schema、durable state、backup、migration を変更する
- WorkManager、background queue、Worker identity、scheduler を変更する
- AI provider、model runtime、cloud data egress、tool boundary を変更する
- Android component、permission、external Intent、WebView、network boundary を変更する
- credential / secret の保存または利用方法を変更する
- 既存機能と重複している可能性がある
- rollback / removal 時に migration が必要になる状態を追加する

表示文言、局所的なUI調整、明らかなバグ修正など、既存の設計判断を変えない変更では省略できる。

## 2. Discovery mode

Impact Brief が完成するまで、agent は production code を変更しない。

調査は次の順序で行う。

1. `docs/spec.md` で現在のユーザー仕様を確認する。
2. `docs/architecture/system-overview.md` で capability と主要 data flow を特定する。
3. `docs/architecture/context-map.md` と `principles.md` で owner と invariant を確認する。
4. `module-map.md`、`persistence.md`、feature-specific architecture document で物理境界を確認する。
5. 関連 ADR を確認し、現在の形になった理由、却下案、compatibility condition を確認する。
6. production code、test、manifest を検索して文書と実装を照合する。
7. 既存 capability で要求を満たせないか確認する。

agent は「コードを見ると現在こうなっている」だけで architecture rule を上書きしない。Accepted ADR がない実装差分は architecture drift の可能性として報告する。

## 3. Evidence rule

Impact Brief の重要な主張には repository 内の evidence path を付ける。

良い例:

```text
Current behavior:
Summary Worker は feature-owned WorkerFactory から application-scope dependency を受け取る。

Evidence:
- feature/summary/data/.../SummaryWorkerFactory.kt
- app/composition/src/main/java/dev/terashima/yomitorirss/AppWorkerFactory.kt
- docs/architecture/principles.md
- docs/adr/0146-workmanager-worker-factory-injection.md
```

避ける例:

```text
たぶん Summary は WorkManager を使っている。
```

コードから一意に求められる module / table / version の一覧を brief へ大量コピーせず、正本への path を示す。

## 4. Impact Brief template

agent は実装前に次の形式で提出する。

```text
# Change Impact Brief

## Goal
ユーザーが何をできるようになるか。
この変更を行わない場合に何が困るか。

## Existing capability
現在もっとも近い capability / flow は何か。
既存機能の拡張で満たせるか。
重複する機能や過去の却下案はないか。

Evidence:
- ...

## Affected system areas
Capabilities:
- ...

Contexts:
- ...

Modules:
- ...

Persistence:
- unchanged / ...

Background runtime:
- unchanged / ...

External / trust boundary:
- unchanged / ...

## Boundaries that must not change
- ...

## Options
### Option A
概要:
利点:
欠点:
増える概念 / 状態 / 依存:
削除コスト:

### Option B
...

## Recommendation
選択する案と理由。

## System delta
New concepts:
- ...

Removed concepts:
- ...

New durable state:
- none / ...

New dependencies:
- none / ...

New external communication:
- none / ...

Changed data flow:
- none / before -> after

## Compatibility / migration / rollback
- ...

## Verification plan
Unit:
- ...

Integration / architecture:
- ...

Android / E2E:
- ...

Public repository review:
- ...

## Documentation
ADR:
- required / not required。理由: ...

Current architecture docs:
- ...

Spec:
- ...

## Human decision
人間が実装前に決める必要がある点:
- none / ...
```

## 5. Human decision rules

次の判断を agent の実装上の都合だけで決定しない。

- 新しい system-wide abstraction / framework を導入する
- Context / ownership を移動する
- new durable source of truth を作る
- cloud data egress を追加する
- credential / permission boundary を広げる
- compatibility baseline を変更する
- 既存 capability と並行する第二実装を追加する
- 将来の migration を必要とする状態を追加する

判断が必要な場合、Impact Brief では推奨案を1つ示したうえで、選択によって何が固定されるかを明示する。

## 6. Implementation mode

人間の判断が不要、または必要な判断が確定した後に実装へ進む。

実装中は次を守る。

- brief で「変更しない」とした boundary を暗黙に変えない。
- 新しい architecture decision が発生したら実装だけで済ませず ADR を追加・更新する。
- 機械検査可能な invariant を新設する場合、review rule だけでなく verifier / lint / manifest 化を検討する。
- implementation が brief から実質的に変わった場合は brief の前提を再評価する。

## 7. Post-change system diff

実装後、PR を作る前に別観点で system diff を作る。

```text
# System Diff

Changed capabilities:
- ...

Changed Context / ownership:
- none / ...

Changed dependency direction:
- none / ...

Changed durable data / schema:
- none / ...

Changed background execution:
- none / ...

Changed external communication / permission / credential boundary:
- none / ...

New concepts introduced:
- ...

Concepts removed:
- ...

Invariants affected:
- ...

Documentation updated:
- ...

Tests proving the change:
- ...
```

System Diff が Impact Brief と大きく異なる場合は、実装途中で新しい設計判断が混入した可能性を確認する。

## 8. Independent review before PR

実装を担当した agent の自己説明とは別に、独立観点で最低限次を確認する。

### Public repository

- credential、token、OAuth secret、private key が含まれていないか。
- 実ユーザーのメールアドレス、URL、個人データ、private artifact が含まれていないか。
- fixture / log / screenshot / document に機微情報が入っていないか。
- `scripts/verify_public_repository.py` の対象外でも意味的に公開不適切な情報がないか。

### Architecture

- `docs/architecture/principles.md` の dependency / ownership rule に反していないか。
- owner Context を迂回して foreign table / concrete Data implementation へ触れていないか。
- app shell、presentation、composition の責務を混ぜていないか。
- duplicate runtime / duplicate source of truth / generic shared abstraction を増やしていないか。
- 既存 ADR の判断を暗黙に覆していないか。

### Tests

- 変更した責務に最も近い level の test があるか。
- architecture boundary の変更は `verifyArchitecture` 等で検証できるか。
- persistence / migration は upgrade / restore path を検証しているか。
- Android component / permission / navigation は unit test だけで十分かを確認したか。
- bug fix では可能な限り回帰 test があるか。

### Documentation

- user-visible behavior が変わる場合 `docs/spec.md` を更新したか。
- current architecture が変わる場合 `docs/architecture/` を更新したか。
- design decision がある場合 ADR を追加・更新したか。
- superseded / amended ADR relationship の更新漏れがないか。

## 9. Repository verification baseline

PR 前の基準は CI と同じものを利用する。

```bash
python3 scripts/test_verify_public_repository.py
python3 scripts/verify_public_repository.py
python3 -m unittest scripts.test_verify_adr_integrity
python3 scripts/verify_adr_integrity.py
./gradlew --no-daemon -I gradle/architecture-metadata.gradle.kts -I gradle/table-ownership.gradle.kts verifyArchitecture
./gradlew --no-daemon test
./gradlew --no-daemon :app:lintRelease
```

変更範囲によって instrumentation / E2E test が必要な場合は追加する。

## 10. Reusable agent task

以下を新規要求の discovery prompt としてそのまま利用できる。

```text
このリポジトリの production code はまだ変更しないでください。

要求を実装する前に Change Impact Review を行ってください。

1. docs/spec.md、docs/architecture/system-overview.md、principles.md、context-map.md を読む。
2. 関連する architecture document と ADR を特定する。
3. production code、test、machine-readable manifest を検索して現在の実装を確認する。
4. 既存 capability の拡張で要求を満たせるかを最初に検討する。
5. docs/architecture/change-impact-review.md の Impact Brief template に従って報告する。
6. 重要な主張には repository 内の evidence path を付ける。
7. ownership、durable state、cloud data egress、permission、compatibility、system-wide abstraction に新しい判断が必要なら明示する。
8. 実装案は複数比較し、推奨案と増える複雑性・削除コストを示す。
9. 人間の判断が必要な場合は production code を変更せず、その判断点までで止める。
10. 人間の判断が不要な場合は、Impact Brief の後に実装へ進める。
```

## Sources

- [`system-overview.md`](system-overview.md)
- [`principles.md`](principles.md)
- [`context-map.md`](context-map.md)
- [`testing.md`](testing.md)
- [`README.md`](README.md)
- [`../adr/0228-human-architecture-control-plane.md`](../adr/0228-human-architecture-control-plane.md)
