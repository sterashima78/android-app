# ADR-0035: Kindle 表紙を Open Library から明示的に補完する

- Status: Accepted
- Date: 2026-08-13
- Refines: ADR-0013, ADR-0026, ADR-0033

## Context

Kindle の `Digital.Content.Ownership*.json` からは ASIN と商品名を取得できる一方、観測済みデータでは表紙 URL や ISBN が常に得られるわけではない。蔵書 UI は `LibraryBook.thumbnailUrl` をすでに表示できるため、欠落している表紙だけを外部カタログから補完したい。

Amazon の公式商品 API は認証用 secret を必要とし、公開リポジトリや APK に埋め込めない。Amazon 商品ページの scraping、非公開 API、推測した画像 URL には依存しない。

Open Library は認証不要の公開 Search API と Covers API を提供する。ただし検索時にはタイトル、著者、ISBN などの書誌情報が外部サービスへ送信されるため、Kindle インポートの端末内処理とは明確に分離する必要がある。また未識別 API リクエストには 1 request/second の制限があり、ISBN 等による Covers API 画像取得は CoverID による取得より厳しい制限を受ける。

## Decision

Kindle 表紙補完は初期状態を無効とし、設定画面でユーザーが明示的に有効化した場合だけ Open Library を利用する。

所有情報の取得には引き続き外部 API、Amazon の Cookie・セッション、非公開 API、Web scraping を利用しない。外部通信は表示用の表紙メタデータ補完だけに限定する。

表紙補完は Kindle インポート完了後に WorkManager で実行し、インポートの成否を外部サービスの状態に依存させない。ネットワーク接続を制約とし、通信障害は backoff 付きで再試行する。検索クライアントは1回の Worker 実行につき Open Library への検索を1回だけ許可し、同一バッチの2冊目に到達した場合は追加通信を行わず `IOException` として Worker の exponential backoff に委ねる。これにより外部APIへ短時間に連続して検索しない。

検索結果に含まれる CoverID を保存 URL に利用し、ISBN 等による Covers API の追加レート制限を避ける。ユーザー個人の連絡先を User-Agent に埋め込むことはしない。

マッチングは誤表紙を避けることを優先する。

- ISBN-13 があれば最優先する
- 次に ISBN-10 を使う
- ISBN がなければタイトルと著者で検索する
- タイトルは Unicode NFKC、大小文字、句読点・空白差を正規化して比較する
- 明示的な巻数表現がある場合は巻数一致を必須とする
- 著者情報がある場合は著者一致を必須とする
- 高信頼候補が1件に定まらない場合は表紙を採用しない

補完結果は `library_items` とは別の `library_item_external_metadata` に保存する。これにより source 単位の再インポートで取得済み表紙を失わず、元データに `thumbnail_url` が存在する場合は常にそちらを優先できる。`FOUND`、`NOT_FOUND`、`AMBIGUOUS` を保存し、未発見・曖昧結果は一定期間後に再確認できるようにする。

設定を無効化した場合は新しい Open Library 問い合わせを停止する。すでに取得済みの表紙 URL はローカルキャッシュとして表示を継続する。

実ユーザーの ASIN、タイトル、著者、ISBN、Amazon エクスポート内容はパブリックリポジトリの fixture・ログ・ADR に保存しない。テストは人工データだけを使用する。

## Consequences

### Positive

- Amazon API secret や自前バックエンドなしで Kindle の表紙欠落を補完できる
- Kindle インポートは引き続き端末内で完結し、外部障害の影響を受けない
- 再インポート後も取得済み表紙を維持できる
- 同名書籍や別巻の誤表紙を保守的に回避できる
- 公開リポジトリにユーザーデータや秘密情報を追加しない
- Open Library への検索を低頻度に抑え、通信失敗時も WorkManager の backoff を利用できる

### Negative

- Open Library に登録されていない書籍は表紙を取得できない
- タイトルしかない Kindle データでは曖昧判定となり表紙を設定できない場合がある
- Open Library の検索・応答仕様やレート制限変更には追従が必要になる
- 有効化したユーザーの書誌情報は Open Library へ送信される
- 1 Worker 1検索と backoff を優先するため、大量蔵書の初回補完には時間がかかる

## Relationship to existing ADRs

- ADR-0013 のサービス非依存 `LibraryBook` と Kindle 所有情報に外部 API を使わない原則を維持し、表示メタデータ補完だけを例外として明示する
- ADR-0026 / ADR-0033 の ownership JSON 解析、実ユーザーデータを fixture に保存しない方針を維持する
- ADR-0006 の再開可能なバックグラウンド処理方針に従い WorkManager を利用する
