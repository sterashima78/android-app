# ADR-0057: Kindle 蔵書は Web 収集 JSON を正規入力とする

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-15
- Supersedes: ADR-0026 の Kindle インポート判断、ADR-0031、ADR-0033、ADR-0036、ADR-0039 の Kindle 入力形式判断、ADR-0041、ADR-0043、ADR-0044、ADR-0053
- Refines: ADR-0017, ADR-0054, ADR-0061

## Context

Kindle 蔵書は以前、Amazon Request Your Data の ownership JSON / ZIP を正規入力とし、表紙がない書籍は Amazon 商品ページ、Google Books、Open Library などからバックグラウンド補完していた。この方式は複数データ源と再試行キューを維持する必要があり、取得失敗の診断も複雑だった。

Kindle Web Library を実ブラウザで検証したところ、ログイン済みの `read.amazon.co.jp` 内では購入済み蔵書一覧から ASIN、タイトル、著者、Amazon の表紙 URL を取得できる。また MANGA 一覧の `parentSeriesInfo` からシリーズ ID と巻位置、シリーズ表示タブから多くのシリーズ名を取得できる。

Send to Kindle などで追加した Personal Document は Kindle Web Library には表示されないが、Amazon の「コンテンツと端末の管理」の Personal Document 一覧では、ログイン済み Web コンテキストから `GetContentOwnershipData` を利用して必要なメタデータを取得できる。

Web Library API / DOM / MYCD API は Amazon がアプリ向けに安定性を保証する公開 API ではない。また、ユーザーに JSON ファイルを保存・選択させる経路は、WebView 収集経路の導入後は操作と保守コードを増やすだけになるため廃止する。

## Decision

### 正規入力

Kindle の購入済み本と Personal Document は、専用 WebView の collector が生成した JSON をアプリ内部の正規入力とする。ユーザーが端末上の JSON / ZIP / CSV を選択するファイルインポートは提供しない。外部ブラウザ用ブックマークレットも提供しない。

購入済み Kindle 本の内部入力形式 v1 は次を持つ。

- `format`: `kindle-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `authors`, `coverUrl`, `series`
- `series`: 存在する場合 `id`, `name`, `position`

Personal Document の内部入力形式 v1 は次を持つ。

- `format`: `kindle-personal-library-export`
- `version`: `1`
- `books`: Personal Document 配列
- 各項目: `id`, `title`, `authors`, `contentType`, `acquiredAt`
- `id`: Amazon MYCD が返す32文字の英大文字・数字

アプリが蔵書へ保存する Personal Document の `sourceId` は、購入済み本の ASIN と名前空間を分離するため `PDOC:<32文字ID>` とする。購入済み本の取り込みは購入済み本だけを置換し、Personal Document の取り込みは Personal Document だけを置換する。

旧 ownership JSON / ZIP、SagaSeries CSV、WebView 外で生成した JSON ファイルは受け付けず、後方互換は提供しない。

### WebView 取り込み

蔵書の設定画面には次の導線だけを表示する。

- 購入済み本: アプリ内 Kindle WebView 取り込み
- Personal Document: アプリ内 Amazon WebView 取り込み

WebView collector が生成した JSON はファイルへ保存せず、Web Message 経由でネイティブ側へ渡す。ネイティブ側では JSON 文字列を `LibraryRepository` へ直接渡し、ファイル名や `InputStream` をインポート API の契約に含めない。

認証・origin・WebView profile・メッセージ分割の境界は ADR-0061 に従う。

### Personal Document のオープン動作

購入済み Kindle 本は10文字 ASIN を使った Kindle deep link で対象書籍を直接開く。

Personal Document は対象ドキュメントを直接開く公開 deep link が確認できないため、タップ時に次を行う。

1. Kindle Android アプリを起動する
2. 対象 Personal Document のタイトルをクリップボードへコピーする
3. 「タイトルをコピーしました。Kindleで検索してください」と案内する

Kindle アプリがインストールされていない場合もタイトルのコピーは行う。

### 表紙

購入済み本では `coverUrl` を `library_items.thumbnail_url` に保存し、これを Kindle 表紙の正規データとする。`coverUrl` がない場合、アプリは追加検索を行わず「表紙なし」と表示する。

Personal Document は現時点では表紙なしとして取り込む。公開書籍メタデータサービスへタイトルを送信して表紙を推測しない。

### シリーズ

構造化シリーズの同一性は `series.id` で判定する。`series.name` と `series.position` は任意とし、シリーズ名がない場合も ID を保持する。シリーズ位置が明示されない場合はタイトルから推測しない。

優先順位は次の通り。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Kindle Web Library の構造化シリーズ情報
4. タイトルからの自動推定

`library_source_series` は再構築可能な source metadata として維持する。Personal Document の取り込みでは購入済み本の `library_source_series` を消去・再構築しない。

### 安全境界

Kindle JSON は最大 25 MB とする。購入済み本では ASIN とシリーズ ID を10文字の英数字として検証する。Personal Document では ID を32文字の英大文字・数字として検証する。タイトル必須、シリーズ位置は存在する場合1以上とする。

Personal Document collector は Cookie、CSRF token、端末 ID、配送先、Amazon アカウント情報を JSON に含めない。MYCD の API 応答から蔵書用途に必要な項目だけを allowlist する。

実ユーザーの JSON、ASIN / Personal Document ID 一覧、タイトル一覧、Cookie、セッション情報をログ、fixture、ADR、公開リポジトリへ追加しない。テストデータは人工的な値のみを使用する。

## Consequences

### Positive

- Kindle / Personal Document の取り込み操作を WebView 内に統一できる
- Android のドキュメントピッカー、ファイル名判定、外部ブックマークレットとその説明を削除できる
- domain / data API からファイルという概念を除去できる
- 認証情報をデータ層へ渡さずに済む
- 表紙・シリーズを Web 収集結果から一貫して取り込める

### Negative

- WebView または Amazon 側の Web 経路が利用できない場合、ファイルインポートによるフォールバックはない
- Web Library / MYCD の非公開 Web 経路変更で collector が動作しなくなる可能性がある
- Personal Document を対象ドキュメントまで直接開けず、Kindle 側でタイトル検索が必要
- Personal Document は現時点では表紙なしになる

## Relationship to existing ADRs

- ADR-0026 の Kindle ownership JSON / ZIP 判断を置き換える
- ADR-0036、ADR-0041、ADR-0043、ADR-0044、ADR-0053 の表紙補完・診断判断を置き換える
- ADR-0039 の source-series テーブルと手動設定優先は維持するが、SagaSeries CSV 入力は使用しない
- ADR-0054 に従い、取り込み UI は library feature が所有する
- ADR-0061 に従い、購入済み本と Personal Document の Web 収集は専用 WebView profile 内で完結させ、外部ファイル入力は提供しない
