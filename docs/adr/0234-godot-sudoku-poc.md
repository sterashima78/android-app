# ADR-0234: Godot Android Library で数独 POC を追加する

- Status: Accepted
- Date: 2026-09-07
- Refines: [ADR-0080](0080-game-section-and-sudoku.md), [ADR-0088](0088-offline-puzzle-game-expansion.md)

## Context

Game feature はこれまで Compose UI + ViewModel + pure Kotlin domain で一人用ゲームを実装してきた。数独、2048、ノノグラム、マインスイーパー、クロンダイク、スパイダーはこの方式で十分に扱える。

今後、リアルタイム 2D ゲームなどを追加する場合、game loop、sprite animation、scene、particle、audio、collision 等を Compose 上で個別実装すると、アプリ固有の小規模 game engine を再実装する方向へ進みやすい。

Godot を既存 Android アプリへ埋め込む方式を評価するため、game engine を本来必要としない数独を意図的に Godot で実装する POC を追加する。既存数独は比較対象として残す。

## Decision

- Godot 4.7.2 stable の Android AAR `org.godotengine:godot:4.7.2.stable` を `:feature:game:ui` に限定して追加する。
- 既存 Compose 数独は変更せず、ゲーム一覧に `Godot 数独 (POC)` を別項目として追加する。
- Godot runtime は専用 `GodotSudokuActivity` で起動し、Game feature を開いただけでは生成しない。
- Godot project は `:feature:game:ui` の Android assets に配置し、Android APK へ同梱する。外部 asset download、network、permission は追加しない。
- POC の数独ルールと画面状態は Godot project 内の GDScript が所有する。既存 `:feature:game:domain` の Sudoku model と同期させず、永続 source of truth も追加しない。
- Godot の Control / GridContainer / Tween 等を使い、盤面、number pad、選択状態、正解入力、誤入力、完成演出を engine 側 UI / animation として実装する。
- 画面回転は portrait に固定する。Godot Android library の制約に合わせ、orientation / screen size configuration change は Activity 側で処理対象として宣言する。
- POC は process あたり単一 Godot instance という Godot Android library の制約を受け入れる。複数 Godot game、PCK 分割、Android-Godot 間 plugin bridge は今回の対象外とする。

## Change Impact Brief

- Changed capabilities: Game に Godot runtime を利用する実験的数独を追加する。
- Changed Context / ownership: Game ownership は維持する。Godot project は Game UI の実装詳細とする。
- Changed dependency direction: `:feature:game:ui -> Godot Android AAR` を追加する。Domain 依存方向は変更しない。
- Changed durable data / schema: none。
- Changed background execution: none。
- Changed external communication / permission / credential boundary: none。
- New concepts introduced: embedded Godot runtime、Godot-owned game scene。
- Rollback: Godot dependency、Activity、assets、一覧項目を削除するだけでよく migration は不要。

## Consequences

### Positive

- Mosaic 全体を game engine application にせず、特定ゲームだけ engine を利用できるか検証できる。
- Compose と Godot の UI / animation 実装コスト、APK size、起動時間、lifecycle を比較できる。
- 将来のリアルタイム 2D ゲームで engine 再実装を避ける判断材料になる。

### Negative

- 同じ数独を Compose と Godot で二重実装するため、POC 期間中は意図的に duplicate implementation を持つ。
- 約 100 MB 級の Godot Android AAR を build dependency に追加するため、APK size 増加が見込まれる。
- Godot project の GDScript は既存 Kotlin unit test の対象外であり、POC では Android 上の起動・操作確認が重要になる。

## Exit criteria

POC 評価後、次のいずれかを別 ADR で決める。

1. Godot をリアルタイム Game の正式 runtime とし、数独 POC 自体は削除する。
2. Godot 採用を見送り、dependency / Activity / Godot assets / 一覧項目を削除する。

POC を理由なく恒久的な第二数独実装として残さない。
