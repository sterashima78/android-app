# ADR-0057: Kindle 蔵書は Web Library から生成した JSON を正規入力とする

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-14
- Supersedes: ADR-0026 の Kindle インポート判断、ADR-0031、ADR-0033、ADR-0036、ADR-0039 の Kindle 入力形式判断、ADR-0041、ADR-0043、ADR-0044、ADR-0053
- Refines: ADR-0017, ADR-0054

## Context

Kindle 蔵書は以前、Amazon Request Your Data の ownership JSON / ZIP を正規入力とし、表紙がない書籍は Amazon 商品ページ、Google Books、Open Library などからバックグラウンド補完していた。この方式は複数データ源と再試行キューを維持する必要があり、取得失敗の診断も複雑だった。

Kindle Web Library を実ブラウザで検証したところ、ログイン済みの `read.amazon.co.jp` 内では蔵書一覧から ASIN、タイトル、著者、Amazon の表紙 URL を取得できる。また MANGA 一覧の `parentSeriesInfo` からシリーズ ID と巻位置、シリーズ表示タブから多くのシリーズ名を取得できる。

Web Library 由来の実エクスポートでは表紙 URL をほぼ全書籍で取得できることを確認した。残る少数の表紙欠損のために、複数の外部プロバイダー、WorkManager、診断キュー、外部メタデータキャッシュを維持するコストは現行方式では見合わない。

これらの Web Library API / DOM は Amazon がアプリ向けに安定性を保証する公開 API ではない。アプリ自身が Amazon の認証情報や Cookie を保持して直接アクセスする方式は採用しない。

## Decision

### 正規入力

Kindle のインポートは、設定画面で提示するブックマークレットをログイン済み Kindle Web Library 上で実行して生成する JSON のみを正規入力とする。

入力形式 v1 は次を持つ。

- `format`: `kindle-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `authors`, `coverUrl`, `series`
- `series`: 存在する場合 `id`, `name`, `position`

ブックマークレットは `resourceType == EBOOK` のみを書き出し、ASIN 単位で重複排除する。旧 ownership JSON / ZIP と SagaSeries CSV は受け付けず、後方互換は提供しない。

### 表紙

`coverUrl` を `library_items.thumbnail_url` に保存し、これを Kindle 表紙の唯一の正規データとする。

Web Library JSON に `coverUrl` がない場合、アプリは追加検索を行わず「表紙なし」と表示する。Amazon 商品ページ、Google Books、Open Library、NDL 等を利用したバックグラウンド表紙補完は廃止する。

これに伴い、表紙補完の Worker / WorkManager scheduler、外部メタデータキャッシュ、表紙取得キュー、再試行・診断 UI、プロバイダー別クライアントを削除する。

### シリーズ

構造化シリーズの同一性は `series.id` で判定する。`series.name` と `series.position` は任意とし、シリーズ名がない場合も ID を保持する。シリーズ位置が明示されない場合はタイトルから推測しない。

優先順位は次の通り。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Kindle Web Library の構造化シリーズ情報
4. タイトルからの自動推定

`library_source_series` は再構築可能な source metadata として維持する。

### データ作成 UI

蔵書の設定画面に Kindle Web Library を開く導線、ブックマークレットのコピー、Android Chrome での実行手順、生成 JSON のインポート手順を表示する。

### 安全境界

Kindle JSON は最大 25 MB とする。ASIN とシリーズ ID は10文字の英数字として検証し、タイトル必須、シリーズ位置は存在する場合1以上とする。著者、表紙、シリーズ名、巻位置の欠損は許容する。

実ユーザーのエクスポート JSON、ASIN 一覧、タイトル一覧、Cookie、セッション情報をログ、fixture、ADR、公開リポジトリへ追加しない。テストデータは人工的な値のみを使用する。

## Consequences

### Positive

- Kindle の蔵書・表紙・シリーズを1つの JSON から取り込める
- 表紙取得の追加ネットワーク通信、バックグラウンドジョブ、再試行状態を削除できる
- Amazon / Google Books / Open Library / NDL に蔵書情報を追加送信しない
- 表紙の正規データ源が Web Library JSON だけになり、表示規則が単純になる
- Amazon の認証情報をアプリへ渡さずに済む

### Negative

- Web Library 側で表紙 URL が欠けた書籍は「表紙なし」のままになる
- Web Library の検索経路と DOM の変更でブックマークレットが動かなくなる可能性がある
- 非コミックなど一部のシリーズではシリーズ名が取得できず、IDを使った代替表示になる

## Relationship to existing ADRs

- ADR-0026 の Kindle ownership JSON / ZIP 判断を置き換える
- ADR-0036、ADR-0041、ADR-0043、ADR-0044、ADR-0053 の表紙補完・診断判断を置き換える
- ADR-0039 の source-series テーブルと手動設定優先は維持するが、SagaSeries CSV 入力は使用しない
- ADR-0054 に従い、データ作成手順とインポート UI は library feature が所有する
