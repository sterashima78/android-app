# ADR-0207: X のリストタブを単一項目ではなく URL group として表示する

- Status: Accepted
- Date: 2026-08-28
- Supersedes: ADR-0206 の単一タブ keep-only rule
- Amends: ADR-0115

## Context

ADR-0206 では、X のヘッダータブのように一意な CSS selector を作りにくい sibling 集合に対して、選択した1項目だけを残す `KEEP_ONLY_MATCHING_ITEM` rule を導入した。

しかし実際の要件は「選択したリスト1件だけを残す」ことではなく、複数存在する X List のタブをすべて表示し、`For you` や `Following` など List 以外のタブだけを隠すことだった。

表示文字列、`aria-label`、個別 List の exact `href` を fingerprint にすると1項目しか残せず、この要件を表現できない。一方、X の List URL は `/i/lists/` 配下という意味のある URL path family を持つため、個別値ではなく URL group を target にできる。

## Decision

semantic DOM display rule を単一 target ではなく matching item group を残せる形へ変更する。

### Rule semantics

`XViewerDomRuleKind` は `KEEP_MATCHING_ITEMS` とする。

初期 group matcher として `XViewerDomTargetKind.HREF_PATH_PREFIX` を追加し、X List tab では target value に `/i/lists/` を保存する。

同じ `pagePath` / `containerSelector` / `itemSelector` を同じ scope とし、scope 内で新しい display rule を保存した場合は既存 rule を置き換える。

ADR-0206 で保存された `KEEP_ONLY_MATCHING_ITEM` は新しい enum に含めず、読み込み時に無効な legacy rule として無視する。これにより、以前の APK で作成された単一表示 rule がアップデート後も1件だけを隠し残すことを防ぐ。

### Rule generation

要素選択 UI の action を「同じ列のリストだけ表示」とする。

選択対象は引き続き `[role="tab"]` とし、次を満たす場合だけ rule を作成する。

- 選択した tab 自身または子 link の URL pathname が `/i/lists/` で始まる
- 最寄りに `[role="tablist"]` または `[data-testid="ScrollSnap-List"]` が存在する
- sibling item が2件以上存在する
- 選択した tab が List URL group に含まれる

List 名や List ID 自体は rule の target value に保存せず、URL path prefix のみを保存する。

### Runtime application

runtime は container 内の item ごとに link URL を同一 origin 基準で解決し、pathname が `/i/lists/` で始まる item をすべて matching item とする。

- matching item が1件以上あれば、それらをすべて表示し、それ以外の sibling を app-owned hidden attribute で隠す
- matching item が0件なら何も隠さない fail-open とする
- `MutationObserver` による再適用は ADR-0206 の方針を維持する
- page path scope も維持する

これにより List が2件でも10件でも同時に表示される。

## Consequences

### Positive

- 複数の X List tab をまとめて残せる
- List 名、表示言語、個別 List ID に依存しない
- `For you` / `Following` など同じ tab row の非 List item をまとめて隠せる
- X が List tab を一時的に描画しない場合は全表示に戻るため操作不能になりにくい
- legacy の単一表示 rule は自動的に無効化される

### Negative

- X が List URL path を `/i/lists/` から変更した場合は rule が適用されなくなる
- 現時点では List tab group に特化しており、任意の URL family を UI から指定する機能ではない

## Alternatives

### List 名の文字列一致

却下。言語設定やユーザーによる List 名変更に依存する。

### 個別 List の href を複数保存

却下。List の追加・削除のたびに rule 更新が必要になり、今回の「List という種類を残す」要件と一致しない。

### CSS の `:has()` だけで表現

X の DOM 構造次第では可能だが、既存 semantic rule runtime が SPA 再描画への再適用と fail-open をすでに提供しているため、同じ mechanism に group matcher を追加する。

## Relationship to other ADRs

- ADR-0206 の semantic DOM rule / MutationObserver / fail-open 方針を維持し、単一 target semantics のみ置き換える
- ADR-0115 の local WebView customization 方針を維持する
- ADR-0102 の UI / Domain / Data ownership を変更しない
- ADR-0136 に従い、実際の List ID や List 名を repository・テスト fixture・ログへ保存しない
