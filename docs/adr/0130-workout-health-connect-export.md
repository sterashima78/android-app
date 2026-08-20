# ADR-0130: Workout を source of truth として Health Connect へ一方向 export する

- Status: Accepted
- Date: 2026-08-20
- Refines: [ADR-0127](0127-health-connect-read-only.md), [ADR-0125](0125-application-service-and-capability-segregation.md), [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md)

## Context

ADR-0127 では Health Connect 導入時の権限とデータ流通を最小化するため、Health Context を読み取り専用とし、Workout Context との相互同期を行わないことを決めた。

一方、Workout Context でユーザーが記録・完了したワークアウトは、このアプリ自身が所有するデータである。これを Health Connect の運動セッションとして公開すれば、Health Connect を利用する他の健康・フィットネス機能から同じ実績を参照できる。

ただし Health Connect から読み取った運動を Workout の永続状態へ取り込んだり、読み取ったデータをそのまま Health Connect へ書き戻したりすると、source of truth、重複、同期ループが曖昧になる。また Health Connect は機微な健康データを扱うため、書き込み対象と権限を必要最小限に保つ必要がある。

## Decision

- Workout Context は引き続きアプリ内ワークアウト記録の source of truth とする。
- ユーザーが Workout を終了して保存したことを契機に、完成した `WorkoutHistory` を Health Connect へ一方向に export する。
- Health Connect から Workout Context への import / 同期は行わない。Health Connect から読み取った運動をそのまま書き戻す経路も作らない。
- Workout Domain は Health Connect API に依存せず、`WorkoutHistoryExporter` capability を通じて外部 export を要求する。
- Health Domain は `HealthWorkoutWriter` capability と、Health Connect 非依存のワークアウト書き込みモデルを公開する。
- Workout と Health のモデル変換は app composition 層の application adapter が担当し、Health Connect の Record 型は `:feature:health:data` に閉じる。
- Health Connect への書き込み権限は `WRITE_EXERCISE` のみ追加する。既存の read permissions、バックグラウンド権限、履歴権限は拡大しない。
- 書き込み前に毎回権限を確認し、未許可・Health Connect 利用不可・書き込み失敗でもローカルの Workout 保存は維持する。
- Health Connect の `ExerciseSessionRecord` を筋力トレーニングとして書き込み、各セットを可能な範囲で `ExerciseSegment` に変換する。
  - リバースクランチ: crunch
  - ランジ: lunge
  - プランク: plank
  - 踏み台昇降: stair climbing
  - 安定版 API に専用種別がない腕立て伏せ、ユーザー追加種目: other workout
- 回数種目は repetitions を書き込む。時間種目は segment の start/end で時間を表現する。
- 新規セットには start/end を保存する。既存データで start/end がない場合は、記録時刻と回数/秒数から export 用区間を補完する。
- Health Connect の segment は重複不可のため、export adapter で時刻順に並べ、重複する区間は後続 segment の開始を前 segment の終了へ切り詰める。
- `Metadata.clientRecordId` に Workout history ID 由来の安定 ID を設定し、再試行時は `insertRecords` の upsert semantics を利用して重複を防ぐ。
- Health Connect へ渡す notes は種目名・セット数・回数/秒数・踏み台段数から生成する。Workout の自由記述 memo は送信しない。
- `androidx.health.connect:connect-client:1.1.0` を維持する。`setIndex`、weight、RPE など 1.2 alpha 系で追加された項目のためだけに不安定版へ更新しない。
- Health Connect の permission rationale と Health UI で、読み取りに加えて「このアプリで終了した Workout のみを書き込む」ことを明示する。

## Consequences

### Positive

- Workout の ownership を維持したまま、Health Connect を介してワークアウト実績を他の健康機能から利用できる。
- 一方向 export に限定するため、Health Connect と Workout 間の同期ループや競合を避けられる。
- Health Connect API 依存を Health Data 層に閉じ、Workout Domain/UI の platform 依存を増やさない。
- client record ID により export の再試行を安全に行いやすい。
- 自由記述 memo を外部健康データ基盤へ送らず、必要なデータだけに限定できる。

### Negative

- Health Connect 側で編集・削除された内容は Workout Context へ反映されない。
- stable 1.1.0 では腕立て伏せ専用 segment type や set index / weight を十分に表現できない。
- 既存記録にはセットの厳密な開始/終了時刻がないため、export 時の時刻は補完値になる。
- 権限未許可や一時的な書き込み失敗時に自動バックグラウンド再試行は行わない。

## Verification

- Workout history から Health workout session への mapping を unit test し、種目、repetitions、時刻、segment 非重複を確認する。
- Health Connect writer が `WRITE_EXERCISE` を毎回確認し、`ExerciseSessionRecord` / `ExerciseSegment` のみに書き込むことをレビューする。
- final merged manifest に追加される write permission が `android.permission.health.WRITE_EXERCISE` のみであることを確認する。
- `verifyArchitecture`、影響 module の unit tests、全体 unit tests、release lint を CI で実行する。
- Workout Domain/UI から `androidx.health.connect` への直接依存がないことを確認する。
- Health Connect 由来データを Workout DB、Backup、AI task、外部 API へコピーする経路が追加されていないことを確認する。
- 公開リポジトリに実健康データ、credential、token、個人を識別する fixture が含まれていないことを確認する。

## Public repository note

テストでは架空の日時・回数・段数だけを使用する。実ユーザーの Workout / Health Connect データ、アカウント情報、credential、token はリポジトリへ保存しない。
