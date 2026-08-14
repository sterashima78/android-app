# ADR-0037: Audible 表紙を商品ページと Catalog API から補完する

- Status: Accepted
- Date: 2026-08-13
- Amended: 2026-08-14
- Refines: ADR-0006, ADR-0013, ADR-0036

## Context

Audible のエクスポートでは ASIN を取得できる一方、表紙 URL が欠落する場合がある。蔵書 UI は既存の `thumbnailUrl` と外部メタデータキャッシュを利用できる。

当初は公開商品ページ `https://www.audible.co.jp/pd/{ASIN}` の `og:image` 等だけから補完していた。しかし、商品によってはページ自体は存在しても対象 meta タグがなく、表紙を取得できない。またインポート元に有効な 10 文字 ASIN がない場合は商品ページを直接参照できない。

Audible のアプリが利用する `api.audible.co.jp` には Catalog API が存在し、`media` response group の `product_images` から商品画像を取得できる。ただしこの API は公開・安定版として文書化された API ではなく、仕様変更の可能性がある。

ADR-0036 は Kindle の表紙取得方法を定めている。今回の決定は Audible に限定し、Kindle の方針は変更しない。

## Decision

表紙がない Audible 項目は次の順序で補完する。

1. ASIN が有効な場合、公開商品ページ `https://www.audible.co.jp/pd/{ASIN}` を取得し、HTML の `og:image`、`twitter:image`、`twitter:image:src` を確認する
2. 商品ページで取得できない場合、`https://api.audible.co.jp/1.0/catalog/products/{ASIN}` を `media,product_desc,contributors` response groups 付きで参照する
3. ASIN がない、または ASIN 指定の Catalog API でも表紙が得られない場合、Catalog API を書名と先頭著者で検索する

Catalog API の検索結果は API の順位だけでは採用しない。アプリ側で NFKC・小文字化・英数字/文字のみへの正規化を行い、書名が完全一致することを必須とする。蔵書側に著者がある場合は、候補側の著者にも正規化後の完全一致が少なくとも1件必要とする。条件を満たす表紙付き候補が複数ある場合は `AMBIGUOUS` とし、表紙を設定しない。

Catalog API からは複数サイズの `product_images` のうち最も大きい信頼可能な画像を採用する。商品ページと Catalog API のどちらでも画像 URL は HTTPS に限定し、Amazon / Audible 系の既知ホストだけを許可する。

Catalog API の接続先は `api.audible.co.jp` に固定し、認証情報、アクセストークン、Cookie、ユーザー固有の API 設定はリポジトリにもアプリにも追加しない。レスポンスが別ホストへリダイレクトされた場合は失敗とする。

商品ページの通信失敗や HTML 構造変更だけでは処理を停止せず Catalog API を試す。Catalog API 自体の通信・解析に失敗し、代替経路でも確定結果を得られない場合は `IOException` として WorkManager の exponential backoff に委ねる。

処理は WorkManager で実行し、ネットワーク接続を制約とする。1 バッチは最大 5 件、書籍ごとの処理間隔は 1 秒以上とする。インポート処理自体は外部サイトの状態に依存させない。

結果は `library_item_external_metadata` に保存し、取得経路を provider で区別する。

- `AUDIBLE_PRODUCT_PAGE`: 公開商品ページ
- `AUDIBLE_CATALOG_API_ASIN`: Catalog API の ASIN 指定
- `AUDIBLE_CATALOG_API_SEARCH`: Catalog API の書名・著者検索

元データに表紙 URL がある場合は元データを優先する。`NOT_FOUND` / `AMBIGUOUS` は 30 日後に再確認できるようにする。

蔵書画面の初期表示時と Audible の同期時刻更新時に一意 Work を enqueue するため、既にインポート済みの Audible 蔵書も再インポートなしで補完対象になる。

Catalog API の書名検索を使う場合、対象の書名と先頭著者が Audible 側へ送信される。これは Audible の商品検索に必要な最小限の情報とし、それ以外の蔵書一覧やユーザー情報は送信しない。

実ユーザーの ASIN、書名、著者、エクスポート内容、取得 HTML / JSON をリポジトリのテストデータやログへ追加しない。テストには人工データだけを使用する。

## Consequences

### Positive

- 商品ページに画像 meta タグがない Audible 書籍でも表紙を補完できる
- 有効な ASIN がない項目も書名・著者から補完できる可能性がある
- 取得経路を既存の表紙取得状況画面から識別できる
- API キーやユーザー認証情報を公開リポジトリへ持ち込まない

### Negative

- Audible の非公開内部 API に依存するため、予告なく仕様変更・停止される可能性がある
- 書名検索時は書名と先頭著者が Audible 側に送信される
- 厳格一致を採用するため、表記揺れが大きい商品は安全側に倒して `NOT_FOUND` になる可能性がある
- 商品ページに加えて Catalog API を利用するため、最悪時の HTTP リクエスト数が増える

## Relationship to existing ADRs

- ADR-0006 のバックグラウンド処理方針に従う
- ADR-0013 のサービス非依存 `LibraryBook` を維持する
- ADR-0036 の外部メタデータキャッシュと人工テストデータ方針を再利用する
- ADR-0051 の表紙取得状況画面では provider の違いを取得経路として表示できる
- ADR-0036 の Kindle に関する決定は変更しない
