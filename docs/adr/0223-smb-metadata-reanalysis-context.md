# ADR-0223: SMB 書誌正規化の再解析で前回結果と任意補足を引き継ぐ

- Status: Accepted
- Date: 2026-08-31
- Refines: ADR-0134

## Context

ADR-0134 の SMB 書誌正規化では、表紙画像と現在のファイル名から端末内 AI が書誌候補を生成し、ユーザーがレビューして反映・却下する。

誤りや不足がある候補は再解析できるが、従来は再解析時に `metadata_json` を破棄していたため、AI は直前に生成した候補を知らない状態で同じ表紙とファイル名を再度処理していた。このため入力が実質同一なら同じ候補が再生成されやすく、再解析による改善の手掛かりも与えられなかった。

また、書誌を判断するためにユーザーだけが知っている情報を補足したい場合がある。例として、著者名、シリーズ巻数、表紙上の文字が副題であることなどがある。

再解析は WorkManager と永続キューで UI の寿命から分離されているため、前回結果や補足を画面状態だけに保持すると process 再生成や待機中に失われる。

## Decision

### 1. 再解析時は直前の構造化書誌候補を保持する

再解析要求では、直前の `SmbBookMetadataProposal` を `smb_metadata_normalization_items.metadata_json` に残し、次に claim された worker へ `previousProposal` として渡す。

再解析中は新しいファイル名候補がまだ確定していないため `proposed_file_name` はクリアする。一方、書誌候補本体は次回推論の参考情報として保持する。

新しい推論が成功した時点で `metadata_json` を新結果へ置き換える。再解析が失敗した場合は前回結果を保持し、さらに再解析できるようにする。

候補生成後にファイル revision の差異を検出して再解析可能状態へ移す場合も、既存の書誌候補は破棄しない。

### 2. ユーザーは再解析時だけ任意の補足情報を追加できる

レビュー画面の「再解析」から確認ダイアログを開き、最大 2,000 文字の自由記述を任意で入力できるようにする。

補足は `smb_metadata_normalization_items.reanalysis_context` に保存し、worker が claim した対象とともに読み取る。これは Library Context が所有する一時的な workflow state であり、別 Context や global AI state へ昇格させない。

既存 database には additive schema refinement として `reanalysis_context TEXT` を idempotent に追加し、database version は上げない。これは `docs/architecture/persistence.md` の additive refinement 方針に従う。

補足は1回の再解析要求に属する。新しい候補生成に成功した時点で削除する。表紙 cache 消失によって同じ再解析要求を内部的に再キューする場合だけ、同じ補足を引き継ぐ。

### 3. 前回結果は正解として扱わず、独立再評価を要求する

推論 prompt には次を固定 instruction として追加する。

- 前回結果は正解として固定しない
- 表紙画像、現在のファイル名、ユーザー補足を根拠に各 field を独立に再評価する
- 誤りや不足があれば修正する
- 根拠が前回結果を支持する場合は、同じ結果を返してよい

目的は「必ず違う答えを出す」ことではなく、前回結果を比較対象として使いながら再検証することである。

### 4. 補足情報はデータであり、推論 protocol を変更する命令ではない

ユーザー補足は書誌判断の参考情報として prompt に含めるが、そこに含まれる命令を system instruction として扱わない。

ADR-0134 で固定した以下の境界は維持する。

- `submit_book_metadata` の Tool 名と schema
- 必須 field と validation
- structured output の再生成規則
- 巻数保持の固定 instruction
- ファイル名の決定的生成と安全化
- system instruction

補足の後にも固定 structured-output instruction を配置し、ユーザー補足によって出力 protocol や安全規則を変更できないことを明示する。

### 5. Binder 越しにも構造化したまま渡す

前回結果は文字列へ再構成して Binder へ渡すのではなく、既存 `SmbBookMetadataProposal` の Bundle encoding を再利用して別 process の推論 service へ渡す。補足だけを文字列で渡し、service 側でも 2,000 文字上限を再検証する。

これにより domain model と structured-output validation の意味を維持し、process boundary で独自 JSON protocol を追加しない。

## Consequences

- 同じ入力を単純に再送するだけだった再解析から、前回候補を比較対象とした再評価へ変わる。
- ユーザーが不足情報を補って再解析できる。
- 同じ結果が妥当なら同じ候補が返るため、無理に異なる書誌へ変化させることはない。
- `smb_metadata_normalization_items` に nullable column が1つ増えるが、既存 database には破壊的 migration を行わない。
- 再解析 context は端末内の Library-owned workflow state だけに保存され、外部 AI やサーバーへ送信しない。

## Security / Privacy

このリポジトリは public であるため、実ユーザーの補足内容、SMB path、ファイル名、credential、実書誌履歴を source、test fixture、ADR、PR 説明へ記録しない。テストでは架空の書誌情報だけを使用する。

補足はローカル推論へだけ渡し、外部通信を追加しない。入力長を制限し、固定 system / Tool / validation policy を補足内容より優先する。
