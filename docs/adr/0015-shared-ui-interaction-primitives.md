# ADR-0015: feature 横断の UI interaction primitive を design system で共有する

- Status: Accepted
- Date: 2026-08-10

## Context

RSS の記事一覧、メール一覧、YouTube の動画一覧では、横スワイプによる操作をそれぞれ独自実装していた。

これらは操作対象やラベルは異なる一方で、次の UI interaction は同じである。

- 通常スワイプと深いスワイプの閾値
- 指に追従するオフセットと離した後の spring animation
- 操作確定後に行を画面外へ退場させる時間
- 行が一覧から消えた後に残りの要素を滑らかに詰める list item animation
- 一覧に残る操作では行を元の位置へ戻す挙動

この部分を feature ごとに複製すると、RSS だけ滑らかでメールでは即座に詰め直される、といった体験差が生じる。また、調整時に複数 feature を個別修正する必要がある。

ADR-0003 では、feature に依存しない横断的な技術 capability は `core` に置き、例として `:core:designsystem` を挙げている。

## Decision

feature 固有の意味を持たない UI interaction primitive は `:core:designsystem` に配置する。

スワイプ操作付きの一覧項目については `SwipeActionListItem` を共通 primitive とし、次を design system 側で所有する。

- swipe gesture の追従
- swipe commit の判定
- spring / snap の motion specification
- action 発火前の退場 animation
- `LazyItemScope.animateItem()` による削除・並べ替え時の list reflow animation
- 一覧に残る action の復帰 animation
- 共通の card shape、余白、action background 表示

feature 側は次だけを指定する。

- action label
- action color
- action callback
- action 後に現在の一覧から項目が消えるかどうか
- 項目本体の content

`core` は feature の model や repository を参照しない。callback を通じて feature 側へ操作を返す。

現在存在する同種の実装は RSS/Article、Mail、YouTube から `SwipeActionListItem` へ移行する。今後同じ interaction を追加する feature も原則としてこの primitive を利用する。

## Consequences

### Positive

- RSS、メール、YouTube でスワイプと削除後の詰め直し animation が一致する
- motion parameter の調整箇所が一つになる
- feature UI から pointer gesture と animation の定型実装を除去できる
- feature は業務上の action 定義に集中できる

### Negative

- UI feature が `:core:designsystem` に依存する
- 共通 primitive に feature 固有の例外を持ち込むと API が肥大化する可能性がある

## Guardrails

- feature 固有の文言、model、repository、use case は `:core:designsystem` に置かない
- 共有するのは見た目・motion・gesture といった interaction primitive に限定する
- feature 固有要件のために共通 API が複雑化する場合は、feature 側で primitive を組み合わせて表現する
- 同種の interaction を feature 内で再実装する前に、既存 primitive の拡張で表現できるか確認する

## Relationship to ADR-0003

ADR-0003 の `core` を共有 capability とする方針を UI interaction に具体化する。`core -> feature` の依存は禁止したまま、`feature:*:ui -> core:designsystem` の依存を許可する。
