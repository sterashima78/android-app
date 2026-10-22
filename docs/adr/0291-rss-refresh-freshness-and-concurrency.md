# ADR-0291: RSS画面・周期更新の再取得間隔とフィード単位の排他

- Status: Accepted
- Date: 2026-10-22
- Refines: [ADR-0220](0220-integrated-background-refresh-and-notification.md)

## Context

登録済みフィードは画面のViewModel生成時とアプリ全体の定期Workerで取得されるほか、ウィジェット等の直接呼び出しでも取得される。従来はViewModelごとのin-flightフラグだけで取得を制御し、画面遷移やWorkerとの競合によって同じフィードへの通信を繰り返し得た。表示自体は端末内の記事で可能なため、起動・再訪時の無条件ダウンロードを避ける。

## Decision

- RSS-owned `RefreshFeedsUseCase` は、自動画面表示・定期Worker・明示手動更新を区別する。画面表示と失敗後の再試行時間はRSS-owned preferenceで保存する。既定は画面30分・失敗5分。既存の背景Worker周期設定はそのまま独立して使用する。
- `DefaultFeedRepository` はフィードIDごとにcoroutine mutexを持ち、施錠後にDBの最新購読状態を再取得する。重複した同時要求は直前の完了結果を共有し、追加のHTTP通信を発生させない。異なるID同士の既存4並列制限は維持する。
- 成功応答（304を含む）は既存 `last_fetched_at` を更新する。失敗による再試行抑制の時刻はアプリプロセス内のtransient stateとし、プロセス再起動時は再試行可能とする。フィード削除と復元の追加スキーマは導入しない。
- OPMLインポート直後の未取得購読は `last_fetched_at = null` とする。
- 画面の更新進捗表示では、RSS Repositoryから公開する取得中フィードIDを参照する。RSS以外のネットワーク更新処理とWorker identityは変更しない。

## Alternatives

- ViewModel単位のクールダウン: Workerとウィジェットに適用されず、画面再生成で失われるため不採用。
- RSS専用の新規WorkManager周期タスク: 既存統合Workerと取得が重なり、更新間隔も二重に管理するため不採用。
- 失敗時刻のDB列追加: データ移行とバックアップ互換性の費用に対して5分前後の一時的抑制の利点が小さいため見送る。

## Compatibility and rollback

既存のRSS購読、ETag、記事、既読情報、Worker予約は維持される。新規SharedPreferencesは設定値のみを保存する。機能を撤去する場合はタイミング設定と条件付き更新呼び出しを戻せばよく、DB migrationは不要。

## Verification

フィード単位の排他と最終取得時刻の再確認、自動更新と手動更新の差、間隔設定の再読み込みと既定値、キャンセル伝播、画面・周期Workerによる更新と既存の通知を検証する。

## References

- [Background refresh](../architecture/background-refresh.md)
- [RSS specification](../spec/04-content.md)
- [Background execution specification](../spec/13-background-execution.md)
