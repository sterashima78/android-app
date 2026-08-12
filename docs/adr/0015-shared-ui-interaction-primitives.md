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

更新可能な一覧については、ヘッダー上の常設更新ボタンではなく、一覧先頭で下方向へ引く pull-to-refresh を標準 interaction とする。`PullToRefreshContainer` を `:core:designsystem` の共通 primitive とし、RSS、Reddit、メール、YouTube、およびフィード管理から利用する。更新中の表示は Material 3 の pull-to-refresh indicator に統一する。

ジェスチャーだけに依存すると支援技術から更新できなくなるため、`PullToRefreshContainer` は画面上に常設ボタンを表示せず、アクセシビリティの custom action として「更新」を公開する。

一覧項目に複数の補助 action があり、従来は縦三点などの action button から `DropdownMenu` を開いていた場合、一覧上の常設 action button は置かず、項目の長押しで同じ menu を開くことを標準 interaction とする。蔵書一覧で先行している長押し操作に RSS 記事一覧、統合ビュー、タスク一覧を合わせる。

長押し menu を持つ項目では次を守る。

- 通常タップの primary action は従来どおり維持する
- swipe、checkbox、展開ボタンなど直接操作できる control は長押し menu へ吸収しない
- 長押しに単独 action が割り当てられていた場合は、その action を menu 項目へ移し、長押し自体は menu 呼び出しに統一する
- 既に通常タップ可能な行は Compose の `combinedClickable` を使って click と long click を同じ領域に定義する
- checkbox など独立した子 control を持つ行では、子 control の操作を妨げない本文領域で長押しを受け、アクセシビリティ semantics にも long-click action を公開する

この interaction は Compose 標準 gesture API と `DropdownMenu` の単純な組み合わせで表現できるため、薄い wrapper を `:core:designsystem` に追加すること自体は目的としない。独自の gesture 判定、motion、共通 styling など共有すべきロジックが発生した時点で design system primitive へ切り出す。

## Consequences

### Positive

- RSS、メール、YouTube でスワイプと削除後の詰め直し animation が一致する
- RSS、Reddit、メール、YouTube、フィード管理で更新操作と indicator が一致する
- ヘッダーから更新専用ボタンを除去し、主要操作のための領域を確保できる
- motion parameter の調整箇所が一つになる
- feature UI から pointer gesture と animation の定型実装を除去できる
- feature は業務上の action 定義に集中できる
- 一覧の縦三点ボタンを除去でき、タイトルや本文に使える横幅が増える
- 蔵書、RSS、統合ビュー、タスクで補助 action の呼び出し方法が一致する

### Negative

- UI feature と app host が `:core:designsystem` に依存する
- pull-to-refresh はスクロール可能な一覧を前提とするため、空状態も `LazyColumn` 内に配置する必要がある
- 共通 primitive に feature 固有の例外を持ち込むと API が肥大化する可能性がある
- 長押し menu は常設ボタンより発見性が低いため、支援技術向け semantics を欠かさない必要がある

## Guardrails

- feature 固有の文言、model、repository、use case は `:core:designsystem` に置かない
- 共有するのは見た目・motion・gesture といった interaction primitive に限定する
- feature 固有要件のために共通 API が複雑化する場合は、feature 側で primitive を組み合わせて表現する
- 同種の interaction を feature 内で再実装する前に、既存 primitive の拡張で表現できるか確認する
- 更新可能な一覧では `PullToRefreshContainer` を優先し、通常の更新のためだけにヘッダーへ常設ボタンを追加しない
- 更新失敗時の明示的な再試行など、状態回復に必要なボタンは pull-to-refresh とは別の操作として残してよい
- 補助 action のためだけに一覧へ縦三点などの常設 menu button を追加しない
- 長押し menu を追加する場合は、通常タップ、swipe、checkbox など既存 gesture/control と競合しないことを確認する
- 長押しだけに依存する操作はアクセシビリティ semantics からも実行可能にする

## Relationship to ADR-0003

ADR-0003 の `core` を共有 capability とする方針を UI interaction に具体化する。`core -> feature` の依存は禁止したまま、`feature:*:ui -> core:designsystem` の依存を許可する。
