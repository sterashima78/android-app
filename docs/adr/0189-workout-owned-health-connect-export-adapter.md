# ADR-0189: Health Connect export adapter を Workout Context が所有する

- Status: Accepted
- Date: 2026-08-26
- Supersedes: [ADR-0131](0131-workout-health-connect-export.md) の app composition adapter / Health-owned write capability に関する決定
- Refines: [ADR-0127](0127-health-connect-read-only.md), [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md)

## Context

ADR-0131 では Workout から Health Connect への一方向 export を導入する際、Workout Domain の `WorkoutHistoryExporter` と Health Domain の `HealthWorkoutWriter` を app composition 層の `WorkoutHealthConnectExporter` が接続する構成を採用した。

その後、`:app` に残る cross-feature / platform adapter を再点検したところ、この export は Bookmark から Library への移動のように二つの Context の永続状態を command orchestration する処理とは性質が異なることが分かった。

`WorkoutHealthConnectExporter` が実際に所有していたのは、Workout の種目・セット・回数・開始終了時刻を Health Connect の `ExerciseSessionRecord` / `ExerciseSegment` へ変換する規則、重複区間の補正、client record ID と notes の生成である。これらは Workout の完了済み記録を外部プラットフォームへ publish するための outbound adapter の責務であり、Health Context の状態を command していない。

また Health Domain に `HealthWorkoutWriter` と export 専用 DTO を置くと、Health Context が Health Connect API の汎用 facade のようになり、ADR-0127 で定めた「Health Connect を健康データの read-only source として扱う」責務が曖昧になる。

Health Connect は Bounded Context ではなく Android の外部データ共有プラットフォームである。同じ外部プラットフォームを複数 Context が、それぞれ異なる目的と権限で利用してよい。

## Decision

- Workout Context は完了済み Workout の Health Connect export を自身の outbound capability として所有する。
- `WorkoutHistoryExporter` は `:feature:workout:domain` に維持し、Domain/UI は Health Connect Record 型へ依存しない。
- Health Connect の具体的な write adapter は `:feature:workout:data` の `HealthConnectWorkoutHistoryExporter` とする。
- Workout -> `ExerciseSessionRecord` / `ExerciseSegment` の mapping、時刻補完、segment 非重複化、client record ID、notes 生成は Workout data adapter が所有する。
- `WRITE_EXERCISE` の permission set と manifest 宣言も `:feature:workout:data` が所有する。
- 書き込み権限は Workout Route から要求する。権限未許可で Workout を終了した場合もローカル保存を先に完了し、権限付与後に直近の保存済み Workout の export を再試行する。
- `:app` は concrete Workout repository / exporter の application-scope construction と Route への依存受け渡しだけを行う。Workout/Health model の変換規則を持たない。
- Health Context から `HealthWorkoutWriter` と export 専用 DTO を削除する。
- `HealthConnectHealthRepository` は Health Connect の read capability に戻し、通常の Health permission request と Health data manifest に write permission を含めない。
- Health Connect の read data を Workout へ import / sync しない方針、Workout を app 内記録の source of truth とする方針は維持する。
- export 対象は引き続き `ExerciseSessionRecord` と `ExerciseSegment` のみとし、歩数、消費カロリー、心拍などを Workout から推定して書き込まない。
- Health Connect SDK は stable `androidx.health.connect:connect-client:1.1.0` を維持する。

## Ownership after this decision

```text
Workout Context
  domain
    WorkoutHistory
    WorkoutHistoryExporter
  data
    DefaultWorkoutRepository
    HealthConnectWorkoutHistoryExporter
      -> WRITE_EXERCISE permission / manifest declaration
      -> ExerciseSessionRecord / ExerciseSegment
  ui
    WorkoutRoute
      -> Health Connect write permission request

Health Context
  domain
    HealthRepository / Health read models
  data
    HealthConnectHealthRepository
      -> Health Connect read permissions
  ui
    HealthRoute
      -> Health Connect read permission request

app
  -> construct Workout repository/exporter and Health repository
  -> pass dependencies to routes
```

## Consequences

### Positive

- Workout の export 仕様変更が `:feature:workout` 内に閉じる。
- `reverse-crunch`、`lunge`、`plank`、`step-up` など Workout 固有語彙から Health Connect segment への mapping ownership が明確になる。
- Health Domain から Workout export 専用 DTO / writer capability を除去でき、Health Context を read responsibility に戻せる。
- `:app` の cross-feature adapter を一つ削減し、composition root を construction/wiring に限定できる。
- Health Connect の read permission と Workout write permission の利用目的が UI 上も分離される。

### Negative

- `androidx.health.connect` への依存が `:feature:health:{data,ui}` だけでなく `:feature:workout:{data,ui}` にも存在する。
- 同じ外部 SDK を複数 Context が利用するため、SDK version はプロジェクト全体で揃えて更新する必要がある。
- Workout export と Health read の Health Connect client は別 adapter から生成される。ただし SDK client は外部 platform access であり、Domain state の二重 ownership ではない。

## Verification

- `:feature:workout:data` の unit test で Workout history -> `ExerciseSessionRecord` mapping、segment type、repetitions、時刻、非重複化、notes を検証する。
- Workout data test で write permission set が `WRITE_EXERCISE` だけであることを検証する。
- exporter gateway test で unavailable / permission required / success / write failure を `WorkoutExportResult` へ変換することを検証する。
- Health data test では read permission / history permission のみを検証し、write permission ownership が戻らないようにする。
- `WRITE_EXERCISE` の manifest 宣言が `:feature:workout:data` にだけあり、`:feature:health:data` に残っていないことを確認する。
- `HealthWorkoutWriter`、app-level `WorkoutHealthConnectExporter`、Health data の Workout write method が production source に残っていないことを確認する。
- `verifyArchitecture`、`:feature:workout:data:testDebugUnitTest`、`:feature:health:data:testDebugUnitTest`、影響 module tests、release lint を CI で実行する。
- 公開リポジトリに実 Workout/Health データ、credential、token、個人を識別する fixture を含めない。
