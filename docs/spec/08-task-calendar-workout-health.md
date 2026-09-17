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
- AIによるメニュー提案は通常のWorkoutメニューと同じ構造で生成し、当日だけ使うかプリセットとして保存できる。
- AI支援の実行先は Local / cloud を明示選択し、既定はLocalとする。cloud選択時はWorkout記録・メモ・方針・メニュー候補をクラウドへ送信することを画面上で明示し、自動fallbackは行わない。
- AI支援へHealth Connect由来のread dataを入力しない。
- 完了したWorkoutは、許可されている場合にHealth Connectへ一方向exportできる。
- Workoutから活動消費カロリーや心拍数を推定して保存・書き込みしない。

## 8.4 Health

- Health Connectから歩数、活動消費カロリー、運動、心拍、睡眠、体重、体脂肪率、栄養情報等を読み取る。
- Health Connect由来のread dataはアプリdatabaseへ複製せず、Health画面のread modelとして利用する。
- Health ConnectからWorkoutへのimport / 双方向同期は行わない。
- アプリ内Workoutのexport以外の健康データを書き込まない。
