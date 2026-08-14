# ADR-0013: 蔵書をサービス非依存モデルで管理し Google Play Books を API 同期する

- Status: Accepted
- Date: 2026-08-09
- Updated: 2026-08-11

## Context

電子書籍・オーディオブックの所有情報をアプリ内で横断して参照できる蔵書機能を追加する。

対象サービスは Google Play Books、Kindle、Audible を想定するが、利用可能な連携手段が異なる。Google Books API は OAuth 2.0 で認証済みユーザーの My Library bookshelf を取得できる一方、Kindle と Audible は今回の実装で安定した公式蔵書 API を前提にしない。

蔵書そのものは将来複数サービスを統合する概念であり、Google Play Books のデータ構造を domain model に露出させると、Kindle/Audible の追加時に UI と永続化構造までサービス固有仕様に引きずられる。

## Decision

### 独立した library feature

ADR-0003 / ADR-0004 に従い、蔵書を独立した ownership として次の module に分離する。

```text
:feature:library:domain
:feature:library:data
:feature:library:ui
```

Domain は `LibraryBook` と `LibrarySource` を所有する。`LibrarySource` は `GOOGLE_PLAY_BOOKS`、`KINDLE`、`AUDIBLE` を定義し、サービスごとの取得方法は data layer に閉じ込める。

### Google Play Books

Google Play Books は Google Books API の My Library を OAuth 2.0 で取得する。要求 scope は以下に限定する。

```text
https://www.googleapis.com/auth/books
```

Android 側の承認には、メール機能と同じ Google Play services の `AuthorizationClient` を利用する。ただしメールのアカウント状態とは結合せず、蔵書同期時にユーザーが Google アカウントを選択する。

同期対象は Google が標準 bookshelf として定義する次の 2 つとする。

- Purchased (`1`)
- My eBooks (`7`)

各 bookshelf を `maxResults=40` で最後までページングし、Google Books の volume ID をキーに重複排除する。My eBooks を先に、Purchased を後に処理し、両方に含まれる同一 Volume では Purchased 側の状態を最終値として残す。全ページ取得に成功してからローカルの `GOOGLE_PLAY_BOOKS` 行を置換する。途中で API エラーが発生した場合は既存キャッシュを保持する。

購入済み判定には、認証済み Volume の `userInfo.isPurchased` と Purchased bookshelf (`1`) の所属情報を利用する。My eBooks (`7`) は購入済み書籍が自動追加される一方でユーザーが手動追加できるため、My eBooks に存在することだけでは購入済みと判定しない。

Google Books API が返す URL は用途を区別する。対象 Volume を読むための `accessInfo.webReaderLink` がある場合は `LibraryBook.infoUrl` に保存する。`webReaderLink` がなく購入済みと判定できる場合は、Google Play Books アプリの front-door 起動用フォールバックとして `https://play.google.com/books` を保存する。書籍情報ページである `volumeInfo.infoLink` は読書 URL のフォールバックに利用しない。未購入かつ `webReaderLink` がない Volume は `infoUrl = null` とする。Android での Reader URL と Play Books フォールバックの扱いは ADR-0048 に従う。

アクセストークンは永続化しない。Google Play services が返した短期トークンを同期処理にだけ渡す。

### Kindle / Audible のファイルインポート

Kindle と Audible には、ネットワーク API、WebView scraping、非公開 API、Amazon のアカウント情報・Cookie・セッション情報を利用しない。

ユーザーが自身で取得した蔵書データを Android の `OpenDocument` で明示的に選択し、端末内で解析する。対応入力は CSV、TSV、およびそれらを格納した ZIP とする。Amazon 側のエクスポート形式は固定スキーマとして扱わず、一般的な英語ヘッダー名の別名を許容する。

インポータは次の責務を `library:data` に持つ。

- CSV/TSV の引用符、エスケープされた引用符、セル内改行を解析する
- UTF-8 BOM を許容する
- ASIN 等の source-specific ID があれば優先し、無い場合は source/title/authors/date から安定した SHA-256 派生 ID を生成する
- ZIP はファイルシステムへ展開せずストリームで読み、CSV/TSV/text の候補のみを解析する
- 入力 25 MB、ZIP 展開後合計 50 MB、ZIP 100 エントリまでに制限する
- 認識可能な書籍が 0 件の場合は失敗とし、既存の Kindle/Audible 蔵書を消さない

ファイル選択後の URI 権限、元ファイル、元ファイルの内容は永続化しない。解析結果の `LibraryBook` のみを DB に保存する。ログやテスト fixture に実ユーザーのエクスポート内容を含めない。

インポート成功時は対象 source の `library_items` だけをトランザクション内で置換する。Google Play Books や他方の Amazon source は変更しない。既存の表示非表示・手動シリーズ設定は source/sourceId をキーに別テーブルで保持されるため、同じ ID の再インポート後も維持される。

### 永続化

`library_items` は source と source-specific ID の組を主キーとする。これによりサービス間で同一書籍と推測されるものを無理に同一レコードへ統合せず、将来 ISBN/ASIN 等を使った表示上の統合を追加できる。

`library_sources` には最終同期・インポート日時と、必要な場合だけ表示用のアカウント名を保存する。Google のアクセストークンは保存しない。Kindle/Audible ではアカウント名も保存しない。

これらのテーブル定義は `library:data` が所有し、repository の IO 操作時に `CREATE TABLE IF NOT EXISTS` で遅延初期化する。`core:database` は汎用的な接続・トランザクション capability のままとし、蔵書固有のスキーマを持たせない。repository の生成時には DB I/O を行わない。

Google Play Books のデータと、再インポート可能な Kindle/Audible のデータは source ごとに再構築可能なローカルコピーとして扱う。

## Consequences

### Positive

- Google Play Books の公式 API で蔵書を同期できる
- Kindle/Audible を Amazon の認証情報や非公開 API に依存せず取り込める
- Google Books 固有 JSON/OAuth と Amazon ファイル解析を data layer に閉じ込められる
- 取得・解析エラー時に既存の蔵書を失わない
- OAuth access token や Amazon セッション情報をアプリの DB に保存しない
- 蔵書固有の DB スキーマを `core:database` に流出させない
- Google Books の情報ページを読書 URL と誤認して Google Play ストアへ遷移しない
- 購入済み書籍で Reader URL が返らない場合でも Google Play Books アプリまでフォールバックできる

### Negative

- Google Play Books の同期操作では Google アカウント選択が必要になる
- Purchased と My eBooks の意味や内容が Google 側で変化した場合は同期対象と購入判定の見直しが必要になる
- 購入済みフォールバックは Play Books のホームを開くため、対象書籍を自動選択できるとは限らない
- `webReaderLink` が返らず購入済みとも判定できない Google Books 項目はアプリから直接読書開始できない
- Kindle/Audible はユーザーがデータファイルを取得して手動でインポートする必要がある
- Amazon のエクスポート列名が変わった場合は importer のヘッダー別名を追従する必要がある
- source-specific ID が無いデータでは派生 ID を使うため、タイトル・著者・日付が大きく変わると同一書籍を別レコードとして扱う可能性がある

## Relationship to existing ADRs

- ADR-0003 の layer 分離に従い `domain -> data/ui` の逆依存を作らない
- ADR-0004 の concept-oriented ownership として `library` を独立させる
- `core:network` と `core:database` は汎用 capability のまま維持し、Google Books 固有処理や Amazon ファイル形式固有処理を持たせない
- Google Books の Reader URL と Android 外部アプリ連携は ADR-0048 に従う
