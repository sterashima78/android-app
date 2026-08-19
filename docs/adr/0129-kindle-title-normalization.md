# ADR-0129: Kindle購入済み本のタイトル末尾ノイズを正規化する

- Status: Accepted
- Date: 2026-08-20
- Refines: ADR-0057

## Context

Kindle Web Library が返す購入済み本のタイトルには、書名そのものではない `(Japanese Edition)` が末尾に付く場合がある。この値をそのまま `library_items.title` に保存すると、蔵書一覧、検索、整理処理などアプリ内で扱う書名に外部サービス固有の表示ノイズが混入する。

一方、Personal Document のタイトルはユーザーが付けた名前であり、同じ文字列が末尾にあっても自動削除すべきではない。

## Decision

- 購入済み Kindle 本では、データ層の永続化境界でタイトルを trim し、末尾の `(Japanese Edition)` を削除してから `library_items.title` に保存する。
- suffix を削除するとタイトルが空になる場合は元の trim 済みタイトルを保持する。
- Personal Document (`PDOC:` sourceId) はこの正規化の対象外とする。
- 既存の購入済み Kindle レコードも、library schema を確認するタイミングで同じ規則を冪等に適用して補正する。
- collector が生成する一時 JSON の形式は変更せず、Amazon 固有データからアプリの蔵書モデルへ変換する data layer が正規化を所有する。

## Consequences

- 既存・新規の購入済み Kindle 本で表示、検索、整理に使うタイトルが一貫する。
- Amazon 側の表示ノイズを domain / UI に持ち込まない。
- Personal Document のユーザー定義タイトルは保持される。
- `(Japanese Edition)` が実際の書名の一部である購入済み Kindle 本でも末尾に一致すれば削除されるため、この規則は Kindle 購入済み本に限定する。
