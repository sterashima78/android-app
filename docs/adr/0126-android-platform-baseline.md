# ADR-0126: Android 14 を最小プラットフォーム基準とする

- Status: Accepted
- Date: 2026-08-19
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0046](0046-automated-architecture-verification.md)

## Context

アプリの最小 SDK は API 29 (Android 10) だったが、実利用する端末と主要機能はより新しい Android API を前提としている。一方で、通知権限、`PendingIntent` の mutability、foreground service type などに旧 OS 向け分岐が残り、現在の実行環境では到達しない互換コードを維持していた。

ローカル AI、バックグラウンド処理、Widget など Android framework 依存の強い領域では、古い OS を維持することによるテスト・実装コストが、対応端末を広げる便益を上回っている。

Android 17 / API 37 は現在 preview SDK として提供されている。最小対応 OS の変更に preview compile SDK の採用を結び付けず、安定版 SDK の更新と Android 17 の target-specific behavior change 検証は別の変更として扱う。

## Decision

- アプリの `minSdk` を API 34 (Android 14) とする。
- `compileSdk` と `targetSdk` は安定版 API 36 を維持する。
- Android 17 / API 37 の compile/target SDK 採用は、SDK の提供状態と Android 17 の互換性を確認する別変更で判断する。
- API 34 未満だけを支える OS バージョン分岐は、対象コードの module が API 34 を最低要件として宣言できる場合に削除する。
- `PendingIntent` mutability、通知権限、API 34 の foreground service type など、API 34 では常に成立する前提を直接コードで表現する。
- SDK の引き上げを理由に、永続化された WorkManager class identity、DB migration、backup format 等のデータ・ジョブ互換性を削除しない。それらは OS バージョン互換とは別の契約として扱う。

## Consequences

### Positive

- Android 10〜13 向けだけの framework 分岐を減らせる。
- API 34 以降の foreground/background API を明示的に利用できる。
- Android framework の動作前提とテスト対象 OS の差が小さくなる。
- preview SDK を通常 CI の必須依存にせずに済む。

### Negative

- Android 13 / API 33 以下の端末では新しい APK をインストール・更新できない。
- module 単位で API 34 専用コードを導入する場合、その module の min SDK 宣言も合わせて確認する必要がある。
- Android 17 / API 37 の採用は別途残る。

## Alternatives considered

### API 29 を維持する

既存端末互換性は最大になるが、現在不要な framework 分岐とテスト対象を維持し続けるため採用しない。

### minSdk と compile/target SDK を同時に API 37 へ上げる

一度に最新化できるが、現時点では Android 17 SDK が preview であり、通常 CI に preview channel を要求する。インストール下限の判断と Android 17 の互換性検証も混在するため採用しない。

### minSdk を API 31 または 33 に留める

一部の分岐は削除できるが、API 34 foreground service API など主要な改善箇所に互換処理が残るため採用しない。

## Verification

- `verifyArchitecture`
- unit tests
- `:app:lintRelease`
- ADR integrity verification
- API 34 以上を前提にしたコードから不要な `SDK_INT` 分岐が除去されていることのレビュー

## Public repository note

この ADR は公開可能な Android platform の設計情報のみを記録する。credential、token、ユーザーデータ、非公開 endpoint は含めない。
