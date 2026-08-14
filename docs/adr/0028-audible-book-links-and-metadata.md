# ADR-0028: Audible 蔵書は ASIN から商品ページを開き、表示用メタデータを保持する

- Status: Accepted
- Date: 2026-08-13
- Amended: 2026-08-14
- Amended by: ADR-0058, ADR-0059
- Supersedes: ADR-0026 の「Narrator は同期キャッシュに保存しない」という判断

## Context

ADR-0026 では Audible の `Library.csv` を蔵書の正規入力とし、Author と Narrator を同一概念として結合しない方針を採用した。一方、当時の `LibraryBook` に Narrator 専用フィールドがなかったため、Narrator は同期キャッシュへ保存しないことにしていた。

蔵書画面では書籍カードのタップ時に `infoUrl` がある場合だけ外部ページを開いている。Audible の蔵書データでは ASIN を取得できる一方、商品 URL が常に含まれるとは限らないため、ASIN があってもタップ操作ができない場合がある。

Audible の書籍では著者に加えてナレーターと再生時間が識別・選択に有用であり、これらは Author と混同せず専用属性として保持する必要がある。

2026-08-14 に ADR-0058 で Audible Web Library JSON を正規入力へ変更した。商品 URL、Narrator、Duration の入力元は旧 `Library.csv` ではなく Web Library JSON となるが、本 ADR のドメインモデルと表示方針は維持する。

## Decision

### Audible 商品ページ

Audible 書籍は次の優先順位で開く URL を決める。

1. Web Library JSON から取得済みの明示的な商品 URL
2. 10 文字の英数字 ASIN がある場合は `https://www.audible.co.jp/pd/{ASIN}`
3. ASIN を取得できない項目は URL を推測しない

UI からは HTTPS URL を Android の通常の URI オープン処理へ渡す。Audible アプリがその Audible.co.jp App Link を処理できる端末では Audible アプリの商品ページが開き、処理できない場合は Web ブラウザへフォールバックできる構成とする。特定バージョンの非公開カスタム URI scheme には依存しない。

### 表示用メタデータ

`LibraryBook` は次を保持する。

- `narrators: List<String>`
- `duration: String?`

Audible Web Library JSON から Narrator / Duration を読み取り、Author とは別々に保持する。

蔵書カードでは Audible の場合だけ、ナレーター、再生時間、配信日を補助情報として表示する。情報が存在しない項目は表示しない。

### 永続化

`library_items` は次の列を現行スキーマの一部として持つ。

- `narrators TEXT NOT NULL DEFAULT '[]'`
- `duration TEXT`

本 ADR 導入時は既存インストール向けに `PRAGMA table_info` で列の有無を確認し、不足列を `ALTER TABLE ... ADD COLUMN` する起動時互換処理を持っていた。

ADR-0059 により database version 12 を更新互換性のベースラインとしたため、この列追加を必要とする旧 DB からの直接更新はサポート対象外とする。現行スキーマには両列が常設されており、Repository 起動時の列存在確認と `ALTER TABLE` fallback は削除する。

### データ保護

元の Audible データ、認証情報、実ユーザーのタイトル・ASIN・視聴履歴等をリポジトリへ保存しない。テストは人工的なタイトル、ASIN、Narrator、Duration だけを使用する。

## Consequences

### Positive

- Audible 蔵書をタップして商品詳細へ直接移動できる
- URL が欠けていても ASIN があれば商品ページを解決できる
- Author と Narrator の意味を混同せず、Audible に必要な表示情報を保持できる
- 現行 DB スキーマだけを前提にでき、Repository から完了済み列移行コードを除去できる

### Negative

- Audible.co.jp の商品 URL 形式や App Link の扱いが将来変わった場合は追従が必要になる
- Duration の書式は入力値をそのまま保持するため、表示形式は Web Library JSON の生成値に依存する
- ASIN が取得できない項目では商品ページを自動推測できない
- database version 12 より前の蔵書 DB から最新版への直接更新は保証しない

## Relationship to existing ADRs

- ADR-0058 により Audible の正規入力は Web Library JSON へ変更する
- ADR-0059 により database version 12 より前の列追加 fallback を廃止する
- ADR-0013 のサービス非依存 `LibraryBook` を維持し、Audible 固有の入力解釈は `feature:library:data` に閉じ込める
- ADR-0003 / ADR-0004 の ui / domain / data の責務境界を維持する
