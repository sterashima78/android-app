# ADR-0210: X ホームの固定タイムラインタブを fingerprint 集合として保持する

- Status: Accepted
- Date: 2026-08-28
- Supersedes: ADR-0209 の `/i/lists/` URL group 判定
- Refines: ADR-0206
- Amends: ADR-0115

## Context

ADR-0209 では、X ホームで複数の List タブだけを残すため、各タブの link pathname が `/i/lists/` で始まることを意味的な group 判定として利用した。

しかし実際の X Web UI では、ホーム上部に固定された List / custom timeline タブが必ずしも `/i/lists/` の link を DOM に公開しない。表示上は標準タイムラインの右側に複数の固定タブが並んでいても、WebView から安定した List URL family を取得できない場合がある。このため ADR-0209 の rule は生成できず、要件を満たせなかった。

一方、X ホームの対象 tab row では、先頭2項目が X 標準タイムラインで、その後ろにユーザーが固定した timeline tab が並ぶ。個別の固定タブは `href`、`aria-label`、または表示文字列によって、その時点の sibling 集合内では一意に識別できる場合がある。

必要なのは個別 List ID の意味を推測することではなく、「現在固定されている複数タブをまとめて残し、標準タイムライン等を隠す」ことである。

## Decision

X ホームの固定 timeline tab を URL family ではなく、現在の tab row から得られる fingerprint 集合として保存する。

### Group boundary

rule 作成は pathname が `/` または `/home` のときだけ許可する。

対象 tab row の timeline tab のうち先頭2項目を X 標準タイムラインとみなし、3項目目以降を現在固定されている custom timeline group とする。

ユーザーが3項目目以降のいずれかを選択して「同じ列のリストだけ表示」を実行したとき、その1項目だけではなく3項目目以降の全 timeline tab の fingerprint を一括保存する。

先頭2項目の具体的な表示文字列には依存しない。これにより X の表示言語に依存しない。

### Tab recognition

タイムラインタブの識別は `role="tab"` を基準とする。`aria-selected` は選択状態の補助情報であり、固定タブの存在判定条件には使用しない。

X の DOM では、選択中の標準タブには `aria-selected` が存在しても、右側の固定タブには同属性が存在しない場合がある。そのため `[role="tab"][aria-selected]` で列挙すると、固定タブが sibling 集合から欠落して rule を生成できない。

picker が `role="tab"` 自体ではなく、その内側の要素または単一 tab を含む presentation wrapper を選択した場合も、その tab を解決して同じ処理へ進める。

### Fingerprint

各 custom timeline item について、同じ rule scope 内で一意になる最初の値を次の優先順位で選ぶ。

1. link URL の pathname
2. exact `href`
3. `aria-label`
4. whitespace を正規化した `textContent`

保存形式は `XViewerDomTargetKind.FINGERPRINT_SET` とし、`targetValue` に fingerprint の JSON array を保存する。

個別 fingerprint の kind は runtime 内部の固定 vocabulary とし、任意 JavaScript や任意 selector を保存しない。

### Container / item scope

container selector は、可能なら X の primary column に scope した semantic selector を使う。selector が document 全体で一意ならそのまま採用するが、X が同種の `tablist` や `ScrollSnap-List` を複数持つ場合は、選択した container を含む selector も保存可能とする。

container selector 自体が複数要素へ一致する場合は、保存済み fingerprint 集合を各候補 container に対して解決し、全 fingerprint が一意に解決できる候補が正確に1件の場合だけその container を採用する。0件または複数件なら fail-open とする。

DOM が direct child の `[role="presentation"]` wrapper を持つ場合は、その wrapper を display item とする。これにより標準 timeline tab だけでなく同じ row の追加 control も非表示対象にできる。

wrapper 構造を検出できない場合は `[role="tab"]` 自体へ fallback する。この場合、tab ではない追加 control の非表示は保証しない。

### Runtime application

保存したすべての fingerprint が現在の item 集合内でそれぞれ正確に1項目へ解決され、かつ同じ item へ重複解決しない場合だけ rule を適用する。

