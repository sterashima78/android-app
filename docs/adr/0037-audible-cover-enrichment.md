# ADR-0037: Audible 表紙を公開商品ページから補完する

- Status: Accepted
- Date: 2026-08-13
- Refines: ADR-0006, ADR-0013, ADR-0036

## Context

Audible のエクスポートでは ASIN を取得できる一方、表紙 URL が欠落する場合がある。蔵書 UI は既存の `thumbnailUrl` と外部メタデータキャッシュを利用できる。

ADR-0036 は Kindle の表紙取得方法を定めている。今回の決定は Audible に限定し、Kindle の方針は変更しない。

## Decision

表紙がない Audible 項目は ASIN から公開商品ページ `https://www.audible.co.jp/pd/{ASIN}` を取得し、HTML の `og:image`、`twitter:image`、`twitter:image:src` から HTTPS の画像 URL を抽出する。

取得時に使用する書籍識別情報は ASIN のみとする。画像 URL は Amazon / Audible 系ホストに限定する。

処理は WorkManager で実行し、ネットワーク接続を制約とする。通信エラーは exponential backoff で再試行する。1 バッチは最大 5 件、商品ページ取得間隔は 1 秒以上とする。インポート処理自体は外部サイトの状態に依存させない。

結果は `library_item_external_metadata` に provider `AUDIBLE_PRODUCT_PAGE` として保存する。元データに表紙 URL がある場合は元データを優先する。未発見結果は 30 日後に再確認できるようにする。

蔵書画面の初期表示時と Audible の同期時刻更新時に一意 Work を enqueue するため、既にインポート済みの Audible 蔵書も再インポートなしで補完対象になる。

実ユーザーの ASIN、書名、エクスポート内容、取得 HTML をリポジトリのテストデータやログへ追加しない。テストには人工データだけを使用する。

## Consequences

- API 用の固定設定をアプリに持たずに表紙を補完できる
- 既存蔵書にも適用でき、取得結果は再インポート後も維持できる
- Audible の HTML 構造変更やアクセス制限の影響を受ける
- Audible 側には対象 ASIN の商品ページアクセスが観測可能になる

## Relationship to existing ADRs

- ADR-0006 のバックグラウンド処理方針に従う
- ADR-0013 のサービス非依存 `LibraryBook` を維持する
- ADR-0036 の外部メタデータキャッシュと人工テストデータ方針を再利用する
- ADR-0036 の Kindle に関する決定は変更しない
