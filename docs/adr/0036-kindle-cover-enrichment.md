# ADR-0036: Kindle 表紙を Amazon 商品ページと Open Library から補完する

- Status: Accepted
- Date: 2026-08-13
- Amended: 2026-08-14
- Refines: ADR-0013, ADR-0026, ADR-0033

## Context

Kindle の `Digital.Content.Ownership*.json` からは ASIN と商品名を取得できる一方、観測済みデータでは表紙 URL や ISBN が常に得られるわけではない。蔵書 UI は `LibraryBook.thumbnailUrl` をすでに表示できるため、欠落している表紙だけを外部カタログから補完したい。

当初は Amazon 商品ページの解析を避け、Open Library の Search API と Covers API だけを利用していた。しかし Kindle 専売本や日本語コミックなど Open Library に登録されていない商品が多く、ASIN を取得できているにもかかわらず `NOT_FOUND` となる割合が高い。

Amazon の公式商品 API は認証用 secret を必要とし、公開リポジトリや APK に埋め込めない。一方、公開商品ページは ASIN から直接参照でき、商品画像が OGP や商品画像要素として含まれる場合がある。HTML 構造は公開 API ではなく変更され得るため、Amazon 商品ページだけには依存しない。

Open Library は認証不要の公開 Search API と Covers API を提供する。ただし検索時にはタイトル、著者、ISBN などの書誌情報が外部サービスへ送信されるため、Kindle インポートの端末内処理とは明確に分離する必要がある。また未識別 API リクエストには 1 request/second の制限があり、ISBN 等による Covers API 画像取得は CoverID による取得より厳しい制限を受ける。

## Decision

Kindle 表紙補完は初期状態を無効とし、設定画面でユーザーが明示的に有効化した場合だけ外部通信を行う。

表紙がない Kindle 項目は次の順序で補完する。

1. 有効な 10 文字 ASIN がある場合、`https://www.amazon.co.jp/dp/{ASIN}` の公開商品ページを取得する
2. 商品ページの `og:image`、`twitter:image`、`twitter:image:src` を確認する
3. OGP 等で得られない場合、商品画像要素 `landingImage`、`imgBlkFront`、`ebooksImgBlkFront` の高解像度属性または動的画像情報を確認する
4. Amazon 商品ページで取得できない、ASIN がない、または通信・HTML 解析に失敗した場合は Open Library にフォールバックする
5. Open Library では ISBN-13、ISBN-10、タイトル＋著者の順で検索する

Amazon 商品ページは HTTPS の `amazon.co.jp` 配下かつ要求した ASIN を含む商品パスへの最終 URL だけを受け入れる。画像 URL も HTTPS に限定し、Amazon の既知画像ホストだけを許可する。Amazon の Cookie、セッション、認証情報、非公開 API、推測した画像 URL は利用しない。

Amazon 商品ページの通信失敗や HTML 構造変更だけでは Worker を失敗させず Open Library を試す。Open Library も通信・解析に失敗した場合は `IOException` として WorkManager の exponential backoff に委ねる。インポートの成否は外部サービスの状態に依存させない。

通常の複数冊処理では backoff を利用しない。1回の Worker は1冊だけを処理し、続きがある場合は次の Worker を unique work chain に追加する。継続 Worker には 1.1 秒の初期待機を設定し、Open Library の検索が 1 request/second を超えないようにする。最初の1冊は追加待機なしで開始できる。Worker のキャンセルは失敗へ変換せず、そのままキャンセルとして伝播させる。

検索結果に含まれる CoverID を保存 URL に利用し、ISBN 等による Covers API の追加レート制限を避ける。ユーザー個人の連絡先を User-Agent に埋め込むことはしない。

Open Library のマッチングは誤表紙を避けることを優先する。

- ISBN-13 があれば最優先する
- 次に ISBN-10 を使う
- ISBN がなければタイトルと著者で検索する
- タイトルは Unicode NFKC、大小文字、句読点・空白差を正規化して比較する
- 明示的な巻数表現がある場合は巻数一致を必須とする
- 著者情報がある場合は著者一致を必須とする
- 高信頼候補が1件に定まらない場合は表紙を採用しない

補完結果は `library_items` とは別の `library_item_external_metadata` に保存する。取得経路は provider で区別する。

- `AMAZON_PRODUCT_PAGE_OGP`: Amazon 商品ページの OGP / Twitter Card
- `AMAZON_PRODUCT_PAGE_IMAGE`: Amazon 商品ページの商品画像要素
- `OPEN_LIBRARY`: Open Library

これにより source 単位の再インポートで取得済み表紙を失わず、元データに `thumbnail_url` が存在する場合は常にそちらを優先できる。`FOUND`、`NOT_FOUND`、`AMBIGUOUS` を保存し、未発見・曖昧結果は一定期間後に再確認できるようにする。

設定を無効化した場合は新しい Amazon / Open Library 問い合わせを停止する。すでに取得済みの表紙 URL はローカルキャッシュとして表示を継続する。

Amazon 商品ページへの問い合わせでは ASIN だけが送信される。Open Library へフォールバックする場合は検索に必要な書誌情報だけを送信する。実ユーザーの ASIN、タイトル、著者、ISBN、Amazon エクスポート内容、取得 HTML はパブリックリポジトリの fixture・ログ・ADR に保存しない。テストは人工データだけを使用する。

## Consequences

### Positive

- Open Library に未登録の Kindle 専売本や日本語書籍でも、ASIN から表紙を取得できる可能性が上がる
- Amazon 商品ページで取得できれば書名や著者を Open Library へ送信せずに済む
- Amazon API secret や自前バックエンドなしで Kindle の表紙欠落を補完できる
- Kindle インポートは引き続き端末内で完結し、外部障害の影響を受けない
- 再インポート後も取得済み表紙を維持できる
- 同名書籍や別巻の誤表紙を保守的に回避できる
- 公開リポジトリにユーザーデータや秘密情報を追加しない
- 通常の複数冊処理を WorkManager の失敗・backoff と分離できる
- Open Library への検索間隔を明示的に確保できる

### Negative

- Amazon 商品ページの HTML は公開 API ではなく、構造変更やアクセス制限により取得できなくなる可能性がある
- 商品ページ取得の HTTP リクエストが追加される
- Open Library にフォールバックした場合は従来と同じ未発見・曖昧判定が残る
- タイトルしかない Kindle データでは曖昧判定となり表紙を設定できない場合がある
- Open Library の検索・応答仕様やレート制限変更には追従が必要になる
- 有効化したユーザーの ASIN は Amazon へ、フォールバック時の書誌情報は Open Library へ送信される
- 1冊ごとに1.1秒以上空けるため、大量蔵書の初回補完には一定の時間がかかる

## Relationship to existing ADRs

- ADR-0013 のサービス非依存 `LibraryBook` と Kindle 所有情報に外部 API を使わない原則を維持し、表示メタデータ補完だけを例外として明示する
- ADR-0026 / ADR-0033 の ownership JSON 解析、実ユーザーデータを fixture に保存しない方針を維持する
- ADR-0006 の再開可能なバックグラウンド処理方針に従い WorkManager を利用する
- ADR-0037 の Audible 商品ページ補完と同様に、公開商品ページは失敗可能なフォールバック可能経路として扱う
