# ADR-0026: Kindle / Audible は実エクスポートの蔵書ファイルを明示選択して取り込む

- Status: Accepted
- Date: 2026-08-12
- Supersedes: ADR-0013 の Kindle / Audible ファイルインポートに関する「複数ファイル ZIP を汎用ヘッダーで探索する」部分

## Context

ADR-0013 では、Amazon 側のエクスポート形式を固定スキーマとみなさず、CSV / TSV / ZIP 内のファイルを一般的な英語ヘッダー名で探索する方針を採用した。

その後、Amazon Kindle と Audible が実際に生成する `FileDescriptions` を確認したところ、この前提では蔵書以外のデータを取り込む可能性があることが分かった。

Kindle のエクスポートには、蔵書の権利情報を表す `Digital.Content.Ownership.json` とともに、`Kindle.Devices.ReadingSession.csv`、`Kindle.Devices.*`、`BookRelation.csv` など多数の行動・利用・関係データが存在する。`Digital.Content.Ownership` は Grant / Revoke のような権利イベントが複数ファイルに分かれる場合がある。

Audible のエクスポートには `Library.csv` のほか、`Listening History.csv`、`Purchase History.csv`、`Wishlist.csv`、`Collections.csv` などタイトルを含み得るファイルが存在する。タイトル列の有無だけで判定すると、再生履歴や Wishlist を蔵書として誤認できてしまう。

外部で公開されている 2026 年の Kindle データエクスポート調査でも、`Digital.Content.Ownership` がタイトル、ASIN、購入時期を含むライブラリ相当データとして確認されている。実際のユーザーデータそのものはリポジトリへ保存しない。

## Decision

### Kindle

Amazon のエクスポート ZIP では、basename が `Digital.Content.Ownership*.json` に一致するファイルだけを蔵書候補として読む。

複数の ownership JSON に同じ ASIN の権利イベントが存在する場合は、イベント日時を優先し、日時が解釈できない場合は出現順を使って最新状態を決める。認識可能な最新イベントが Revoke / Return / Expire 相当なら現在所有していないものとして除外し、Grant / Purchase / Acquire 相当または明示的な失効がないものを蔵書候補とする。

ownership JSON に音楽・動画・Audible など明示的な非書籍 content type がある場合は Kindle 蔵書から除外する。ASIN、タイトル、著者などは一般的なキー名の別名を許容するが、購入日を出版日として扱わない。

旧実装との互換性のため、ユーザーが単一の CSV / TSV を明示選択した場合だけ従来の汎用ヘッダー解析を残す。複数ファイル ZIP では ownership JSON が無い場合に `Kindle.Devices.*` 等へフォールバックしない。

`BookRelation.csv`、`SeriesRelation.csv`、`Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv` はシリーズに関係することが `FileDescriptions` から分かるが、列スキーマと書籍 ASIN との結合規則を実データでまだ検証できていない。そのため今回の変更では同期シリーズ情報の正規データとして使用せず、ADR-0017 のタイトル推定と手動設定を維持する。実ファイルの匿名化された列構造を確認できた時点で別途判断する。

### Audible

Audible のエクスポート ZIP では basename が `Library.csv`（互換目的で `Library.tsv` も許容）に一致するファイルだけを蔵書として読む。

`Listening History.csv`、`Purchase History.csv`、`Wishlist.csv`、`Cart History.csv`、`Collections.csv` など `FileDescriptions` 上で蔵書そのものではないことが確認できるファイルは、タイトル列を含んでいても蔵書として読まない。これら既知の非蔵書ファイルを単体選択した場合も拒否する。

`Library.csv` に削除状態を示す一般的な列がある場合、truthy な削除行は現在のライブラリから除外する。Author と Narrator は同一概念として結合しない。現在の `LibraryBook` は narrator 専用フィールドを持たないため、Narrator は今回の同期キャッシュには保存しない。

### 共通

ZIP は引き続きストリームで読み、入力 25 MB、展開後合計 50 MB、100 エントリまでの安全制限を維持する。Kindle 対応のため ZIP 内の `.json` も候補として読み込む。

インポート結果が 0 件の場合は失敗として扱い、既存の対象 source の蔵書を置換しない。元ファイル、Amazon/Audible の認証情報、実ユーザーのエクスポート内容は永続化・ログ出力・テスト fixture への保存をしない。

テストでは実データをコピーせず、`FileDescriptions` で確認したファイル名と役割だけを使った人工データで次を検証する。

- Kindle ZIP で ownership JSON 以外を無視する
- 複数 ownership JSON にまたがる Grant / Revoke を解決する
- ownership JSON に混在する明示的な非書籍 content type を除外する
- Audible ZIP で `Library.csv` だけを読む
- `Listening History.csv` / `Purchase History.csv` / `Wishlist.csv` を蔵書へ混入させない
- 旧単一 CSV / TSV インポートの互換性を維持する

## Consequences

### Positive

- 実際の Amazon / Audible エクスポートのファイル役割に沿って蔵書を取り込める
- Kindle の行動ログや Audible の Wishlist / 履歴が蔵書へ混入しない
- Kindle の Grant / Revoke が別ファイルでも現在の所有状態を解決できる
- 実ユーザーのエクスポートデータをパブリックリポジトリへ追加せずに回帰テストできる
- 旧単一 CSV / TSV 利用者の互換性を残せる

### Negative

- Amazon / Audible がファイル名を変更した場合は追従が必要になる
- Kindle ownership JSON のキー名や権利イベント表現が変わった場合は別名・状態判定を更新する必要がある
- `BookRelation.csv` / `SeriesRelation.csv` のシリーズ情報は今回まだ利用しない
- Audible の Narrator は専用 domain model を追加するまで保持されない

## Relationship to existing ADRs

- ADR-0013 のサービス非依存 `LibraryBook`、source ごとの置換、認証情報を保存しない方針は維持する
- ADR-0013 の Kindle / Audible ZIP を汎用的に探索する判断のみ本 ADR で置き換える
- ADR-0017 のシリーズ自動推定・手動上書き・除外状態は維持する
- ADR-0003 / ADR-0004 に従い、Amazon/Audible 固有解析は `feature:library:data` に閉じ込める
