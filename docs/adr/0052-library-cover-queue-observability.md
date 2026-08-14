# ADR-0052: 表紙取得キューを既存DBと WorkManager から可視化する

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-14
- Refines: ADR-0006, ADR-0036, ADR-0057

## Context

Kindle の表紙補完は WorkManager でバックグラウンド実行しているが、ユーザーからは現在取得中なのか、何件待っているのか、取得結果がどうなったのかを確認できない。

当初は Audible も同じキューに含めていたが、ADR-0058 で Audible Web Library エクスポート JSON 自体に Catalog API の表紙を含める方式へ移行し、Audible のバックグラウンド表紙補完を廃止した。

書籍単位の専用キューテーブルは持たず、WorkManager と `library_items` / `library_item_external_metadata` から状態を再構成する方針は維持する。

## Decision

蔵書画面の「設定」タブに「表紙取得状況」を置き、Kindle の表紙補完状態を確認できるようにする。

書籍単位の状態は次のように解釈する。

- 元データに表紙がある場合は「元データに表紙あり」
- 外部メタデータに表紙がある場合は「取得済み」
- 表紙がなく、未試行または再確認期限を過ぎた場合は「取得待ち」
- 最近の `NOT_FOUND` は「見つからない」
- 最近の `AMBIGUOUS` は「候補を特定できない」
- 通信・解析エラーは「エラー」

再確認期限は ADR-0036 と同じ30日とする。

実行状態は Kindle の WorkManager unique work を監視し、「開始待ち」「取得中」「通信エラー後の再試行待ち」「エラー」を表示する。Kindle の表紙補完を無効化している場合は「停止中」と表示する。

「未取得を再試行」はユーザーの明示操作とし、Kindle の未取得結果だけをリセットして WorkManager へ再投入する。取得済み表紙は保持する。

「実行をキャンセル」は Kindle の unique work をキャンセルするだけで、表紙補完設定を変更しない。

Audible はこの画面・キュー・再試行処理の対象にしない。Audible の表紙は ADR-0058 の Web JSON の `coverUrl` を使用する。

表紙取得画面に表示する書名、ASIN 等は端末内だけで扱い、ログ、fixture、ADR、公開リポジトリへ実ユーザーデータを追加しない。

## Consequences

### Positive

- Kindle の WorkManager 実行状態とDBから取得待ちを復元できる
- 書籍単位のキューを二重管理せずに状態を確認できる
- Audible の不要な background work と再試行状態を管理しなくてよい
- 未取得結果を30日待たずにユーザー操作で再試行できる

### Negative

- 書籍単位の「現在まさにHTTP取得中」は永続化しないため、処理中表示はサービス単位になる
- 明示的な履歴テーブルを持たないため、過去の試行履歴は最新結果より前には遡れない

## Relationship to existing ADRs

- ADR-0006 の WorkManager による再開可能なバックグラウンド処理を維持する
- ADR-0036 / ADR-0057 の Kindle 表紙補完を維持する
- ADR-0037 の Audible 表紙補完は ADR-0058 により廃止されたため、本キューから除外する
