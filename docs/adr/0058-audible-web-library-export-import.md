# ADR-0058: Audible 蔵書は Web 収集 JSON を正規入力とする

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-15
- Supersedes: ADR-0026 の Audible インポート判断、ADR-0037
- Refines: ADR-0017, ADR-0028, ADR-0052, ADR-0054, ADR-0061

## Context

Amazon Request Your Data の `Library.csv` / ZIP では ASIN、タイトル、著者などは取得できる一方、表紙やシリーズを同じ入力から安定して取得できず、別の表紙補完処理が必要だった。

Audible.co.jp のログイン済み Web Library では各項目の `data-asin` を取得でき、`/library/titles?page=N` を同一オリジンで巡回することで蔵書の ASIN を収集できる。`api.audible.co.jp/1.0/catalog/products` からはタイトル、著者、ナレーター、出版社、配信日、説明、表紙、再生時間を取得でき、シリーズは単品 API から補完できる。

Web Library と Catalog API はアプリ向けに安定性が保証された公開仕様として扱わない。アプリ自身が Audible の Cookie、パスワード、セッショントークンを保存して直接アクセスする方式は採用しない。

WebView 取り込みの導入後は、外部ブラウザでブックマークレットを実行して JSON を保存し、アプリでそのファイルを選択する経路は操作と保守コードを増やすため廃止する。

## Decision

### 正規入力

Audible の取り込みは専用 WebView の collector が生成した JSON のみに限定する。JSON はアプリ内部の受け渡し形式であり、ユーザーが端末上の JSON / CSV / ZIP を選択するファイルインポートは提供しない。外部ブラウザ用ブックマークレットも提供しない。

旧 `Library.csv` / ZIP、検証時の裸配列 JSON、WebView 外で生成した JSON ファイルは受け付けず、後方互換も提供しない。

内部入力形式 v1 は次を持つ。

- `format`: `audible-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `subtitle`, `authors`, `narrators`, `publisher`, `publishedDate`, `description`, `coverUrl`, `durationMinutes`, `series`, `productUrl`
- `series`: 存在する場合 `id`, `name`, `position`

ASIN は10文字英数字、タイトル必須、`durationMinutes` と `series.position` は存在する場合1以上の整数とする。表紙 URL は HTTPS のみ保存する。商品 URL は入力値を信用せず、検証済み ASIN から `https://www.audible.co.jp/pd/{asin}` を再構築する。入力上限は25 MBとする。

### WebView での収集

1. `www.audible.co.jp/library/titles` でページを巡回して ASIN を重複排除する
2. 最初の50件を含む Catalog API ページへ WebView 内で遷移する
3. 残りの ASIN を50件ずつ取得する
4. シリーズ情報を単品 API から補完する
5. v1 JSON を生成し、Web Message 経由でネイティブ側へ渡す

生成 JSON はファイルへ保存しない。ネイティブ側では JSON 文字列を `LibraryRepository` へ直接渡し、ファイル名や `InputStream` をインポート API の契約に含めない。

認証・origin・WebView profile・メッセージ分割の境界は ADR-0061 に従う。

実ユーザーの JSON、ASIN 一覧、書名一覧、Cookie、セッション情報をログ、fixture、ADR、公開リポジトリへ追加しない。

### アプリ内の保持

Web JSON は `AudibleWebLibraryImporter` から直接 `LibraryBook` へ変換し、既存の source 置換処理で保存する。CSV への内部変換や旧 Amazon export importer は使用しない。

`durationMinutes` は現行ドメインモデルに合わせて `N時間M分` の表示文字列へ変換する。subtitle は現行モデルでは保存しない。

構造化シリーズは再生成可能な source metadata として保持する。優先順位は次の通り。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Audible Web Library / Catalog の構造化シリーズ情報
4. タイトルからの自動推定

シリーズ位置が API から明示されない場合、タイトルから推測しない。

### 表紙

Web JSON が Catalog API の `product_images` を含むため、Audible 専用のバックグラウンド表紙補完は使用しない。`coverUrl` が欠損した場合も、アプリ側では追加検索せず入力値の欠損として扱う。

## Consequences

### Positive

- Audible の取り込み操作を WebView 内に統一できる
- Android のドキュメントピッカー、ファイル名判定、外部ブックマークレット、裸配列 JSON 互換を削除できる
- domain / data API からファイルという概念を除去できる
- 1つの内部 JSON から蔵書、表紙、著者、ナレーター、再生時間、シリーズを取り込める
- Audible の認証情報をデータ層へ渡さずに済む

### Negative

- WebView または Audible 側の Web 経路が利用できない場合、ファイルインポートによるフォールバックはない
- Web Library DOM と Catalog API は安定性が保証された公開仕様ではなく、Audible 側の変更で collector が動作しなくなる可能性がある
- Catalog API で表紙が得られない書籍はアプリ側で追加補完しない
- 現行モデルでは subtitle と durationMinutes を独立した型として保持しない

## Relationship to existing ADRs

- ADR-0026 の Audible `Library.csv` / ZIP 入力判断を置き換える
- ADR-0037 の Audible バックグラウンド表紙補完を廃止する
- ADR-0028 の Narrator / Duration / 商品 URL 保持は維持する
- ADR-0017 の手動シリーズ設定、解除、自動推定の優先関係を維持する
- ADR-0054 に従い、取り込み UI は library feature が所有する
- ADR-0061 に従い、Audible の Web 収集は専用 WebView profile 内で完結させ、外部ファイル入力は提供しない
