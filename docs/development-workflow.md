# Development Workflow

この文書は、このリポジトリで AI agent に開発を依頼するときの共通入口である。

依頼時にこのファイルを参照するよう指示された agent は、個別の実装指示だけでなく、この文書に定めた調査・設計判断・Draft PR・実装・検証・レビュー・PR・マージ・APK共有までの流れを適用する。

詳細なアーキテクチャ判断や current state はこの文書へ複製せず、`docs/architecture/`、`docs/adr/`、machine-readable architecture rule、production code、test を正本とする。

## 1. 基本方針

このプロジェクトでは、実装作業の大部分を AI agent に任せる。

人間がすべての生成コードを行単位で把握することは前提にしない。代わりに、次を人間が判断できる状態を維持する。

- 何を作るか / 作らないか
- 現在の system のどこへ変更を加えるか
- 既存 capability を再利用できるか
- どの設計案を採用するか
- 追加される複雑性や将来の削除コストを許容するか
- ownership、durable state、trust boundary、compatibility を変更してよいか

AI agent は調査、設計案作成、Draft PR 作成、実装、テスト、ドキュメント更新、独立レビュー、PR更新、マージまで担当できる。

## 2. 全体フロー

```text
要求
  |
  v
現状調査 / Discovery
  |
  v
Change Impact Brief（必要な変更のみ）
  |
  +--> 人間の判断が必要 --> 判断を得るまで production code を変更しない
  |
  v
実装ブランチ作成 / 最初の変更
  |
  v
Draft PR 作成
  |
  v
AI agent による実装
  |
  v
変更範囲に応じたテスト / architecture verification
  |
  v
System Diff
  |
  v
独立レビュー
  |
  v
Draft PR 更新 / Ready for review
  |
  v
CI
  |
  v
Squash merge
  |
  v
main の signed release APK build
  |
  v
APK共有
```

## 3. 依頼を受けたとき最初に行うこと

### 3.1 要求の目的を確認する

最初に「何を実現したいか」をユーザー視点で捉える。実装方法を要求そのものと混同しない。

要求が十分明確なら、不要な確認質問をせず調査へ進む。

### 3.2 この文書から current architecture へ降りる

調査の入口は次の順序とする。

1. `docs/development-workflow.md` — 開発手順
2. `docs/spec.md` — 現在のユーザー仕様
3. `docs/architecture/system-overview.md` — system 全体像、capability、主要 data flow
4. `docs/architecture/principles.md` — invariant、dependency / ownership rule
5. `docs/architecture/context-map.md` — Domain Context と関係
6. 対象に応じた `docs/architecture/*.md`
7. 関連 ADR — 判断理由、却下案、compatibility condition
8. production code / tests / machine-readable manifests — 実装との照合

関連 ADR は最新のものだけでなく、対象判断に至る古い ADR、`Superseded` / `Amended by` / `Refines` 等で接続された ADR も必要に応じて確認する。

詳細な調査手順は `docs/architecture/change-impact-review.md` を参照する。

## 4. 実装前の Change Impact Review

次のような変更では、production code を変更する前に Change Impact Brief を作る。

- 複数 Context / feature / module にまたがる
- 新しい capability、shared concept、module、application-scope dependency を追加する
- ownership、layer、module dependency、public capability を変更する
- database schema、durable state、backup、migration を変更する
- Worker、background queue、scheduler、background runtime を変更する
- AI provider、model runtime、cloud data egress、tool boundary を変更する
- Android component、permission、external Intent、WebView、network boundary を変更する
- credential / secret の保存・利用方法を変更する
- compatibility baseline を変更する
- 既存 capability と並行する第二実装を追加する可能性がある
- rollback / removal 時に migration が必要になる状態を追加する

表示文言、局所的な UI 調整、既存設計を変えない明確な bug fix などでは省略できる。

Change Impact Brief の形式と evidence rule は `docs/architecture/change-impact-review.md` を正本とする。

## 5. 人間の判断が必要な場合

