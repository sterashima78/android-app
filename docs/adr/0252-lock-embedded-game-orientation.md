# ADR-0252: 埋め込みゲームの画面向きを Activity 境界で固定する

- Status: Accepted
- Date: 2026-09-10
- Refines: [ADR-0236](0236-godot-android17-version-fallback.md), [ADR-0251](0251-godot-klondike.md)
- Amended by: [ADR-0253](0253-ignore-klondike-runtime-orientation-requests.md)

## Context

ADR-0251 ではクロンダイクを横向きで表示するため、Activity manifest の `sensorLandscape`、Activity の `setRequestedOrientation` override、GDScript の `DisplayServer.screen_set_orientation()` を併用していた。

埋め込み Android ライブラリでは automatic resize / orientation configuration event が安定した前提ではなく、実機でクロンダイク起動後に画面が連続して切り替わるようなちらつきが発生した。加えて、共有 Godot project は数独向けに portrait を既定値としており、engine 起動時に project の orientation を Activity へ要求する。そのため manifest だけではクロンダイクの landscape を維持できない。

## Decision

- 画面向きの ownership は Android Activity 境界に置き、ゲーム内 GDScript から orientation を変更しない。
- クロンダイク manifest は `landscape` に固定し、センサーに応じた 180 度回転を行わない。
- `GodotKlondikeActivity` は `setRequestedOrientation` を override し、共有 project 由来を含む runtime の orientation 要求を固定 `landscape` へ coerce する。
- クロンダイク GDScript は `DisplayServer.screen_set_orientation()` を呼ばない。
- クロンダイク scene は固定 landscape 前提で起動後に一度初期描画し、window resize を契機に盤面全体を再生成しない。
- Android の configuration change 処理と既存の `:godot` process isolation は維持する。

## Consequences

### Positive

- project の portrait 既定値がクロンダイク Activity を portrait に戻すことを防げる。
- sensor orientation と runtime script の orientation 変更をなくし、起動時の configuration churn を避けられる。
- orientation policy が Android Activity 境界に集約され、ゲームロジックから platform orientation 制御を除去できる。

### Negative

- 端末を上下逆に持った場合でもクロンダイクは reverse landscape へ自動回転しない。
- 共有 project の portrait 既定値を維持するため、クロンダイク Activity には固定 landscape へ coerce する platform guard が必要になる。

## Relationship

ADR-0251 の「`sensorLandscape` とする」という判断を、本 ADR の固定 `landscape` で置き換える。クロンダイクを横向きの board-first UI とする方針自体は変更しない。

ADR-0236 の runtime baseline と process isolation は変更しない。

ADR-0253 により、本 ADR の runtime orientation request を固定 landscape へ coerce する部分だけを、runtime request を Android framework へ渡さず無視する方針へ置き換える。
