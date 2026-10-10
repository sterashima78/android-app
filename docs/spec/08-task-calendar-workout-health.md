# 8. タスク、カレンダー、ワークアウト、ヘルス

## 8.1 Task

- 階層を持つタスクを管理する。
- 完了状態、期限、並び順などを扱う。
- タスクの説明文に含まれる HTTP / HTTPS URL はリンクとして表示し、タップして開ける。
- ホーム画面widgetからタスクを参照できる。

## 8.2 Calendar

<!-- formal-requirement
id: CALENDAR-PROJECTION-OWNERSHIP-001
models:
  - spec-models/alloy/calendar_read_model_ownership.als
-->
- Calendarは独自のdurable event stateを所有せず、Task・Workout・端末カレンダーの現在状態から`CalendarEvent`を生成するread-only projectionとする。
- Task由来eventは`TaskReader`から期限付きTaskを読み`DEADLINE`へ、Workout由来eventは`WorkoutReader`から実績を読み`ACTIVITY`へ投影し、CalendarからTask / Workoutのcommand capability、table、private storageを参照しない。
- 端末カレンダーはAndroid Calendar Providerをread-only sourceとして扱い、予定を`SCHEDULE`へ投影する。Calendarは端末予定を書き込むcommandを所有しない。
- 各`CalendarEvent`はちょうど1つのsource recordから導出し、source固有のdurable stateのownerは元Contextまたは外部platformに残す。
<!-- /formal-requirement -->

### 形式モデル

- [Alloy: `calendar_read_model_ownership.als`](../../spec-models/alloy/calendar_read_model_ownership.als) — Calendarのdurable state / command非所有、sourceごとのownerとevent kind、各projectionの単一source関係を検査する。

## 8.3 Workout

- アプリ内で種目、セット、回数、時間などの運動記録を作成する。
- セットごとに、任意のRPE（1〜10）、フォーム状態（安定・崩れ・痛み/違和感）、負荷条件（身体の傾斜・追加重量・補助重量・その他）、実測した直前の休憩秒数を記録できる。未記録は未記録として保存する。
- 記録画面は詳細項目を折りたたみ、同種目の前回の負荷条件のみを明示操作で引き継げる。負荷を記録する場合は種類と値の両方が必要で、重量はkgで指定する。
- 記録済みの詳細情報は実施中のセットと完了履歴・コピー出力に表示し、AIレビューとメニュー提案へ一次情報として渡す。異なる負荷条件の単純比較を行わず、タイマー設定や時刻差を実休憩へ推定しない。
- Workout の記録を source of truth とする。
- 保存済みのWorkout payloadが破損・未知形式で読み込めない場合は、元データを保持し、空の記録として上書きせず画面にエラーを表示する。
- 種目マスタと、複数種目を組み合わせるメニューを分離して管理する。
- メニューは複数のプリセットとして保存でき、当日に使うメニューを選択できる。
- メニュー項目は種目ごとのセット数に加え、各セットの目標回数または目標秒数を保持できる。
- 構造化されたメニューJSONをインポートできる。未登録の種目が含まれる場合は種目マスタへ追加し、インポートしたメニューを当日だけ使うかプリセットとして保存できる。
- 日付単位のメモへ当日の所感等を記録できる。
- 直近14日間のWorkout実績、当日メモ、事前設定した方針、登録済み種目、プリセットメニューを使い、「メニュー提案」と「完了後レビュー」の2種類のAI支援を実行できる。
- Workout完了時は当日に選択していたメニューを履歴へsnapshotとして残し、完了後レビューでは登録済みプリセット全体ではなく、その日の予定メニューと実績を区別して評価する。
<!-- formal-requirement
id: WORKOUT-REVIEW-UNIQUENESS-001
models:
  - spec-models/alloy/workout_review_uniqueness.als
-->
- 完了後レビューは日付単位で保存し、後からチャット画面で閲覧できる。同じ日のレビューを再実行した場合は最新レビューへ置き換える。
<!-- /formal-requirement -->
- AIによるメニュー提案は通常のWorkoutメニューと同じ構造で生成し、当日だけ使うかプリセットとして保存できる。直近14日間の保存済みレビューも二次情報として参照し、現在の実績・メモ・方針を優先する。
- AI支援の実行先は Local / cloud を明示選択し、既定はLocalとする。cloud選択時はWorkout記録・メモ・方針・メニュー候補・保存済みレビューをクラウドへ送信することを画面上で明示し、自動fallbackは行わない。
<!-- formal-requirement
id: WORKOUT-AI-BACKGROUND-001
models:
  - spec-models/quint/workout_ai_task_lifecycle.qnt
