# ADR-0091: マンガワン作品ページをWebView由来RSSとして購読する

- Status: Accepted
- Date: 2026-08-17
- Updated: 2026-08-18

## Context

RSS機能は通常のRSS / Atomに加え、ADR-0090でヤンマガWeb作品ページをHTML由来の合成フィードとして扱っている。マンガワンにも作品単位の公開RSSはないが、Web版の作品第1話URLから作品と話一覧へ到達できる。

初期実装では第1話URLを `/manga/{作品ID}/chapter/first` のみに限定していた。しかし現在のマンガワンWebでは、作品によって `/manga/{作品ID}/chapter/first` が使われる場合と、第1話にも数値の話IDが割り当てられて `/manga/{作品ID}/chapter/{話ID}` となる場合の両方が存在する。話一覧から遷移したURLには `type`、`sort_type`、`page`、`limit` などの表示状態を表すクエリが付くことがあるため、これらをフィード識別子へ含めると同一作品・同一話が別フィードとして扱われる。

マンガワンの現在の作品ページは、通常のHTTP取得で得られる初期HTMLには話一覧が含まれず、JavaScript実行後に `#chapterList` が構築される。したがって、ADR-0090のHTTP + jsoup方式だけでは無料話の一覧を安定して取得できない。

さらに、数値話IDが古い話を指すURLでは、入力URLに付いている一覧ページ番号を除去した状態でWebViewを開くと話一覧の初期化が遅延するケースがある。描画用URLに一覧表示パラメータを固定しタイムアウトを延長しても実機で話一覧を取得できないケースが継続したため、URLだけでなくWebViewのブラウザ実行環境自体を通常のAndroid Chromeに近づける必要があると判断した。画面へ追加していないWebViewはレイアウトサイズが確定しておらず、既定User-AgentにもWebViewを示す識別子が含まれる。レスポンシブ表示や遅延マウントを行うページでは、これらがDOM構築条件へ影響し得る。

マンガワン公式の利用案内では、アイテム消費なしで読める「無料話」は赤い「無料」ラベルで示される。「毎日無料」「先読み」は利用条件が異なるため、RSSに含める対象を明確に分離する必要がある。

ADR-0006ではRSS更新をActivityの寿命に依存しない耐久バックグラウンド処理として扱い、ADR-0078では漫画RSSを `COMIC` として扱って自動AI enrichmentから除外している。

## Decision

### 1. 第1話URLの `first` と数値話IDの両形式を受け入れる

`manga-one.com` または `www.manga-one.com` のうち、パスが次のいずれかとなるURLをマンガワン作品URLとして認識する。

- `/manga/{数字の作品ID}/chapter/first`
- `/manga/{数字の作品ID}/chapter/{数字の話ID}`

ユーザーは作品の第1話URLを入力する。クライアントは数値話IDのURLだけからその話が第1話かどうかを推測せず、マンガワンの正規の話URL形式であることだけを検証する。

クエリ・フラグメント・`www` は除去し、`https://manga-one.com/manga/{作品ID}/chapter/{first または話ID}` を正規フィードURLとして保存する。`type`、`sort_type`、`page`、`limit` など話一覧の表示状態に由来するクエリはフィード識別子へ含めない。既に登録可能だった `chapter/first` 形式は引き続き維持する。

### 2. RSS data層のサイト固有アダプターでWebViewを使用する

`:feature:rss:data` に `MangaOneFeedClient` を置き、通常RSS・ヤンマガWebとは別の取得経路にする。

マンガワンの話一覧はJavaScript実行後に生成されるため、AndroidのWebViewをアプリケーションコンテキストからMain dispatcher上で生成し、画面へ表示せずに作品ページを読み込む。ActivityやComposeのライフサイクルには依存させないため、WorkManagerからの更新でも同じ取得処理を利用できる。

保存する正規フィードURLとは別に、WebViewでの描画時だけ `type=chapter&sort_type=desc&page=1&limit=10` を付与する。入力URLの `page` は第1話など過去話の位置を示すだけなので引き継がず、常に降順1ページ目を描画して現在の無料話を取得する。これによりページング状態をフィード識別子へ混入させず、マンガワン側が想定する話一覧表示パラメータを明示できる。

画面へattachしないWebViewでも通常のモバイルブラウザに近い描画条件を与える。既定User-Agentから `; wv` と `Version/4.0` を除去して現在端末のChrome/WebViewエンジンバージョンを維持したChrome相当User-Agentを使用し、1080 × 2400 pxの仮想viewportでmeasure/layoutしてから読み込む。`useWideViewPort` と `loadWithOverviewMode` も有効化する。固定のChromeバージョン文字列はコードへ埋め込まない。

WebViewは30秒でタイムアウトし、完了・失敗・キャンセルのいずれでも破棄する。ファイルアクセスとcontent URIアクセスを無効化し、mixed contentを拒否し、トップレベル遷移は `manga-one.com` / `www.manga-one.com` に限定する。JavaScript bridgeは追加しない。

