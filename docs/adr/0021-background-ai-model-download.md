# ADR-0021: AIモデルのダウンロードはユーザー起点の永続バックグラウンド転送として実行する

- Status: Accepted
- Date: 2026-08-11

## Context

ローカルAIモデルは数GB規模の単一ファイルであり、ダウンロード完了まで設定画面や `ViewModel` のライフサイクルに処理を結び付けるべきではない。従来は `AiSettingsViewModel` の `viewModelScope` から同期的な `LocalModelManager.downloadModel` をIO coroutineで実行していたため、画面側のライフサイクルと長時間のネットワーク転送が直接結び付いていた。

Androidでは、ユーザー操作によって開始され、完了まで進捗をユーザーへ示す必要がある長時間のデータ転送に User-Initiated Data Transfer (UIDT) Job を利用できる。UIDT Job は API 34 で導入され、`JobScheduler` の `setUserInitiated(true)` と `JobService.setNotification` を使用する。API 34 未満では同じAPIを利用できないため、WorkManager の long-running worker を foreground service として実行する必要がある。

ADR-0006 は Gmail 初回同期のようにページ単位へ分割できる大量同期を短い WorkManager ジョブへ分割する方針を採用している。一方、AIモデルは配布元から単一のモデルファイルを取得して完全性を検証する処理であり、アプリ側の意味的なページ単位へ分割できない。また Android 16 以降では long-running WorkManager worker も JobScheduler quota を消費するため、API 34 以降でユーザー起点転送専用のUIDTを優先する。

## Decision

AIモデルのダウンロードは `:feature:settings:data` が所有するバックグラウンド転送として実行する。

### Android 14 以降

API 34 以降では `JobScheduler` の UIDT Job を利用する。

- `JobInfo.setUserInitiated(true)` を指定する
- インターネット接続を必須条件にする
- モデルの推定サイズを `setEstimatedNetworkBytes` へ渡す
- `RUN_USER_INITIATED_JOBS` permission を宣言する
- `JobService.setNotification` でダウンロード進捗を継続的に表示する
- ユーザーが設定画面上でダウンロードを押した時点でジョブを予約し、UIDT の「ユーザー操作直後に開始する」という意味を維持する

UIDTをユーザーがAndroidのタスクマネージャーから停止した場合、OSはアプリプロセスを終了し、`onStopJob()` を呼ばず、そのUIDTを再スケジュールしない。このため、永続化した進捗が `queued` / `downloading` / `verifying` のまま残っていても、次回Repository生成時に同じJob IDが `JobScheduler` に存在しなければ `failed` として整合させ、再ダウンロード操作を可能にする。

### Android 13 以前

API 29〜33では WorkManager の `CoroutineWorker` を利用し、`dataSync` foreground service として実行する。

- `NetworkType.CONNECTED` を要求する
- `ForegroundInfo` で同じダウンロード通知を表示する
- 一時的なネットワーク障害は WorkManager の retry に委譲する

既存のAI要約用 long-running worker は `specialUse` foreground service を利用しているため、WorkManager の `SystemForegroundService` は `specialUse|dataSync` を宣言し、AI要約とモデルダウンロードの双方を収容する。

### ダウンロード本体

モデルファイルの取得、空き容量確認、ファイル検証、配置、初回モデルの自動選択は引き続き `LocalModelManager` が担当する。バックグラウンドジョブ側はこの処理を複製せず、ライフサイクルと通知、再試行だけを担当する。

### 進捗状態

`LocalModelManager` の `StateFlow` はインスタンス内状態であり、バックグラウンドジョブと画面が異なるインスタンスを持つ可能性がある。そのため、モデルダウンロードの進捗は `:feature:settings:data` の `SharedPreferences` に保存し、UIはその永続状態を購読する。

状態は少なくとも以下を持つ。

- model ID
- phase (`queued`, `downloading`, `verifying`, `completed`, `failed`)
- downloaded bytes
- total bytes
- estimated remaining time

同時に複数モデルを取得せず、進行中のモデルがある場合は別モデルの開始を拒否する。同じモデルの開始操作は冪等に扱う。

## Consequences

- 設定画面を閉じてもモデルのダウンロードを継続できる
- アプリがバックグラウンドにある間もOSが適切な実行経路と通知を管理できる
- ダウンロード状況を画面再生成後も復元できる
- Android 14 以降ではユーザー起点の長時間転送を汎用 long-running WorkManager worker に載せずに済む
- Android 13 以前でも既存のWorkManager基盤で互換性を維持できる
- 現在の `LocalModelManager` はHTTP Rangeによる途中再開を実装していないため、通信切断やOSによる停止後の再実行ではモデルファイルを先頭から取得し直す場合がある。途中再開が必要になった場合は、ダウンロード実装自体にRange requestと検証可能なcheckpointを追加する
- 端末再起動をまたぐUIDTの自動再開は今回の対象外とする。モデルが未完了ならユーザーが再度開始できる

## Relationship to existing ADRs

ADR-0006 の「画面ライフサイクルを越える処理は永続バックグラウンド処理へ委譲する」という原則を踏襲する。ただし、Gmail同期はページ分割可能であるためWorkManagerへ分割し、AIモデルは単一のユーザー起点大容量転送であるためUIDTを優先する。

ADR-0020 のローカルAI実行バックエンド・モデル管理方針は変更しない。本ADRはモデルファイルを端末へ取得する際の実行ライフサイクルだけを定める。

## References

- https://developer.android.com/develop/background-work/background-tasks/data-transfer-options
- https://developer.android.com/develop/background-work/background-tasks/uidt
- https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