-->
- Workoutのメニュー提案と完了後レビューはWorkout-owned background taskとして実行し、`QUEUED` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELLED`の状態を投影する。画面はtaskの登録・状態表示・結果回収だけを行い、推論実行をViewModel lifetimeへ依存させない。
- enqueue時にWorkManagerへ渡す入力はrequest typeと選択provider等のbounded metadataに限定し、Workout記録・メモ・方針・レビューから構築したprompt本文を固定保存しない。Workerが実行を開始するたびに、その時点のWorkout snapshot、設定、対象期間のメモ、保存済みレビューからpromptを構築する。
- provider一時停止等でretryする場合は同じrecoverable task referenceを保持して`QUEUED`へ戻し、再実行時にはpromptを再構築する。
- terminal taskは画面再生成後も結果または失敗状態を回収できるようreferenceを保持し、ユーザーが結果を消去するか失敗・取消しを消費した後にdismissする。対応するWorkが存在しないstale referenceは破棄できる。
<!-- /formal-requirement -->
<!-- formal-requirement
id: WORKOUT-HEALTH-BOUNDARY-001
models:
  - spec-models/alloy/workout_health_data_boundary.als
-->
- AI支援へHealth Connect由来のread dataを入力しない。
- 完了したWorkoutは、許可されている場合にHealth Connectへ一方向exportできる。
- Workoutから活動消費カロリーや心拍数を推定して保存・書き込みしない。
<!-- /formal-requirement -->

### 形式モデル

- [Alloy: `workout_review_uniqueness.als`](../../spec-models/alloy/workout_review_uniqueness.als) — 日付ごとの保存済みレビューを1件に限定し、同日の複数生成attemptがある場合は最新attemptだけを保存状態へ投影する。
- [Alloy: `workout_health_data_boundary.als`](../../spec-models/alloy/workout_health_data_boundary.als) — Health read dataをWorkout / AI / app databaseへ逆流させず、完了済みWorkoutだけを外部健康基盤へのwrite sourceとして許可する。
- [Quint: `workout_ai_task_lifecycle.qnt`](../../spec-models/quint/workout_ai_task_lifecycle.qnt) — Workout AI taskのrecoverable lifecycle、retry、terminal result回収、enqueue時prompt非固定と実行開始時freshnessを検査する。

## 8.4 Health

- Health Connectから歩数、活動消費カロリー、運動、心拍、睡眠、体重、体脂肪率、栄養情報等を読み取る。
<!-- formal-requirement
id: HEALTH-EXERCISE-DEDUP-001
models:
  - spec-models/alloy/health_exercise_deduplication.als
-->
- 運動履歴の表示用sessionは、運動種別・開始時刻・終了時刻が一致する完全一致を同一の実運動候補として重複除去する。
- 提供元が同一と判明しているsession同士は、完全一致でない限り時間帯が重なっていても別の実運動として保持する。
- 異なる提供元のsessionは、時間帯と長さが十分に一致する場合、または詳細sessionのsegmentと単独sessionが運動種別と時間帯の両方で十分に一致する場合に同一の実運動候補として統合できる。
- 重複除去後は各入力sessionをちょうど1つの保持sessionへ対応付け、保持sessionは必ず入力由来とし、保持session同士に同一実運動と判定される組を残さない。
<!-- /formal-requirement -->
- 重複候補の代表にはsegment、notes、title等の情報が豊富なsessionを優先する。具体的な時間重複率と長さ比率の閾値はData層の実装とunit testを正本とする。
<!-- formal-requirement
id: HEALTH-READ-OWNERSHIP-001
models:
  - spec-models/alloy/workout_health_data_boundary.als
-->
- Health Connect由来のread dataはアプリdatabaseへ複製せず、Health画面のread modelとして利用する。
- Health ConnectからWorkoutへのimport / 双方向同期は行わない。
- アプリ内Workoutのexport以外の健康データを書き込まない。
<!-- /formal-requirement -->

### 形式モデル

- [Alloy: `workout_health_data_boundary.als`](../../spec-models/alloy/workout_health_data_boundary.als) — Health由来read dataをHealth read modelだけへ限定し、WorkoutへのimportやAI入力、app databaseへの複製を禁止する。
- [Alloy: `health_exercise_deduplication.als`](../../spec-models/alloy/health_exercise_deduplication.als) — 運動sessionの代表projection、同一提供元の非完全一致保持、cross-origin重複証拠、kept集合の重複排除を検査する。
