# Game Architecture

この文書は Game feature の current architecture を示す。設計判断の履歴は ADR-0080、ADR-0082、ADR-0083、ADR-0085、ADR-0088、ADR-0114、ADR-0234、ADR-0236、ADR-0238、ADR-0251、ADR-0252 を参照する。

## Ownership

Game は `:feature:game:domain` と `:feature:game:ui` が所有する。永続化や外部 I/O を必要とする game data は現在ないため `:feature:game:data` は持たない。

2048、ノノグラム、マインスイーパー、スパイダーソリティアなど通常の一人用ゲームは次の構成を基本とする。

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

数独とクロンダイクは Godot project が UI とゲーム状態を所有する例外とする。Game 一覧は Compose のまま維持し、対象ゲームを選択したときだけ専用 Activity で Godot runtime を起動する。

## Godot games

ADR-0238 により数独、ADR-0251 によりクロンダイクを正式な Godot 実装とする。Compose / ViewModel / Kotlin Domain に同じゲーム状態を並行保持せず、各ゲームの scene / GDScript を source of truth とする。ADR-0236 により Android 17 実機での native crash 回避として runtime baseline は Godot 4.6.3 stable とする。

```text
GameRoute / Compose game list
        |
        | explicit Activity launch
        v
GodotSudokuActivity / GodotKlondikeActivity (:godot process)
        |
        v
Godot Android Library 4.6.3
        |
        v
assets/project.godot
        |
        +--> sudoku.tscn + sudoku.gd
        |
        +--> klondike.tscn + klondike.gd + klondike_model.gd
```

Godot runtime は Game 一覧を表示しただけでは生成しない。ユーザーが対象ゲームを選択したときだけ専用 Activity / process を起動する。`project.godot` の default scene は数独とし、クロンダイク Activity は command line で `res://klondike.tscn` を明示する。

Godot project は `:feature:game:ui` の assets とし、各ゲームの盤面状態、入力、ルール判定、Control UI、animation を GDScript が所有する。Godot 化したゲーム専用の Kotlin Domain model / ViewModel / Compose Screen は持たない。

Godot Android dependency は `:feature:game:ui` に閉じ、app shell、Game domain、他 feature へ公開しない。Godot game 間で runtime / project boundary は共有するが、ゲーム固有ルールを共通 engine へ抽象化しない。

## Sudoku interaction rules

- 9×9 盤面は portrait viewport の横幅を優先して大きく表示する。
- 固定値ではないセルをタップすると、そのセルの近くへ 1〜9 と消去操作を持つ floating number panel を表示する。
- number panel は選択セルの下側を優先し、収まらない場合は上側へ配置して viewport 内へ clamp する。
- 入力時には solution と比較しない。任意の 1〜9 を盤面へ反映でき、入力済みセルも再選択して変更・消去できる。
- 入力途中に正解・不正解の色、MISS count、正解セルへの自動確定などの採点 feedback を表示しない。
- 81 マスすべてが埋まった場合だけ solution 全体と比較する。一致した場合は完成演出を表示し、不一致の場合はどのセルが誤りかを示さず盤面の見直しを促す。

## Klondike interaction rules

- 52枚を7列の場札、山札、捨て札、4つの組札へ配る。山札は1枚ずつめくる。
- 山札が空になった場合、捨て札を回数制限なく山札へ戻せる。
- 基本操作はタップ選択とし、選択したカード列から合法な場札列・組札を強調する。ドラッグを必須操作にはしない。
- 場札は赤黒交互の降順で積み、空列へは K から始まる列だけを移動できる。
- 組札は同一スートを A から K の昇順で積む。
- 場札を移動して伏せ札が最上段へ露出した場合は自動的に表向きにする。
- 52枚すべてを組札へ移動すると完成とする。
- 盤面は landscape viewport を優先し、7列を同時に確認できる board-first layout とする。

## Runtime and platform boundary

- 数独 Activity は portrait 固定、クロンダイク Activity は landscape 固定とする。
- 画面向きの ownership は Android Activity 境界に置く。manifest で固定向きを宣言し、クロンダイク Activity は共有 project 由来を含む runtime の向き要求を固定 landscape へ coerce する。GDScript から orientation は変更しない。
- Activity configuration change を manifest で処理対象として宣言し、Godot runtime 実行中の Activity recreation を避ける。
- クロンダイクは固定 landscape 前提で起動後に一度初期描画し、window resize を契機に盤面全体を再生成しない。
- Godot runtime は専用 `:godot` process で実行し、engine / scene の異常終了や force quit が Mosaic の main process を巻き込まないよう隔離する。
- process あたり Godot Engine instance が1つという Godot Android Library の制約を受け入れ、Godot game は独立 Activity として逐次起動する。
- release build の R8 では Godot native JNI が名前解決する `org.godotengine.godot.**` の class/member 名を保持する。keep rule は Godot dependency と同じ `:feature:game:ui` が consumer rule として所有する。
- network、permission、credential、background execution、durable state は追加しない。
- Godot project は APK assets に同梱し、runtime asset download を行わない。

## Verification

Godot 数独の変更では Gradle / architecture verification に加え、Android 17 実機で少なくとも起動、戻る、上段・中央・下段セルの number panel 配置、値の変更・消去、途中で採点されないこと、全盤面完成時の判定を確認する。

Godot クロンダイクの変更では Gradle / architecture verification に加え、Godot 4.6.3 で scene / script が load でき、`klondike.tscn` が実際に起動して初期フレームを処理できること、および Android 17 実機で少なくとも固定 landscape で安定して起動すること、戻る、新規ゲーム、山札1枚めくり、捨て札 recycle、場札の合法手、組札への移動、自動 flip、合法手強調、完成判定を確認する。

クロンダイクの GDScript parse / project load、実 scene 起動、および pure game rule の headless regression は、Godot 4.6.3 executable を指定して次を実行する。テストは配札、山札 recycle、組札移動、場札移動と自動 flip、不正な場札列の拒否、完成判定を検証する。

```bash
bash scripts/test_godot_klondike.sh /path/to/Godot_v4.6.3-stable_linux.x86_64
```

## Sources

- [ADR-0080](../adr/0080-game-section-and-sudoku.md)
- [ADR-0082](../adr/0082-klondike-card-game-interaction.md)
- [ADR-0114](../adr/0114-solitaire-board-first-visual-feedback.md)
- [ADR-0234](../adr/0234-godot-sudoku-poc.md)
- [ADR-0236](../adr/0236-godot-android17-version-fallback.md)
- [ADR-0238](../adr/0238-promote-godot-sudoku.md)
- [ADR-0251](../adr/0251-godot-klondike.md)
- [ADR-0252](../adr/0252-lock-embedded-game-orientation.md)
- `feature/game/domain/`
- `feature/game/ui/`
