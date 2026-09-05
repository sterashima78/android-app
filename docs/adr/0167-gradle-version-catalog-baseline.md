# ADR-0167: 共通 dependency version を Gradle version catalog へ集約する

- Status: Accepted
- Date: 2026-08-25
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0046](0046-automated-architecture-verification.md), [ADR-0126](0126-android-platform-baseline.md)

## Context

Android module の `build.gradle.kts` には、同じ external dependency version を複数箇所で文字列として宣言する残存がある。具体的には Settings data と Chat data が Kotlin coroutines 1.11.0 を重複して保持し、Chat data は Kotlin serialization と JUnit4 の version も直接保持していた。

一方、Android platform baseline の `minSdk = 34` は module ごとの明示宣言自体を architecture verification の対象としている。dependency version の重複削減と platform baseline の明示性は別の問題として扱う必要がある。

## Decision

### 1. Gradle version catalog を共通 dependency version の正本にする

標準位置 `gradle/libs.versions.toml` を追加し、今回移行する共通 dependency の version と alias を定義する。

- Kotlin coroutines Android
- Kotlin serialization JSON
- JUnit4

Settings data と Chat data は hard-coded coordinates/version ではなく generated `libs` accessor を利用する。

### 2. Android platform baseline の宣言方式は変更しない

本変更では `compileSdk = 36`、`minSdk = 34`、Java 17 の宣言方式を変更しない。特に `minSdk = 34` は各 Android module で明示し、既存 architecture verification を維持する。

compileSdk / Java toolchain の convention plugin 化は、全 Android module を同時に移行して source-of-truth を曖昧にしない形で別判断として扱う。

### 3. 移行済み dependency は literal version へ戻さない

source regression test で catalog entry と Settings/Chat data の alias 利用を確認し、今回移行した dependency が再び hard-coded version へ戻ることを防ぐ。

他 module の既存 dependency literal はこの ADR だけで一括移行済みとはみなさない。catalog を拡張する場合は、対象 dependency の利用箇所を同じ変更で移行する。

## Consequences

### Positive

- 共通 dependency version の更新箇所を Gradle 標準の catalog へ寄せられる。
- module build file は dependency の意味を alias で表現できる。
- platform baseline の既存 guardrail を壊さず段階的に build configuration を整理できる。

### Negative

- repository 全体の external dependency が一度に catalog 化されるわけではなく、移行期間中は catalog と literal declaration が併存する。
- alias naming を追加時にレビューする必要がある。

## Verification

- `ArchitectureCleanupSourceTest` で catalog entry と Settings/Chat data の alias 利用を検査する。
- existing Architecture / Test / Lint を実行する。
- `minSdk = 34` の module-level verification は変更しない。

## Documentation

- `docs/architecture/module-map.md` に build configuration source-of-truth の扱いを追記する。
- ADR index を ADR-0167 まで同期する。

## Public repository review

version catalog、build script、source regression、architecture documentation のみを変更する。credential、token、private endpoint、ユーザーデータ、database、backup、診断 artifact は含めない。

## 2026-09-03 extension

ADR-0221 により、上記の `minSdk = 34` は現在の platform baseline ではない。現在は `compileSdk = 36` / `targetSdk = 36` / `minSdk = 35` を維持し、本拡張でも platform baseline 自体は変更しない。

version catalog の対象を、既存の Kotlin coroutines Android / Kotlin serialization JSON / JUnit4 に加えて次へ拡張する。

- AndroidX Core KTX 1.17.0
- AndroidX Activity Compose 1.13.0
- AndroidX Navigation Compose 2.9.8
- AndroidX WebKit 1.16.0

追加した dependency は既存の全利用箇所を同じ変更で generated `libs` accessor へ移行する。`RepositoryGovernanceSourceTest` は移行対象 coordinate の literal 宣言を repository 内の `build.gradle.kts` から横断検出し、catalog と module-local declaration の二重正本化を防ぐ。

同時に Gradle wrapper を 9.5.0 から 9.6.1、Activity Compose を 1.11.0 から 1.13.0 へ更新する。AndroidX WebKit は 2026-09-03 時点の stable である 1.16.0 を維持し、version catalog への集約のみ行う。

2026-09-03 時点で、API 37 build baseline への更新を伴う dependency 群は本拡張には含めない。`compileSdk` / `targetSdk` を変更する場合は既存の platform ADR に従い、別の platform decision として扱う。また、公開直後の Android Gradle Plugin 9.4 系への更新は今回の catalog migration と分離し、現行 checks と build pipeline を安定させたうえで別変更として扱う。
