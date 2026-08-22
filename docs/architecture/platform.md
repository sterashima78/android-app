# Android Platform Baseline

このドキュメントは、現在の Android platform / SDK 前提を現在形で示す。

## Supported runtime

- アプリの最小対応 OS は Android 14 / API 34 とする。
- Android 13 / API 33 以下は新しい APK の対応対象外とする。
- OS バージョン分岐は、API 34 未満だけを支える目的では追加しない。
- ただし、DB migration、backup format、永続化された background job の class identity など、アプリ内データ・ジョブ互換性は OS 互換性とは別契約として維持する。

## Build SDK

- `compileSdk`: API 36
- `targetSdk`: API 36
- `minSdk`: API 34

`compileSdk` と `targetSdk` は stable API 36 を維持する。Android 17 / API 37 は現在 preview SDK なので、通常 CI に preview channel を要求せず、SDK の提供状態と target-specific behavior change を確認する別変更で採用を判断する。

Android library module は app より低い `minSdk` / `compileSdk` を宣言していても app のインストール下限を下げない。module 単体で API 34 前提の分岐を削除する場合に、その module の min SDK 宣言を API 34 へ合わせる。全 module の宣言値を機械的に同一化すること自体は今回の目的としない。

## Implementation guidance

API 34 以上で常に成立する framework 契約は直接表現する。代表例は次の通り。

- `POST_NOTIFICATIONS` の runtime permission は API 34 実行を前提として扱う。
- Widget の fill-in intent 用 `PendingIntent` は mutability を明示する。
- API 34 の foreground service type を利用する background worker では、API 34 未満へフォールバックする `SDK_INT` 分岐を持たない。
- Health Connect など API 34 で platform 統合される capability を導入する module は、必要に応じて module 自身の `minSdk` も 34 とする。
- Health Connect の capability は API level だけで利用可能性を決めない。`READ_HEALTH_DATA_HISTORY` など extension / provider 更新に依存する機能は `HealthConnectFeatures` で feature status を確認してから対応 API や権限を利用する。

新しい platform API を導入する際は、アプリの min SDK だけでなく、その API を直接参照する Android library module の SDK 宣言も確認する。

## CI

CI は stable Android API 36 platform を利用し、少なくとも次を検証する。

- architecture verification
- unit tests
- release lint
- main branch の signed release APK build

Android 17 / API 37 を採用する際は、SDK の提供状態と behavior change を確認するテストを追加または更新してから行う。

## Sources

- [ADR-0126](../adr/0126-android-platform-baseline.md)
- [ADR-0127](../adr/0127-health-connect-read-only.md)
