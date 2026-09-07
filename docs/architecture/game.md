# Game Architecture

この文書は Game feature の current architecture を示す。設計判断の履歴は ADR-0080、ADR-0082、ADR-0083、ADR-0085、ADR-0088、ADR-0234、ADR-0236、ADR-0238 を参照する。

## Ownership

Game は `:feature:game:domain` と `:feature:game:ui` が所有する。永続化や外部 I/O を必要とする game data は現在ないため `:feature:game:data` は持たない。

2048、ノノグラム、マインスイーパー、クロンダイク、スパイダーソリティアなど通常の一人用ゲームは次の構成を基本とする。

```text
Compose Screen / Route
        |
        v
ViewModel / UI state
        |
        v
:feature:game:domain
pure Kotlin rules / state transition
```

数独は ADR-0238 によりこの標準経路の例外とし、Godot project が UI とゲーム状態を所有する。

## Godot Sudoku

ADR-0238 により Godot 数独を正式な数独実装とする。ADR-0234 で比較用に残していた Compose 数独は削除し、Game 一覧の `数独` から Godot runtime を起動する。ADR-0236 により Android 17 実機での Godot 4.7.2 native crash 回避として runtime baseline は Godot 4.6.3 stable とする。

```text
GameRoute / Compose game list
        |
        | explicit Activity launch
        v
GodotSudokuActivity (:godot process)
        |
        v
Godot Android Library 4.6.3
        |
        v
assets/project.godot
        |
        v
sudoku.tscn + sudoku.gd
```

Godot runtime は Game 一覧を表示しただけでは生成しない。ユーザーが `数独` を選択したときだけ専用 Activity / process を起動する。

Godot project は `:feature:game:ui` の assets とし、盤面状態、入力、数独解判定、Control UI、Tween animation は GDScript が所有する。数独専用の Kotlin Domain model / ViewModel / Compose Screen は持たない。

Godot Android dependency は `:feature:game:ui` に閉じ、app shell、Game domain、他 feature へ公開しない。

## Sudoku interaction rules

- 9×9 盤面は portrait viewport の横幅を優先して大きく表示する。
- 固定値ではないセルをタップすると、そのセルの近くへ 1〜9 と消去操作を持つ floating number panel を表示する。
- number panel は選択セルの下側を優先し、収まらない場合は上側へ配置して viewport 内へ clamp する。
- 入力時には solution と比較しない。任意の 1〜9 を盤面へ反映でき、入力済みセルも再選択して変更・消去できる。
- 入力途中に正解・不正解の色、MISS count、正解セルへの自動確定などの採点 feedback を表示しない。
- 81 マスすべてが埋まった場合だけ solution 全体と比較する。一致した場合は完成演出を表示し、不一致の場合はどのセルが誤りかを示さず盤面の見直しを促す。

## Runtime and platform boundary

- Godot Activity は portrait 固定とする。
- Godot Android sample が扱う Activity configuration change を manifest で処理対象として宣言し、Godot runtime 実行中の Activity recreation を避ける。
- Godot runtime は専用 `:godot` process で実行し、engine / scene の異常終了や force quit が Mosaic の main process を巻き込まないよう隔離する。
- process あたり Godot Engine instance が1つという Godot Android Library の制約を受け入れる。
- release build の R8 では Godot native JNI が名前解決する `org.godotengine.godot.**` の class/member 名を保持する。keep rule は Godot dependency と同じ `:feature:game:ui` が consumer rule として所有する。
- network、permission、credential、background execution、durable state は追加しない。
- Godot project は APK assets に同梱し、runtime asset download を行わない。

## Verification

Godot 数独の変更では Gradle / architecture verification に加え、Android 17 実機で少なくとも起動、戻る、上段・中央・下段セルの number panel 配置、値の変更・消去、途中で採点されないこと、全盤面完成時の判定を確認する。

## Sources

- [ADR-0080](../adr/0080-game-section-and-sudoku.md)
- [ADR-0088](../adr/0088-offline-puzzle-game-expansion.md)
- [ADR-0234](../adr/0234-godot-sudoku-poc.md)
- [ADR-0236](../adr/0236-godot-android17-version-fallback.md)
- [ADR-0238](../adr/0238-promote-godot-sudoku.md)
- `feature/game/domain/`
- `feature/game/ui/`
