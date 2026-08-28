# ADR-0211: X の表示カスタマイズを CSS-only に戻す

- Status: Accepted
- Date: 2026-08-28
- Supersedes: ADR-0206, ADR-0209, ADR-0210
- Restores: ADR-0115
- Amended by: ADR-0213

## Context

ADR-0206 以降、X のヘッダータブのように一意な CSS selector だけでは扱いにくい DOM を対象として、structured DOM display rule、URL group、fingerprint 集合による判定を段階的に導入した。

実端末上の X Web UI では、タブの属性、wrapper、container の構造が想定と異なるケースが続き、要素 picker の単純な「選択して非表示」操作に対して複雑さと失敗経路を増やした。今回の用途は X の特定表示に対する個別カスタマイズであり、汎用機能として semantic DOM rule を維持する費用に見合わない。

## Decision

X の表示カスタマイズを ADR-0115 と PR #348 時点の CSS-only 方式へ戻す。

- 要素 picker は選択要素から永続可能な CSS selector を生成し、`display: none !important` を現在の CSS セットへ追記する
- `nth-of-type()` を含む不安定な selector は保存しない
- href と semantic boundary を用いた selector 一意化は維持する
- semantic DOM display rule、MutationObserver による rule 再適用、fingerprint 集合、List group 専用 action は削除する
- `XViewerCssSettings` は CSS の有効状態、3つの CSS セット、選択中セットだけを保持する
- SharedPreferences の `dom_rules_v1` は読み書きしない。既存端末に残る値は未参照の legacy preference として扱う
- 設定画面から DOM rule 件数・削除 UI を撤去する

特定の X UI をまとめて隠す必要がある場合は、まずユーザー編集可能なカスタム CSS で表現する。X の DOM に特化した新しい runtime rule engine は、複数の実 DOM 例と安定した識別根拠が得られるまで再導入しない。

ADR-0213 では、この rollback のうち app-owned semantic DOM rule engine を導入しない判断は維持したまま、明示的なユーザー入力として任意 JavaScript を保存・実行できる escape hatch を追加する。そのため本 ADR の「CSS だけを永続化する」という制約のみを緩和する。

## Consequences

### Positive

- 要素 picker の責務が「選択した要素を CSS で非表示」に戻り、操作と実装が一致する
- Domain / Data から X DOM 固有の永続モデルを除去できる
- MutationObserver と追加 JavaScript runtime を削除できる
- X の DOM variation による専用 action の誤判定をなくせる
- CSS は設定画面から直接確認・編集・削除できる

### Negative

- CSS selector だけでは表示文字列を条件にできない
- sibling 集合の一部だけを意味的に残す操作は、DOM 構造に応じた CSS を手動で記述する必要がある
- X の DOM 変更により手動 CSS が無効になる可能性は残る

## Privacy and public repository review

CSS-only 方式では runtime から取得した List 名、URL、fingerprint を新たに永続化しない。repository、ADR、テストには実ユーザー固有の X 情報を含めない。

## Relationship to other ADRs

- ADR-0206, ADR-0209, ADR-0210 の semantic DOM display rule 系の決定を廃止する
- ADR-0115 の local WebView CSS customization と selector safety 方針へ戻る
- ADR-0102 の UI / Domain / Data ownership は維持する
- ADR-0136 の公開 repository content verification を維持する
- ADR-0213 は app-owned semantic rule を復活させず、ユーザーが明示的に保存する JavaScript だけを追加する
