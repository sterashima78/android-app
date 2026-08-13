# ADR-0031: Kindle インポートは ZIP 全階層と入れ子 ZIP を再帰探索する

- Status: Accepted
- Date: 2026-08-13
- Refines: ADR-0026

## Context

ADR-0026 では、Amazon エクスポート ZIP をストリームで走査し、`Digital.Content.Ownership*.json` だけを Kindle 蔵書の権利情報として解析する方針を定めた。

その後、実際の Amazon エクスポート ZIP を端末から選択しても ownership JSON を認識できないケースが継続した。通常の ZIP ディレクトリは `ZipInputStream` のエントリとして列挙されるため、単純なディレクトリ深度だけでは説明できない。また旧実装には、パスに `kindle` / `ebook` を含む ownership ファイルを1件でも見つけると、それ以外の ownership ファイルの候補を採用しない最適化があり、パス名と実データの役割が一致しないエクスポートでは有効な候補を捨てる可能性があった。

Amazon のエクスポート構造の差異に対して、ユーザーに ZIP の展開や対象ファイルの手動選別を要求しないことを優先する。

## Decision

Kindle ZIP の探索を次のようにする。

- 外側 ZIP の全エントリを先頭から走査し、ディレクトリ深度に依存せず `Digital.Content.Ownership*.json` を探す
- `Digital.Content.Ownership` を名前に持つディレクトリ配下の JSON も対象とする
- ZIP エントリ自体が `.zip` の場合は、その内容も再帰的に走査する
- 入れ子 ZIP は最大4階層までとする
- パスに `kindle` / `ebook` が含まれるかどうかで候補を優先・除外しない
- 見つかったすべての ownership JSON から候補を収集し、既存の Grant / Revoke 解決規則で現在所有している書籍を決める

安全上限は ZIP の階層をまたいで共有する。

- ownership JSON 1ファイルあたり最大 25 MB
- ownership JSON の展開サイズ合計は最大 256 MB
- 走査する ZIP エントリ数は合計最大 100,000 件
- 入れ子 ZIP は最大4階層

診断性のため、次の失敗を別メッセージにする。

- 全階層を走査しても ownership JSON が見つからない
- ownership JSON は見つかったが、現在のスキーマ解釈では Kindle 蔵書を1冊も解析できない

元の ZIP、ownership JSON の内容、ASIN、アカウント情報などをログ・永続化・公開テスト fixture へ追加しない。テストには人工データのみを使用する。

Audible のインポート方針は変更しない。

## Consequences

### Positive

- Amazon エクスポート ZIP のディレクトリ配置差異に依存しにくくなる
- ZIP が別 ZIP をラップしている構造でもユーザーによる手動展開を要求しない
- パス名による候補の誤った優先付けで有効な ownership 情報を捨てない
- 次回失敗時に「探索失敗」か「JSON スキーマ解析失敗」かを画面メッセージから判別できる

### Negative

- 入れ子 ZIP を走査する場合は処理時間が増える
- ZIP の構造を広く受け付けるため、安全上限の維持が必要になる
- ownership JSON の実スキーマが現在のキー解釈と異なる場合は、探索に成功しても追加のパーサー対応が必要になる

## Relationship to existing ADRs

- ADR-0026 の「ownership JSON だけを Kindle 蔵書の正規入力として扱う」方針は維持する
- ADR-0026 のストリーミング処理とサイズ上限を維持し、探索対象を入れ子 ZIP まで拡張する
- ADR-0003 / ADR-0004 に従い、Amazon 固有の探索・解析は `feature:library:data` に閉じ込める
