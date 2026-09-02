# ADR-0231: スクロール可能なオーバーレイをフルスクリーン modal に統一する

- Status: Accepted
- Date: 2026-09-03
- Refines: [ADR-0213](0213-x-custom-javascript-fullscreen-settings.md)

## Context

Compose Material 3 の `ModalBottomSheet` は drag / swipe による dismiss gesture を持つ。内部に長い編集フォーム、記事一覧、要約本文などの縦スクロール領域を置くと、ユーザーがコンテンツをスクロールする操作と sheet の dismiss gesture が競合し、意図せず閉じることがある。

ADR-0213 では X の CSS / JavaScript 編集についてこの問題を確認し、`ModalBottomSheet` を full-screen `Dialog` へ置き換えた。現在も RSS Web 取得ルール、Web Library metadata 取得ルール、記事要約、Bookmark タグ内の記事一覧、Library シリーズ内の書籍一覧に同じ構造が残っている。

これらは操作中の入力や閲覧位置を保持したい UI であり、overlay 自体を drag して閉じる利点より、内部スクロールを安定させることを優先する。

## Decision

内部に独立した縦スクロール領域を持つ `ModalBottomSheet` を、画面全体を覆う `Dialog` へ置き換える。

対象:

- RSS Web 取得ルールの追加 / 編集
- Web Library metadata 取得ルールの追加 / 編集
- 記事要約
- Bookmark タグに属する記事一覧
- Library シリーズに属する書籍一覧

共通方針:

- `DialogProperties(usePlatformDefaultWidth = false)` を利用し、overlay を画面全体へ展開する
- outside tap では dismiss しない
- vertical / horizontal swipe による overlay dismiss gesture を持たせない
- system back による dismiss は許可する
- 編集 UI では safe drawing inset と IME inset を考慮する
- スクロールはコンテンツ領域だけに持たせ、主要 action は可能な限り固定する
- feature ownership、ViewModel、Domain / Data contract、durable state は変更しない

アプリ全体の `ModalNavigationDrawer` はこの対象に含めない。navigation drawer は app-shell navigation 自体が目的であり、今回問題となった縦方向の長文編集・閲覧 overlay と dismiss gesture の組み合わせとは性質が異なる。将来 navigation drawer で同様の誤 dismiss が再現する場合は別途扱う。

## Change impact

Changed capabilities:
- RSS / Library の Web rule 編集 presentation
- Summary / Bookmark / Library の overlay presentation

Changed Context / ownership:
- none

Changed dependency direction:
- none

Changed durable data / schema:
- none

Changed background execution:
- none

Changed external communication / permission / credential boundary:
- none

New concepts introduced:
- none。ADR-0213 で採用済みの full-screen `Dialog` pattern を再利用する

Rollback / removal cost:
- UI container を戻すだけで、migration は不要

## Consequences

### Positive

- overlay 内の縦スクロールが swipe-dismiss と競合しない
- 長い script や本文、一覧を閲覧中に誤って閉じる可能性を下げられる
- X customization ですでに採用済みの操作モデルへ揃えられる

### Negative

- bottom sheet の視覚的な軽さと drag dismiss は失われる
- 小さな内容でも画面全体を占有する

## Alternatives

### `ModalBottomSheet` の gesture を調整して維持する

却下。個々の nested scroll 状態や Compose version に依存した調整を増やすより、dismiss gesture 自体を持たない container の方が今回の要求を直接満たす。

### 編集フォームだけ full-screen にする

却下。要約、タグ記事一覧、シリーズ書籍一覧も内部スクロールが主操作であり、同じ誤 dismiss 経路を持つため一緒に解消する。

## Relationship to other ADRs

- ADR-0213 の full-screen customization dialog を一般的な scrollable overlay presentation の先例として継承する
- 各 feature の ownership、persistence、external boundary に関する既存 ADR は変更しない
