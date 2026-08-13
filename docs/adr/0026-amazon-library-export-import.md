# ADR-0026: Kindle / Audible は実エクスポートの蔵書ファイルを明示選択して取り込む

- Status: Accepted
- Date: 2026-08-12
- Amended: 2026-08-13
- Supersedes: ADR-0013 の Kindle / Audible ファイルインポートに関する汎用ファイル探索方針

## Context

ADR-0013 では、Amazon 側のエクスポート形式を固定スキーマとみなさず、CSV / TSV / ZIP 内のファイルを一般的な英語ヘッダー名で探索する方針を採用した。

その後、Amazon Kindle と Audible が実際に生成する `FileDescriptions` を確認したところ、この前提では蔵書以外のデータを取り込む可能性があることが分かった。

Kindle のエクスポートには、蔵書の権利情報を表す `Digital.Content.Ownership.json` とともに、`Kindle.Devices.ReadingSession.csv`、`Kindle.Devices.*`、`BookRelation.csv` など多数の行動・利用・関係データが存在する。`Digital.Content.Ownership` は Grant / Revoke のような権利イベントが複数ファイルに分かれる場合がある。

Audible のエクスポートには `Library.csv` のほか、`Listening History.csv`、`Purchase History.csv`、`Wishlist.csv`、`Collections.csv` などタイトルを含み得るファイルが存在する。タイトル列の有無だけで判定すると、再生履歴や Wishlist を蔵書として誤認できてしまう。

このインポート機能はまだ利用開始前であり、旧形式との後方互換性を維持する必要はない。実エクスポート形式に合わせて入力を限定し、曖昧なフォールバックを持たない方が誤取り込みを防ぎやすい。

2026-08-13 に、Amazon から取得したエクスポート ZIP が 25 MB を大きく超え、かつ ZIP 内に多数の JSON / CSV が含まれるケースを確認した。ZIP 全体を `ByteArray` に読み込む従来方式では、対象の ownership JSON が小さくても外側の ZIP サイズだけでインポートできない。また ZIP 全体のエントリ数を 100 件に制限すると、対象外ファイルが多いだけで正規の Kindle ownership JSON まで到達できない。

## Decision

### Kindle

受け付ける入力を次のみに限定する。

- basename が `Digital.Content.Ownership*.json` に一致する JSON
- 上記 JSON を含む ZIP

単体 CSV / TSV や、ownership JSON を含まない ZIP にはフォールバックしない。

複数の ownership JSON に同じ ASIN の権利イベントが存在する場合は、イベント日時を優先し、日時が解釈できない場合は出現順を使って最新状態を決める。認識可能な最新イベントが Revoke / Return / Expire 相当なら現在所有していないものとして除外し、Grant / Purchase / Acquire 相当または明示的な失効がないものを蔵書候補とする。

ownership JSON に音楽・動画・Audible など明示的な非書籍 content type がある場合は Kindle 蔵書から除外する。ASIN、タイトル、著者などは同一 ownership データ内で想定されるキー名の揺れを許容するが、別形式のインポート互換性には利用しない。

`BookRelation.csv`、`SeriesRelation.csv`、`Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv` はシリーズに関係することが `FileDescriptions` から分かるが、列スキーマと書籍 ASIN との結合規則を実データでまだ検証できていない。そのため今回の変更では同期シリーズ情報の正規データとして使用せず、ADR-0017 のタイトル推定と手動設定を維持する。

Kindle の ZIP は Android `ContentResolver` が返す `InputStream` を `library:data` まで渡し、外側の ZIP 全体をメモリへ読み込まない。ZIP を先頭から走査し、`Digital.Content.Ownership*.json` に一致するエントリだけを1ファイルずつメモリへ読み込んで解析し、解析後のバイト列は保持しない。無関係な JSON / CSV の内容は保持しない。

外側の Kindle ZIP 自体には 25 MB の入力サイズ上限を設けない。一方、端末内処理に対する安全境界として次を維持する。

- ownership JSON 1ファイルあたり最大 25 MB
- ownership JSON の展開サイズ合計は最大 256 MB
- ZIP の走査対象エントリ数は最大 100,000 件

単体の `Digital.Content.Ownership*.json` は従来どおり 25 MB を上限とする。

