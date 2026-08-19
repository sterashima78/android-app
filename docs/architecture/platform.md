# Android Platform Baseline

このドキュメントは、現在の Android platform / SDK 前提を現在形で示す。

## Supported runtime

- アプリの最小対応 OS は Android 14 / API 34 とする。
- Android 13 / API 33 以下は新しい APK の対応対象外とする。
- OS バージョン分岐は、API 34 未満だけを支える目的では追加しない。
- ただし、DB migration、backup format、永続化された background job の class identity など、アプリ内データ・ジョブ互換性は OS 互換性とは別契約として維持する。

## Build SDK

- `compileSdk`: API 37
- `targetSdk`: API 36
- `minSdk`: API 34

`compileSdk` は利用可能な新しい Android API をコンパイル・lint 対象にするため最新化する。一方、`targetSdk` の更新は target-specific behavior change を受け入れる操作なので、compile SDK 更新とは分離して互換性を検証する。

Android library module は app より低い `minSdk` / `compileSdk` を宣言していても app のインストール下限を下げない。module 単体で API 34 前提の分岐を削除する場合に、その module の SDK 宣言も API 34 / 37 へ合わせる。全 module の宣言値を機械的に同一化すること自体は今回の目的としない。

## Implementation guidance

API 34 以上で常に成立する framework 契約は直接表現する。代表例は次の通り。

- `POST_NOTIFICATIONS` の runtime permission は API 34 実行を前提として扱う。
- Widget の fill-in intent 用 `PendingIntent` は mutability を明示する。
- API 34 の foreground service type を利用する background worker では、API 34 未満へフォールバックする `SDK_INT` 分岐を持たない。

新しい platform API を導入する際は、アプリの min SDK だけでなく、その API を直接参照する Android library module の SDK 宣言も確認する。

## CI

CI は Android API 37 platform を導入し、少なくとも次を検証する。

- architecture verification
- unit tests
- release lint
- main branch の signed release APK build

`targetSdk` を API 37 に変更する際は、Android 17 の behavior change を確認するテストを追加または更新してから行う。

## Sources

- [ADR-0124](../adr/0124-android-platform-baseline.md)
