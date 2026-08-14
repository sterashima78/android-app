# ADR-0061: Amazon 系蔵書の Web 収集は専用 WebView 内で完結させる

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-15
- Refines: ADR-0057, ADR-0058

## Context

ADR-0057 と ADR-0058 では、Kindle / Audible のログイン済み Web Library からブックマークレットで JSON を生成し、その JSON をアプリへインポートする方式を採用した。この方式は Amazon のパスワードや Cookie をアプリのデータ層へ渡さない一方、ユーザーが外部ブラウザでブックマークレットを準備・実行し、生成した JSON ファイルを再度アプリで選択する必要がある。

Kindle の購入済み本は `read.amazon.co.jp` 内で収集できる。Kindle Personal Document は `www.amazon.co.jp` の「コンテンツと端末の管理」で `GetContentOwnershipData` を利用して収集できる。Audible は `www.audible.co.jp` で蔵書 ASIN を集めた後、`api.audible.co.jp` へ遷移してカタログとシリーズを収集する。いずれもログイン済み Web コンテキスト上で JavaScript を順次実行できれば、外部 JSON ファイルを介さず既存の importer へ同じ v1 JSON を渡せる。

一方、Amazon / Audible のページはアプリ管理下の HTML ではない。汎用的な JavaScript interface を公開すると、ページ側の XSS や意図しないナビゲーションからネイティブ機能へ到達する境界が広くなる。また、このリポジトリは public であるため、実ユーザーの蔵書・ASIN・Personal Document ID・Cookie・セッション情報を fixture やログへ残してはならない。

## Decision

### 通常のインポート経路

Kindle 購入済み本 / Kindle Personal Document / Audible の通常のインポート UI はアプリ内 WebView を第一選択とする。

1. Amazon インポート専用 WebView を開く
2. 未ログインなら WebView 内でユーザー自身がログインする
3. 対象ページでユーザーが「取り込む」を実行する
4. アプリが既存ブックマークレット相当の collector script を `evaluateJavascript` で実行する
5. collector は対象に応じて `kindle-library-export` / `kindle-personal-library-export` / `audible-library-export` v1 JSON を生成する
6. JSON をネイティブ側へ渡し、既存 `LibraryRepository` の importer へ直接入力する

外部ブラウザのブックマークレットと JSON ファイルインポートは、WebView / Web 側の仕様変更に備えたフォールバックとして残す。

### 認証・Cookie の境界

WebView は AndroidX WebKit の Multi Profile が利用できる場合のみアプリ内インポートに使用し、`yomitori-amazon-library` 専用 profile を割り当てる。

- Amazon / Audible のパスワードをネイティブコードへ渡さない
- Cookie 値をネイティブコードから読み取らない
- Cookie / Web Storage は専用 WebView profile 内にのみ保持する
- library data/domain 層から Amazon へ認証付き HTTP リクエストを送らない
- Multi Profile が利用できない WebView 実装ではアプリ内インポートを無効にし、外部ブラウザ方式へフォールバックする

したがって ADR-0057 / 0058 の「アプリ自身が Cookie を保持して直接アクセスしない」は、「アプリのデータ層・ドメイン層は認証情報や Cookie を取得・保存・利用しない。認証状態は隔離した WebView profile のブラウザコンテキストだけが保持する」と具体化する。

Personal Document collector が使用する MYCD の CSRF token もブラウザコンテキスト内だけで利用する。collector はページ上の token を同一 origin の MYCD リクエストへ渡すが、token を Web メッセージ、ログ、JSON、データ層へ渡さない。

### WebView とネイティブの通信

`addJavascriptInterface` は使用しない。AndroidX WebKit の `WebViewCompat.addWebMessageListener` を使用し、JavaScript オブジェクトを次の origin のみに公開する。

- Kindle 購入済み本: `https://read.amazon.co.jp`
- Kindle Personal Document: `https://www.amazon.co.jp`
- Audible library: `https://www.audible.co.jp`
- Audible catalog: `https://api.audible.co.jp`

ネイティブ側でも main frame と source origin を再検証する。collector script は対象 source の正規ページでのみ実行する。購入済み本用 collector を Personal Document ページで、または Personal Document collector を他の Amazon ページで実行しない。

