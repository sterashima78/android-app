# ADR-0192: PR quality checks と main APK build を分離する

- Status: Accepted
- Date: 2026-08-27
- Supersedes in part: [ADR-0164](0164-p1-owner-boundary-and-main-quality-gate.md) Decision 3
- Refines: [ADR-0093](0093-main-apk-build-run-status.md)

## Context

ADR-0164 では、branch protection の設定だけに依存せず、`main` push の signed APK build 自体を Architecture / Test / Lint の成功後に実行する方針を採用した。

その後、PR の品質 gate を GitHub repository ruleset の required status checks として直接管理し、`main` への直接 push を禁止する運用へ移行する方針を決めた。この構成では、PR で成功済みの Architecture / Test / Lint を `main` push でも再実行してから APK build することは重複になる。

また、従来の `quality` 集約 job は個々の required check をまとめるための互換 layer だったが、`Public repository` / `Architecture` / `Test` / `Lint` を ruleset から直接 require する場合は不要になる。

ADR integrity は path filter 付きの独立 workflow だった。GitHub の required status check として扱う workflow は trigger の path filter で workflow 全体が skip されると Pending のまま merge を妨げる可能性があるため、常時実行される `Architecture` check に統合する。

## Decision

1. Pull Request の品質検証は `.github/workflows/check.yml` が所有する。
   - `Public repository`
   - `Architecture`
   - `Test`
   - `Lint`
   の4つを安定した check name として直接公開し、集約 `quality` job は置かない。

2. `Architecture` check は module map / architecture verification に加え、ADR integrity の unit test と verifier を毎回実行する。独立した `.github/workflows/adr-integrity.yml` は廃止する。

3. `main` の signed APK build は `.github/workflows/build.yml` が所有する。`main` push では Architecture / Test / Lint を再実行せず、PR 側の required checks と repository ruleset を merge gate とする。

4. `main` build は release keystore を復元する前に `scripts/verify_public_repository.py` を実行する。公開リポジトリ安全性を signing secret の利用前に再確認する defense-in-depth は維持する。

5. ADR-0093 で定めた signed release APK、APK signature verification、artifact upload、`apk/main` commit status は維持する。

6. 最終状態の `main` ruleset は Pull Request 経由の変更を必須とし、`Public repository` / `Architecture` / `Test` / `Lint` を required status checks とする。force push、branch deletion、bypass を許可しない。

7. CI workflow の移行を ruleset 設定より先に行うことを許容する。repository の変更者が単一である現在の運用では、この移行中に一時的に direct push を技術的に阻止できない期間が生じることを受け入れ、ruleset 設定を直後の follow-up とする。

## Consequences

- PR の各品質検証が branch rule と一対一で対応し、失敗原因を直接確認できる。
- `main` push で PR 済みの Android test / lint / architecture verification を重複実行しないため、merge 後の APK 配布までの CI 時間と runner 消費を削減できる。
- merge safety の最終的な enforcement は workflow 内の aggregate dependency ではなく GitHub repository ruleset が担う。
- ruleset 設定完了までの短期間は direct push に対する自動防止が弱くなる。これは単一変更者という移行条件に限定して許容する。
- ADR integrity は全 PR の `Architecture` check に含まれるため、ADR 以外の変更でも identifier / link integrity を検証する。

## Verification

- Pull Request で `Public repository` / `Architecture` / `Test` / `Lint` の4 check が発行されること。
- `Architecture` check で `scripts.test_verify_adr_integrity` と `scripts/verify_adr_integrity.py` が実行されること。
- `.github/workflows/build.yml` が `main` で signed release APK を build し、Architecture / Test / Lint matrix を持たないこと。
- `ArchitectureCleanupSourceTest` で PR check workflow と main build workflow の分離を source regression として固定すること。
- workflow merge 後に repository ruleset へ4 checkを required status checks として設定し、direct push / force push / deletion / bypass が禁止されていることを確認すること。

## Relationship to previous decisions

ADR-0164 の Decision 1 と Decision 2（owner boundary に関する判断）は引き続き有効とする。Decision 3 と、それに対応する main quality gate の consequence / verification だけを本 ADR で置き換える。
