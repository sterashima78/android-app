# ADR-0090: ヤンマガWeb作品ページをHTML由来RSSとして購読する

- Status: Accepted
- Date: 2026-08-17

## Context

RSS機能はRSS 2.0 / Atom / RSS 1.0と、Webページ内の `link[rel~=alternate]` によるフィード検出を扱っている。一方、ヤンマガWebの作品ページは作品単位のRSSリンクを公開していないため、そのままでは通常のフィード追加経路から購読できない。

公開実装 `shishi/rss_generator` では、ヤンマガWeb作品ページのエピソード一覧を `.mod-episode-item`、タイトルを `.mod-episode-title`、リンクを `.mod-episode-link`、公開日を `.mod-episode-date` から取得し、`data-is-free="false"` の項目を除外することで作品更新フィードを生成している。

本アプリはサーバーを持たず、主要データと処理を端末内へ閉じる方針である。特定の外部RSS生成サービスへ依存すると、そのサービスの可用性・登録作品・運用変更に購読機能が依存する。また、作品追加のたびに外部ジェネレーター側へ設定を追加する運用も避けたい。

ADR-0078では漫画RSSを `COMIC` として扱い、自動AI enrichmentから除外する方針を採用している。ヤンマガWeb由来の記事も同じ意味を持つため、この既存のコンテンツ種別へ接続する必要がある。

## Decision

### 1. ヤンマガWebの作品URLをフィードURLとして直接登録できるようにする

`https://yanmaga.jp/comics/{work}` および同等の `www.yanmaga.jp` URLだけをヤンマガWeb作品URLとして認識する。

`/comics/{work}/{episode}` のようなエピソードURLや、他ドメインのURLは対象にしない。通常のRSS・Atom・Webページフィード検出は既存の `FeedClient` が引き続き処理する。

### 2. RSS data層にサイト固有のHTMLアダプターを置く

`:feature:rss:data` に `YanmagaFeedClient` を置き、共有 `HttpClient` で作品ページHTMLを取得してjsoupで解析する。

解析セレクターは公開実装 `shishi/rss_generator` と同じものを使用する。

- エピソード: `.mod-episode-item`
- タイトル: `.mod-episode-title`
- URL: `.mod-episode-link`
- 公開日: `.mod-episode-date`
- 非無料話の除外: `data-is-free="false"`

取得した作品ページの最終URLを `feedUrl` と `siteUrl` に保存する。各エピソードURLを外部IDと重複判定の基準にし、公開日は日本時間の日付として解釈する。

サイト固有のHTML構造を汎用RSS parserへ混在させず、`DefaultFeedRepository` がURL種別に応じて通常フィードとヤンマガWebアダプターを切り替える。

### 3. 登録時にフィードを `COMIC` として明示する

ヤンマガWeb作品URLから新規登録したフィードは `ContentType.COMIC` を明示設定する。

これによりADR-0078の既存ルールをそのまま利用し、自動要約や自動AI enrichmentを実行しない。登録後にユーザーがコンテンツ種別を変更した場合、その設定を更新処理で上書きしない。

### 4. 条件付きHTTP取得を維持する

通常フィードと同様にETagとLast-Modifiedを保存し、更新時に `If-None-Match` / `If-Modified-Since` を送る。304応答ではHTMLを再解析しない。

### 5. Playwright・WebView・外部RSSホストへ依存しない

初期実装では通常のHTTP取得で得られるHTMLだけを解析する。PlaywrightやWebViewをRSSバックグラウンド取得へ導入せず、外部の `rss_generator` ホストにも依存しない。

ヤンマガWebが将来JavaScript実行後にしかエピソード一覧を提供しない構造へ変更した場合は、エピソード一覧未検出を明示的な取得失敗として扱い、その時点で取得方式を再検討する。

### 6. 公開リポジトリに実購読データを残さない

テスト・ADR・ログへ実際に購読している作品名や作品URLを追加しない。回帰テストは架空の作品名・作品パスだけでHTML構造を再現する。

## Consequences

### Positive

- ユーザーはヤンマガWebの作品URLを通常のフィード追加欄へ貼り付けるだけで購読できる。
- 外部RSS生成サービスへの依存や作品ごとのサーバー設定が不要になる。
- 通常RSSの解析経路を変更せず、サイト固有ロジックを独立して保守できる。
- 漫画として自動分類され、不要なAIタスクが自動的に抑止される。
- ETag / Last-Modifiedが提供される場合は不要な再解析を避けられる。

### Negative

- ヤンマガWebのHTMLクラス名変更に追従する必要がある。
- サイト側がJavaScript必須の構造へ移行した場合、このHTTP+jsoup方式だけでは取得できなくなる。
- サイト固有アダプターがRSS data層に1つ増える。

## Relationship to existing ADRs

- ADR-0003: RSSの外部取得実装をdata層へ置く既存のモジュール境界を維持する。
- ADR-0066相当のフィード取得方針: 通常RSSの条件付き取得・重複排除を維持し、HTML由来フィードだけを分岐する。
- ADR-0078: ヤンマガWeb由来フィードを `COMIC` として登録し、既存のコンテンツ種別継承とAI処理可否を利用する。
