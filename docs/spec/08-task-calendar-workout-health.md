# 8. タスク、カレンダー、ワークアウト、ヘルス

## 8.1 Task

- 階層を持つタスクを管理する。
- 完了状態、期限、並び順などを扱う。
- タスクの説明文に含まれる HTTP / HTTPS URL はリンクとして表示し、タップして開ける。
- ホーム画面widgetからタスクを参照できる。

## 8.2 Calendar

- Calendar は日付軸のread-only projectionとして扱う。
- Android Calendar Provider の予定に加え、Task の期限や Workout の実績を共通 `CalendarEvent` として表示する。
- Calendar 自身は Task / Workout の永続状態を所有しない。

## 8.3 Workout

- アプリ内で種目、セット、回数、時間などの運動記録を作成する。
- Workout の記録を source of truth とする。
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
- メニュー提案と完了後レビューはWorkout-owned background taskへ登録し、Workerが実行直前のWorkout記録・メモ・設定から入力を構築する。画面はtask stateと結果を表示し、推論実行をViewModel lifetimeへ依存させない。
- AI支援へHealth Connect由来のread dataを入力しない。
- 完了したWorkoutは、許可されている場合にHealth Connectへ一方向exportできる。
- Workoutから活動消費カロリーや心拍数を推定して保存・書き込みしない。

### 形式モデル

- [Alloy: `workout_review_uniqueness.als`](../../spec-models/alloy/workout_review_uniqueness.als) — 日付ごとの保存済みレビューを1件に限定し、同日の複数生成attemptがある場合は最新attemptだけを保存状態へ投影する。

## 8.4 Health

- Health Connectから歩数、活動消費カロリー、運動、心拍、睡眠、体重、体脂肪率、栄養情報等を読み取る。
- Health Connect由来のread dataはアプリdatabaseへ複製せず、Health画面のread modelとして利用する。
- Health ConnectからWorkoutへのimport / 双方向同期は行わない。
- アプリ内Workoutのexport以外の健康データを書き込まない。
