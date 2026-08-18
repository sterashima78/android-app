# ADR-0061: Amazon 系蔵書の Web 収集は専用 WebView 内で完結させる

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-15, 2026-08-18
- Refines: ADR-0057, ADR-0058

## Context

Kindle / Audible の蔵書は、ログイン済み Web コンテキストから必要なデータを収集できる。Kindle の購入済み本は `read.amazon.co.jp`、Kindle Personal Document は `www.amazon.co.jp` の「コンテンツと端末の管理」、Audible は `www.audible.co.jp` と `api.audible.co.jp` を利用する。

当初は外部ブラウザのブックマークレットで JSON を生成し、そのファイルをアプリで選択する経路をフォールバックとして残した。しかし専用 WebView で同じ collector を実行できるため、ファイル保存・ドキュメントピッカー・ブックマークレットという第2経路を維持する利点より、操作とコードの複雑さの方が大きい。

Amazon / Audible のページはアプリ管理下の HTML ではない。汎用的な JavaScript interface を公開すると、ページ側の XSS や意図しないナビゲーションからネイティブ機能へ到達する境界が広くなる。また、このリポジトリは public であるため、実ユーザーの蔵書・ASIN・Personal Document ID・Cookie・セッション情報を fixture やログへ残してはならない。

2026-08-18 に資産取得などでも同じ安全な WebView 収集が必要になり、`:core:web-collector` が横断 capability として導入された。Amazon 専用 UI に WebView の profile、navigation、Web Message、chunk 復元を重複実装すると、同じセキュリティ境界を複数箇所で保守することになる。

## Decision

### 唯一のインポート経路

Kindle 購入済み本 / Kindle Personal Document / Audible のインポートはアプリ内の専用 WebView だけから行う。

1. Amazon インポート専用 WebView を開く
2. 未ログインなら WebView 内でユーザー自身がログインする
3. 対象ページでユーザーが「取り込む」を実行する
4. アプリが collector script を `evaluateJavascript` で実行する
5. collector は対象に応じて `kindle-library-export` / `kindle-personal-library-export` / `audible-library-export` v1 JSON を生成する
6. JSON を Web Message 経由でネイティブ側へ渡し、`LibraryRepository` へ JSON 文字列として直接入力する

外部ブラウザ用ブックマークレット、JSON / CSV / ZIP のファイル選択 UI、ファイル名判定、`InputStream` を受け取るインポート API は提供しない。v1 JSON は WebView collector とネイティブ処理間の内部データ契約として維持する。

WebView が利用できない場合はインポートを利用不可として扱い、外部ファイル経路へフォールバックしない。

### WebView runtime の ownership

WebView の生成・破棄、AndroidX WebKit Multi Profile、navigation 制限、Web Message の origin 検証、chunk 復元、結果サイズ制限、対象ページへの復帰 UI は `:core:web-collector` の `SecureWebCollectorDialog` が所有する。

`:feature:library:ui` は Amazon / Audible 固有の次だけを設定として渡す。

- start URL と collector 実行可能 URL prefix
- bridge を公開する origin
- WebView 内で許可する host
- 専用 profile 名
- collector script
- bridge 名
- Cookie / User-Agent のサイト互換設定
- 最大結果サイズと chunk 数
- Audible の2段階目 collector のような continuation script
- 受信後に期待する `format` / `version`

これにより、Library は蔵書取得契約を所有し続ける一方、安全な WebView 実行基盤は横断 capability として一箇所に集約する。`:core:web-collector` は Kindle / Audible や Library のドメイン概念を知らない。

### 認証・Cookie の境界

WebView は AndroidX WebKit の Multi Profile が利用できる場合のみアプリ内インポートに使用し、`yomitori-amazon-library` 専用 profile を割り当てる。この profile 設定と Cookie policy の適用は `:core:web-collector` が行い、Library は Amazon 用設定として第三者 Cookie 許可を明示する。

- Amazon / Audible のパスワードをネイティブコードへ渡さない
- Cookie 値をネイティブコードから読み取らない
- Cookie / Web Storage は専用 WebView profile 内にのみ保持する
- library data/domain 層から Amazon へ認証付き HTTP リクエストを送らない
- Multi Profile が利用できない WebView 実装ではインポートを無効にする

Personal Document collector が使用する MYCD の CSRF token もブラウザコンテキスト内だけで利用する。collector はページ上の token を同一 origin の MYCD リクエストへ渡すが、token を Web メッセージ、ログ、JSON、データ層へ渡さない。

### WebView とネイティブの通信

`addJavascriptInterface` は使用しない。AndroidX WebKit の `WebViewCompat.addWebMessageListener` を `:core:web-collector` で使用し、Library が指定する JavaScript bridge を次の origin のみに公開する。

