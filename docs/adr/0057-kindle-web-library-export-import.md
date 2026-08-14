# ADR-0057: Kindle 蔵書は Web から生成した JSON を正規入力とする

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-14
- Supersedes: ADR-0026 の Kindle インポート判断、ADR-0031、ADR-0033、ADR-0036、ADR-0039 の Kindle 入力形式判断、ADR-0041、ADR-0043、ADR-0044、ADR-0053
- Refines: ADR-0017, ADR-0054

## Context

Kindle 蔵書は以前、Amazon Request Your Data の ownership JSON / ZIP を正規入力とし、表紙がない書籍は Amazon 商品ページ、Google Books、Open Library などからバックグラウンド補完していた。この方式は複数データ源と再試行キューを維持する必要があり、取得失敗の診断も複雑だった。

Kindle Web Library を実ブラウザで検証したところ、ログイン済みの `read.amazon.co.jp` 内では購入済み蔵書一覧から ASIN、タイトル、著者、Amazon の表紙 URL を取得できる。また MANGA 一覧の `parentSeriesInfo` からシリーズ ID と巻位置、シリーズ表示タブから多くのシリーズ名を取得できる。

Web Library 由来の実エクスポートでは表紙 URL をほぼ全書籍で取得できることを確認した。残る少数の表紙欠損のために、複数の外部プロバイダー、WorkManager、診断キュー、外部メタデータキャッシュを維持するコストは現行方式では見合わない。

一方、Send to Kindle などで追加した Personal Document は Kindle Web Library には表示されない。Amazon の「コンテンツと端末の管理」の Personal Document 一覧では、ログイン済みブラウザから `GetContentOwnershipData` を利用してタイトル、著者、32文字のドキュメント ID、コンテンツ種別、取得日時を取得できることを実データで検証した。74件の実エクスポートで全件取得と ID の一意性を確認した。

Personal Document の32文字 ID、`ID:KindlePDoc`、`contentIdentifier` 等を Kindle Android アプリの既知の deep link 形式へ渡す実機検証を行ったが、対象ドキュメントを直接開くことはできなかった。Amazon から Personal Document を外部アプリが直接開く公開仕様も提供されていないため、非公開 Activity / Intent extra には依存しない。

これらの Web Library API / DOM / MYCD API は Amazon がアプリ向けに安定性を保証する公開 API ではない。アプリ自身が Amazon のパスワードや Cookie をインポートデータとして保持する方式は採用しない。

## Decision

### 正規入力

Kindle の購入済み本と Personal Document は、それぞれログイン済み Amazon Web 上で生成した JSON を正規入力とする。

購入済み Kindle 本の入力形式 v1 は次を持つ。

