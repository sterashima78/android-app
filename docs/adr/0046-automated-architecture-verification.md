# ADR-0046: アーキテクチャ制約を CI で自動検証する

- Status: Accepted
- Date: 2026-08-14
- Updated: 2026-08-15

## Context

ADR-0003 では feature-first のマルチモジュール構成と layer 間の依存方向を定義している。

レビューだけでは、モジュール数と feature 間依存が増えるにつれて逆依存、循環依存、過去の構成に由来する source layout のずれを見落としやすくなる。

特に次のルールは機械的に検証できる。

- `core` -> `feature` を禁止する
- `domain` -> `ui` / `data` を禁止する
- `ui` -> feature の concrete `data` implementation を禁止する
- Gradle project dependency の循環を禁止する
- production Kotlin source の package declaration と `src/main/java` / `src/main/kotlin` 以下の物理 path を一致させる
- domain module の production Kotlin source から Android framework (`android.*`) を直接 import しない

## Decision

ルート Gradle project の `verifyArchitecture` task で、Gradle project dependency と production source layout / import を検証する。

project dependency の対象 configuration は次とする。

- `api`
- `implementation`
- `compileOnly`
- `compileOnlyApi`
- `runtimeOnly`
- build type / product flavor による上記 configuration の派生形（例: `debugImplementation`）

名前に `test` を含む configuration は production architecture の依存方向とは別の責務を持つため対象外とする。

source 検証では、各 subproject の `src/main/java` と `src/main/kotlin` にある Kotlin file を対象にする。

package/path 検証では、宣言 package を `/` 区切りへ変換した path と file の親 directory を比較する。package 宣言を持たない file はこの検証の対象外とする。

domain module では同じ production Kotlin source を走査し、`android.*` の直接 import を禁止する。Domain contract と業務ルールを Android framework から独立させ、Android 型の露出を早い段階で防ぐためである。

検証に失敗した場合は、依存違反、package/path mismatch、または Android import の箇所をエラーメッセージへ出力する。

GitHub Actions では次のタイミングで `./gradlew verifyArchitecture` を実行する。

- `main` 向け pull request の quality job
- `main` push 時の release build job

これにより、pull request だけでなく main への直接 push に対しても同じ制約を適用する。

## Scope

source layout 検証は package と directory の構造的一致だけを確認する。package が意味的に正しい owner module に属しているかまでは判定しない。

domain の Android 検証は直接の `android.*` import を対象とする。型 alias や別 module の API を介した間接的な Android 型露出、DB / HTTP 実装型の公開 API 露出までは判定しない。

次の制約は意味解析や source/API 解析が必要なため、この task では扱わない。

- feature 固有コードが generic `core` module に置かれていないこと
- DB / HTTP 型を Domain API に露出していないこと
- module の公開 API が必要以上に広くないこと
- package 名そのものが機能の ownership と意味的に一致していること

これらは引き続きレビューで確認し、機械判定可能な条件が明確になった場合に検証を追加する。

## Consequences

### Positive

- ADR-0003 の主要な依存方向を pull request ごとに自動検証できる
- module 数が増えても禁止依存のレビュー漏れを防げる
- 過去の directory 構成に source file だけが残る drift を検出できる
- Domain への Android framework 依存を追加時点で検出できる
- 違反時に修正箇所を特定しやすい
- main への直接 push でも architecture drift を検出できる

### Negative

- Gradle configuration 後の verification で production Kotlin source を走査する処理が追加される
- source-level coupling や ownership の意味までは検出できない
- Android 型の間接露出や DB / HTTP API 露出は別途レビューが必要
- 新しい layer、configuration naming、source layout を導入した場合は検証 task の更新が必要になる

## Relationship to ADR-0003

ADR-0003 の設計判断は変更しない。本 ADR は、そのうち機械的に判定できる Gradle project dependency rule、production source layout、domain の直接 Android import の強制方法を定める。