- Kindle 購入済み本: `https://read.amazon.co.jp`
- Kindle Personal Document: `https://www.amazon.co.jp`
- Audible library: `https://www.audible.co.jp`
- Audible catalog: `https://api.audible.co.jp`

core 側でも main frame と source origin を再検証する。collector script は Library が指定した対象 URL prefix でのみ実行する。

JSON は一度に巨大なメッセージとして渡さず、32 KiB 文字単位の chunk に分割する。各転送は session ID、総 chunk 数、UTF-8 byte 長を持ち、core 側で次を検証してから復元する。

- Amazon importer では最大 25 MB
- Amazon importer では最大 2048 chunk
- session ID の一致
- chunk index / 総数の一致
- 全 chunk の受信
- 宣言された UTF-8 byte 長と復元結果の一致

復元後に Library 側で `format` と `version` が要求 target と一致することを検証し、ファイルへ書き出さず、そのまま Repository へ渡す。

### ナビゲーション境界

WebView 内の main-frame ナビゲーションは HTTPS の `amazon.co.jp` / `*.amazon.co.jp` / `audible.co.jp` / `*.audible.co.jp` のみに許可する。host suffix の判定は `:core:web-collector` が行い、それ以外の HTTP / HTTPS リンクは外部アプリへ委譲し、`intent:` などその他の scheme はブロックする。

WebView では file access / content access / mixed content / JavaScript window open を無効化する。JavaScript と DOM storage は Amazon / Audible のページ動作と collector 実行に必要なため有効化する。

### Kindle Personal Document の処理

Personal Document は購入済み Kindle 本と同じ `LibrarySource.KINDLE` だが、WebView collector と期待 JSON 形式を分離する。

1. `www.amazon.co.jp/hz/mycd/digital-console/contentlist/pdocs/...` を開く
2. collector がページ上の CSRF token を取得する
3. `/hz/mycd/digital-console/ajax` の `GetContentOwnershipData` をページングする
4. API 応答から `id`, `title`, `authors`, `contentType`, `acquiredAt` だけを allowlist する
5. ID とタイトルを collector 側でも検証・重複排除する
6. `kindle-personal-library-export` v1 JSON をネイティブ側へ分割転送する
7. Kindle importer が Personal Document だけを置換する

Cookie、CSRF token、端末 ID、配送先、Amazon アカウント情報、MYCD の生レスポンスはネイティブ側へ送信しない。

### Audible の二段階処理

Audible は WebView 内で二段階の Web 収集を自動化する。

1. `www.audible.co.jp/library/titles` で全ページを巡回して ASIN を収集する
2. collector 自身が最初の 50 ASIN を含む `api.audible.co.jp` URL へ遷移する
3. API ページ読み込み完了を `:core:web-collector` が continuation URL prefix で検知し、Library が設定した2段階目 collector を一度だけ自動実行する
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

- Kindle 購入済み本、Personal Document、Audible の操作を「アプリ内でログイン → 取り込む」に統一できる
- Library から WebView/WebKit の実行詳細を除去し、collector と蔵書形式に責務を限定できる
- 資産取得など他機能と WebView の security boundary、chunk 復元、profile lifecycle を共有できる
- ドキュメントピッカー、ファイル名判定、外部ブックマークレットと説明 UI を削除した状態を維持できる
- data/domain のインポート API からファイルという概念を除去できる
- 認証付き通信をブラウザコンテキスト内に閉じたまま維持できる
- Amazon 用 Cookie を他のアプリ内 WebView と分離できる

### Negative

- Android System WebView / AndroidX WebKit の Multi Profile と Web Message Listener が必要になる
- Amazon 固有要件を満たすため `:core:web-collector` の設定 API に Cookie、User-Agent、continuation、サイズ上限の拡張点が必要になる
- Amazon / Audible が埋め込み WebView のログインや対象 Web 経路を制限した場合、代替のファイルインポート経路はない
- Web Library DOM、MYCD API、Audible catalog API の変更には引き続き影響を受ける

## Relationship to existing ADRs

- ADR-0004 に従い、WebView による安全なデータ収集は複数 feature から利用する横断 capability として `:core:web-collector` が所有し、Library 固有のドメイン概念は持たない
- ADR-0057 の Kindle 購入済み本 / Personal Document v1 JSON、25 MB 上限、source metadata、カテゴリ別置換の判断は維持する
- ADR-0058 の Audible v1 JSON、catalog / series 取得方法の判断は維持する
- ADR-0057 / 0058 の外部ブラウザ・ファイルインポート判断を廃止し、WebView-only に統一する
- Superseded 済みの ADR-0054 を WebView runtime ownership の根拠にはしない
