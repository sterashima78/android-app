# ADR-0253: クロンダイクでは runtime の画面向き要求を適用しない

- Status: Accepted
- Date: 2026-09-10
- Amends: [ADR-0252](0252-lock-embedded-game-orientation.md)

## Context

ADR-0252 では、共有 project が数独向けの portrait を既定値としているため、クロンダイク Activity が受け取る runtime の `setRequestedOrientation()` 要求を固定 `landscape` へ変換して Android framework へ渡す方針を採用した。

しかし実機ではこの変更後も起動時の連続したちらつきが解消しなかった。利用中の embedded runtime では画面向き変更が Android の `Activity.setRequestedOrientation()` へ直接転送されるため、要求値を landscape へ変換しても Android framework への orientation request 自体は残る。

クロンダイク Activity は manifest ですでに `landscape` 固定であり、ゲーム実行中に runtime から向きを変更する必要はない。

## Decision

- クロンダイク Activity の固定向きは manifest の `android:screenOrientation="landscape"` で宣言する。
- `GodotKlondikeActivity.setRequestedOrientation()` は runtime 由来の要求をすべて無視し、`super.setRequestedOrientation()` を呼ばない。
- 共有 project の portrait 既定値を含め、クロンダイク実行中の runtime orientation request は Android configuration change を発生させない。
- GDScript から orientation を変更しない既存方針を維持する。
- 数独 Activity の portrait 固定には影響を与えない。
- 既存の `:godot` process isolation、runtime baseline、ゲーム状態 ownership は変更しない。

## Consequences

### Positive

- runtime から Android framework への orientation request を完全に遮断できる。
- manifest で確定した landscape に対し、共有 project の portrait 既定値が configuration churn を起こさない。
- クロンダイクの orientation policy が Android Activity boundary に閉じる。

### Negative

- クロンダイク実行中に runtime から画面向きを変更することはできない。
- 将来クロンダイクで動的 orientation が必要になった場合は、この no-op guard を見直す必要がある。

## Relationship

ADR-0252 の「runtime の orientation request を固定 landscape へ変換して Android framework へ渡す」という部分を、本 ADR の「runtime request を Android framework へ渡さず無視する」で置き換える。

ADR-0252 の固定 landscape、GDScript から orientation を変更しない方針、および ADR-0236 の runtime baseline / process isolation は維持する。
