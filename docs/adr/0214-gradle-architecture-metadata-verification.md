# ADR-0214: architecture metadata verification を Gradle/Kotlin に統合する

- Status: Accepted
- Date: 2026-08-28
- Refines: [ADR-0046](0046-automated-architecture-verification.md), [ADR-0055](0055-adr-numbering-policy.md), [ADR-0144](0144-composition-runtime-groups-and-module-map-verification.md), [ADR-0197](0197-split-pr-checks-and-main-apk-build.md)

## Context

Architecture CI は Kotlin/Gradle の module/source ownership 検証に加え、次の metadata verifier を Python script として実行していた。

- `scripts/verify_module_map.py`
- `scripts/test_verify_module_map.py`
- `scripts/verify_adr_integrity.py`
- `scripts/test_verify_adr_integrity.py`

module map verifier は `settings.gradle.kts` の `include("...")` 文字列を正規表現で再解析していた。しかし module graph の正本は Gradle が評価した project model であり、build script の表記方法を別 runtime で再解釈する必要はない。

ADR integrity は Markdown text の検証なので text parsing 自体は必要だが、Architecture CI が既に Java/Gradle を起動する中で、この2検証のためだけに独立した Python test/implementation pair を維持する理由は小さい。

一方、public repository verifier は credential/private artifact の検出を担当する security boundary であり、今回の機械的な移植対象にはしない。また Kotlin source の semantic ownership rule は現在 Gradle task / source regression test に Regex が残っており、これらは custom Android Lint や class/package dependency checker への段階移行を別途検討する。

## Decision

### 1. module map と ADR integrity を `gradle/architecture-metadata.gradle.kts` に統合する

Architecture CI は次を実行する。

```bash
./gradlew --no-daemon \
  -I gradle/architecture-metadata.gradle.kts \
  -I gradle/table-ownership.gradle.kts \
  verifyArchitecture
```

metadata init script は Gradle configuration 後に module map と ADR integrity を検証する。

### 2. module graph は評価済み `Project.path` を正本にする

feature/layer 一覧は `rootProject.allprojects` から取得する。

`settings.gradle.kts` の source text を Regex で解析しない。これにより `include` の記述形式や settings のリファクタリングに verifier が不必要に結合しない。

`docs/architecture/module-map.md` の `feature-modules:start/end` block は引き続き documentation projection とし、missing row、stale row、layer mismatch を検出する。

### 3. ADR integrity fixture も Kotlin 側で維持する

次を最低限 fixture として固定する。

- 正常な ADR / current architecture document の参照
- duplicate ADR number
- filename / heading number mismatch
- malformed filename
- missing numeric ADR reference
- missing explicit ADR link target
- current architecture document からの broken reference

Python unit test を別に維持しない。

### 4. security verification と source semantic verification は一括移植しない

`verify_public_repository.py` は今回残す。公開情報漏洩を防ぐ security-critical verifier なので、置換する場合は同等以上の fixture と review を持つ独立変更とする。

Kotlin source の API usage / import / inheritance を source string Regex で検査している rule は、今後次の順で段階移行を検討する。

1. Gradle dependency rule -> Gradle `ProjectDependency` / convention plugin
2. Android/Kotlin API usage rule -> custom Android Lint (UAST / symbol resolution)
3. compiled class/package dependency rule -> ArchUnit 等
4. 特定ファイル名・関数名だけを見る brittle regression -> behavior test / visibility / module boundary で代替できるなら削除

今回これらを同時に変更しない。

## Consequences

### Positive

- Architecture CI から module-map / ADR integrity 用 Python runtime invocation を除去できる。
- module map が build script text ではなく実際の Gradle project graph を検査する。
- architecture metadata fixture と production verification が同じ Kotlin implementation を通る。
- verifier の実行入口を `verifyArchitecture` + Gradle init script に集約できる。

### Negative

- `architecture-metadata.gradle.kts` は init script であり、将来 rule が増えすぎる場合は included build / convention plugin への昇格が必要になる。
- Markdown reference validation は引き続き Regex ベースである。これは Kotlin source semantic analysis とは異なり、Markdown text contract を直接検証するため許容する。

## Verification

- metadata init script 内の compatibility fixture
- repository 実データに対する module map consistency
- repository 実データに対する ADR integrity
- existing `verifyArchitecture`
- Public repository / Test / Lint CI

## Public repository review

本変更は build/CI verification code、synthetic fixture、architecture documentation のみを扱う。credential、token、private endpoint、実ユーザー情報、database/backup artifact、diagnostic export を追加しない。
