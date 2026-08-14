# ADR-0052: Kindle 表紙補完に Google Books と構造化診断を追加する

- Status: Accepted
- Date: 2026-08-14
- Refines: ADR-0036, ADR-0041

## Context

ADR-0036 では Kindle の表紙補完を Amazon 商品ページから開始し、取得できない場合に Open Library へフォールバックする方針を定めた。ADR-0041 では失敗時に実エラーを保存して調査可能にした。

実際の未取得ログでは、有効な ASIN を持つ日本語 Kindle 書籍でも最終結果が `OPEN_LIBRARY / NOT_FOUND` となる例が複数あった。この状態では Amazon 商品ページが HTTP 200 だったが既知の画像要素を検出できなかったのか、アクセス確認やリダイレクトが発生したのか、Open Library の候補をタイトル・著者・巻数のどの条件で除外したのかを区別できない。

また Kindle の商品名には `(Japanese Edition)`、`(Kindle Edition)`、`(English Edition)` のような版情報が付く場合がある。これは表示タイトルとしては保持すべきだが、書誌検索では外部データベース側のタイトルに含まれず、同一書籍を完全一致できない要因になる。

Google Books API の公開 Volume 検索は認証なしで利用でき、`isbn:`、`intitle:`、`inauthor:` による検索を提供している。既存アプリも Google Play Books の蔵書取得で Google Books の Volume レスポンスを扱っているため、Kindle 表紙補完の追加フォールバックとして利用できる。

## Decision

Kindle 表紙補完の取得順序を次のようにする。

1. Amazon 商品ページを ASIN で取得する
2. Amazon で確定できなければ Google Books を検索する
3. Google Books で確定できなければ Open Library を検索する
4. いずれでも高信頼候補が一件に定まらない場合だけ未取得とする

Google Books と Open Library は ISBN-13、ISBN-10 があれば ISBN 検索を優先する。ISBN がない場合はタイトルと著者で検索し、タイトル、明示的な巻数、著者の順に候補を絞る。複数の高信頼候補が残る場合は誤表紙を避けるため `AMBIGUOUS` とする。

検索・照合時だけ、タイトル末尾の `(Japanese Edition)`、`(Kindle Edition)`、`(English Edition)` と全角括弧相当を除外する。元の `LibraryBook.title` は変更しない。一般的な括弧書きは意味を持つ可能性があるため削除しない。

Amazon 商品ページでは従来の OGP / Twitter Card と既知の商品画像要素に加え、`application/ld+json` の `image` と `<link rel="image_src">` も候補にする。採用する画像 URL は従来どおり HTTPS、既知の Amazon 画像ホスト、`/images/I/` パスの条件を満たすものだけに限定する。

各書籍の直近一回の取得処理について、`library_item_external_metadata.diagnostic_trace` に version 付き JSON を最大 8,192 文字で保存する。履歴を追加し続けるのではなく、再試行時は直近の結果で置き換える。

診断ステップには次のような構造化情報だけを保存する。

- provider
- `FOUND` / `NOT_FOUND` / `AMBIGUOUS` / `ERROR`
- 理由コード
- HTTP status
- response body のバイト数
- API の取得候補数、画像あり候補数
- タイトル一致数、巻数一致数、著者一致数
- Amazon の OGP、商品画像、JSON-LD、`image_src` の候補数
- Content-Type、要求 ASIN の商品ページ判定、HTML 中の ASIN 存在有無などの真偽値

診断情報には次を保存しない。

- HTTP レスポンス本文や HTML 本文
- Cookie
- Authorization や認証トークン
- リクエストヘッダー
- Google Books / Open Library の検索 URL や検索クエリ全文
- 外部 API が返した候補タイトル、著者名、説明文

通信例外や 408 / 429 / 5xx は従来どおり WorkManager の上限付き再試行対象とする。Amazon、Google Books、Open Library の途中で一時エラーが発生しても後続フォールバックで表紙を取得できれば成功とする。すべてのフォールバックで確定できず、一時エラーが含まれていた場合だけ再試行する。再試行上限到達時にも、それまでの構造化診断を `diagnostic_trace` として保存する。

表紙取得状況画面では診断ステップの provider、status、理由、候補数、一致数の要約を表示する。ユーザーが明示的に「診断情報をコピー」を実行した場合だけ、書籍タイトル、sourceId、最終状態、構造化診断をクリップボードへ出力できるようにする。これは端末上の明示操作であり、リポジトリや外部サーバーへ自動送信しない。

Google Books へのフォールバックでは ISBN またはタイトル・著者が Google へ送信される。これは Kindle 表紙補完を明示的に有効化した場合だけ行う。Amazon エクスポートファイル、蔵書一覧全体、Cookie、Google アカウントのアクセストークンは送信しない。

実ユーザーの ASIN、タイトル、著者、ISBN、取得 HTML、診断ログはパブリックリポジトリの fixture、テスト、ADR に追加しない。テストデータは人工データだけを使用する。

## Consequences

### Positive

- Amazon HTML の変化で既知画像要素が見つからない場合でも追加経路で取得できる可能性が上がる
- Google Books に収録された日本語書籍を Open Library より前に取得できる
- Kindle 固有の版表記によるタイトル不一致を減らせる
- 「API に候補がない」と「候補はあるがタイトル・著者照合で落ちた」を区別できる
- レスポンス本文を保持せずに次の実装改善へ必要な情報を収集できる
- 再試行上限到達後も、途中の各 provider の失敗理由を確認できる

### Negative

- 最悪時の外部 HTTP リクエスト数が Amazon + Google Books + Open Library の三系統へ増える
- Google Books へ書誌情報が送信される
- Google Books と Open Library のデータ品質差を吸収する照合ロジックを維持する必要がある
- Amazon 商品ページは引き続き安定 API ではなく、HTML 変更への追従が必要になる
- `diagnostic_trace` の追加分だけ端末 DB サイズが増える

## Relationship to existing ADRs

- ADR-0036 の「外部表紙補完は任意」「誤表紙を避ける」「秘密情報を APK に埋め込まない」という原則を維持し、フォールバック順と Amazon 解析経路を拡張する
- ADR-0041 の「レスポンス本文や認証情報を自動保存しない」という原則を維持し、単一エラー文字列を補完する安全な構造化診断を追加する
- ADR-0051 の表紙取得状況画面を、診断情報の確認と明示コピーの導線として利用する
