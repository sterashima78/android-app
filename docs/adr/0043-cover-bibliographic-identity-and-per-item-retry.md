# ADR-0043: 表紙補完で書誌同定と画像取得を分離し、書籍単位で再試行する

- Status: Accepted
- Date: 2026-08-14
- Refines: ADR-0036, ADR-0041, ADR-0053

## Context

Kindle 表紙補完は Amazon 商品ページ、Google Books、Open Library を順に利用している。実際の診断では Amazon がアクセス確認ページになり、Google Books が HTTP 429、Open Library が正常に `NOT_FOUND` となった場合でも、画面上は最終的に `NOT_FOUND` と表示されていた。これは通信・レート制限による取得不能と、本当に候補が存在しない状態を区別できない。

また Google Books / Open Library のタイトル検索では、表紙 URL を持つ候補だけを先に残してからタイトル・著者・巻数を照合していた。そのため、高信頼で同一書籍と判断でき、ISBN まで返っているが表紙だけ欠落している候補から書誌識別子を引き継げなかった。

従来の WorkManager 再試行は一冊の通信失敗を Worker 自体の失敗として扱うため、後続書籍の処理も待たせる。短時間の再試行上限を使い切った後は `ERROR` を30日間の未取得相当として扱っていたため、障害種別に応じた適切な待機もできなかった。

Google Books の表紙検索で API key を APK に埋め込むことは、公開リポジトリと公開可能なバイナリの方針に反する。既存の Google Play Books 連携は Google Identity Services で Books scope の OAuth 認可を取得している。

## Decision

### 状態を分離する

`LibraryCoverAcquisitionState` に `ERROR` を追加する。

- `NOT_FOUND`: 必要な検索経路を正常に完了したが、高信頼な表紙候補を確認できない
- `AMBIGUOUS`: 高信頼候補が複数残り、誤表紙を避けるため採用できない
- `ERROR`: 通信障害、HTTP 429 / 5xx、アクセス確認、解析不能などにより検索経路を正常完了できない

DB の `CoverLookupStatus.ERROR` を UI で `NOT_FOUND` に変換しない。

### 書誌同定と画像取得を分離する

タイトル・著者・巻数の照合は、表紙の有無に関係なく全候補へ適用する。高信頼候補が一件に定まった場合、その候補が返す ISBN-13 / ISBN-10 を一時的な `ResolvedBookIdentifier` として保持する。

識別子には `EXACT_EDITION` または `SAME_WORK` の関係を付ける。タイトル・著者による解決は `SAME_WORK`、元データまたは ISBN 検索による一致は `EXACT_EDITION` とする。

外部から解決した ISBN は `library_items.isbn10` / `isbn13` へ書き戻さない。紙版・電子版・Kindle版が別 ISBN を持つ可能性があるため、表紙取得処理内の一時書誌情報と診断情報としてのみ利用する。

Kindle の検索順は次とする。

1. Amazon 商品ページ / ASIN
2. 元 ISBN がある場合は Google Books / ISBN
3. 元 ISBN がない場合は Google Books / title + author
4. Google Books の高信頼候補から ISBN を解決できた場合は Google Books / ISBN
5. 解決済み ISBN がある場合は Open Library / ISBN
6. Open Library / title + author
7. 高信頼な表紙が一件に定まらない場合に集約判定する

同一処理内で同じ識別子を不必要に永続保存しない。

### Google Books 認証

Google Books の公開 Volume 検索用 API key はソースコード、Gradle 設定、APK に埋め込まない。

既存の Books OAuth grant を Google Identity Services からユーザー操作なしで取得できる場合だけ、その access token を `Authorization: Bearer` として Google Books 検索に利用する。解決 UI が必要な場合はバックグラウンド Worker から UI を起動せず、Google Books のステップを `AUTH_UNAVAILABLE` としてスキップし、Open Library へ進む。

access token、Authorization header、Cookie、API key は DB、診断ログ、リポジトリへ保存しない。

### 書籍単位の再試行

`library_item_external_metadata` に後方互換の nullable / default column として以下を追加する。

- `retry_count INTEGER NOT NULL DEFAULT 0`
- `next_attempt_at INTEGER`

一時的な provider failure は Worker 自体を失敗させず、対象書籍を `ERROR` として保存する。その後すぐ次の書籍を処理する。

再試行間隔は次とする。

- 通常のネットワーク障害、HTTP 408 / 5xx: 15分、2時間、以降24時間
- HTTP 429: `Retry-After` が秒数で得られる場合はそれを優先する
- Amazon `CHALLENGE_PAGE`: 24時間
- 非再試行エラー: 30日後に自動再確認可能とする
- `NOT_FOUND` / `AMBIGUOUS`: 従来どおり30日で stale とする

Worker は現在処理可能な項目がなく、将来の `next_attempt_at` が存在する場合、その最短時刻まで遅延した continuation を追加する。

ユーザーが「未取得を再試行」を実行した場合は未取得メタデータを削除し、既存の遅延 Worker を置き換えて即時実行する。

### 診断 v2

`diagnostic_trace` を version 2 とし、最大8ステップを保存する。各ステップには必要に応じて次を保存する。

- provider
- operation
- status / reason
- retryable
- 解析済み `retryAfterSeconds`
- HTTP status / response bytes
- 候補数、表紙候補数、タイトル・巻数・著者一致数
- search mode / match relation などの安全な属性

さらに解決した ISBN の type、value、relation、source と `nextAttemptAt` を保存できる。

次は保存しない。

- HTTP / HTML response body
- Cookie
- Authorization / OAuth token / API key
- request header 全体
- 外部 API の候補説明文
- 実ユーザーデータを fixture、テスト、ADR へ転記したもの

テストデータは人工データのみを利用する。

## Consequences

### Positive

- 一時障害と本当の未発見を UI と DB で区別できる
- Amazon や Google Books の一時障害が一冊の後続キューを止めない
- 表紙なし候補から ISBN を引き継ぎ、別検索経路で表紙を見つけられる可能性が上がる
- Google Books 用 secret を公開リポジトリや APK に追加しない
- 429 / challenge page をサービス特性に応じた間隔で再試行できる
- 診断情報から書誌同定、表紙取得、再試行判断を分離して調査できる

### Negative

- `library_item_external_metadata` の schema と Worker scheduling が複雑になる
- Google Play Books の Books OAuth grant がない利用者は Google Books fallback を利用せず Open Library へ進む
- OAuth token を取得するため Worker ごとに Google Identity Services への確認が発生し得る
- 同一 work の紙版表紙を採用する場合、Kindle版固有の装丁と異なる可能性は残る

## Relationship to existing ADRs

- ADR-0036 の外部表紙補完を任意とすること、誤表紙を避けること、Amazon secret を持たない方針を維持する
- ADR-0041 のレスポンス本文や認証情報を自動保存しない方針を維持する
- ADR-0053 の Amazon → Google Books → Open Library と構造化診断を発展させる
- ADR-0006 の再開可能な WorkManager 処理方針に従い、一冊の障害をキュー全体の障害にしない