`core:web-collector` はユーザー操作を伴う収集ダイアログの共通基盤であり、バックグラウンドRSS更新のためのヘッドレスレンダラーではない。そのため、現時点ではマンガワンの取得処理を同モジュールへ移さない。将来ヘッドレスWeb取得の共通基盤を導入する場合に統合を再検討する。

### 3. `#chapterList` から現在の無料話だけを抽出する

JavaScript実行後の `#chapterList` から同一作品の `/manga/{作品ID}/chapter/...` リンクを収集する。各話の最小の共通行領域を特定し、話タイトル、URL、ラベル、表示日を取得する。

RSS記事に採用するのは、ラベル文字列が厳密に `無料` の話だけとする。`毎日無料`、`先読み`、ラベルなしの話は除外する。

話URLを外部IDと重複判定の基準にする。公開日の年月日を取得できる場合は日本時間0時として保存し、取得できない場合はその話を初めて取得した時刻を使用する。

### 4. DOMの遅延構築をポーリングし、失敗地点を区別する

`onPageFinished` はSPAの非同期DOM構築完了を示さないため、取得開始条件には使わない。`loadUrl` の直後から500ms間隔でJavaScriptを評価し、ページ末尾と話一覧領域をスクロールしながら `#chapterList` と同一作品の話リンクを確認する。同一の話一覧が複数回連続して得られた時点で確定し、遅延ロード中の一時的な一覧を保存しない。

ポーリング結果は「document loading」「サービス側エラー表示」「chapter list未生成」「chapter link未生成」「ready」を内部状態として区別する。タイムアウト時は最後に観測した状態に応じて一般化したエラーメッセージを返す。実際の作品URL、作品ID、話ID、Cookie等をエラーメッセージやログへ埋め込まない。

### 5. HTTPキャッシュバリデータは使用しない

WebView経由では既存 `HttpClient` のETag / Last-Modified制御を適用できないため、マンガワン合成フィードの `etag` と `lastModified` は保存しない。定期更新ごとにページを描画して話一覧を比較する。

外部RSS生成サービスや非公開APIを固定的に呼び出す方式は採用しない。公開WebページのDOM変更だけを追従対象にする。

### 6. 登録時に `COMIC` として明示する

マンガワン作品URLから新規登録したフィードは、ヤンマガWebと同様に `ContentType.COMIC` を設定する。これによりADR-0078の既存ルールを利用し、自動要約などの記事向けAI処理を実行しない。

### 7. 公開リポジトリに実購読情報を残さない

テスト・ADR・ログには実際に購読している作品名や作品ID・話IDを残さない。テストでは架空の作品名とIDのみを使用する。認証情報やCookieをコードへ埋め込まない。

## Consequences

### Positive

- ユーザーはマンガワン作品の実際の第1話URLを通常のRSS追加欄へ貼り付けるだけで購読できる。
- `chapter/first` と数値話IDの両方の現行URL形式へ対応できる。
- URLにページング等のクエリが付いていても、同一の話URLとして正規化できる。
- 古い第1話URLから追加しても、描画時は最新話一覧の表示状態を明示して取得できる。
- 非表示WebViewでも通常のモバイルChromeに近いUser-Agentとviewportを与え、レスポンシブ表示や遅延DOM構築を促せる。
- 取得失敗時に、ページ自体・話一覧・話リンクのどこまで到達したかをユーザー向けエラーで切り分けられる。
- 赤い「無料」話だけがRSS記事になり、毎日無料や先読みを誤って更新扱いしない。
- 外部RSSサービスや非公開APIの仕様・可用性へ依存しない。
- Activityに依存しないため、既存のバックグラウンドRSS更新経路へ統合できる。
- 漫画として自動分類され、記事向けAIタスクを抑止できる。

### Negative

- 入力された数値話IDが本当に第1話かどうかはURL構造だけでは検証しない。
- 通常HTTP取得よりWebViewの起動コストと通信量が大きい。
- Chrome相当User-Agentと仮想viewportは互換性を高めるためのheuristicであり、通常Chromeと完全に同一の実行環境にはならない。
- ETag / Last-Modifiedによる304最適化を利用できない。
- マンガワンのDOM構造やラベル表現が変わった場合はアダプターの修正が必要になる。
- Android WebView実装へ依存するため、純粋なJVM単体テストでは描画部分を直接再現できない。URL判定・フィード変換・User-Agent変換は単体テストし、実DOM描画は実機確認が必要になる。

## Relationship to existing ADRs

- ADR-0006: ActivityではなくアプリケーションコンテキストとMain dispatcherを使い、WorkManagerから利用可能な取得処理にする。
- ADR-0049: 既存のフィード管理・登録UIを維持し、マンガワンURLも同じ入力欄へ統合する。
- ADR-0078: マンガワン由来フィードを `COMIC` として登録し、既存のAI処理可否を利用する。
- ADR-0090: サイト固有アダプターという境界は踏襲する。一方、ヤンマガWebは静的HTMLを取得できるためHTTP + jsoupのままとし、WebView利用をマンガワンへ限定する。
