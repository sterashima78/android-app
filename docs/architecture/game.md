# Game Architecture

この文書は Game feature の current architecture を示す。設計判断の履歴は ADR-0080、ADR-0082、ADR-0083、ADR-0085、ADR-0088、ADR-0234、ADR-0235 を参照する。

## Ownership

Game は `:feature:game:domain` と `:feature:game:ui` が所有する。永続化や外部 I/O を必要とする game data は現在ないため `:feature:game:data` は持たない。

通常の一人用ゲームは次の構成を基本とする。

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

数独、2048、ノノグラム、マインスイーパー、クロンダイク、スパイダーソリティアはこの経路を利用する。

## Godot Sudoku POC

ADR-0234 により、リアルタイム 2D game engine 採用可能性を評価するため、既存 Compose 数独とは別に Godot 数独を POC として持つ。ADR-0235 により、Android 17 実機での 4.7.2 native crash 回避検証として runtime baseline は一時的に Godot 4.6.3 stable とする。

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

Godot runtime は Game 一覧を表示しただけでは生成しない。ユーザーが `Godot 数独 (POC)` を選択したときだけ専用 Activity / process を起動する。

Godot project は `:feature:game:ui` の assets とし、POC 内の盤面状態、入力、数独解判定、Control UI、Tween animation は GDScript が所有する。既存 Kotlin `Sudoku` model と状態同期せず、二つの数独は比較用の独立実装である。

Godot Android dependency は `:feature:game:ui` に閉じ、app shell、Game domain、他 feature へ公開しない。

## Runtime and platform boundary

- Godot Activity は portrait 固定とする。
- Godot Android sample が扱う Activity configuration change を manifest で処理対象として宣言し、Godot runtime 実行中の Activity recreation を避ける。
- Godot runtime は専用 `:godot` process で実行し、engine / scene の異常終了や force quit が Mosaic の main process を巻き込まないよう隔離する。
- process あたり Godot Engine instance が1つという Godot Android Library の制約を受け入れる。
- network、permission、credential、background execution、durable state は追加しない。
- Godot project は APK assets に同梱し、runtime asset download を行わない。

## POC lifecycle

この Godot 数独は恒久的な duplicate implementation を目的としない。POC 評価後は ADR-0234 の exit criteria に従い、Godot を将来のリアルタイム Game runtime として正式採用するか、POC 一式を削除する。

評価時には少なくとも次を比較する。

- APK size 増加
- cold / warm launch behavior
- Android Activity / process / app navigation lifecycle
- UI / animation の実装量と変更容易性
- Android 端末上の入力 responsiveness
- 将来の sprite / scene / audio / particle を使うゲームへの拡張性

## Sources

- [ADR-0080](../adr/0080-game-section-and-sudoku.md)
- [ADR-0088](../adr/0088-offline-puzzle-game-expansion.md)
- [ADR-0234](../adr/0234-godot-sudoku-poc.md)
- [ADR-0235](../adr/0235-godot-android17-version-fallback.md)
- `feature/game/domain/`
- `feature/game/ui/`
