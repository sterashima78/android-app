# ADR-0044: Kindle 表紙補完で一時障害と日本語書誌解決を分離する

- Status: Accepted
- Date: 2026-08-14
- Refines: ADR-0036, ADR-0041, ADR-0052, ADR-0043

## Context

Kindle 表紙補完では Amazon 商品ページ、Google Books、Open Library を順に利用している。実運用では Amazon 商品ページ自体は取得できても既知の OGP・商品画像要素・JSON-LD・image_src に表紙候補が現れないケースがあり、Google Books が 503 を返すと次回の書籍単位再試行まで待つ必要があった。また、日本語書籍ではタイトルから ISBN を解決できれば別プロバイダの ISBN 検索へ切り替えられる可能性がある。

Google Books の公開書誌検索はユーザー個人のデータへのアクセスではないが、Google Books API の現行仕様ではリクエスト元アプリケーションを API key または OAuth 2.0 access token で識別する必要がある。パブリックリポジトリや配布 APK に無制限の API key を埋め込むことは避ける。ADR-0043 の判断に従い、既存の Books OAuth grant が利用できる場合だけ Google Books を使用する。

国立国会図書館サーチは OpenSearch を含む検索 API を提供しており、ISBN、タイトル、作成者を検索条件にできる。一方、書影 API は 2026-03-31 でサービス終了しているため、新規の表紙画像取得先として利用できない。

## Decision

Google Books の認証方式は ADR-0043 の OAuth access token 方針を維持する。リポジトリへ API key を追加せず、access token がない場合は従来どおり Google Books をスキップする。API key の端末ローカル注入や制限方法は、必要になった場合に独立した設計判断として扱う。

Google Books が HTTP 502、503、504 を返した場合だけ、同一検索をその場で1回再実行する。成功すれば通常結果として処理し、2回目も失敗した場合は ADR-0043 の書籍単位バックオフへ渡す。429、408、その他の 5xx はその場では繰り返さず、既存の Retry-After とバックオフ処理へ渡す。診断 trace には `requestAttempts` を件数だけ保存する。

ISBN を持たない Kindle 書籍で Google Books のタイトル検索から ISBN を解決できず、タイトルに日本語文字が含まれる場合のみ NDL Search OpenSearch を書誌解決用フォールバックとして利用する。

- `title` と、存在する場合は先頭著者を `creator` として検索する。
- `mediatype=books` と最大10件を指定する。
- 応答 RSS の候補タイトルを正規化し、検索タイトルとの完全一致だけを採用する。
- 候補中のチェックサムが有効な ISBN-13 だけを採用する。
- 一意な ISBN-13 に絞れた場合だけ `SAME_WORK` の解決済み識別子として扱う。
- 複数 ISBN に分かれた場合は `AMBIGUOUS` として ISBN を採用しない。
- NDL Search から表紙 URL は生成しない。終了済みの書影 API は呼び出さない。

NDL Search で ISBN を解決できた場合、Google Books の先行タイトル検索が通信エラーでなければ Google Books の ISBN 検索を試し、その後 Open Library の ISBN 検索へ進む。Google Books がすでに一時通信エラーだった場合は同一処理内でさらに Google Books を呼ばず、NDL 解決 ISBN を Open Library に渡す。次回の書籍単位再試行では通常どおり Google Books を再評価する。

Amazon 商品ページで既知の抽出器が表紙を見つけられない場合の診断を拡張する。ただし HTML、画像 URL、script 本文は保存せず、次の手掛かりの出現件数だけを trace 属性へ保存する。

- `/images/I/` パス
- `hiRes` フィールド
- `large` と画像パスの組み合わせ
- `colorImages` フィールド
- `ImageBlockATF` マーカー

これらは将来の抽出器追加を判断するための観測情報であり、その場で未検証の URL を表紙として採用する根拠にはしない。

## Privacy and public repository constraints

NDL Search へ送信する情報は、表紙補完を明示的に有効化した書籍の検索用タイトルと先頭著者に限定する。Amazon、Google Books、Open Library と同様に、インポートファイルそのものや蔵書一覧を送信しない。

診断情報には認証情報、Cookie、HTTP ヘッダー、HTML/XML/JSON レスポンス本文、画像 URL を保存しない。実ユーザーの ASIN、タイトル、著者、ISBN を fixture や ADR に追加せず、テストは人工データだけを使用する。

## Consequences

### Positive

- 短い Google Books 503 障害なら15分の書籍単位待機に入る前に回復できる。
- 429 に対して不要な即時再試行を行わない。
- 日本語書籍で ISBN が欠落していても、NDL Search の書誌情報を別プロバイダの厳密検索に利用できる。
- 終了済み NDL 書影 API や公開 API key に依存しない。
- Amazon HTML 構造変更の兆候を、本文を保存せずに追加観測できる。

### Negative

- Google Books の access token がない環境では、引き続き Google Books を利用できない。
- 日本語タイトルの一部では NDL Search への追加リクエストが発生する。
- NDL Search が同一作品の複数版を返した場合は保守的に ISBN を採用しないため、表紙取得率が上がらない場合がある。
- Amazon の追加診断は候補の存在を示すだけで、新しい HTML 形式から直ちに表紙を取得するものではない。

## Relationship to existing ADRs

- ADR-0036 の「表示メタデータ補完だけを外部通信とし、所有情報インポートを外部サービスに依存させない」原則を維持する。
- ADR-0041 の「本文を保存せず、共有可能な構造化診断だけを残す」原則を Amazon の script 手掛かりへ拡張する。
- ADR-0052 の Amazon → Google Books → Open Library と構造化診断を維持する。
- ADR-0043 の書誌識別子、OAuth、書籍単位再試行方針を維持し、日本語書誌同定と短時間リトライを追加する。
