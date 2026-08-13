# ADR-0036: Kindle 表紙を Amazon 商品ページと Open Library から補完する

- Status: Accepted
- Date: 2026-08-13
- Updated: 2026-08-14
- Refines: ADR-0013, ADR-0026, ADR-0033

## Context

Kindle の `Digital.Content.Ownership*.json` からは ASIN と商品名を取得できる一方、観測済みデータでは表紙 URL や ISBN が常に得られるわけではない。蔵書 UI は `LibraryBook.thumbnailUrl` をすでに表示できるため、欠落している表紙だけを外部サービスから補完したい。

当初は Open Library の Search API と Covers API だけを利用していた。しかし Kindle 専売本や日本語書籍など Open Library に収録されていない商品があり、10文字の ASIN を取得できている項目でも `NOT_FOUND` になることが多い。

Amazon の公式商品 API は認証用 secret を必要とし、公開リポジトリや APK に埋め込めない。一方、`amazon.co.jp` の公開商品ページは ASIN から参照でき、OGP や商品画像要素から表紙を取得できる場合がある。これは公開・安定した API ではなく、HTML 構造変更やアクセス確認ページの影響を受ける可能性があるため、Amazon 商品ページだけには依存しない。

Open Library は認証不要の公開 Search API と Covers API を提供する。ただし検索時にはタイトル、著者、ISBN などの書誌情報が外部サービスへ送信されるため、Kindle インポートの端末内処理とは明確に分離する必要がある。また未識別 API リクエストには 1 request/second の制限があり、ISBN 等による Covers API 画像取得は CoverID による取得より厳しい制限を受ける。

当初の実装は Open Library の非成功 HTTP 応答と実ネットワーク障害をどちらも `IOException` として扱い、WorkManager の無制限な exponential backoff へ渡していた。このため、一冊の一時的または恒久的な取得失敗が後続書籍の処理まで長時間止める可能性があった。また `OpenLibraryCoverClient` 自体がインスタンスごとに1リクエストだけを許可していたため、呼び出し側が同じクライアントを再利用すると実通信とは無関係な `IOException` を発生させる構造になっていた。

## Decision

Kindle 表紙補完は初期状態を無効とし、設定画面でユーザーが明示的に有効化した場合だけ外部通信を行う。

表紙がない Kindle 項目は次の順序で補完する。

1. 有効な10文字 ASIN がある場合、`https://www.amazon.co.jp/dp/{ASIN}` の公開商品ページを取得する
2. `og:image`、`twitter:image`、`twitter:image:src` を確認する
3. OGP 等で取得できない場合は `landingImage`、`imgBlkFront`、`ebooksImgBlkFront` の高解像度属性、動的画像情報、通常の `src` の順で商品画像を確認する
4. Amazon 商品ページで表紙を確定できない場合は Open Library にフォールバックする
5. Open Library では ISBN-13、ISBN-10、タイトル＋著者の順で検索する

Amazon 商品ページは HTTPS の `amazon.co.jp` 配下で、最終 URL が要求した ASIN の商品パスである場合だけ受け入れる。アクセス確認・CAPTCHA と判断できる HTML は商品ページとして扱わない。HTML は8 MBを上限とする。画像 URL は HTTPS に限定し、Amazon の既知画像ホストかつ商品画像の `/images/I/` パスだけを許可する。Amazon の Cookie、セッション、アクセストークン、非公開 API、推測した画像 URL は利用しない。

所有情報の取得には引き続き外部 API を利用しない。外部通信は表示用の表紙メタデータ補完だけに限定し、Kindle インポートの成否を外部サービスの状態に依存させない。

表紙補完は Kindle インポート完了後に WorkManager で実行し、ネットワーク接続を制約とする。1回の Worker は1冊だけを処理し、続きがある場合は次の Worker を unique work chain に追加する。継続 Worker には1.1秒の初期待機を設定し、Open Library の検索が1 request/secondを超えないようにする。最初の1冊は追加待機なしで開始できる。Worker のキャンセルは失敗へ変換せず、そのままキャンセルとして伝播させる。

Amazon 商品ページの 404 / 410、または正常な商品ページで表紙が見つからない場合は通信エラーとせず、そのまま Open Library を試す。Amazon 商品ページの通信失敗、非成功 HTTP、想定外リダイレクト、アクセス確認ページ、上限超過などは一時的な取得失敗として記録せず、まず Open Library を試す。Open Library で表紙を取得できればその結果を採用する。Amazon 側が一時的に失敗し、Open Library でも表紙を確定できなかった場合は `IOException` として Worker の上限付き再試行へ渡し、一時的な Amazon 障害を30日間の `NOT_FOUND` として固定しない。

Open Library の失敗は次のように分類する。

