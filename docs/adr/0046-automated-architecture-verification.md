# ADR-0046: ADR-0003 の Gradle 依存ルールを CI で自動検証する

- Status: Accepted
- Date: 2026-08-14

## Context

ADR-0003 では feature-first のマルチモジュール構成と layer 間の依存方向を定義している。

これまではレビュー時の確認に依存しており、モジュール数と feature 間依存が増えるほど、意図しない逆依存や循環依存を見落とす可能性が高くなる。

特に次のルールは Gradle project dependency から機械的に検証できる。

- `core` -> `feature` を禁止する
- `domain` -> `ui` / `data` を禁止する
- `ui` -> feature の concrete `data` implementation を禁止する
- Gradle project dependency の循環を禁止する

## Decision

ルート Gradle project に `verifyArchitecture` task を追加し、ADR-0003 の主要な project dependency rule を検証する。

対象は production code の直接 project dependency を表す次の configuration とする。

- `api`
- `implementation`
- `compileOnly`
- `runtimeOnly`

`testImplementation` や `androidTestImplementation` は production architecture の依存方向とは別の責務を持つため対象外とする。

検証に失敗した場合は、違反した source module、configuration、target module をエラーメッセージへ出力する。

GitHub Actions では次のタイミングで `./gradlew verifyArchitecture` を実行する。

- `main` 向け pull request の quality job
- `main` push 時の release build job

これにより、pull request だけでなく main への直接 push に対しても同じ制約を適用する。

## Scope

この検証は Gradle project dependency の構造だけを対象とする。

次の ADR-0003 ルールは意味解析や source/API 解析が必要なため、この task では扱わない。

- feature 固有コードが generic `core` module に置かれていないこと
- Android / DB / HTTP 型を Domain API に露出していないこと
- module の公開 API が必要以上に広くないこと
- Kotlin package / Android namespace と ownership が一致していること

これらは引き続きレビューで確認し、必要性が高まった場合は別の機械検証を追加する。

## Consequences

### Positive

- ADR-0003 の主要な依存方向を pull request ごとに自動検証できる
- module 数が増えても禁止依存のレビュー漏れを防げる
- 違反時に依存元と依存先が明示され、修正箇所を特定しやすい
- main への直接 push でも architecture drift を検出できる

### Negative

- Gradle configuration 時に全 subproject の直接 project dependency を確認する処理が追加される
- project dependency 以外の source-level coupling は検出できない
- 新しい layer や configuration を導入した場合は検証 task の更新が必要になる

## Relationship to ADR-0003

ADR-0003 の設計判断は変更しない。本 ADR は、そのうち機械的に判定できる Gradle project dependency rule の強制方法を定める。