次の判断は agent の実装上の都合だけで確定しない。

- 新しい system-wide abstraction / framework
- Context / ownership の移動
- new durable source of truth
- cloud data egress の追加
- credential / permission boundary の拡張
- compatibility baseline の変更
- 既存 capability と並行する第二実装
- 将来 migration を必要とする新しい状態
- 大きな将来コストを固定する設計

agent がレビューまたは事前判断が必要と判断した場合は、作業の最初にユーザーへ「この判断を agent 自身で行ってマージまで進めてよいか」を確認し、回答を得てから変更作業へ進む。

それ以外は、原則として独立レビューと CI に問題がなければ agent 自身で PR をマージしてよい。

## 6. 実装

実装時は `docs/architecture/principles.md` と対象領域の current architecture document を守る。

実装ブランチを remote へ push し、`main` との差分になる最初の変更ができた時点で Draft PR を作成する。大部分の実装、テスト、ドキュメント更新を終えるまで PR 作成を遅らせない。

`.github/workflows/cleanup-merged-branches.yml` は default / protected branch と open PR が使用する branch だけを保持するため、open な Draft PR は作業ブランチを保持するシグナルでもある。remote に作業ブランチを残したまま open PR がない状態を継続しない。

Draft PR の作成時点では最終的な System Diff や Verification result が未確定でもよい。目的、想定 scope、作業中であることが判別できる情報を記載し、実装の進行に合わせて更新する。

特に次を暗黙に変更しない。

- Context / feature ownership
- UI / Domain / Data dependency direction
- durable table ownership
- app / app:presentation / app:composition の責務
- application-scope runtime lifetime
- background runtime ownership
- external communication / credential / permission boundary
- compatibility contract

既存の仕組みで要求を満たせる場合は、新しい概念や第二実装を追加するより既存 capability の拡張を優先する。

設計的な意思決定または既存設計の変更が発生した場合は ADR に記録する。新規 ADR の追加だけを選択肢とせず、関連する古い ADR を確認し、判断の関係に応じて追記、`Amended by` / `Refines` 等の関係更新、`Superseded` への変更などを行う。古い ADR を現在形へ単純に書き換えて履歴を失わせない。

current architecture が変わる場合は `docs/architecture/` も同じ変更内で更新する。

## 7. テストと検証

変更範囲に最も近い level で検証する。

```text
Domain rule
  -> unit test

Repository / persistence
  -> repository / database / migration test

UI behavior
  -> Compose test

複数 feature にまたがる user flow
  -> integration / E2E / instrumented test

Architecture boundary
  -> verifyArchitecture / lint / machine-readable rule

Android component / permission / platform integration
  -> instrumented / integration test を検討
```

テスト数を増やすこと自体を目的とせず、変更によって壊れる可能性がある contract を検証する。

PR CI の baseline は `.github/workflows/check.yml` とし、少なくとも次を通す。

- Public repository verification
- Architecture verification
- Unit tests
- Android lint

変更範囲に応じて追加の instrumentation / E2E test を行う。

## 8. 実装後の System Diff

Draft PR を Ready for review にする前に、コード diff とは別に system の変化を整理する。

最低限、次を確認する。