- `format`: `kindle-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `authors`, `coverUrl`, `series`
- `series`: 存在する場合 `id`, `name`, `position`

購入済み本のブックマークレットは `resourceType == EBOOK` のみを書き出し、ASIN 単位で重複排除する。

Personal Document の入力形式 v1 は次を持つ。

- `format`: `kindle-personal-library-export`
- `version`: `1`
- `books`: Personal Document 配列
- 各項目: `id`, `title`, `authors`, `contentType`, `acquiredAt`
- `id`: Amazon MYCD が返す32文字の英大文字・数字

アプリが蔵書へ保存する Personal Document の `sourceId` は、購入済み本の ASIN と名前空間を分離するため `PDOC:<32文字ID>` とする。これにより既存 DB の `(source, source_id)` 主キーを変更せず、同じ `LibrarySource.KINDLE` の中で購入済み本と Personal Document を判別する。

購入済み本のインポートは Kindle の購入済み本だけを置換し、Personal Document のインポートは Personal Document だけを置換する。どちらか一方の再インポートで他方を削除しない。

旧 ownership JSON / ZIP と SagaSeries CSV は受け付けず、後方互換は提供しない。

### Personal Document のオープン動作

購入済み Kindle 本は従来どおり10文字 ASIN を使った Kindle deep link で対象書籍を直接開く。

Personal Document は対象ドキュメントを直接開く公開 deep link が確認できないため、タップ時に次を行う。

1. Kindle Android アプリを起動する
2. 対象 Personal Document のタイトルをクリップボードへコピーする
3. 「タイトルをコピーしました。Kindleで検索してください」と案内する

Kindle アプリがインストールされていない場合もタイトルのコピーは行い、起動できなかったことを表示する。検証用の deep link 候補画面、Activity 列挙、非公開 Intent の試行コードは正式機能には残さない。

### 表紙

購入済み本では `coverUrl` を `library_items.thumbnail_url` に保存し、これを Kindle 表紙の唯一の正規データとする。

Web Library JSON に `coverUrl` がない場合、アプリは追加検索を行わず「表紙なし」と表示する。Amazon 商品ページ、Google Books、Open Library、NDL 等を利用したバックグラウンド表紙補完は廃止する。

Personal Document の MYCD エクスポートでは安定した表紙 URL を正規データとして確認できていないため、現時点では表紙なしとして取り込む。公開書籍メタデータサービスへタイトルを送信して表紙を推測しない。

### シリーズ

構造化シリーズの同一性は `series.id` で判定する。`series.name` と `series.position` は任意とし、シリーズ名がない場合も ID を保持する。シリーズ位置が明示されない場合はタイトルから推測しない。

優先順位は次の通り。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Kindle Web Library の構造化シリーズ情報
4. タイトルからの自動推定

`library_source_series` は再構築可能な source metadata として維持する。Personal Document のインポートでは購入済み本の `library_source_series` を消去・再構築しない。

### データ作成 UI

蔵書の設定画面に以下を表示する。

- 購入済み本: アプリ内 Kindle WebView 取り込み
- 購入済み本のフォールバック: Kindle Web Library を開く導線とブックマークレット
- Personal Document: 「コンテンツと端末の管理」の Personal Document 一覧を開く導線と専用ブックマークレット
- 生成した JSON を Kindle インポートから選択する手順

Personal Document のブックマークレットは `numberOfItems` を基準にページングし、100件を超えるライブラリも `startIndex` を進めて全件取得する。

### 安全境界

Kindle JSON は最大 25 MB とする。購入済み本では ASIN とシリーズ ID を10文字の英数字として検証する。Personal Document では ID を32文字の英大文字・数字として検証する。タイトル必須、シリーズ位置は存在する場合1以上とする。著者、表紙、シリーズ名、巻位置の欠損は許容する。

Personal Document エクスポートには Cookie、CSRF token、端末 ID、配送先、Amazon アカウント情報を含めない。MYCD の API 応答から蔵書用途に必要な項目だけを allowlist して書き出す。

実ユーザーのエクスポート JSON、ASIN / Personal Document ID 一覧、タイトル一覧、著者メールアドレス、Cookie、セッション情報をログ、fixture、ADR、公開リポジトリへ追加しない。テストデータは人工的な値のみを使用する。

## Consequences

### Positive

- 購入済み Kindle 本と Personal Document を同じ Kindle 蔵書として一覧・検索できる
- 片方だけを再インポートしてももう片方を維持できる
- Personal Document でもタップから Kindle 起動とタイトル検索までの操作を短縮できる
- Kindle の購入済み蔵書・表紙・シリーズを1つの JSON から取り込める
- 表紙取得の追加ネットワーク通信、バックグラウンドジョブ、再試行状態を削除できる
- Amazon / Google Books / Open Library / NDL に蔵書情報を追加送信しない
- Amazon の認証情報をインポート JSON へ渡さずに済む

### Negative

- Personal Document を対象ドキュメントまで直接開けず、Kindle 側でタイトル検索が必要
- Personal Document は現時点では表紙なしになる
- Web Library / MYCD の非公開 Web 経路変更でブックマークレットが動作しなくなる可能性がある
- Kindle Web Library 側で表紙 URL が欠けた購入済み本は「表紙なし」のままになる
- 非コミックなど一部のシリーズではシリーズ名が取得できず、IDを使った代替表示になる

## Relationship to existing ADRs

- ADR-0026 の Kindle ownership JSON / ZIP 判断を置き換える
- ADR-0036、ADR-0041、ADR-0043、ADR-0044、ADR-0053 の表紙補完・診断判断を置き換える
- ADR-0039 の source-series テーブルと手動設定優先は維持するが、SagaSeries CSV 入力は使用しない
- ADR-0054 に従い、データ作成手順とインポート UI は library feature が所有する
