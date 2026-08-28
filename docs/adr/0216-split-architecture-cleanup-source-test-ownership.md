# ADR-0216: architecture cleanup source regression を owner 別 test へ分割する

- Status: Accepted
- Date: 2026-08-29
- Refines: [ADR-0164](0164-p1-owner-boundary-and-main-quality-gate.md), [ADR-0166](0166-lan-web-and-route-composition-responsibility-split.md), [ADR-0167](0167-gradle-version-catalog-baseline.md), [ADR-0190](0190-isolate-local-text-inference-process.md), [ADR-0197](0197-split-pr-checks-and-main-apk-build.md), [ADR-0204](0204-app-composition-internal-package-ownership.md)

## Context

`app/src/test/.../ArchitectureCleanupSourceTest` は、過去の architecture cleanup で追加された source regression を追加順に集約していた。その結果、単一 class が次の独立した責務を同時に検査する状態になっていた。

- Reddit owner boundary
- LAN Web Data の transport / read model / renderer 分離と test source layout
- application composition の Settings / Summary ownership、provider-neutral AI inference、Route façade
- repository governance の diagnostic artifact、version catalog、CI workflow、external version identifier

各規則自体は ADR-0164、ADR-0166、ADR-0167、ADR-0190、ADR-0197、ADR-0204 で引き続き有効である。一方、それらを executable `:app` の1 test class が所有する理由はなく、変更時の test ownership と失敗原因が不明瞭になっていた。

## Decision

既存の source regression semantics を変更せず、検証対象を所有する module / responsibility へ分割する。

- `:feature:reddit:domain`
  - `RedditSourceBoundaryUsageArchitectureTest`
  - Reddit feature 外から低レベル classification API を利用しないことと、app route composition が `RedditSourceBoundary` を利用することを固定する。
- `:feature:web:data`
  - `LanWebArchitectureSourceTest`
  - `LanWebServer` / `LanWebReadModel` / `LanWebRenderer` の責務分離と Web Data test package/path consistency を固定する。
- `:app:composition`
  - `AppCompositionCleanupSourceTest`
  - Settings / Summary prompt ownership、provider-neutral text inference composition、`AppRouteDependencies` façade、generic runtime graph 非露出を固定する。
- executable `:app`
  - `RepositoryGovernanceSourceTest`
  - diagnostic artifact ignore、version catalog、PR/main workflow separation、external version identifier の repository-wide governance を固定する。

旧 `ArchitectureCleanupSourceTest` は削除する。

この分割は既存ADRの設計判断、production source、Gradle dependency graph、runtime lifetime、CI command を変更しない。古いADRに記録された `ArchitectureCleanupSourceTest` という検証責務は、本ADR以降は上記 owner-specific test 群が引き継ぐ。

## Consequences

- source regression の失敗箇所が実際の owner module / responsibility と一致する。
- executable `:app` test が feature Data / Domain / composition 内部の詳細を一括所有しなくなる。
- 既存の禁止条件と positive assertion は維持されるため、architecture guardrail の強度は下げない。
- repository-wide governance は executable application とCI artifactに関わるため `:app` test に残す。
- source text assertion 自体は implementation-sensitive なので、behavior contract へ置換可能な項目は将来 owner module で個別に判断する。

## Verification

- `RedditSourceBoundaryUsageArchitectureTest`
- `LanWebArchitectureSourceTest`
- `AppCompositionCleanupSourceTest`
- `RepositoryGovernanceSourceTest`
- `./gradlew --no-daemon test`
- `./gradlew --no-daemon -I gradle/architecture-metadata.gradle.kts -I gradle/table-ownership.gradle.kts verifyArchitecture`
- `./gradlew --no-daemon :app:lintRelease`
- public repository verifier と PR 前の意味的な公開情報レビュー

## Public repository review

本変更は synthetic source regression test と architecture documentation のみを扱う。credential、token、OAuth secret、private endpoint、実ユーザー URL / mail / library / health data、database、backup、diagnostic artifact を追加しない。
