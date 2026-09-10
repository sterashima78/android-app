# ADR-0252: 埋め込みゲームの画面向きを Activity 境界で固定する

- Status: Accepted
- Date: 2026-09-10
- Refines: [ADR-0236](0236-godot-android17-version-fallback.md), [ADR-0251](0251-godot-klondike.md)

## Context

ADR-0251 ではクロンダイクを横向きで表示するため、Activity manifest の `sensorLandscape`、Activity の `setRequestedOrientation` override、GDScript の `DisplayServer.screen_set_orientation()` を併用していた。

埋め込み Android ライブラリでは automatic resize / orientation configuration event が安定した前提ではなく、実機でクロンダイク起動後に画面が連続して切り替わるようなちらつきが発生した。複数レイヤーから同じ orientation を変更すると、Android と game runtime の間で configuration change 要求が循環する余地がある。

## Decision

- 埋め込みゲーム Activity の orientation は Android manifest の `android:screenOrientation` を唯一の source of truth とする。
- クロンダイクは `landscape` に固定し、センサーに応じた 180 度回転を行わない。
- `GodotKlondikeActivity` は `setRequestedOrientation` を override しない。
- クロンダイク GDScript は `DisplayServer.screen_set_orientation()` を呼ばない。
- クロンダイク scene は起動後に orientation を変更せず、既に確定した landscape window のサイズで layout する。
- Android の configuration change 処理と既存の `:godot` process isolation は維持する。

## Consequences

### Positive

- Android と game runtime の orientation 要求競合をなくし、起動時の configuration churn を避けられる。
- orientation ownership が Activity manifest に一本化され、数独の portrait 固定と同じ境界で理解できる。
- runtime 内で画面向きを変更しないため、埋め込みライブラリの自動 orientation change 制約に沿う。

### Negative

- 端末を上下逆に持った場合でもクロンダイクは reverse landscape へ自動回転しない。
- orientation を変更したい場合は runtime script ではなく Activity manifest / platform integration の変更として扱う必要がある。

## Relationship

ADR-0251 の「`sensorLandscape` とする」という判断を、本 ADR の固定 `landscape` で置き換える。クロンダイクを横向きの board-first UI とする方針自体は変更しない。

ADR-0236 の runtime baseline と process isolation は変更しない。
