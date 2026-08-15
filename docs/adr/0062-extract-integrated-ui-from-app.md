# ADR-0062: 統合ビューの再利用可能 UI を app から feature module へ分離する

- Status: Accepted
- Date: 2026-08-15

## Context

ADR-0004 では `:app` を navigation、DI、feature 間の composition を担う薄い層とし、再利用可能な feature 実装は feature module に置くことを決めている。ADR-0025 では統合ビューについて、RSS、Reddit、YouTube、メールの所有権を維持したまま上位の composition layer で統合することを決めている。

一方、統合ビューの Compose 表示、表示モデル、スワイプ定義、フィルタ UI が `:app` の `IntegratedScreen.kt` にまとまっていた。`IntegratedRoute` が各 feature の ViewModel を接続することは app の責務だが、表示だけで完結する実装まで app に置く必要はない。

また、表示実装をそのまま feature module へ移して各 feature の `UiState` や domain model に直接依存させると、統合 UI module が RSS、Reddit、YouTube、メールの変更を横断的に知ることになり、ADR-0025 の feature 境界を弱める。

## Decision

`feature:integrated:ui` module を追加し、次を所有させる。

- `IntegratedScreen` の Compose 表示
- `IntegratedTab` / `IntegratedSource`
- 表示に必要な値だけを持つ `IntegratedItem`
- スワイプ操作の表示契約と `IntegratedItemAction`

`feature:integrated:ui` は `core:designsystem` のみに project dependency を持ち、RSS、Reddit、YouTube、メールの feature module へ依存しない。

`:app` の `IntegratedRoute` は composition adapter として次を担当する。

- 各 feature の既存 `UiState` から `IntegratedItem` への射影
- 元の `Article`、`YouTubeVideo`、`MailThread` と表示 item の対応付け
- 表示 item に対する操作を各 feature の既存 ViewModel へ委譲
- Android Intent など app-level の外部遷移

統合 UI 用に横断 Repository、共通永続化 model、feature 間の直接依存は追加しない。

統合対象の抽出条件と並び順は ADR-0025 を維持する。未読は新しい順、あとで読むは古い順とする。スワイプ操作も各専用画面との既存 parity を維持し、UI module の単体テストで固定する。

併せて、パッケージ宣言と物理 source path が過去の構成からずれていた `MainTab` と bookmark data 実装を、内容を変えず現在の package に一致する位置へ移動する。

## Consequences

### Positive

- `:app` が composition と Android application wiring に集中し、再利用可能な Compose 実装を持たなくなる
- 統合 UI は sibling feature の `UiState` や domain model から独立する
- 各 feature のデータ所有権と既存 ViewModel の状態管理を維持できる
- 統合ビューの表示契約と app-level composition を別々に単体テストできる
- source path と package の対応が明確になり、過去構成を現行構成と誤認しにくくなる

### Negative

- `IntegratedRoute` に各 feature から共通表示モデルへ変換する adapter code が残る
- 新しい UI module が1つ増える
- 統合対象 feature の `UiState` 変更時には app-level adapter の追随が必要になる

## Relationship to existing ADRs

- ADR-0001 の UI / Domain / Data の責務分離を維持する
- ADR-0003 の multi-module dependency direction に従う
- ADR-0004 の app を薄い composition layer とする方針を具体化する
- ADR-0025 の feature 所有権を維持した統合ビュー方針を補足する
- ADR-0060 の「現行構成へ収束し、不要な過去構成を steady-state に残さない」という整理方針に沿う
