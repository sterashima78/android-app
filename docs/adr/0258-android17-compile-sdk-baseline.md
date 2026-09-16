# ADR-0258: Android 17 / API 37 を compile SDK baseline とする

- Status: Accepted
- Date: 2026-09-16
- Refines: [ADR-0160](0160-worker-runtime-and-android-17-baseline-cleanup.md), [ADR-0221](0221-android15-minimum-platform-baseline.md)

## Context

Android 17 / API 37 SDK は stable platform として利用可能になっている。現行 runtime baseline は Android 15 / API 35 以上で、Android 17 / API 37 も既にサポート対象としている。一方、build baseline は `compileSdk = 36` / `targetSdk = 36` のまま維持してきた。

AndroidX の current stable release の一部は compileSdk 37 を要求するため、compile SDK を36へ据え置くことが dependency maintenance の制約になっている。compileSdk の更新自体は target-specific runtime behavior を有効化しないため、`targetSdk = 37` への移行とは分離できる。

`targetSdk = 37` はローカルネットワーク通信の runtime permission と target-specific behavior changes を伴う。SMB と LAN Web Server を持つ本アプリでは、permission UX と実機・integration verification を伴う独立した platform migration として扱う必要がある。

## Decision

- すべての `com.android.application` / `com.android.library` module の `compileSdk` を API 37 に統一する。
- executable app の `targetSdk` は API 36 を維持する。
- `minSdk` は API 35 を維持する。
- CI と release build は Android API 37 platform / Build Tools 37 を利用する。
- API 37 compile baseline を要求する current stable dependency を通常の dependency maintenance 対象として利用できるようにする。
- この変更では `ACCESS_LOCAL_NETWORK` runtime permission を導入しない。`targetSdk = 37` への移行時に、SMB / LAN Web Server の permission UX、拒否時の挙動、integration test、Android 17 target-specific behavior changesを同じ変更で扱う。
- API 36 compile baseline を前提にした過去ADRの記述は歴史として保持し、current architecture document は本ADRを正として更新する。

## Consequences

### Positive

- API 37 SDK を通常のcompile contractとして利用できる。
- API 37 compile baselineを要求するAndroidX stable releaseへ更新できる。
- compile-time migrationとtarget-specific runtime behavior migrationを分離できる。
- Android 17 APIを数値compatibility constantで参照していた箇所を将来framework constantへ収束させる余地ができる。

### Negative

- CI / local development環境でAPI 37 SDKが必要になる。
- `targetSdk = 36` のため、Android 17 target-specific behaviorへの移行は別途残る。
- compileSdkとtargetSdkが一時的に異なるため、runtime availabilityとtarget-specific behaviorを混同しないレビューが必要になる。

## Verification

- 全Android application/library moduleが `compileSdk = 37`、`minSdk >= 35` であることを確認する。
- CIでAPI 37 SDKをinstallしてArchitecture / Test / Lint / R8を実行する。
- current stableのCore / Lifecycle / Navigation dependencyへ更新し、AAR metadataとcompile/test/lint/R8が成功することを確認する。
- executable appの `targetSdk = 36` が維持されていることを確認する。
- public repository verificationを維持する。

## Follow-up

`targetSdk = 37` は独立したADR/PRで扱う。少なくともローカルネットワークruntime permission、SMB接続・表紙先読み、LAN Web Server、Android 17 target-specific behavior、大画面でのorientation/resizability契約を検証してから採用する。
