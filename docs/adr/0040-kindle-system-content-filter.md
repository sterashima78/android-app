# ADR-0040: Kindle インポートからシステム補助コンテンツを除外する

- Status: Accepted
- Date: 2026-08-14
- Refines: ADR-0026, ADR-0033

## Context

Kindle の `Digital.Content.Ownership*.json` には、通常の購入書籍だけでなく Kindle が提供する補助コンテンツも含まれる。

ADR-0033 では `origin.originType` の観測例が1件だけだったため、`originType` を蔵書除外の根拠には使わない判断としていた。その後、辞書が `resourceType=KindleEBook` のまま `origin.originType=KindleDictionary` として記録される実例を確認した。したがって `resourceType` だけでは辞書を通常書籍と区別できない。

ユーザーガイドについては同等の `originType` 実例をまだ確認できていない。一方、タイトル中の `guide` や `dictionary` だけで除外すると、一般書籍まで誤って除外する可能性がある。

実ユーザーの ASIN、タイトル、日時、ownership JSON はパブリックリポジトリへ保存しない。

## Decision

Kindle ownership の候補生成前に、次の順でシステム補助コンテンツを判定して除外する。

1. 従来どおり `resourceType` などの content type が音楽・動画・Audible を示す場合は除外する。
2. `originType` が Kindle の辞書・ユーザーガイド・端末ガイド・端末マニュアルを明示する場合は除外する。
3. `originType` で識別できないユーザーガイドへの限定フォールバックとして、タイトルが Kindle ブランドで始まり、かつ `User Guide` / `User's Guide` / `ユーザーガイド` / `取扱説明書` 相当を含む場合だけ除外する。

タイトルに単に `guide`、`dictionary`、`辞書` が含まれるだけでは除外しない。これにより、一般の技術書・辞書商品などをタイトルだけでシステムコンテンツと誤認しない。

テストは人工 ASIN・人工タイトルだけを使用し、実ユーザーデータを fixture にしない。

## Consequences

### Positive

- `KindleDictionary` として配布された辞書が蔵書へ混入しない。
- Kindle のユーザーガイドや端末ガイドを通常書籍として表示しにくくなる。
- 一般書籍のタイトルに `guide` が含まれていても、それだけでは除外されない。
- 実ユーザーのエクスポート内容を公開リポジトリへ追加せず回帰テストできる。

### Negative

- Amazon が新しい `originType` を追加した場合はマーカーの追従が必要になる。
- 未観測のユーザーガイド形式は限定的なタイトル判定でも検出できない場合がある。
- Kindle ブランド名から始まる一般書籍がユーザーガイド形式の語を含む場合、まれに誤除外する可能性がある。

## Relationship to existing ADRs

- ADR-0026 の `Digital.Content.Ownership*.json` を正規入力とする方針と、実ユーザーデータをテスト fixture に保存しない方針を維持する。
- ADR-0033 の観測済み `resource` / `rights` スキーマ解釈を維持する。
- ADR-0033 の「`originType` は除外根拠に使用しない」という判断を、本 ADR の観測済み `KindleDictionary` について更新する。
