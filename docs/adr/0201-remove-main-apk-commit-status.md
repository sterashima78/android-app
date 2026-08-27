# ADR-0201: main APK build の commit status publication を廃止する

- Status: Accepted
- Date: 2026-08-27
- Supersedes: [ADR-0093](0093-main-apk-build-run-status.md)
- Refines: [ADR-0197](0197-split-pr-checks-and-main-apk-build.md) Decision 5

## Context

ADR-0093 では、main の signed release APK artifact を生成した Actions run をコミットから追跡するため、artifact upload 後に `apk/main` commit status を登録する方針を採用した。

現在は APK の取得元として GitHub Actions run と `mosaic-android-apk` artifact を直接利用しており、`apk/main` status を追加の参照点として維持する必要がない。status publication のためだけに `statuses: write` 権限と GitHub Status API 呼び出しを持つことは、workflow の責務と失敗要因を増やす。

## Decision

main APK build から `Publish APK build run` step を削除し、`apk/main` commit status を登録しない。

同時に、build job から不要になった `statuses: write` 権限を削除し、workflow token は `contents: read` のみに戻す。

次の main build 契約は維持する。

1. tracked public content を検証する
2. release keystore を復元する
3. signed release APK をビルドする
4. `apksigner` で署名を検証する
5. version を含む APK 名へ変更する
6. `mosaic-android-apk` artifact として upload する
7. release keystore を常に削除する

APK の所在は GitHub Actions run と artifact 自体を参照し、commit status を配布契約として扱わない。

## Consequences

### Positive

- 不要な `statuses: write` 権限を削除できる。
- Status API 呼び出し失敗によって、APK upload 後の build job が失敗扱いになる経路をなくせる。
- main build workflow の責務が APK の安全な生成と artifact upload に限定される。

### Negative

- コミットの `apk/main` status から対応 Actions run へ直接遷移する参照点はなくなる。
- APK を探す処理は Actions run / artifact を直接参照する必要がある。

## Verification

- `.github/workflows/build.yml` に `Publish APK build run` step が存在しないこと。
- build job に `statuses: write` が存在しないこと。
- Status API の `/statuses/${{ github.sha }}` 呼び出しが存在しないこと。
- signed release APK の build、signature verification、`mosaic-android-apk` artifact upload が維持されること。
- PR の `Public repository` / `Architecture` / `Test` / `Lint` checks が成功すること。

## Relationship to existing ADRs

- ADR-0093 を本 ADR で廃止する。
- ADR-0197 の PR quality checks と main APK build の分離方針は維持し、Decision 5 のうち `apk/main` commit status のみを変更する。
