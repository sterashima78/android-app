# ADR-0221: Android 15 / API 35 を最小プラットフォーム基準とする

- Status: Accepted
- Date: 2026-08-30
- Supersedes: [ADR-0126](0126-android-platform-baseline.md)
- Refines: [ADR-0003](0003-multi-module-architecture.md), [ADR-0046](0046-automated-architecture-verification.md)

## Context

ADR-0126 では Android 14 / API 34 を最小 runtime baseline とし、すべての Android application/library module を同じ `minSdk` に揃えた。その後も compile / target SDK は API 36 を維持し、Android module の platform contract は Architecture job で機械検証している。

現在のコードベースでは Android module を API 34 向けに個別配布・再利用する要件はなく、最小対応 OS を Android 15 / API 35 に引き上げても module ごとに異なる互換契約を保持する理由はない。runtime baseline と module baseline を引き続き一致させることで、API 34 専用 compatibility branch が再導入される余地を減らせる。

この変更は install 下限の更新であり、compileSdk / targetSdk の migration とは分離する。Android 15 / API 35 は安定版 SDK であり、現行の compileSdk / targetSdk 36 より低いため、build SDK を変更せずに採用できる。

## Decision

- アプリの最小対応 OS を Android 15 / API 35 とする。
- `com.android.application` または `com.android.library` plugin を利用するすべての module は `minSdk = 35` 以上を宣言する。
- JVM-only module には Android `minSdk` を導入しない。
- `compileSdk = 36` と `targetSdk = 36` は維持する。
- `gradle/table-ownership.gradle.kts` の Android platform baseline verification を API 35 基準へ更新し、API 34 以下または `minSdk` 未宣言の Android module を Architecture job で拒否する。
- API 34 だけを支えるための platform compatibility branch は新規追加しない。既存 branch を削除する場合は、behavior とテストへの影響を個別に確認する。
- DB migration、backup format、永続化済み WorkManager class identity などのデータ・ジョブ互換性は、OS install baseline とは別契約として維持する。

## Consequences

### Positive

- executable app と Android library module の runtime contract が API 35 に統一される。
- API 34 専用の dead compatibility path が再導入されることを architecture verification で防げる。
- compileSdk / targetSdk 36 を変更せず、install baseline の変更だけを独立して検証できる。
- JVM-only module に不要な Android platform contract を持ち込まない。

### Negative

- Android 14 / API 34 以下の端末では、新しい APK をインストールまたは更新できない。
- 新しい Android application/library module を追加する場合は API 35 以上を明示する必要がある。
- API 35 への引き上げだけでは targetSdk 由来の Android 15/16 behavior change は増減しないため、それらは targetSdk migration の責務として別途扱う。

## Alternatives considered

### API 34 を維持する

既存の Android 14 端末を継続サポートできるが、現在の platform baseline を広く保つ必要性より、runtime contract を単純化する利点を優先するため採用しない。

### app だけ API 35 にして library module は API 34 を許容する

APK の install 下限だけなら成立するが、module 内へ API 34 compatibility branch が残り得る。Android library を API 34 向けに独立配布する要件もないため採用しない。

### compileSdk / targetSdk も同時に変更する

変更目的と検証範囲が広がり、install baseline の変更と target-specific behavior change が混在する。現行 compileSdk / targetSdk 36 は維持し、別の platform migration として扱う。

## Verification

- `./gradlew --no-daemon -I gradle/architecture-metadata.gradle.kts -I gradle/table-ownership.gradle.kts verifyArchitecture`
- `./gradlew --no-daemon test`
- `./gradlew --no-daemon :app:lintRelease`
- `scripts/test_verify_public_repository.py`
- `scripts/verify_public_repository.py`
- PR diff で Android application/library module の `minSdk` が 35 以上であることを独立レビューする。
- API 35 managed device を利用する instrumentation/e2e 経路は既存設定を維持する。

## Public repository note

この ADR は公開可能な Android platform と build configuration の判断だけを記録する。credential、token、private endpoint、実ユーザーの URL・メールアドレス・個人データは含めない。
