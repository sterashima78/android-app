# ADR-0013: 蔵書をサービス非依存モデルで管理し Google Play Books を API 同期する

- Status: Accepted
- Date: 2026-08-09

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

Domain は `LibraryBook` と `LibrarySource` を所有する。`LibrarySource` は現時点で `GOOGLE_PLAY_BOOKS`、`KINDLE`、`AUDIBLE` を定義するが、実際に同期処理を提供するのは Google Play Books のみとする。

### Google Play Books

Google Play Books は Google Books API の My Library を OAuth 2.0 で取得する。要求 scope は以下に限定する。

```text
https://www.googleapis.com/auth/books
```

Android 側の承認には、メール機能と同じ Google Play services の `AuthorizationClient` を利用する。ただしメールのアカウント状態とは結合せず、蔵書同期時にユーザーが Google アカウントを選択する。

同期対象は Google が標準 bookshelf として定義する次の 2 つとする。

- Purchased (`1`)
- My eBooks (`7`)

各 bookshelf を `maxResults=40` で最後までページングし、Google Books の volume ID をキーに重複排除する。全ページ取得に成功してからローカルの `GOOGLE_PLAY_BOOKS` 行を置換する。途中で API エラーが発生した場合は既存キャッシュを保持する。

アクセストークンは永続化しない。Google Play services が返した短期トークンを同期処理にだけ渡す。

### 永続化

`library_items` は source と source-specific ID の組を主キーとする。これによりサービス間で同一書籍と推測されるものを無理に同一レコードへ統合せず、将来 ISBN/ASIN 等を使った表示上の統合を追加できる。

`library_sources` には最終同期日時と表示用のアカウント名だけを保存する。Google のアクセストークンは保存しない。

これらのテーブル定義は `library:data` が所有し、repository の IO 操作時に `CREATE TABLE IF NOT EXISTS` で遅延初期化する。`core:database` は汎用的な接続・トランザクション capability のままとし、蔵書固有のスキーマを持たせない。repository の生成時には DB I/O を行わない。

Google Play Books のデータは API から再構築できるキャッシュとして扱う。

### Kindle / Audible

Kindle と Audible は今回ネットワーク API、WebView scraping、非公開 API を使用しない。将来、ユーザーが取得したファイルを明示的にインポートする data source を追加する。

その際も `LibraryBook` / `LibrarySource` を利用し、Google Play Books の同期実装とは独立させる。

## Consequences

### Positive

- Google Play Books の公式 API で蔵書を同期できる
- Google Books 固有 JSON/OAuth 処理を data layer に閉じ込められる
- Kindle/Audible の将来のファイルインポートを同じ蔵書 UI に追加できる
- API エラー時に既存の蔵書キャッシュを失わない
- OAuth access token をアプリの DB に保存しない
- 蔵書固有の DB スキーマを `core:database` に流出させない

### Negative

- Google Play Books の同期操作では Google アカウント選択が必要になる
- Purchased と My eBooks の意味や内容が Google 側で変化した場合は同期対象の見直しが必要になる
- Kindle/Audible は現時点では一覧に取り込めない

## Relationship to existing ADRs

- ADR-0003 の layer 分離に従い `domain -> data/ui` の逆依存を作らない
- ADR-0004 の concept-oriented ownership として `library` を独立させる
- `core:network` と `core:database` は汎用 capability のまま維持し、Google Books 固有処理を持たせない
