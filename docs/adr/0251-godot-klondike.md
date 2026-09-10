# ADR-0251: クロンダイクを Godot 実装へ移行する

- Status: Accepted
- Date: 2026-09-10
- Supersedes in part: [ADR-0082](0082-klondike-card-game-interaction.md), [ADR-0114](0114-solitaire-board-first-visual-feedback.md)
- Refines: [ADR-0238](0238-promote-godot-sudoku.md)
- Amended by: [ADR-0253](0253-bootstrap-embedded-game-scene-selection.md)

## Context

クロンダイクは現在 `:feature:game:domain` の pure Kotlin state transition、`KlondikeViewModel`、Compose の `KlondikeScreen` で実装している。ADR-0082 では合法手判定を domain に置き、ADR-0114 では横向きの盤面優先 UI とタップ選択を採用した。

一方、ADR-0238 により数独は Godot project が UI とゲーム状態を所有する正式実装へ移行済みであり、`:feature:game:ui` には Godot Android Library、専用 `:godot` process、APK assets としての Godot project がすでに存在する。

クロンダイクについても、カードの選択・移動先強調・盤面レイアウト・勝利演出を同じ Godot runtime 上で完結させることで、ゲーム固有 UI と状態遷移を1つの scene/script に集約できる。既存 Compose 実装と Godot 実装を並行維持すると source of truth とテスト対象が二重化するため、置換として実施する。

## Decision

- Game 一覧の `クロンダイク` 導線は専用 `GodotKlondikeActivity` を起動する。
- `GodotKlondikeActivity` は既存 Godot Android Library と同じ `:godot` process を利用し、`--scene res://klondike.tscn` でクロンダイク scene を起動する。
- クロンダイクの盤面状態、配札、合法手判定、選択状態、手数、勝利判定、Control UI は Godot project の GDScript が所有する。
- Compose の `KlondikeScreen` / `KlondikeViewModel` と Kotlin Domain の `KlondikeGameState` / rule / test は削除し、クロンダイクの source of truth を Godot project に一本化する。
- `PlayingCard` はスパイダーソリティアが引き続き利用するため Game domain に残す。クロンダイクとスパイダーのゲーム固有 rule を共有 engine へ統合しない。
- 既存のクロンダイク仕様を維持する。山札は1枚めくり、捨て札の山札への戻しは無制限、場札からカードを移動して伏せ札が露出した場合は自動で表向きにする。
- 基本操作はタップ選択とし、選択中カードから合法な場札・組札を強調する。ドラッグを必須操作にはしない。
- クロンダイク Activity は `sensorLandscape` とし、Godot project 側は現在の viewport に追従して7列を描画する。アプリ共通 chrome は Activity 外にあるため表示しない。
- 永続化、network、permission、credential、background execution は追加しない。ゲーム状態は Godot process のメモリ上だけに保持する。
- Godot runtime baseline は ADR-0236 の 4.6.3 stable を維持し、この変更では更新しない。

## Consequences

### Positive

- クロンダイクの UI とゲーム状態が1つの Godot scene/script に集約され、Compose/ViewModel/GDScript の並行実装を持たない。
- 既存の Godot Android dependency と process boundary を再利用でき、新しい engine runtime や module を増やさない。
- カード移動、選択、強調、勝利演出を Godot の Control / draw API で一体的に実装できる。
- Game 一覧を表示しただけでは Godot runtime を起動せず、選択時だけ起動する現在の遅延起動方針を維持できる。

### Negative

- クロンダイクの rule は JVM unit test では直接検証できなくなり、GDScript の headless test と Android 実機確認へ検証主体が移る。
- クロンダイクとスパイダーで共有していた Compose のカード視覚プリミティブは、クロンダイク側では利用しなくなる。
- 同一 `:godot` process では Godot Engine instance を同時に複数保持できないため、各 Godot game は独立 Activity として逐次起動する前提を維持する。

## Relationship

ADR-0082 のクロンダイクに関する「Kotlin domain が rule を所有する」「`KlondikeViewModel` が状態を所有する」という判断を本 ADR で supersede する。一方、1枚めくり、無制限 recycle、自動 flip、タップ選択というゲーム仕様は維持する。

ADR-0114 のクロンダイクに関する Compose / domain 間の責務分担と `GameOrientationPreference` による向き通知は supersede する。盤面優先、横向き、合法手強調という user interaction 方針は Godot 実装でも維持する。スパイダーに関する ADR-0114 の判断は変更しない。

ADR-0238 の Godot project ownership と遅延起動方針をクロンダイクへ拡張する。ADR-0236 の runtime baseline と process isolation は変更しない。

ADR-0253 により、本 ADR の `--scene` によるクロンダイク scene 選択だけを bootstrap + user argument 方式へ置き換える。