- HTTP 408、429、5xx は一時的な失敗として扱う
- HTTP 408、429、5xx と実ネットワーク `IOException` は WorkManager の linear backoff で再試行する
- backoff は10秒から開始し、初回を含め合計3回まで試行する
- 合計3回失敗した場合は対象書籍を `ERROR` として外部メタデータへ記録し、後続書籍の処理へ進む
- Amazon 商品ページ由来を含む上限到達エラーは provider を `KINDLE_COVER_ENRICHMENT` とし、特定サービスの障害だと誤表示しない
- その他の Open Library 非成功 HTTP 応答は恒久的な取得失敗として即座に `ERROR` とする
- `ERROR` は表紙取得状況では既存の「未取得」として扱い、「未取得を再試行」で明示的に再投入できる
- `ERROR` も他の未取得結果と同様に30日後は再確認対象へ戻る

リクエスト間隔の責務は Worker chain 側へ集約し、`OpenLibraryCoverClient` はインスタンス内のリクエスト回数を制限しない。同一クライアントの再利用を通信障害として扱わない。

Open Library の検索結果に含まれる CoverID を保存 URL に利用し、ISBN 等による Covers API の追加レート制限を避ける。ユーザー個人の連絡先を User-Agent に埋め込むことはしない。

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
- `KINDLE_COVER_ENRICHMENT`: 上限付き再試行を使い切った取得エラー

これにより source 単位の再インポートで取得済み表紙を失わず、元データに `thumbnail_url` が存在する場合は常にそちらを優先できる。`FOUND`、`NOT_FOUND`、`AMBIGUOUS`、`ERROR` を保存し、未発見・曖昧・取得エラー結果は一定期間後に再確認できるようにする。

設定を無効化した場合は新しい Amazon / Open Library 問い合わせを停止する。すでに取得済みの表紙 URL はローカルキャッシュとして表示を継続する。

Amazon 商品ページへの問い合わせでは ASIN が Amazon 側へ送信される。Open Library へフォールバックする場合は検索に必要なタイトル・著者・ISBN が送信される。Amazon のエクスポートファイルそのもの、Cookie、認証情報、蔵書一覧は送信しない。

実ユーザーの ASIN、タイトル、著者、ISBN、Amazon エクスポート内容、取得 HTML はパブリックリポジトリの fixture・ログ・ADR に保存しない。テストは人工データだけを使用する。

## Consequences

### Positive

- Open Library に未登録の Kindle 専売本や日本語書籍でも、ASIN から表紙を取得できる可能性が上がる
- Amazon 商品ページで取得できればタイトル・著者・ISBN を Open Library へ送信せずに済む
- Amazon API secret や自前バックエンドなしで Kindle の表紙欠落を補完できる
- Kindle インポートは引き続き端末内で完結し、外部障害の影響を受けない
- 再インポート後も取得済み表紙を維持できる
- 同名書籍や別巻の誤表紙を保守的に回避できる
- 公開リポジトリにユーザーデータや秘密情報を追加しない
- 通常の複数冊処理を WorkManager の失敗・backoff と分離できる
- Open Library への検索間隔を明示的に確保できる
- 一冊の取得失敗がキュー全体を長時間停止させない
- 一時的な Amazon 障害だけで `NOT_FOUND` を長期間固定しない

### Negative

- Amazon 商品ページの HTML は公開 API ではなく、構造変更やアクセス制限により取得できなくなる可能性がある
- Open Library の問い合わせ前に Amazon 商品ページ取得が追加されるため、最悪時の HTTP リクエスト数が増える
- Open Library にフォールバックした場合は従来と同じ未発見・曖昧判定が残る
- タイトルしかない Kindle データでは曖昧判定となり表紙を設定できない場合がある
- Open Library の検索・応答仕様やレート制限変更には追従が必要になる
- 有効化したユーザーの ASIN は Amazon へ、フォールバック時の書誌情報は Open Library へ送信される
- 1冊ごとに1.1秒以上空けるため、大量蔵書の初回補完には一定の時間がかかる
- 30秒程度継続する障害では対象書籍を一度 `ERROR` として退避する場合があり、即時再試行にはユーザー操作が必要になる

## Relationship to existing ADRs

- ADR-0013 のサービス非依存 `LibraryBook` と Kindle 所有情報に外部 API を使わない原則を維持し、表示メタデータ補完だけを例外として明示する
- ADR-0026 / ADR-0033 の ownership JSON 解析、実ユーザーデータを fixture に保存しない方針を維持する
- ADR-0006 の再開可能なバックグラウンド処理方針に従い WorkManager を利用する
- ADR-0037 の Audible 表紙補完と同様に、公開商品ページは失敗可能でフォールバック可能な取得経路として扱う
