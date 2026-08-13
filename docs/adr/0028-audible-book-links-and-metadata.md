# ADR-0028: Audible 蔵書は ASIN から商品ページを開き、表示用メタデータを保持する

- Status: Accepted
- Date: 2026-08-13
- Supersedes: ADR-0026 の「Narrator は同期キャッシュに保存しない」という判断

## Context

ADR-0026 では Audible の `Library.csv` を蔵書の正規入力とし、Author と Narrator を同一概念として結合しない方針を採用した。一方、当時の `LibraryBook` に Narrator 専用フィールドがなかったため、Narrator は同期キャッシュへ保存しないことにしていた。

蔵書画面では書籍カードのタップ時に `infoUrl` がある場合だけ外部ページを開いている。Audible の `Library.csv` では ASIN を取得できる一方、商品 URL が常に含まれるとは限らないため、ASIN があってもタップ操作ができない場合がある。

Audible の書籍では著者に加えてナレーターと再生時間が識別・選択に有用であり、これらは Author と混同せず専用属性として保持する必要がある。

## Decision

### Audible 商品ページ

Audible 書籍は次の優先順位で開く URL を決める。

1. `Library.csv` などから取得済みの明示的な商品 URL
2. 10 文字の英数字 ASIN がある場合は `https://www.audible.co.jp/pd/{ASIN}`
3. ASIN を取得できず `derived:` ID になった項目は URL を推測しない

UI からは HTTPS URL を Android の通常の URI オープン処理へ渡す。Audible アプリがその Audible.co.jp App Link を処理できる端末では Audible アプリの商品ページが開き、処理できない場合は Web ブラウザへフォールバックできる構成とする。特定バージョンの非公開カスタム URI scheme には依存しない。

### 表示用メタデータ

`LibraryBook` に次を追加する。

- `narrators: List<String>`
- `duration: String?`

Audible の `Library.csv` だけから Narrator / Duration 系の列を読み取り、Author とは別々に保持する。列名には `Narrator` / `Narrated By`、`Duration` / `Listening Length` / `Running Time` など既知の表記揺れを許容する。

蔵書カードでは Audible の場合だけ、ナレーター、再生時間、配信日を補助情報として表示する。情報が存在しない項目は表示しない。

### 永続化と移行

`library_items` に次の列を追加する。

- `narrators TEXT NOT NULL DEFAULT '[]'`
- `duration TEXT`

既存インストールでは `PRAGMA table_info` で列の有無を確認し、不足している列だけ `ALTER TABLE ... ADD COLUMN` する。既存データは Narrator 空配列、Duration null として扱う。

### データ保護

ADR-0026 の方針を維持し、元の Audible エクスポートファイル、認証情報、実ユーザーのタイトル・ASIN・視聴履歴等をリポジトリへ保存しない。テストは人工的なタイトル、ASIN、Narrator、Duration だけを使用する。

## Consequences

### Positive

- Audible 蔵書をタップして商品詳細へ直接移動できる
- URL 列がないエクスポートでも ASIN があれば商品ページを解決できる
- Author と Narrator の意味を混同せず、Audible に必要な表示情報を保持できる
- 既存の蔵書 DB を破棄せず列追加だけで移行できる

### Negative

- Audible.co.jp の商品 URL 形式や App Link の扱いが将来変わった場合は追従が必要になる
- Duration の書式はエクスポート値をそのまま保持するため、表示形式は入力データに依存する
- ASIN が取得できない項目では商品ページを自動推測できない

## Relationship to existing ADRs

- ADR-0026 の `Library.csv` のみを Audible 蔵書として読む方針、Author と Narrator を結合しない方針、実ユーザーデータを保存しない方針は維持する
- ADR-0026 の Narrator を同期キャッシュへ保存しない判断だけを本 ADR で置き換える
- ADR-0013 のサービス非依存 `LibraryBook` を維持し、Audible 固有の CSV 解釈は `feature:library:data` に閉じ込める
- ADR-0003 / ADR-0004 の ui / domain / data の責務境界を維持する