```text
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

詳細は `docs/architecture/change-impact-review.md` の Post-change System Diff を参照する。

## 9. Ready for review 前の独立レビュー

実装を担当した観点とは別に、Draft PR を Ready for review にする前に少なくとも次の4点をレビューする。

### 9.1 Public repository

このリポジトリは public repository である。

次が変更へ含まれていないか確認する。

- credential、token、OAuth secret、private key
- 実ユーザーのメールアドレス、URL、個人データ
- private endpoint
- repository へ置くべきでない log、screenshot、trace、heap dump 等の artifact
- fixture / document 内の意味的に公開不適切な情報

`scripts/verify_public_repository.py` の機械検査だけに依存せず、人間または独立 agent の意味的レビューも行う。

### 9.2 Architecture

- Accepted ADR や current architecture から逸脱していないか
- owner Context を迂回していないか
- foreign table / concrete Data implementation に不適切に依存していないか
- duplicate runtime / duplicate source of truth を増やしていないか
- generic shared abstraction を安易に追加していないか
- 既存の意思決定を実装だけで暗黙に覆していないか

### 9.3 Tests

- 変更した責務に適した方法で test しているか
- bug fix に可能な限り regression test があるか
- persistence / migration / restore の変更を適切に検証しているか
- Android component / permission / navigation の変更を unit test だけで済ませてよいか確認したか

### 9.4 Documentation

- user-visible behavior が変わるなら `docs/spec.md`
- current architecture が変わるなら `docs/architecture/`
- design decision があるなら ADR
- 既存 ADR の判断を変更・補足・廃止するなら、関連する古い ADR の status / relationship / reference
- machine-checkable invariant が変わるなら verifier / lint / manifest

の更新漏れがないか確認する。

## 10. Pull Request

Draft PR は実装の早い段階で作成し、進捗に合わせて内容を更新する。Ready for review にする時点では、単なる実装ファイル一覧ではなく変更の意味を記載する。

最低限、次を含める。

- Summary
- System Diff
- Independent review result
- Verification / test result
- 必要な ADR / architecture / spec 更新

Ready for review にする前に `main` との差分を確認し、意図しないファイルが含まれていないことを確認する。

独立レビューと必要な検証が完了し、PR本文が現在の変更内容を反映したら Draft を解除して Ready for review にする。

## 11. CI とマージ

PR CI が成功し、独立レビューに重大な指摘がなければ squash merge する。

このリポジトリでは squash merge を基本とし、マージされた PR を1つの main commit へ収束させる。

CI が失敗した場合は、失敗した検証の内容を確認して修正する。単に検証を迂回したり無効化してマージしない。

## 12. マージ後の APK

`main` への push では `.github/workflows/build.yml` により signed release APK を生成する。

build では少なくとも次を行う。

- tracked public content verification
- signed release build
- APK signature verification
- version を含む APK 名への rename
- GitHub Actions artifact upload

build 成功後、agent は生成された APK artifact を取得してユーザーへ共有する。

production code を変更していない documentation-only PR であっても、通常の main build が生成した APK を共有する。

## 13. 情報の正本

この文書は「開発をどう進めるか」の正本であり、system の現在形そのものの正本ではない。

```text
docs/development-workflow.md
  開発の進め方 / decision gate / Draft PR / PR / merge / delivery

        |
        v

docs/architecture/system-overview.md
  人間が全体像を把握するための入口

        |
        v

docs/architecture/*.md
  現在の設計 / invariant / ownership

        |
        v

docs/adr/*.md
  なぜその判断をしたか

        |
        v

machine-readable rules / production code / tests
  機械的に検査可能な構造と実装
```

実装と architecture document が食い違い、設計変更を記録した Accepted ADR がない場合は、実装だけを理由に文書を書き換えず architecture drift の可能性として扱う。

## 14. 新規依頼での使い方

ユーザーは新しい依頼で、例えば次のように指示できる。

```text
この変更を実施してください。
docs/development-workflow.md に従って進めてください。
```

この指示を受けた agent は、この文書を入口として必要な current architecture / ADR / code / test を調査し、必要な場合だけ Change Impact Brief と人間の判断を挟み、Draft PR 作成から実装、APK 共有までを一連の作業として扱う。

## Related documents

- [`spec.md`](spec.md)
- [`architecture/system-overview.md`](architecture/system-overview.md)
- [`architecture/change-impact-review.md`](architecture/change-impact-review.md)
- [`architecture/principles.md`](architecture/principles.md)
- [`architecture/context-map.md`](architecture/context-map.md)
- [`architecture/testing.md`](architecture/testing.md)
- [`architecture/README.md`](architecture/README.md)
- [`adr/README.md`](adr/README.md)
- [`adr/0228-human-architecture-control-plane.md`](adr/0228-human-architecture-control-plane.md)