# ADR-0218: Kotlin source ownership rule を custom Android Lint へ段階移行する

- Status: Accepted
- Date: 2026-08-29
- Refines: [ADR-0046](0046-automated-architecture-verification.md), [ADR-0205](0205-app-presentation-module-boundary.md), [ADR-0214](0214-gradle-architecture-metadata-verification.md)

## Context

Architecture verification には Gradle project graph を直接検査する rule と、Kotlin production source を `readText()` / Regex で検査する rule が混在している。

Gradle dependency direction は `ProjectDependency` 等の Gradle model を使うのが適切だが、Kotlin/Java の import、API call、inheritance を source text として再解析すると alias、構文変更、コメント等へ不要に結合する。Android build では既に release Lint を必須 gate としているため、source semantic rule は Android Lint の UAST / symbol-aware detector へ段階的に寄せる方が自然である。

一方、既存 rule を一度に置換すると検出漏れを見逃しやすい。最初の移行では旧 rule と新 rule を重複実行して、実 repository と fixture の両方で互換性を確認する。

## Decision

### 1. repository-local custom Lint を `:lint-rules` が所有する

`:lint-rules` は build-time verification 専用の Kotlin/JVM module とする。

- runtime/application dependency として利用しない。
- Android app から `lintChecks(project(":lint-rules"))` でのみ参照する。
- Lint API は AGP 9.3.0 系と対応する `com.android.tools.lint:lint-api:32.3.0` を `compileOnly` で利用する。
- detector 自体は `lint-tests:32.3.0` の fixture で検証する。
- `IssueRegistry` は standard service loader で登録する。

### 2. 最初の移行対象を MainActivity feature boundary とする

`MainActivityFeatureBoundaryDetector` は `MainActivity.kt` の import declaration を UAST で走査し、次を error とする。

- `dev.terashima.yomitorirss.feature.*` 配下の feature-owned `*ViewModel` import
- `dev.terashima.yomitorirss.feature.*.data.*` の concrete Data import

alias import も同じ rule として扱う。`dev.terashima.yomitorirss.ui.AppViewModel` のような app-shell type は許可する。

これは既存 `sourceArchitectureViolations()` の MainActivity rule と同じ architecture contract を別の解析基盤で表現するものであり、MainActivity の runtime responsibility は変更しない。

### 3. 初回 PR では旧 Regex guard を残す

新しい custom Lint が実 repository の `:app:lintRelease` と detector fixture の両方で安定して動くことを確認するまでは、root Gradle の対応する MainActivity Regex rule と fixture を残す。

Lint導入が main で安定した後、独立した follow-up で重複する旧 Regex rule / fixture を削除する。検証空白を作るために先に旧 rule を削除しない。

### 4. rule の種類ごとに適切な基盤を使い分ける

- Gradle module dependency direction: Gradle model / convention logic
- Kotlin/Java import・API call・inheritance: custom Android Lint
- compiled class/package dependency が必要な場合: bytecode-aware architecture test を個別検討
- Markdown / ADR metadata contract: Gradle metadata verifier
- public repository security scan: security verifierを別境界として維持

全 architecture verification を custom Lint へ集約しない。

## Consequences

### Positive

- Kotlin import rule が file全体のRegexではなく構文上の import declaration を対象にできる。
- alias 等を専用fixtureで検証できる。
- Android Studio / Gradle Lint と同じ仕組みで開発時にも違反を検出できる。
- 段階移行により既存 guard と比較しながら Regex rule を減らせる。

### Negative

- build-time module と Lint API dependency が増える。
- Lint API は完全な安定APIではないため AGP/Lint更新時に detector test の追従が必要になる。
- 移行期間中は一部 rule が旧Gradle Regexとcustom Lintで重複する。

## Verification

- `MainActivityFeatureBoundaryDetectorTest`
  - feature ViewModel import
  - aliased feature ViewModel import（alias 前後の空白差を含む）
  - concrete feature Data import
  - app-shell ViewModel allow case
  - MainActivity以外の非対象file
- `./gradlew --no-daemon test`
- `./gradlew --no-daemon :app:lintRelease`
- existing Architecture / Public repository checks

## Public repository review

本変更は build-time Lint detector、synthetic fixture、Gradle wiring、architecture documentation のみを追加する。credential、token、keystore、private endpoint、実ユーザー URL / mail / library / health data、database / backup / diagnostic artifact は追加しない。
