# ADR-0215: current architecture documentation compatibility verification を Gradle metadata verifier へ統合する

- Status: Accepted
- Date: 2026-08-29
- Refines: [ADR-0122](0122-current-architecture-documentation.md), [ADR-0151](0151-retire-current-architecture-compatibility-redirects.md), [ADR-0214](0214-gradle-architecture-metadata-verification.md)

## Context

`ArchitectureDocumentationSourceTest` は、ADR-0151 で廃止した旧 Context Map compatibility entry が再作成されないことと、Markdown 文書がその互換 path を再参照しないことを app の JVM unit test として検査していた。

この規則はアプリ runtime や feature behavior ではなく repository documentation の整合性である。ADR-0214 で module map と ADR integrity を `gradle/architecture-metadata.gradle.kts` に集約した後も、この1規則だけが app unit test から repository 全体の Markdown を走査していた。

## Decision

1. 廃止済み current-architecture compatibility path の存在・参照検査を `gradle/architecture-metadata.gradle.kts` が所有する。
2. 旧 compatibility entry は引き続き存在させず、current document は `docs/architecture/context-map.md` を直接参照する。具体的な退役 path は verifier の禁止値としてだけ保持し、current documentation へ再掲しない。
3. verifier 内に次の fixture を持つ。
   - current path の直接参照は許可する。
   - retired compatibility document の再作成は拒否する。
   - Markdown から retired path を参照する場合は拒否する。
4. 重複する `ArchitectureDocumentationSourceTest` は削除する。
5. 文書の意味的な正しさ、リンク先が意図した設計判断かどうかは引き続きレビュー対象とする。

## Consequences

- documentation-only guard が app unit test から Architecture metadata verification へ移り、検証責務が実装対象と一致する。
- `./gradlew test` が repository documentation を走査する必要がなくなる。
- current architecture compatibility rule とその fixture が module-map / ADR integrity と同じ Gradle verification entrypoint で実行される。
- Markdown text の参照検査自体は文字列一致であり、意味解析は行わない。

## Verification

- `gradle/architecture-metadata.gradle.kts` の current-path / restored-path / stale-reference fixture
- repository の `docs/**/*.md` に対する retired path reference 検査
- Architecture / Test / Lint / Public repository CI

## Public repository review

本変更は synthetic fixture、architecture verification code、architecture documentation のみを扱う。credential、token、private endpoint、実ユーザー情報、database/backup artifact、診断 export を追加しない。
