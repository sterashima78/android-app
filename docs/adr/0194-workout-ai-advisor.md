# ADR-0194: Workout のメニュー提案・完了後レビューを feature-owned AI capability として実装する

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0016](0016-workout-tracking.md), [ADR-0165](0165-provider-neutral-text-inference-contract.md), [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md)

## Context

Workout では手入力したセット記録と履歴を端末内に保持している。新たに、直近2週間の実績、ユーザーが事前入力したワークアウト方針とメニュー候補、当日の所感を LLM に渡し、「今日のメニュー提案」と「完了後レビュー」の2操作だけを提供したい。

これは自由入力のチャットではなく Workout 文脈に閉じた2種類の one-shot inference である。また既存アプリには provider-neutral な Local AI capability と ChatGPT / Codex cloud adapter があり、Summary / Knowledge ではユーザーが Local / ChatGPT を明示選択する方針を採用している。

Workout の記録は身体活動に関するデータであるため、Cloud を選択した場合に何が外部送信されるかを UI 上で明示する必要がある。一方で Health Connect の read data を AI へ流さないという既存の privacy boundary は維持する。

## Decision

### 1. 自由チャットではなく2つの one-shot action とする

Workout 画面に次の2操作を追加する。

- `メニュー提案`: 今日行う種目、セット数、1セットあたりの回数または秒数を提案する。
- `完了後レビュー`: 当日の実績と所感を基に、良かった点、負荷評価、次回調整案を返す。

会話履歴や任意のユーザーメッセージ入力は持たない。各実行はその時点の Workout data から prompt を再構築する。

### 2. Workout context は直近14日を feature 内で組み立てる

`WorkoutAiPromptBuilder` は次を入力にする。

- WorkoutRepository の直近14日間の履歴
- 当日まだ終了していないセット記録
- 当日および直近履歴日のワークアウトメモ
- ユーザーが設定したワークアウト方針
- ユーザーが設定したメニュー候補

14日の範囲は当日を含むため `today - 13 days` 以降とする。Health Connect repository や Health Context からは情報を取得しない。

記録にない重量、回数、体調等を事実として補完しないよう prompt に明示する。痛みや強い不調がメモにある場合に無理な運動を勧めないよう安全制約を置くが、医療診断機能にはしない。

### 3. ワークアウトメモと AI 設定は Workout data ownership に置く

セット単位の既存 `WorkoutSet.memo` とは別に、日付単位のワークアウトメモを持つ。AI 専用の方針・候補・provider 設定とともに `WorkoutAiSettingsRepository` を Workout domain capability とし、Workout data implementation が `workout_ai` SharedPreferences に保存する。

このメモは Workout の AI context / 所感として扱い、Health Connect export の ExerciseSession / ExerciseRoute metadata には含めない。既存 `WorkoutSnapshot` の JSON schema を変更せず、既存記録の migration を不要にする。

`workout_ai` は credential や端末依存値を持たないユーザー所有データであり、既存 `workout` と同様にアプリ独自backupの SharedPreferences allowlistとAndroid標準のcloud backup / device transfer allowlistへ明示追加する。将来 credential 等を保持する用途には転用しない。

### 4. Local / ChatGPT は明示選択し、自動 fallback しない

`WorkoutAiProvider` は `LOCAL` / `CHATGPT` の2値とし、初期値は `LOCAL` とする。

Local は既存 `AiTextInference` / process-isolated LiteRT-LM runtime を利用する。ChatGPT は既存の ChatGPT / Codex login・model selection と provider-neutral な cloud `AiTextInference` adapter を利用する。Workout feature は OpenAI wire format や credential を所有しない。

ChatGPT 選択時は、直近14日間の Workout 記録、メモ、方針、メニュー候補がクラウドへ送信されることを Workout UI に表示する。Cloud failure 時に Local へ自動 fallback しない。

### 5. 既存 WorkoutViewModel と AI state を分離する

記録、タイマー、Health Connect export を担当する既存 `WorkoutViewModel` には AI state を追加しない。`WorkoutAiViewModel` が AI 設定、メモ、one-shot request state を所有する。

推論要求ごとに `WorkoutReader.load()` を再実行し、直前に保存されたセットを prompt に反映する。これにより長寿命 UI state の snapshot を AI request source of truth にしない。

### 6. 選択モデルの prompt budget を超えた場合は現在の依頼を優先する

原則として直近14日分を prompt に含めるが、選択した `AiTextInferenceModel.promptBudgetChars` を上限とする。入力が上限を超える場合は、冒頭の安全制約・方針と末尾の当日記録・依頼を残し、中間にある古い履歴から省略する。

Local / ChatGPT のどちらでも同じ budget 処理を適用し、provider ごとに Workout prompt 構築規則を分岐させない。省略が発生した場合は prompt 内に省略マーカーを入れる。

## Consequences

### Positive

- Workout の既存記録・タイマー責務を肥大化させずに AI 操作を追加できる。
- Local / Cloud の実行先がユーザーの明示設定と一致する。
- Health Connect read data を AI context へ混入させない。
- prompt context window と2種類の action を JVM unit test で固定できる。
- 既存 Workout JSON の migration を必要としない。
- 小さい context の Local model でも当日の記録と依頼を優先して実行できる。
- 日次メモとAI設定を既存のユーザーデータbackupと一緒に復元できる。

### Negative

- ワークアウトメモは既存 WorkoutSnapshot と別 SharedPreferences に保存されるため、同一日付の情報が複数 persistence key に分かれる。
- ChatGPT を選択すると Workout 記録とメモが外部 provider へ送信される。
- one-shot response のみで、自由な追加質問や会話履歴は提供しない。
- 選択モデルの prompt budget が小さい場合、14日間の古い履歴の一部が省略される。

## Verification

- 14日前ではなく `today - 13 days` 以降の履歴だけが prompt に含まれることを unit test する。
- メニュー提案 prompt に方針、候補、当日メモ、セット数・回数/秒数を求める指示が含まれることを unit test する。
- 完了後レビュー prompt に当日のセット記録と所感が含まれることを unit test する。
- Local / ChatGPT routing が明示選択だけで決まり、自動 fallback がないことを unit test する。
- prompt budget 超過時も安全制約側の冒頭と当日依頼側の末尾が保持されることを unit test する。
- `workout_ai` のユーザー所有データがアプリ独自backupで復元され、Android標準のcloud backup / device transfer allowlistにも含まれることを unit test する。
- Cloud 選択時の送信内容説明が UI にあることを確認する。
- Workout AI が Health repository / Health Connect read API に依存しないことを architecture review する。
- Architecture / Test / Lint / public repository verification を実行する。

## References

- [ADR-0016](0016-workout-tracking.md)
- [ADR-0099](0099-database-snapshot-backup.md)
- [ADR-0136](0136-public-repository-content-verification.md)
- [ADR-0165](0165-provider-neutral-text-inference-contract.md)
- [ADR-0172](0172-separate-ai-provider-routing-and-runtime-controls.md)
- [ADR-0190](0190-isolate-local-text-inference-process.md)