container selector が複数の候補へ一致する場合は、上記 fingerprint 条件を満たす候補 container が正確に1件であることも必要とする。

条件を満たした場合は fingerprint 集合に含まれる item を表示し、それ以外の sibling item を app-owned hidden attribute で非表示にする。

次のいずれかでは fail-open とし、何も隠さない。

- container selector の一致が0件
- fingerprint を完全に解決できる container が0件または複数件
- 保存済み fingerprint が壊れている
- いずれかの fingerprint が同一 container 内で0件または複数件へ解決される
- 複数 fingerprint が同じ item へ解決される
- X の DOM 更新によって item scope が成立しない

MutationObserver と requestAnimationFrame による再適用は ADR-0206 の方針を維持する。

### Legacy rules

ADR-0209 の `HREF_PATH_PREFIX` target kind は domain enum から削除する。SharedPreferences に残る旧 `/i/lists/` rule は decode 時に無効値として無視され、アップデート後に誤って再適用されない。

ADR-0206 の `KEEP_ONLY_MATCHING_ITEM` rule も引き続き無効のままとする。

### Privacy

fingerprint には端末利用者の固定 tab 名や link path が含まれる可能性があるため、SharedPreferences の端末内データとしてのみ保存する。

実際の tab 名、List ID、URL path を repository、fixture、ログ、ADR に記録しない。テストは合成値のみを使う。

## Consequences

### Positive

- X が `/i/lists/` link を DOM に公開しなくても、複数の固定 timeline tab をまとめて残せる
- 固定 tab のどれか1つを選ぶだけで、同じ row の固定 tab 全体を対象にできる
- `aria-selected` の有無に依存せず、未選択の固定 tab も group に含められる
- 同種の tab container が複数存在しても fingerprint 集合で一意に識別できれば適用できる
- 標準 timeline の表示文字列や UI 言語に依存しない
- X の DOM が期待から外れた場合は fail-open するため、tab row 全体を誤って消しにくい
- direct presentation wrapper を利用できる DOM では同じ row の追加 control も非表示にできる

### Negative

- X ホームの先頭2 timeline tab が標準 timeline であるという構造に依存する
- 固定 tab の追加、削除、名称変更で fingerprint 集合が一致しなくなった場合は rule の再作成が必要になる
- text fingerprint まで fallback した場合は表示文字列変更に影響される
- 複数 container が同じ fingerprint 集合を同時に満たす場合は安全側に倒して適用しない
- fallback DOM では tab ではない追加 control を非表示にできない場合がある

## Alternatives

### `/i/lists/` pathname group

ADR-0209 で採用したが、実際の X Web DOM が List URL を公開しないケースで rule を生成できないため廃止する。

### `aria-selected` を持つ tab だけを列挙する

選択中タブの識別には使えるが、未選択の固定タブで属性が省略される DOM があるため採用しない。タブの存在判定は `role="tab"` に限定する。

### container selector の global uniqueness を必須にする

単純だが、X が同種の tab container を複数配置しただけで rule 作成が失敗する。fingerprint 集合そのものが container の識別にも利用できるため、selector の一意性だけを必須条件にはしない。

### ユーザーが複数タブを1件ずつ選択する

明示的だが、現在の tab row では「先頭2項目を除く固定 timeline」という group boundary が利用できるため、操作回数を増やさない方を選ぶ。将来この boundary が成立しなくなった場合は multi-select UI を再検討する。

### 表示文字列だけで固定 tab を判定する

言語や名称変更への依存が強いため採用しない。text は個別 fingerprint の最終 fallback に限定する。

## Relationship to other ADRs

- ADR-0209 の URL group 判定を置き換える
- ADR-0206 の semantic DOM rule、MutationObserver、fail-open 方針を維持する
- ADR-0115 の local WebView customization 方針を維持する
- ADR-0102 の UI / Domain / Data ownership を変更しない
- ADR-0136 に従い、runtime で得た user-specific 値を公開 repository やログへ出力しない
