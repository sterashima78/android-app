# ADR-0260: Workout の種目とメニューを分離し日次プランを同じモデルで扱う

- Status: Accepted
- Date: 2026-09-16
- Refines: [ADR-0016](0016-workout-tracking.md), [ADR-0194](0194-workout-ai-advisor.md)

## Context

Workout は従来、`WorkoutExercise` が種目定義と目標セット数を同時に所有していた。この構造では、同じ種目を複数のメニューで異なるセット数・目標値として再利用しづらく、当日の状態に合わせた一時的な調整、複数プリセット、外部で作ったメニューのインポート、AI提案の直接実行を別々の実装に分岐させる要因になる。

Workout の永続状態と履歴は引き続き Workout Context の source of truth とし、新しい外部ストレージや並行するメニュー管理 capability は追加しない。

## Decision

- 種目マスタと `WorkoutMenu` を分離する。
- `WorkoutMenuItem` は種目ID、目標セット数、任意のセット別目標値を持つ。
- 複数の `WorkoutMenu` をプリセットとして Workout state 内へ保存する。
- 当日の `WorkoutDay` は、実行時に選択・生成・インポートされたメニューの snapshot を保持できる。これによりプリセット編集が進行中の当日プランを暗黙変更しない。
- AIによるメニュー提案と構造化テキストからのインポートは同じ `WorkoutMenu` へ変換する。AI専用の第二メニュー形式は作らない。
- 既存 state から `menus` が読めない場合は、既存種目の `targetSets` から「基本メニュー」を生成する。旧 `targetSets` は互換読み込みと新規種目の既定値として残し、実行時の正本はメニュー項目とする。
- SharedPreferences の既存キーを維持し、payload version を 2 とする。

## Consequences

- 同じ種目を複数プリセットで異なるセット構成として再利用できる。
- `[8, 8, 6]` のようなセット単位の目標を当日の入力初期値として利用できる。
- AI生成、インポート、手動プリセットが同一の実行パスを通る。
- 旧データは明示的な破棄や別migration stateを必要とせず読み込み時に基本メニューへ投影される。
- 将来、メニュー編集UIを拡張する場合も種目マスタ自体を複製せずに済む。
