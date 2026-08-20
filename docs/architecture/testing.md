# Testing Strategy

この文書は、変更の責務に応じてどの種類のテスト・architecture verification を適用するかを示す。個別機能の詳細な test case は各 feature の実装と ADR を参照する。

## Principle

テストは実装詳細の階層ではなく、守りたい契約・rule の所有場所に合わせる。

```text
Pure Domain rule
  -> unit test

UseCase / Application Service
  -> fake / stub port を使う unit test

Repository / Data adapter
  -> contract / integration test

Cross-context Query / Projection
  -> integration test

Module / source / table ownership
  -> architecture verification

Android framework integration
  -> framework-aware test only where needed
```

## Domain tests

Domain test は Android、SQLite、HTTP 等から独立させる。

対象例:

- value / policy の invariant
- Content Classification の precedence
- Content Retention の cutoff / protected state
- task priority / state transition
- parser 後の純粋な transformation

Domain module に Android framework dependency を持ち込まないため、可能な限り通常の JVM unit test とする。

## Application Service / UseCase tests

複数 Repository / Context の orchestration は Application Service / UseCase 自体をテストする。

- port / Repository は fake を利用する
- call ordering が意味を持つ場合はその順序を検証する
- added / duplicate / skipped 等の集計規則を検証する
- failure / retry / partial result の意味が Domain/Application rule なら明示的にテストする

例として Bookmark import は Android document I/O や SQLite を UseCase へ持ち込まず、parse 済み entry と port を使う unit test で workflow を検証する。

## Repository and adapter tests

Data implementation は owner contract と persistence / remote semantics を検証する。

- Repository contract を満たすこと
- schema / migration の変更で既存 semantics を壊さないこと
- external API / parser adapter の edge case
- transaction が必要な operation の atomicity
- query port adapter が owner schema の意味を正しく公開すること

他 Context の実 table schema がなくても成立すべき Repository は boundary test でそれを固定する。例えば Content Repository は RSS/Summary の table schema を直接必要としないことを検証する。

## Projection tests

cross-context Projection を導入する場合は integration test を必須とする。

最低限次を確認する。

- read-only であること
- 宣言した owner table だけを参照していること
- schema change で破壊される場合に test が検出できること
- owner Repository/API の通常合成より Projection が必要な理由が ADR または計測で説明されていること

## Architecture verification

### `verifyArchitecture`

Gradle dependency と production source の構造的 guardrail を検査する。

現在の主な対象:

- `core -> feature` 禁止
- `domain -> ui/data` 禁止
- `ui -> concrete data` 禁止
- circular Gradle dependency 禁止
- package / physical path consistency
- Domain から Android import 禁止
- root app shell への feature UI ownership drift
- Screen での concrete dependency construction 禁止
- feature data から app implementation への依存禁止

rule 自体の regression は `verifyArchitectureRuleTests` fixture で検証する。

### Table ownership verification

CI では次のように table ownership init script を併用する。

```bash
./gradlew --no-daemon -I gradle/table-ownership.gradle.kts verifyArchitecture
```

`config/architecture/table-ownership.tsv` の owner 以外から durable table を参照した production source を原則失敗させる。既知の移行負債だけ `foreign-table-access-allowlist.tsv` で明示する。

### Framework provider boundary

Provider lookup は framework-owned entry point に限定し、manifest と production lookup の集合を architecture test で一致させる。不要になった manifest entry も stale として削除する。

### ADR integrity

ADR 自身の identifier / link integrity は次で検査する。

```bash
python3 -m unittest scripts.test_verify_adr_integrity
python3 scripts/verify_adr_integrity.py
```

新しい ADR は現在存在する最大番号より大きい一意な番号を使用し、見出し・ファイル名・参照先を一致させる。

## CI baseline

`.github/workflows/build-apk.yml` の pull request quality checks は、次の3検証を matrix の独立 runner で並列実行する。

```bash
./gradlew --no-daemon -I gradle/table-ownership.gradle.kts verifyArchitecture
./gradlew --no-daemon test
./gradlew --no-daemon :app:lintRelease
```

matrix は `fail-fast: false` とし、1つの検証が失敗しても他の検証結果を取得する。並列検証の完了後は互換性維持用の `quality` 集約 job が全体結果を判定する。

`main` push では architecture verification の後に signed release APK を build / signature verify する。

ADR 関連変更は `.github/workflows/adr-integrity.yml` でも ADR integrity checker を実行する。

CI workflow が変更された場合は、この文書のコマンドを正本とせず workflow を優先して本記述を更新する。

## Choosing tests for a change

変更範囲に対し「すべての種類のテストを追加する」のではなく、変更した契約を守る最小で適切な層を選ぶ。

| Change | Expected validation |
| --- | --- |
| pure Domain rule | unit test |
| multi-port orchestration | UseCase/Application Service unit test |
| SQL / migration | Repository/integration + migration-related test |
| parser / external adapter | adapter/parser test |
| cross-context read optimization | Projection integration test |
| module/source ownership rule | architecture fixture + `verifyArchitecture` |
| new table ownership rule | table ownership manifest/fixture + verification |
| framework Provider exception | boundary manifest/test |
| ADR-only change | ADR integrity; functional test追加は原則不要 |
| architecture docs only | link/source review; code behavior test追加は原則不要 |

PR review では test の「数」ではなく、変更した responsibility と failure mode を適切な test boundary で固定しているかを確認する。

## Sources

- [`.github/workflows/build-apk.yml`](../../.github/workflows/build-apk.yml)
- [ADR-0046](../adr/0046-automated-architecture-verification.md)
- [ADR-0055](../adr/0055-adr-numbering-policy.md)
- [ADR-0106](../adr/0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0119](../adr/0119-content-classification-retention-and-table-ownership-enforcement.md)
- [ADR-0120](../adr/0120-bookmark-application-service-and-framework-provider-boundary.md)
