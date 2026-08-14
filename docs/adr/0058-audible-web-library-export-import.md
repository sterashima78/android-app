# ADR-0058: Audible 蔵書は Web Library から生成した JSON を推奨入力とする

- Status: Accepted
- Date: 2026-08-14
- Refines: ADR-0017, ADR-0026, ADR-0028, ADR-0054

## Context

Audible 蔵書は ADR-0026 で Amazon Request Your Data の `Library.csv` / ZIP を正規入力としてきた。実データではこの方式で ASIN、タイトル、著者などは取得できる一方、表紙やシリーズなどアプリで表示したい情報を同じ入力から安定して得られず、追加の表紙取得処理が必要になる。

Audible.co.jp のログイン済み Web Library を Android Chrome で検証したところ、各ライブラリー項目に対応する `data-asin` を取得でき、`/library/titles?page=N` を同一オリジンで巡回することで全ページの ASIN を収集できた。

Audible のカタログエンドポイント `https://api.audible.co.jp/1.0/catalog/products` では ASIN を指定して、タイトル、著者、ナレーター、出版社、配信日、説明、表紙、再生時間などを取得できた。複数 ASIN の指定は1リクエスト最大50件であることを実動作で確認した。シリーズは複数商品レスポンスでは十分に返らないため、単品 `/1.0/catalog/products/{asin}?response_groups=series` で補完する。

`www.audible.co.jp` と `api.audible.co.jp` は別オリジンであり、Web Library 上のブックマークレットからカタログ API へ直接 `fetch` する方式はブラウザ制約に依存する。このため、Web Library で ASIN を収集してカタログ API の JSON 画面へ遷移し、その API オリジン上で残りのバッチとシリーズを取得する2段階方式を採用する。

これらの Web Library DOM とカタログエンドポイントは、アプリ向けに安定性が保証された公開仕様として扱わない。アプリ自身が Audible の Cookie、パスワード、セッショントークンを保存して直接アクセスする方式は採用しない。

## Decision

### 推奨入力

Audible の推奨インポート入力は、蔵書設定画面で提示する2つのブックマークレットをログイン済みブラウザ上で実行して生成する JSON とする。

入力形式 v1 は次を持つ。

- `format`: `audible-library-export`
- `version`: `1`
- `books`: 蔵書配列
- 各書籍: `asin`, `title`, `subtitle`, `authors`, `narrators`, `publisher`, `publishedDate`, `description`, `coverUrl`, `durationMinutes`, `series`, `productUrl`
- `series`: 存在する場合 `id`, `name`, `position`

`exportedAt`, `count`, `stats` は診断情報として許容するが、蔵書の正規データにはしない。未知フィールドは無視する。

今回の検証過程で生成した、ルートが書籍配列そのものになっている JSON も移行用入力として受け付ける。新しいブックマークレットは必ず v1 の envelope を生成する。

ASIN は10文字の英数字、シリーズ ID が存在する場合も10文字の英数字として検証する。タイトルは必須とする。`durationMinutes` と `series.position` は存在する場合1以上の整数とする。表紙 URL は HTTPS のみ保存する。商品 URL は入力値を信用せず、検証済み ASIN から `https://www.audible.co.jp/pd/{asin}` を再構築する。

JSON の入力上限は25 MBとする。実ユーザーの JSON、ASIN 一覧、書名一覧、Cookie、セッション情報をログ、テスト fixture、ADR、パブリックリポジトリへ追加しない。

### ブラウザでの生成

1段階目は `www.audible.co.jp/library/titles` 上で `/library/titles?page=N` を巡回し、`data-asin` から重複を除いた ASIN を収集する。最初の50件をカタログ API クエリへ渡し、残りは URL fragment に保持して `api.audible.co.jp` へ遷移する。

2段階目は `api.audible.co.jp` 上で、残りを50件ずつ `/1.0/catalog/products?asins=...` へ問い合わせる。シリーズ情報は過度な同時アクセスを避けるため5件ずつ並列で単品 API から取得する。

説明文はブラウザ側で HTML をプレーンテキスト化し、500px 表紙を優先して保存する。500px がない場合は取得できた別サイズへフォールバックする。シリーズ候補が明らかに出版社名だけを示す場合は除外する。

### アプリ内の保持

Web JSON から取得した書籍情報は既存の Audible `Library.csv` インポート経路へ内部変換して取り込み、既存の DB 置換・Narrator / Duration 保存規則を再利用する。`durationMinutes` は現行ドメインモデルに合わせて `N時間M分` の表示文字列へ変換する。

構造化シリーズは手動編集用 `library_item_series` へ直接書かず、再生成可能な source metadata として専用テーブルに保持する。優先順位は ADR-0017 と Kindle の ADR-0057 に合わせる。

1. ユーザーの手動シリーズ設定
2. ユーザーによるシリーズ解除
3. Audible Web Library / Catalog の構造化シリーズ情報
4. タイトルからの自動推定

シリーズ ID が取得できる場合は `LibrarySeries.id` に保持し、取得できない場合はシリーズ名でグルーピングする。シリーズ位置が API から明示されない場合、タイトルの「上」「下」などから位置を推測しない。

### 従来入力

ADR-0026 の `Library.csv` / ZIP は引き続き受け付ける。従来入力をインポートした場合、過去の Web JSON 由来シリーズ情報はクリアし、古い source metadata が残らないようにする。

ファイル選択 UI は JSON、CSV、ZIP を候補として表示する。

## Consequences

### Positive

- 1つの JSON から Audible 蔵書、表紙、著者、ナレーター、再生時間、シリーズを取り込める
- 表紙取得の多くをインポート時に完了できる
- 〈物語〉シリーズなど、タイトル文字列だけでは安定しないシリーズを Audible 側の構造化情報でまとめられる
- Audible の認証情報をアプリへ渡さず、ブラウザの既存ログイン状態だけを利用できる
- `Library.csv` / ZIP の既存利用者を壊さない

### Negative

- Web Library DOM とカタログエンドポイントは安定性が保証された公開仕様ではなく、Audible 側の変更でブックマークレットが動かなくなる可能性がある
- Android Chrome では2つのブックマークレットを登録して順番に実行する操作が必要
- シリーズ API は書籍数に比例してリクエストが増える
- 現行モデルでは subtitle と durationMinutes を独立した型として保持せず、subtitle は保存対象外、duration は表示文字列へ変換する

## Relationship to existing ADRs

- ADR-0026 の Audible `Library.csv` / ZIP 入力は互換経路として維持するが、本 ADR の Web JSON を推奨入力とする。
- ADR-0028 の Narrator / Duration / 商品 URL 保持は維持する。
- ADR-0017 の手動シリーズ設定、シリーズ解除、自動推定の優先関係を維持し、構造化シリーズ情報をその中間に追加する。
- ADR-0057 の「認証情報をアプリに渡さず、ブラウザから JSON を生成する」安全境界を Audible にも適用する。
- ADR-0054 に従い、エクスポート手順とブックマークレットは library feature が所有する。