### Audible

受け付ける入力を次のみに限定する。

- basename が `Library.csv` に一致する CSV
- `Library.csv` を含む ZIP

`Library.tsv` や任意名の CSV / TSV には対応しない。`Listening History.csv`、`Purchase History.csv`、`Wishlist.csv`、`Cart History.csv`、`Collections.csv` などはタイトル列を含んでいても蔵書として読まない。

`Library.csv` に削除状態を示す列がある場合、truthy な削除行は現在のライブラリから除外する。Author と Narrator は同一概念として結合しない。Narrator / Duration の保持と Audible 商品ページ URL の補完は ADR-0028 に従う。

Audible は現状のデータ量で問題が確認されていないため、入力全体 25 MB、ZIP 展開後合計 50 MB、ZIP 100 エントリの制限を維持する。

### 共通

ファイル選択 UI も source ごとに入力形式を分離する。Kindle は JSON / ZIP、Audible は CSV / ZIP を候補として表示する。

UI / ViewModel は選択ファイル全体を `ByteArray` 化しない。`OpenDocument` で取得した URI から必要になった時点で `InputStream` を開き、repository が source ごとの読み込み戦略を決める。これにより Kindle の大容量 ZIP に対する制約を UI 層へ持ち込まず、Amazon 固有の解析・安全制限を `library:data` に閉じ込める。

インポート結果が 0 件の場合は失敗として扱い、既存の対象 source の蔵書を置換しない。元ファイル、Amazon/Audible の認証情報、実ユーザーのエクスポート内容は永続化・ログ出力・テスト fixture への保存をしない。

テストでは実データをコピーせず、`FileDescriptions` で確認したファイル名と役割だけを使った人工データで次を検証する。

- Kindle ZIP で ownership JSON 以外を無視する
- 100 件を超える対象外エントリがあっても後続の ownership JSON を処理できる
- 複数 ownership JSON にまたがる Grant / Revoke を解決する
- ownership JSON に混在する明示的な非書籍 content type を除外する
- Kindle の旧 CSV を拒否する
- Audible ZIP で `Library.csv` だけを読む
- `Listening History.csv` / `Purchase History.csv` / `Wishlist.csv` を蔵書へ混入させない
- Audible の `Library.tsv` を拒否する

## Consequences

### Positive

- 実際の Amazon / Audible エクスポートのファイル役割に沿って蔵書を取り込める
- Kindle の行動ログや Audible の Wishlist / 履歴が蔵書へ混入しない
- Kindle の Grant / Revoke が別ファイルでも現在の所有状態を解決できる
- Amazon の ZIP 全体が大きくても、ZIP サイズだけを理由に Kindle インポートが失敗しない
- 多数の対象外ファイルを含むエクスポートでも ownership JSON まで走査できる
- 外側の ZIP 全体をメモリへ保持しないため、ファイルサイズに比例したヒープ消費を避けられる
- 互換レイヤーを持たないため、入力判定とテストが単純になる
- 実ユーザーのエクスポートデータをパブリックリポジトリへ追加せずに回帰テストできる

### Negative

- Amazon / Audible がファイル名を変更した場合は追従が必要になる
- Kindle ownership JSON のキー名や権利イベント表現が変わった場合は解析規則を更新する必要がある
- 大きな Kindle ZIP は逐次走査するため、対象ファイルが後方にあるほどインポート時間が長くなる
- ZIP bomb や異常なエクスポートに対する完全な無制限処理は行わず、ownership JSON のサイズとエントリ数には上限が残る
- `BookRelation.csv` / `SeriesRelation.csv` のシリーズ情報は今回まだ利用しない

## Relationship to existing ADRs

- ADR-0013 のサービス非依存 `LibraryBook`、source ごとの置換、認証情報を保存しない方針は維持する
- ADR-0013 の Kindle / Audible の汎用ファイル探索・後方互換方針は本 ADR で置き換える
- ADR-0017 のシリーズ自動推定・手動上書き・除外状態は維持する
- ADR-0028 が Audible の Narrator / Duration を保存しないという本 ADR の初期判断を置き換える
- ADR-0003 / ADR-0004 に従い、Amazon/Audible 固有解析とファイルサイズ制限は `feature:library:data` に閉じ込める
