# ADR 0038: Android テストを4層に分けて E2E を実行する

## Status

Accepted

## Context

機能間連携が増え、JVM 上のテストだけでは画面遷移や Android OS との連携を十分に検証できない。既存には JUnit と Robolectric の `StartupSmokeTest` があるが、instrumentation test はない。

配布 APK とローカル AI ランタイムは arm64-v8a を前提とするため、E2E 導入で配布構成を変更しない。

## Decision

テストを次の4層に分ける。

1. Unit Test
   - JUnit 4 を `src/test` で利用する。
   - 純粋関数、UseCase、ViewModel、Repository の契約を主対象とする。

2. Robolectric Test
   - JUnit 4 + Robolectric + AndroidX Test を `src/test` で利用する。
   - Activity 起動や Android API を含む軽量な統合を検証する。
   - 既存の `StartupSmokeTest` はこの層として維持する。

3. Compose Instrumented E2E
   - JUnit 4 + AndroidX Test + Compose UI Test を `src/androidTest` で利用する。
   - Emulator または実機上で `MainActivity` を起動し、主要なユーザーフローを検証する。
   - 初期対象は起動、ドロワー、RSS、ブックマークのナビゲーションとする。

4. System E2E
   - JUnit 4 + UI Automator を `src/androidTest` で利用する。
   - システム UI、権限ダイアログ、ファイルピッカー、他アプリへの Intent など Compose の外側を含む操作に利用する。
   - 自アプリ内だけで完結する操作には Compose UI Test を優先する。

Gradle Managed Devices の基準環境は Pixel 6 / API 35 / Google system image とし、`testedAbi = "arm64-v8a"` を指定する。これにより配布 APK の ABI と AI ランタイム構成を変更しない。

E2E は主要フローに限定する。Unit Test または Robolectric Test で十分に表現できる挙動は、より高速な層で検証する。

## Public repository policy

このリポジトリは public repository であるため、テストコードや fixture には次を含めない。

- 実アカウントの認証情報や OAuth token
- Cookie、セッション情報、API key、秘密鍵
- 実ユーザーのメール、蔵書、ブックマークなどの個人データ

外部サービスの認証後フローは fake/fixture または Repository 層のテストへ分離する。

## Consequences

- JVM テストの速度を維持しながら実 Android 環境の回帰を検出できる。
- Compose UI と Android システム UI の責務を分けてテストできる。
- Emulator テストは JVM テストより実行時間が長い。
- UI の文言や semantics を変更した場合は E2E テストの追従が必要になる。
