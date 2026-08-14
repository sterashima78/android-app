# ADR-0057: Kindle 蔵書は Web Library から生成した JSON を正規入力とする

- Status: Accepted
- Date: 2026-08-14
- Supersedes: ADR-0026 の Kindle インポート判断、ADR-0031、ADR-0033、ADR-0039 の Kindle 入力形式判断
- Refines: ADR-0017, ADR-0036, ADR-0054

## Context

Kindle 蔵書はこれまで Amazon Request Your Data の `Digital.Content.Ownership*.json` / ZIP を正規入力とし、シリーズ情報は同 ZIP の `Kindle.SagaSeriesInfra.CollectionRightsDatastore.csv` から補完していた。しかし ownership データには表紙 URL がなく、実データではシリーズ情報も取得・結合のために複数ファイルへ依存する。表紙は別途 Amazon 商品ページ、Google Books、Open Library へ問い合わせる必要があり、未取得が多数発生するケースがあった。

Kindle Web Library を実ブラウザで検証したところ、ログイン済みの `read.amazon.co.jp` 内では蔵書一覧の検索レスポンスから ASIN、タイトル、著者、Amazon の表紙 URL を取得できる。また MANGA 一覧の `parentSeriesInfo` からシリーズ ID と巻位置を取得でき、シリーズ表示タブの DOM から多くのシリーズ ID に対応する表示名を取得できることを確認した。

一方、非コミックの一部シリーズでは Web Library のシリーズ表示タブにシリーズ名が現れない。Amazon 商品ページではシリーズ名を確認できるが、`read.amazon.co.jp` と `www.amazon.co.jp` の相互 `fetch` はブラウザの同一オリジン制約により失敗した。このため、シリーズ ID と巻位置は取得できてもシリーズ名だけ取得できない正常ケースが存在する。

これらの API は Amazon が公開 API として提供しているものではない。アプリ自身が Amazon の認証情報や Cookie を保持して直接アクセスする方式は採用しない。

## Decision

### Kindle の正規入力

Kindle のインポートは、アプリが設定画面で提示するブックマークレットを、ユーザーがログイン済みの Kindle Web Library 上で実行して生成する JSON のみを正規入力とする。

ブックマークレットは `read.amazon.co.jp` の同一オリジン内でのみ蔵書情報を取得し、結果を端末へ JSON として保存する。アプリは Amazon の Cookie、セッショントークン、パスワードその他の認証情報を受け取らず、保存もしない。

入力形式 v1 は次を持つ。

- `format`: `kindle-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `authors`, `coverUrl`, `series`
- `series`: 存在する場合 `id`, `name`, `position`

`exportedAt`, `count`, `stats` は生成時の診断情報として許容するが、インポートの正規データにはしない。未知フィールドは無視する。

ブックマークレットは Web Library の BOOKS 一覧から `resourceType == EBOOK` のみを書き出し、サンプルを除外する。同一 ASIN が複数現れた場合は ASIN 単位で重複排除する。

アプリの Kindle インポート UI は JSON のみを選択対象とする。旧 `Digital.Content.Ownership*.json` / ZIP と SagaSeries CSV は Kindle インポートの入力として扱わない。後方互換は提供しない。Audible の `Library.csv` / ZIP インポートは変更しない。

### 表紙

`coverUrl` は `library_items.thumbnail_url` へ保存し、通常表示ではこの URL を優先する。Web Library JSON に表紙がない書籍だけ、ADR-0036 以降で定義した既存の Kindle 表紙補完を利用する。

これにより、インポート時に取得できた Amazon の表紙を再検索せず、欠損だけを補完対象にする。

### シリーズ

構造化シリーズの同一性は `series.id` で判定する。`series.name` は表示用の任意情報、`series.position` は任意の巻順情報とする。

`series.name == null` でもシリーズ情報を破棄しない。アプリではシリーズ ID を保持し、同じ ID の書籍を同一シリーズとしてグルーピングする。表示名が取得できない場合はシリーズ ID を含む「シリーズ名未取得」の表示を使用する。タイトル推定は、構造化シリーズ ID が存在する書籍の同一性判定には使用しない。

既存の優先順位は維持する。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Kindle Web Library の構造化シリーズ情報
4. ADR-0017 のタイトル推定

`library_source_series` は引き続き再構築可能な source metadata として利用する。`series_id` を正規のグルーピングキーとして扱い、シリーズ名欠損時は DB 上では空文字を許容表現として使用して読み出し時に未取得へ戻す。

### データ作成 UI

蔵書の設定画面に次を表示する。

- Kindle Web Library のシリーズ画面を開くリンク
- エクスポート用ブックマークレットをクリップボードへコピーするボタン
- Android Chrome でブックマークレットを登録・実行する手順
- 生成された `kindle-library-export-YYYY-MM-DD.json` を Kindle のインポートから選択する手順

ブックマークレットはアプリのバージョンと一緒に管理し、JSON パーサと生成形式の整合を保つ。

### 安全境界

Kindle JSON は最大 25 MB とする。ASIN とシリーズ ID は10文字の英数字として検証し、タイトル必須、シリーズ位置は存在する場合1以上とする。著者、表紙、シリーズ名、巻位置の欠損は許容する。

実ユーザーのエクスポート JSON、ASIN 一覧、タイトル一覧、Cookie、セッション情報をログ、テスト fixture、ADR、パブリックリポジトリへ追加しない。テストデータは人工的な値のみを使用する。

## Consequences

### Positive

- Kindle の蔵書・表紙・シリーズ情報を1つのファイルから取り込める
- 大半の書籍で Amazon が Web Library に表示している表紙をそのまま利用できる
- タイトル文字列に依存せず、Amazon のシリーズ ID で安定してグルーピングできる
- Amazon の認証情報をアプリへ渡さず、ブラウザの既存ログインセッションだけを利用できる
- Amazon Request Your Data の巨大 ZIP、ownership イベント復元、シリーズ CSV の再走査が不要になる

### Negative

- Kindle Web Library の検索経路と DOM は公開 API ではなく、Amazon の画面変更でブックマークレットが動かなくなる可能性がある
- ブックマークレットの登録と実行はユーザー操作が必要
- 非コミックなど一部のシリーズではシリーズ名が取得できず、IDを使った代替表示になる
- Amazon Request Your Data 形式からの直接インポートはできなくなる

## Relationship to existing ADRs

- ADR-0026 は Audible の入力形式判断として継続するが、Kindle の ownership JSON / ZIP に関する判断は本 ADR が置き換える。
- ADR-0031 の Kindle ZIP 再帰走査は正規インポート経路から外れるため、本 ADR が置き換える。
- ADR-0033 の ownership イベント解釈は正規インポート経路から外れるため、本 ADR が置き換える。
- ADR-0039 の source-series テーブルと手動設定優先の考え方は維持するが、SagaSeries CSV を入力にする判断は本 ADR が置き換える。
- ADR-0036 の表紙補完は、Web Library JSON に `coverUrl` がない書籍へのフォールバックとして維持する。
- ADR-0054 に従い、データ作成手順とインポート UI は library feature が所有する。