JSON は一度に巨大なメッセージとして渡さず、32 KiB 文字単位の chunk に分割する。各転送は session ID、総 chunk 数、UTF-8 byte 長を持ち、ネイティブ側で次を検証してから復元する。

- 最大 25 MB
- 最大 2048 chunk
- session ID の一致
- chunk index / 総数の一致
- 全 chunk の受信
- 宣言された UTF-8 byte 長と復元結果の一致
- `format` と `version` が要求 target と一致

### ナビゲーション境界

WebView 内の main-frame ナビゲーションは HTTPS の `amazon.co.jp` / `*.amazon.co.jp` / `audible.co.jp` / `*.audible.co.jp` のみに許可する。それ以外の HTTP / HTTPS リンクは外部アプリへ委譲し、`intent:` などその他の scheme はブロックする。

WebView では file access / content access / mixed content / JavaScript window open を無効化する。JavaScript と DOM storage は Amazon / Audible のページ動作と collector 実行に必要なため有効化する。

### Kindle Personal Document の処理

Personal Document は購入済み Kindle 本と同じ `LibrarySource.KINDLE` だが、WebView collector と期待 JSON 形式を分離する。

1. `www.amazon.co.jp/hz/mycd/digital-console/contentlist/pdocs/...` を開く
2. collector がページ上の CSRF token を取得する
3. `/hz/mycd/digital-console/ajax` の `GetContentOwnershipData` を `startIndex` / `batchSize=100` でページングする
4. API 応答から `id`, `title`, `authors`, `contentType`, `acquiredAt` だけを allowlist する
5. 32文字 ID とタイトルを collector 側でも検証・重複排除する
6. `kindle-personal-library-export` v1 JSON をネイティブ側へ分割転送する
7. 既存 Kindle importer が Personal Document だけを置換する

Cookie、CSRF token、端末 ID、配送先、Amazon アカウント情報、MYCD の生レスポンスはネイティブ側へ送信しない。

### Audible の二段階処理

Audible は従来の二段階ブックマークレットと同じ Web 経路を WebView 内で自動化する。

1. `www.audible.co.jp/library/titles` で全ページを巡回して ASIN を収集する
2. collector 自身が最初の 50 ASIN を含む `api.audible.co.jp` URL へ遷移する
3. API ページ読み込み完了をアプリが検知し、2段階目 collector を自動実行する
4. 残りのカタログ情報とシリーズを取得し、v1 JSON を生成する

ASIN の一覧や生成 JSON はログへ出力しない。

### 公開リポジトリ上のデータ

実ユーザーの次のデータを source、test fixture、ADR、ログ、PR 説明へ追加しない。

- 蔵書 JSON
- ASIN / Personal Document ID / 書名一覧
- Cookie
- CSRF token
- セッショントークン
- ログイン画面の入力値

テストでは人工的な URL、session ID、JSON のみを使用する。

## Consequences

### Positive

- Kindle 購入済み本、Personal Document、Audible の通常操作が「アプリ内でログイン → 取り込む」まで短縮される
- Personal Document でも外部ブラウザで JSON を保存して選択する操作が不要になる
- Audible の2本のブックマークレットをユーザーが順番に実行する必要がなくなる
- 既存 v1 JSON importer と入力検証を再利用できる
- 認証付き通信はブラウザコンテキスト内に閉じたまま維持できる
- Amazon 用 Cookie を他のアプリ内 WebView と分離できる
- 外部ブラウザ方式を障害時のフォールバックとして残せる

### Negative

- Android System WebView / AndroidX WebKit の Multi Profile と Web Message Listener が必要になる
- Amazon / Audible が埋め込み WebView のログインを制限した場合、外部ブラウザ方式へ戻る必要がある
- Web Library DOM、MYCD API、Audible catalog API の変更には引き続き影響を受ける
- collector script と外部ブラウザ用 bookmarklet の2経路を維持する必要がある

## Relationship to existing ADRs

- ADR-0057 の Kindle 購入済み本 / Personal Document v1 JSON、25 MB 上限、source metadata、カテゴリ別置換の判断は維持する
- ADR-0058 の Audible v1 JSON、catalog / series 取得方法の判断は維持する
- ADR-0057 / 0058 の外部ブックマークレットを通常経路からフォールバックへ変更する
- ADR-0054 に従い、WebView UI と runtime state は library feature が所有する
