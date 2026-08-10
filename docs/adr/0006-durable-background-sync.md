# ADR-0006: 長時間の同期は再開可能な WorkManager ジョブへ分割する

- Status: Accepted
- Date: 2026-08-09
- Updated: 2026-08-09

## Context

Gmail の初回同期はアカウント内の多数のスレッドを取得するため、通常の画面ライフサイクルに結び付いた coroutine では完了までアプリを前面に維持する必要があった。また、同期対象のスレッド ID を取得してから本文を順次取得していたため、最初のメールが表示されるまで時間がかかり、途中の DNS やネットワーク障害で初回同期全体が失敗していた。

Android の永続的なバックグラウンド処理には WorkManager を使用できる。WorkManager には長時間実行 Worker もあるが、初回メール同期はページ単位に分割して再開できる処理である。Android 16 以降では long-running worker も JobScheduler の quota を消費するため、分割可能な処理を単一の長時間 Worker にする必要はない。

さらに ADR-0008 により、Yomitori は Gmail の完全なアーカイブを同期せず、受信トレイ内のスレッドと Yomitori 自身がアーカイブしたスレッドだけをローカルで扱う。したがって初回同期の対象は Gmail 全スレッドではなく `in:inbox` に限定する。

## Decision

アプリの画面や Activity の生存期間を越えて完了させる必要がある同期処理は WorkManager に委譲する。

Gmail の初回同期は単一の長時間 Worker ではなく、Gmail API のページ単位の `OneTimeWorkRequest` に分割する。

初回同期では Gmail の `threads.list` に `in:inbox` を指定し、受信トレイ内のスレッドだけをページングする。Gmail 側ですでに受信トレイ外にあるスレッドは初回同期しない。

各ページの処理は次の順序とする。

```text
アカウント認可
  -> アカウントを DB に保存
  -> 初回同期状態を DB に保存
  -> 1 ページ分の OneTimeWorkRequest を enqueue
      -> in:inbox のスレッド一覧 1 ページ取得
      -> スレッド本文を境界付き並列取得
      -> 取得できた内容を即座に DB へ保存
      -> page token / 進捗を DB へ保存
      -> 次ページの OneTimeWorkRequest を enqueue
  -> 最終ページ
      -> label を更新
      -> 今回の同期で確認できなかった古い非ローカル cache を削除
      -> historyId と完了時刻を保存
```

### 再開可能性

初回同期の以下の情報を `mail_accounts` に保存する。

- sync state
- 処理済み thread 数
- API の page token
- 同期開始時の historyId
- full sync generation
- 最後の error

`mail_threads` には full sync generation を保存する。最終ページ完了時に現在 generation で確認できなかった thread のうち、`archived_locally = 0` のものを削除する。これにより途中で同期が停止しても、既存 cache を先に消さずに再開でき、Yomitori 自身がアーカイブした履歴は full sync の世代更新でも保持できる。

各ページ Worker は期待している page token を input として持つ。同一 page token の Worker は unique work とする。DB checkpoint と一致しない古い Worker が再実行された場合は、現在の checkpoint から continuation を再構築し、checkpoint 保存後から次ページ enqueue 前の中断でも同期を継続できるようにする。

### ネットワーク障害

初回同期 Worker には `NetworkType.CONNECTED` を要求し、通信失敗、HTTP 429、HTTP 5xx は exponential backoff で再試行する。

再試行可能な障害では同期状態を `waiting_for_network` とし、ユーザー操作が必要な認可エラーや再試行しても意味がない API エラーは `error` とする。

### UI

認可完了は初回同期完了を待たない。アカウントを保存した時点でメール画面へ戻し、取得済み thread を順次表示する。

UI は DB の同期状態を短い間隔で再読込し、次を表示する。

- バックグラウンド同期中
- 取得済み thread 数
- ネットワーク待機中
- 同期エラーと再試行操作

アプリをバックグラウンドに移しても同期そのものは ViewModel や Activity に依存しない。

### 定期同期

初回同期完了後は既存の Gmail History API を利用した差分同期を継続する。

差分同期で受信トレイ外になったスレッドは、ADR-0008 の `archived_locally` が 1 の場合だけローカルキャッシュを維持する。別クライアント等で Gmail 側だけがアーカイブされたスレッドはローカルキャッシュから削除する。

差分履歴が期限切れの場合は、画面上で全件同期を直接実行せず、新しい初回同期 generation を開始してページ Worker へ委譲する。この再同期も `in:inbox` のみを取得し、ローカルアーカイブは generation cleanup の対象外とする。

## Consequences

- Gmail アカウント追加操作が同期完了を待たなくなる
- 最初のページを保存した時点からメールを閲覧できる
- Gmail 側の既存アーカイブを取得しないため、初回同期の対象件数、通信量、保存量を抑えられる
- アプリをバックグラウンドへ移動しても同期を継続できる
- 一時的なネットワーク障害から WorkManager の backoff で復帰できる
- 一度の Worker が扱う量を制限でき、長時間 Worker と JobScheduler quota への依存を避けられる
- checkpoint、generation、ローカルアーカイブ所有状態のため DB schema が増える
- Gmail 以外で同種の大量同期を追加する場合も、ページ化できる処理は同じ方式を優先する

## Relationship to existing ADRs

ADR-0003 / ADR-0004 の ownership 方針に従い、Gmail 固有の scheduler、Worker、checkpoint 操作は `:feature:mail:data` が所有する。WorkManager 自体を `core` に抽象化しない。別 feature でも同一の意味と lifecycle を持つ同期基盤が必要になった時点で共有 capability 化を再検討する。

メール同期の対象範囲と `archived_locally` の意味は ADR-0008 に従う。
