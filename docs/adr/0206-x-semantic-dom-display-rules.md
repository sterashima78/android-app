# ADR-0206: X の選択要素表示を semantic DOM rule として永続化する

- Status: Accepted
- Date: 2026-08-28
- Amends: ADR-0115, ADR-0102

## Context

ADR-0115 では X WebView 上で選択した要素を一意な CSS selector に変換し、`display: none` の rule として保存する方式を採用した。その後、無限スクロールで同種要素が増える問題に対して、`href` と semantic boundary を用いて selector の一意性を高め、`nth-of-type()` を永続 selector から除外した。

一方、X のヘッダータブのように同じ `role="tab"` を持つ兄弟要素では、残したい項目に安定した一意属性が存在しない場合がある。CSS selector は表示文字列そのものを条件にできないため、「リストのタブだけ残し、同じタブ列の他項目を隠す」という操作は CSS selector の一意化だけでは安全に表現できない。

X は SPA として DOM を差し替えるため、現在の DOM node へ一時的な属性を付けるだけではページ内遷移や再描画後に設定が失われる。

ADR-0102 では X の責務を UI / Domain / Data に分離し、DOM 操作を UI、設定状態を Domain、SharedPreferences の保存形式を Data が所有する方針としている。

## Decision

既存の CSS selector による「選択要素を非表示」は維持し、それとは別に semantic DOM display rule を導入する。

### Rule model

`:feature:x:domain` に `XViewerDomRule` を置き、以下を保持する。

- rule kind
- X の `location.pathname`
- sibling 集合を表す container selector
- sibling item selector
- target fingerprint kind
- target fingerprint value

初期 rule kind は `KEEP_ONLY_MATCHING_ITEM` のみとする。

同一の kind / page path / container selector / item selector は同じ scope とみなし、同じ scope で新しい項目を選択した場合は既存 rule を置き換える。

DOM display rule は CSS セットとは独立した X 表示設定として扱う。3つの CSS セットの既存意味と永続化形式は変更しない。

### Rule generation

X 要素選択 UI に「同じ列ではこれだけ表示」を追加する。

初期対象は以下の構造に限定する。

- item: `[role="tab"]`
- container: 最寄りの `[role="tablist"]` または `[data-testid="ScrollSnap-List"]`

対象 item の fingerprint は兄弟集合内で一意になるものを次の順で採用する。

1. item または子 link の exact `href`
2. item の exact `aria-label`
3. whitespace を正規化した item の `textContent`

どの fingerprint でも兄弟集合内で1件に確定できない場合は rule を作成しない。

### Runtime application

`:feature:x:ui` は保存済み rule を WebView に注入し、app-owned attribute を使って非対象 sibling を非表示にする。

- rule の `pagePath` が現在の `location.pathname` と一致する場合だけ評価する
- container ごとに target fingerprint の一致数を確認する
- target がちょうど1件の場合だけ他の sibling に app-owned hidden attribute を付与する
- target が0件または複数件の場合はその container に何も隠さない fail-open とする
- app-owned hidden attribute だけを CSS で `display: none !important` にする
- X の DOM 再生成に追従するため `MutationObserver` で child/text change を監視し、`requestAnimationFrame` 単位で再適用する
- observer は WebView page ごとに1つとし、rule 再注入時は既存 observer を停止する
- `addJavascriptInterface` は使用しない

`XViewerCssSettings.enabled` は従来の CSS だけでなく X 表示カスタマイズ全体の kill switch とし、無効時は CSS と DOM display rule の双方を適用しない。

設定画面では保存済み DOM display rule の件数を表示し、すべて削除できる復旧手段を提供する。

### Persistence and privacy

`:feature:x:data` は既存 `x_viewer_preferences` に `dom_rules_v1` を追加し、versioned JSON array として保存する。既存の `custom_css_enabled`、`active_css_set`、`custom_css_set_1` 〜 `custom_css_set_3` は変更しない。

fingerprint には X 上の link path やユーザーが見ているタブ文字列が含まれる可能性がある。これらは端末内の設定としてのみ扱い、ログ、クラッシュ診断、repository、テスト fixture へ実ユーザーデータを含めない。テストでは架空の path / label のみ使用する。

## Consequences

### Positive

- CSS selector だけでは区別できない X のヘッダータブでも、選択したタブだけを残せる
- `href` がない場合も、兄弟内で一意な `aria-label` や表示文字列を限定的な fallback として利用できる
- X の SPA 再描画後も設定を再適用できる
- fingerprint が一致しなくなった場合は全表示へ戻るため、DOM drift で必要 UI を全消去しにくい
- 既存の CSS 編集・CSS セット・要素単体非表示を壊さず拡張できる
- UI / Domain / Data の既存 ownership を維持できる

### Negative

- `textContent` fallback は表示言語や X の文言変更に依存する
- MutationObserver による再評価コストが追加される
- 初期実装は `role="tab"` の sibling 集合に限定され、一般的な任意 sibling 操作ではない
- DOM display rule は CSS テキストから直接確認・編集できないため、設定画面では現時点で件数表示と一括削除のみ提供する

## Follow-up

他の横並び navigation や filter でも同じ要求が生じた場合は、item/container の対応範囲を追加する。rule を個別編集・削除する必要が生じた場合は、設定画面に rule 一覧を追加する。

## Relationship to other ADRs

- ADR-0115 の WebView / local customization / JavaScript bridge 非公開方針を維持する
- ADR-0102 の X UI / Domain / Data ownership に従う
- ADR-0136 の公開 repository content verification を維持し、実ユーザーの X path / label を repository に記録しない
