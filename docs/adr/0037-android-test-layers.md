# ADR 0037: Android テスト階層

## Status

Accepted

## Decision

Android のテストを次の4層に分ける。

1. Unit Test: JUnit 4 を `src/test` で利用する。
2. Robolectric Test: JUnit 4 + Robolectric を `src/test` で利用する。
3. Compose E2E: JUnit 4 + AndroidX Test + Compose UI Test を `src/androidTest` で利用する。
4. System E2E: JUnit 4 + UI Automator を `src/androidTest` で利用する。

Gradle Managed Devices は Pixel 6 / API 35 を基準環境とする。新しい E2E は Unit/Robolectric で表現できない主要フローに限定する。

## Consequences

高速な JVM テストを維持しながら Android 環境の回帰を検出できる。Emulator テストは実行時間が長いため、対象を主要フローに絞る。
