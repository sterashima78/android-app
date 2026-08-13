# ADR 0037: Android テストを4層に分けて E2E を CI で実行する

## Status

Accepted

## Context

機能間連携が増え、JVM 上のテストだけでは画面遷移や Android OS との連携を十分に検証できない。既存には JUnit と Robolectric の `StartupSmokeTest` があるが、instrumentation test はない。

配布 APK とローカル AI ランタイムは arm64-v8a を前提とするため、E2E 導入で配布構成を変えない。

## Decision

テストを4層に分ける。

1. Unit Test
   - JUnit 4、`src/test`
   - 純粋関数、UseCase、ViewModel、Repository 契約を主対象とする。

2. Robolectric Test
   - JUnit 4 + Robolectric + AndroidX Test、`src/test`
   - Activity 起動や Android API を含む軽量統合テストを対象とする。
   - 既存 `StartupSmokeTest` をこの層として維持する。

3. Compose Instrumented E2E
   - JUnit 4 + AndroidX Test + Compose UI Test、`src/androidTest`
   - Emulator/実機で MainActivity を起動し、重要なユーザーフローを検証する。
   - 初期対象は起動、ドロワー、RSS とブックマークのボトムタブとする。

4. System E2E
   - JUnit 4 + UI Automator、`src/androidTest`
   - システム UI、権限、ファイルピッカー、外部 Intent など Compose の外側を対象とする。
   - 自アプリ内の操作は Compose UI Test を優先する。

Gradle Managed Devices は Pixel 6 / API 35 / Google system image を用い、`testedAbi = "arm64-v8a"` を指定する。配布 APK の ABI と AI ランタイム構成は変更しない。

テストデータは再現可能な fixture とし、公開リポジトリの既存のデータ取り扱い方針を維持する。

## Consequences

- JVM テストの速度を維持しながら、実 Android 環境の回帰を検出できる。
- Compose UI とシステム UI のテスト責務が明確になる。
- Emulator テストは JVM テストより遅く、UI semantics の変更時には追従が必要になる。

## Follow-up

新しいテストは Unit/Robolectric で表現できるかを先に検討し、必要な主要フローだけを Compose E2E/System E2E に追加する。
