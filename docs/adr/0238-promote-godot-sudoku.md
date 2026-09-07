# ADR-0238: Godot 数独を正式実装へ昇格する

- Status: Accepted
- Date: 2026-09-07
- Supersedes: [ADR-0234](0234-godot-sudoku-poc.md) の「Compose 数独を正式版として残し Godot を比較用 POC とする」判断
- Refines: [ADR-0080](0080-game-section-and-sudoku.md), [ADR-0088](0088-offline-puzzle-game-expansion.md)
- Preserves: [ADR-0236](0236-godot-android17-version-fallback.md) の Android 17 向け Godot runtime baseline / process boundary

## Context

ADR-0234 では既存 Compose 数独を正式版として残し、Godot 数独を比較用 POC として追加した。実機で Godot 版の操作と描画が十分に機能することを確認できたため、二重実装を継続せず Godot 版を正式な数独へ昇格する。

同時に、数独の入力体験を次のように変更する要求がある。

- 9×9 盤面をより大きく表示する。
- 編集可能なマスを選択した位置の近くへ数字入力 UI を表示する。
- 数字を入力するたびに正解・不正解を判定して表示しない。
- 盤面を最後まで埋めるまで正解かどうかを明かさず、全マス入力後にだけ完成判定する。

## Decision

- Game 一覧の通常の `数独` 導線は `GodotSudokuActivity` を起動する。
- `Godot 数独 (POC)` という別項目は廃止する。
- Compose の `SudokuScreen` / `SudokuViewModel` と Kotlin Domain の Sudoku model / test は削除し、数独の盤面状態とルール判定を Godot project の GDScript に一本化する。
- Godot project は引き続き `:feature:game:ui` の Android assets として所有する。Godot Android dependency を app shell や Game domain へ公開しない。
- Godot runtime は ADR-0236 の baseline と専用 `:godot` process boundary を維持する。
- 盤面は portrait viewport 内で利用できる横幅を優先して拡大する。
- 編集可能なマスをタップしたら、そのセルの上下で viewport 内に収まる位置へ 1〜9 と消去操作を持つ floating number panel を表示する。
- 編集可能なセルには 1〜9 の任意の値を一旦入力できるようにする。入力時には solution と比較せず、正解色・不正解色・MISS count・自動的な正解セル確定を行わない。
- 81 マスが埋まった時点だけ solution 全体と比較する。正解なら完成演出へ進み、不一致ならセル単位の誤りを示さず盤面の見直しを促す。
- ゲーム状態の永続化、network、permission、credential、background execution は追加しない。

## Change Impact Brief

- Changed capabilities: 数独の正式実装を Compose から Godot へ変更し、入力 UI と完成判定タイミングを変更する。
- Changed Context / ownership: Game ownership は維持する。数独固有の状態・ルールは `:feature:game:domain` から `:feature:game:ui` 内 Godot project へ移る。
- Changed dependency direction: 新規依存はない。既存 `:feature:game:ui -> Godot Android AAR` を継続する。
- Changed durable data / schema: none。
- Changed background execution: none。
- Changed external communication / permission / credential boundary: none。
- Changed process boundary: ADR-0236 の専用 `:godot` process を維持する。
- Removed implementation: Compose Sudoku screen / ViewModel / pure Kotlin Sudoku domain model / unit test。
- Rollback: ADR-0234 時点の Compose 実装を復元して Game 一覧導線を戻す必要があるため、POC 時点より rollback cost は高くなる。

## Consequences

### Positive

- 数独の二重実装を解消できる。
- 盤面を大きくしながら、入力操作を選択セル付近に集約できる。
- 試行錯誤中の入力を即時採点しないため、通常の紙の数独に近い解答体験になる。
- Godot UI / animation の実装を正式機能として継続利用し、POC 専用コードを残さない。

### Negative

- 数独ルールが pure Kotlin unit test の対象から外れ、GDScript の検証は Godot scene/script load と Android 実機操作への依存が大きくなる。
- Godot runtime の APK size、起動コスト、memory footprint は正式な数独機能のコストとして受け入れる。
- floating number panel は小さい viewport や system inset で盤面と重なるため、常に viewport 内へ clamp する必要がある。

## Verification

変更時には少なくとも次を確認する。

1. Game 一覧の `数独` から Godot 数独が起動し、POC の別項目が存在しない。
2. 9×9 盤面が従来より大きく表示される。
3. 上段・中央・下段の編集可能セルで number panel が viewport 外へはみ出さない。
4. 誤った数字を入力してもその場で正誤表示、MISS 増加、セル色による誤り表示が出ない。
5. 入力済みセルを再選択して値を変更・消去できる。
6. 全セル入力前には solution 判定を行わない。
7. 全セルが solution と一致した場合だけ完成演出を表示する。
8. Android 17 実機で起動・戻る操作を確認する。
