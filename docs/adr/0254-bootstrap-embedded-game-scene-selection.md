# ADR-0254: 埋め込みゲームの scene 選択を bootstrap で行う

- Status: Accepted
- Date: 2026-09-10
- Amends: [ADR-0251](0251-godot-klondike.md)
- Refines: [ADR-0238](0238-promote-godot-sudoku.md)

## Context

ADR-0251 では、共有 Godot project の既定 scene を数独のまま維持し、クロンダイク Activity から `--scene res://klondike.tscn` を渡して起動 scene を上書きする方針を採用した。

しかし Android に組み込んでいる export template は path override を無効化してビルドされており、`--scene` は利用できない。実際の release APK に含まれる native library も、`--scene` が指定された場合は path override 非対応として起動を中断する構成になっている。そのためクロンダイク scene は読み込まれる前に engine startup が終了していた。

一方、engine は `--` 以降を user-provided arguments として script へ渡すことを正式にサポートしており、この経路は scene / project path override を必要としない。

## Decision

- 共有 Godot project の `run/main_scene` は `game_bootstrap.tscn` とする。
- bootstrap は `OS.get_cmdline_user_args()` を読み、起動する game scene を選択して instantiate する。
- 引数がない場合は数独を起動し、既存の数独 Activity の挙動を維持する。
- クロンダイク Activity は `--scene` を使用せず、`--` の後に `--game=klondike` を user argument として渡す。
- bootstrap が認識していない game key を受け取った場合は、別ゲームへ黙って fallback せず起動を失敗させる。
- Android export template の path override 設定は変更せず、custom runtime build も導入しない。
- 既存の `:godot` process isolation、runtime baseline、各ゲームの scene / GDScript ownership は維持する。

## Consequences

### Positive

- export template で禁止されている `--scene` に依存せず、クロンダイク scene を選択できる。
- 数独とクロンダイクで同じ packaged project / runtime を引き続き共有できる。
- 将来 Godot game を追加する場合も、path override ではなく bootstrap の明示的な game selection を利用できる。
- custom runtime build を追加しないため、既存の runtime 更新・配布境界を維持できる。

### Negative

- project の main scene と各 game scene の間に bootstrap という1段の indirection が増える。
- 新しい Godot game を追加する場合は Activity の user argument と bootstrap の対応を同時に更新する必要がある。

## Relationship

ADR-0251 の「`--scene res://klondike.tscn` でクロンダイク scene を起動する」という判断を、本 ADR の bootstrap + user argument 方式で置き換える。

ADR-0238 の packaged project ownership、ADR-0236 の runtime baseline / process isolation、ADR-0252 の固定 orientation 方針は変更しない。
