# ADR-0058: Audible 蔵書は Web Library から生成した JSON を正規入力とする

- Status: Accepted
- Date: 2026-08-14
- Supersedes: ADR-0026 の Audible インポート判断、ADR-0037
- Refines: ADR-0017, ADR-0028, ADR-0052, ADR-0054

## Context

Amazon Request Your Data の `Library.csv` / ZIP では ASIN、タイトル、著者などは取得できる一方、表紙やシリーズを同じ入力から安定して取得できず、別の表紙補完処理が必要だった。

Audible.co.jp のログイン済み Web Library では各項目の `data-asin` を取得でき、`/library/titles?page=N` を同一オリジンで巡回することで全蔵書の ASIN を収集できることを実機で確認した。`api.audible.co.jp/1.0/catalog/products` からはタイトル、著者、ナレーター、出版社、配信日、説明、表紙、再生時間を取得できる。複数 ASIN は1リクエスト最大50件で、シリーズは単品 `/1.0/catalog/products/{asin}?response_groups=series` から補完できる。

Web Library と Catalog API はアプリ向けに安定性が保証された公開仕様として扱わない。アプリ自身が Audible の Cookie、パスワード、セッショントークンを保存して直接アクセスする方式は採用しない。

## Decision

### 正規入力

Audible のインポート入力は、蔵書設定画面で提示する2段階ブックマークレットをログイン済みブラウザ上で実行して生成する JSON のみに限定する。

旧 `Library.csv` / ZIP は受け付けず、後方互換も提供しない。

入力形式 v1 は次を持つ。

- `format`: `audible-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `subtitle`, `authors`, `narrators`, `publisher`, `publishedDate`, `description`, `coverUrl`, `durationMinutes`, `series`, `productUrl`
- `series`: 存在する場合 `id`, `name`, `position`

検証過程で生成した、ルートが書籍配列そのものの JSON も移行用として受け付ける。新しいブックマークレットは v1 envelope を生成する。

ASIN は10文字英数字、タイトル必須、`durationMinutes` と `series.position` は存在する場合1以上の整数とする。表紙 URL は HTTPS のみ保存する。商品 URL は入力値を信用せず、検証済み ASIN から `https://www.audible.co.jp/pd/{asin}` を再構築する。入力上限は25 MBとする。

### ブラウザでの生成

1段階目は `www.audible.co.jp/library/titles` 上でページを巡回して ASIN を重複排除し、最初の50件で Catalog API へ遷移する。残り ASIN は URL fragment に保持する。

2段階目は `api.audible.co.jp` 上で残りを50件ずつ取得し、シリーズ情報を5件ずつ並列で単品 API から補完する。説明はプレーンテキスト化し、500px 表紙を優先して保存する。

実ユーザーの JSON、ASIN 一覧、書名一覧、Cookie、セッション情報をログ、fixture、ADR、公開リポジトリへ追加しない。

### アプリ内の保持

Web JSON は `AudibleWebLibraryImporter` から直接 `LibraryBook` へ変換し、既存の source 置換処理で保存する。CSV への内部変換や旧 Amazon export importer は使用しない。

`durationMinutes` は現行ドメインモデルに合わせて `N時間M分` の表示文字列へ変換する。subtitle は現行モデルでは保存しない。

構造化シリーズは再生成可能な `library_source_series` に保持する。優先順位は次の通り。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Audible Web Library / Catalog の構造化シリーズ情報
4. タイトルからの自動推定

シリーズ位置が API から明示されない場合、タイトルの「上」「下」などから推測しない。

### 表紙

Web JSON が Catalog API の `product_images` を含むため、Audible 専用のバックグラウンド表紙補完は廃止する。

次を削除する。

- Audible 商品ページ / Catalog API の表紙補完クライアント
- Audible 表紙補完 repository / Worker / WorkManager scheduler
- Audible の表紙取得待ち・再試行キュー

表紙取得状況画面とバックグラウンド表紙補完は Kindle のみを対象とする。Audible の `coverUrl` が欠損した場合も、アプリ側では追加検索せず入力値の欠損として扱う。

## Consequences

### Positive

- 1つの JSON から蔵書、表紙、著者、ナレーター、再生時間、シリーズを取り込める
- CSV / ZIP 互換コードと Audible 専用表紙バックグラウンド処理を削除できる
- インポート後の追加ネットワークアクセスを Audible について不要にできる
- Audible の認証情報をアプリへ渡さず、ブラウザのログイン状態だけを利用できる

### Negative

- Web Library DOM と Catalog API は安定性が保証された公開仕様ではなく、Audible 側の変更でブックマークレットが動かなくなる可能性がある
- Android Chrome では2つのブックマークレットを順に実行する必要がある
- Catalog API で表紙が得られない書籍はアプリ側で追加補完しない
- 現行モデルでは subtitle と durationMinutes を独立した型として保持しない

## Relationship to existing ADRs

- ADR-0026 の Audible `Library.csv` / ZIP 入力判断を置き換える
- ADR-0037 の Audible バックグラウンド表紙補完を廃止する
- ADR-0052 の表紙取得キューは Kindle 専用に縮小する
- ADR-0028 の Narrator / Duration / 商品 URL 保持は維持する
- ADR-0017 の手動シリーズ設定、解除、自動推定の優先関係を維持する
- ADR-0054 に従い、エクスポート手順とブックマークレットは library feature が所有する
