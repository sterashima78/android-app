# ADR-0261: Workout の種目とメニューを分離し日次プランを同じモデルで扱う

- Status: Accepted
- Date: 2026-09-16
- Amended: 2026-09-22, 2026-09-29
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
- SharedPreferences の既存キーを維持し、payload version を 2 とする。キー名の `state_v1` は保存slotの識別子として維持し、payload version の判定には使わない。
- 読み込みは payload の `version` を明示的な dispatch point とする。version field がない既存stateは v1 として扱い、`targetSets` から基本メニューを生成して v2 snapshot へ収束させる。v2 は現行形式として読む。
- 未知の payload version は現行形式として推測解釈しない。自動的な load -> save で将来形式のstateを上書きしないため、unsupported version として読み込みを失敗させる。
- v1 decode path は一時的な互換処理であり、v2 payload を含むリリースが current compatibility baseline から外れた時点で削除する。


## Amendment (2026-09-29): v1 payload compatibility の退役

current compatibility baseline から v1 payload を含む配布版が外れたため、Decision に記録した一時的な v1 decode path は終了する。

- version field がない payload と version 1 payload は、現行形式へ暗黙変換せず unsupported version として扱う。
- unsupported payload は読み込み失敗時に保存内容を書き換えず、そのまま保持する。
- `decodeV1` と v1 専用 regression fixture/test は削除し、現行 decoder は version 2 だけを受け付ける。
- SharedPreferences key `state_v1` は payload version ではなく既存保存slotの identity なので変更しない。名前だけを理由に移行して現在データを失うリスクを増やさない。
- `WorkoutExercise.targetSets` は新規種目の既定値として現行用途が残るため、この amendment では削除しない。

この amendment は Decision 内の v1 互換読み込みに関する箇条書きを current state として supersede する。種目とメニューを分離する判断、payload version 2、未知versionを推測解釈しない方針は引き続き有効とする。

## Consequences

- 同じ種目を複数プリセットで異なるセット構成として再利用できる。
- `[8, 8, 6]` のようなセット単位の目標を当日の入力初期値として利用できる。
- AI生成、インポート、手動プリセットが同一の実行パスを通る。
- 旧データは明示的な破棄や別migration stateを必要とせず読み込み時に基本メニューへ投影される。
- 保存形式を将来更新するときは、新しいversionを追加してdecode pathを明示し、未知versionを既存decoderへfall throughさせない。
- 将来、メニュー編集UIを拡張する場合も種目マスタ自体を複製せずに済む。
