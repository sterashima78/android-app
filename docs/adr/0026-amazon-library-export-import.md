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

## Decision

### Kindle

受け付ける入力を次のみに限定する。

- basename が `Digital.Content.Ownership*.json` に一致する JSON
- 上記 JSON を含む ZIP

単体 CSV / TSV や、ownership JSON を含まない ZIP にはフォールバックしない。

複数の ownership JSON に同じ ASIN の権利イベントが存在する場合は、イベント日時を優先し、日時が解釈できない場合は出現順を使って最新状態を決める。認識可能な最新イベントが Revoke / Return / Expire 相当なら現在所有していないものとして除外し、Grant / Purchase / Acquire 相当または明示的な失効がないものを蔵書候補とする。

ownership JSON に音楽・動画・Audible など明示的な非書籍 content type がある場合は Kindle 蔵書から除外する。ASIN、タイトル、著者などは同一 ownership データ内で想定されるキー名の揺れを許容するが、別形式のインポート互換性には利用しない。

`BookRelation.csv`、`SeriesRelation.csv`、`Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv` はシリーズに関係することが `FileDescriptions` から分かるが、列スキーマと書籍 ASIN との結合規則を実データでまだ検証できていない。そのため今回の変更では同期シリーズ情報の正規データとして使用せず、ADR-0017 のタイトル推定と手動設定を維持する。

### Audible

受け付ける入力を次のみに限定する。

- basename が `Library.csv` に一致する CSV
- `Library.csv` を含む ZIP

`Library.tsv` や任意名の CSV / TSV には対応しない。`Listening History.csv`、`Purchase History.csv`、`Wishlist.csv`、`Cart History.csv`、`Collections.csv` などはタイトル列を含んでいても蔵書として読まない。

`Library.csv` に削除状態を示す列がある場合、truthy な削除行は現在のライブラリから除外する。Author と Narrator は同一概念として結合しない。現在の `LibraryBook` は narrator 専用フィールドを持たないため、Narrator は今回の同期キャッシュには保存しない。

### 共通

ファイル選択 UI も source ごとに入力形式を分離する。Kindle は JSON / ZIP、Audible は CSV / ZIP を候補として表示する。

ZIP はストリームで読み、入力 25 MB、展開後合計 50 MB、100 エントリまでの安全制限を維持する。ZIP 内では対象 source の正規ファイルだけを展開し、無関係な CSV / JSON は読み込まない。

インポート結果が 0 件の場合は失敗として扱い、既存の対象 source の蔵書を置換しない。元ファイル、Amazon/Audible の認証情報、実ユーザーのエクスポート内容は永続化・ログ出力・テスト fixture への保存をしない。

テストでは実データをコピーせず、`FileDescriptions` で確認したファイル名と役割だけを使った人工データで次を検証する。

- Kindle ZIP で ownership JSON 以外を無視する
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
- 互換レイヤーを持たないため、入力判定とテストが単純になる
- 実ユーザーのエクスポートデータをパブリックリポジトリへ追加せずに回帰テストできる

### Negative

- Amazon / Audible がファイル名を変更した場合は追従が必要になる
- Kindle ownership JSON のキー名や権利イベント表現が変わった場合は解析規則を更新する必要がある
- `BookRelation.csv` / `SeriesRelation.csv` のシリーズ情報は今回まだ利用しない
- Audible の Narrator は専用 domain model を追加するまで保持されない

## Relationship to existing ADRs

- ADR-0013 のサービス非依存 `LibraryBook`、source ごとの置換、認証情報を保存しない方針は維持する
- ADR-0013 の Kindle / Audible の汎用ファイル探索・後方互換方針は本 ADR で置き換える
- ADR-0017 のシリーズ自動推定・手動上書き・除外状態は維持する
- ADR-0003 / ADR-0004 に従い、Amazon/Audible 固有解析は `feature:library:data` に閉じ込める
