# ADR-0236: Android 17 向け Godot POC は 4.6.3 stable を使用する

- Status: Accepted
- Date: 2026-09-07
- Amends: [ADR-0234](0234-godot-sudoku-poc.md)

## Context

Godot 数独 POC は Godot 4.7.2 stable の Android AAR を使用していたが、Android 17 実機で `GodotSudokuActivity` 起動直後に process が終了する事象が継続して再現した。

APK への Godot native library / project assets の梱包、16 KiB alignment、Godot 4.7.2 headless での scene/script load は確認済みであり、GDScript parse error や asset 欠落では説明できない。

Godot 4.7.2 では Android 17 / Pixel 端末上の `libgodot_android.so` native crash が upstream で報告されている。POC の目的は最新版追従ではなく、Mosaic 内へ game engine を埋め込む方式の UI / animation / lifecycle / size を評価することである。

## Decision

- Godot Android Library を `4.7.2.stable` から `4.6.3.stable` へ一時的に下げる。
- Godot project の feature baseline も `4.6` に合わせる。
- `:godot` process 分離と Godot Android sample 相当の `configChanges` は維持する。
- Sudoku UI / animation / game logic は変更しない。
- 4.6.3 自身で scene/script を headless load して互換性を確認する。

## Change Impact Brief

- Changed capability: Godot Sudoku POC の runtime version のみ。
- Changed Context / ownership: none。
- Changed dependency direction: none。
- Changed durable state / schema: none。
- Changed background execution: none。
- Changed external communication / permission / credential boundary: none。
- Changed compatibility baseline: Godot POC の engine baseline を 4.7.2 から 4.6.3 へ変更。
- Rollback: version catalog と `project.godot` の feature version を戻すだけで migration は不要。

## Consequences

- Android 17 で報告されている 4.7.2 native crash の影響を避けられる可能性がある。
- POC は最新 Godot 4.7 系固有機能を評価しない。
- upstream で Android 17 crash が修正された stable release を確認した時点で、再度 version update を評価する。
