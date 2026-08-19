# ADR-0055: ADR 番号を一意な単調増加番号として管理する

- Status: Accepted
- Date: 2026-08-14
- Updated: 2026-08-19
- Amended by: ADR-0121

## Context

ADR の追加が並行して進んだ結果、複数の異なる設計判断に同じ ADR 番号が割り当てられていた。番号が重複すると、`Supersedes`、`Refines`、本文中の `ADR-xxxx` 参照だけでは対象文書を一意に特定できず、後続の設計判断から過去の判断を追跡しにくくなる。

2026-08-19 の再確認では ADR-0047、0065、0066、0067、0071、0075、0086 に衝突が残っていた。人手のレビューだけでは並行変更による再発を防ぎきれないため、既存の番号管理規則に機械検査を追加する。

## Decision

ADR のファイル名と見出しに使用する4桁番号は、リポジトリ内で一意とする。

新しい ADR を追加するときは、現在存在する最大番号より大きい番号を割り当てる。並行作業で同じ番号が使われた場合は、マージ前に最新 `main` を基準として衝突を解消する。

既存 ADR の番号を変更する場合は、設計判断の本文を変更せず、次を同じ変更内で更新する。

- ファイル名
- 文書先頭の `ADR-xxxx` 見出し
- `Supersedes`、`Refines`、`Amends` などの関係
- 他 ADR からの本文参照
- ADR ファイル名を直接参照するリンク

番号変更は設計判断の置換や廃止を意味しない。Status、Date、Decision の意味は維持する。

### 既存衝突の解消

既存番号の意味を可能な限り維持するため、参照が多い側または後続 ADR の基準として使われている側を旧番号に残す。作業中に最新 `main` へ ADR-0107 が追加されたため、その番号も維持し、衝突側には ADR-0108〜0115 を割り当てる。

- X WebView / CSS: ADR-0047 → ADR-0115。feature-owned database schema の ADR-0047 は維持する。
- 蔵書整理と AI suggestions: ADR-0065 → ADR-0108。SMB Library / Book Reader の ADR-0065 は維持する。
- Generated Knowledge Wiki: ADR-0066 → ADR-0109。background library AI organization queue の ADR-0066 は維持する。
- SMB library deduplication: ADR-0067 → ADR-0110。background bookmark AI enrichment の ADR-0067 は維持する。
- validated series-aware library organization: ADR-0071 → ADR-0111。prioritized background AI task scheduling の ADR-0071 は維持する。
- Gemma artifact revisions / Speculative Decoding: ADR-0075 → ADR-0112。
- Knowledge page lifecycle management: ADR-0075 → ADR-0113。background Knowledge Wiki build queue の ADR-0075 は維持する。
- Solitaire board-first visual feedback: ADR-0086 → ADR-0114。compact bookmark tag browser の ADR-0086 は維持する。

再採番時は、番号だけでは意味を判別できない参照を一律置換しない。参照元の文脈を確認し、移動した設計判断を指している参照だけを新番号へ更新する。

### 自動検査

`scripts/verify_adr_integrity.py` を ADR 文書群の構造検査の正本とする。検査は少なくとも次を保証する。

- `docs/adr/*.md` のファイル名が `NNNN-lowercase-kebab-case.md` 形式である
- ADR 番号が一意である
- 文書先頭の `# ADR-NNNN: ...` とファイル名の番号が一致する
- 本文中の `ADR-NNNN` 参照先が存在する
- `docs/adr/...md` または ADR 間の相対 Markdown link が実在する ADR ファイルを指す

検査器そのものの回帰は `scripts/test_verify_adr_integrity.py` で行う。重複番号、見出し不一致、存在しない番号参照、存在しないファイルリンク、不正なファイル名を fixture で失敗させる。

`.github/workflows/adr-integrity.yml` は ADR、検査器、または workflow 自身が変更された push / pull request で検査器の unit test と実リポジトリ全体の検査を実行する。workflow の権限は `contents: read` のみにする。

ローカルでは次で同じ検査を実行できる。

```bash
python3 -m unittest scripts.test_verify_adr_integrity
python3 scripts/verify_adr_integrity.py
```

この検査は参照先の存在と識別子の整合性を保証するが、「その参照が意味的に正しい ADR を指しているか」までは判定しない。再採番時の意味的な参照更新は引き続きレビュー対象とする。

## Consequences

### Positive

- `ADR-xxxx` だけで設計判断を一意に特定できる
- ADR 間の関係を機械的に検査できる
- 並行開発による番号衝突や見出しの更新漏れを PR 時点で検出できる
- 再採番後に古い番号やファイル名への参照を残しにくくなる
- ADR 検査は Python 標準ライブラリだけで実行でき、Android build を必要としない

### Negative

- 並行ブランチではマージ前に再採番が必要になる場合がある
- 過去に外部から特定ファイル名へ直接リンクしている場合、再採番によって外部リンクが切れる可能性がある
- 数字として有効な別 ADR へ誤って参照を書き換えた場合、機械検査だけでは意味的な誤参照を検出できない

## Relationship to existing ADRs

既存 ADR の設計内容は変更しない。本 ADR は ADR 文書群そのものの識別子管理規則を定める。

ADR-0046 の `verifyArchitecture` がアプリコードの module / package / layer 境界を検査するのに対し、本 ADR の検査は ADR 文書群自身の識別子・参照整合性を担当する。両者は別の検査責務として維持する。
