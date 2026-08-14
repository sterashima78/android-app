# ADR-0041: バックグラウンドのデータ取得に共通ネットワークポリシーを適用する

- Status: Accepted
- Date: 2026-08-14

## Context

RSS ウィジェット更新、Gmail の定期同期、Kindle / Audible の表紙補完、AI モデルのバックグラウンドダウンロードは、それぞれ独立してネットワーク制約を設定していた。従来はいずれも主に `NetworkType.CONNECTED` を使用していたため、モバイル回線でもバックグラウンド通信が発生し得る。

ユーザーが通信量を抑えたい場合、これらの自動・バックグラウンド取得を Wi-Fi 接続中だけに限定できる共通設定が必要である。

`NetworkType.UNMETERED` は「従量課金されないネットワーク」を表し、Wi-Fi transport そのものを意味しない。Yomitori は minSdk 29 であり、利用中の WorkManager 2.11.2 は Android 9 以降で `NetworkRequest` を制約として利用できるため、Wi-Fi transport を明示的に要求できる。

## Decision

`core:background` モジュールを追加し、バックグラウンドのデータ取得に関するネットワークポリシーを集約する。

設定画面に「Wi-Fi 接続中のみ取得」を追加する。初期値は OFF とし、既存ユーザーの動作を変更しない。

設定が ON の場合は、WorkManager と JobScheduler の新規ジョブに `NET_CAPABILITY_INTERNET` と `TRANSPORT_WIFI` を持つ `NetworkRequest` を設定する。OFF の場合は従来どおり接続済みネットワークを許可する。

また、設定変更前にすでにキューへ入っていたジョブがモバイル回線で通信しないよう、実行直前にも現在の既定ネットワークが Wi-Fi かを確認する。HTTP クライアントが JobScheduler / WorkManager の要求ネットワークへ自動的に bind されるとは限らないため、Wi-Fi 限定時は既定ネットワーク自体が Wi-Fi であることを条件とする。

対象は次のバックグラウンド取得とする。

- Gmail の定期差分同期
- ホーム画面ウィジェットから行う RSS 更新
- Kindle / Audible の表紙補完
- AI モデルのバックグラウンドダウンロード

次は対象外とする。

- 画面上からユーザーが明示的に開始する RSS 等の手動更新
- Gmail アカウント接続直後の初回同期
- Google Drive バックアップなど、データ取得ではなく送信を主目的とする処理

設定変更時にすでに実行中の通信は強制キャンセルしない。実行開始前のジョブと、それ以降に作成されるジョブへ新しいポリシーを適用する。

## Consequences

Wi-Fi 限定を有効にすると、モバイル回線しか利用できない間は対象のバックグラウンド取得が待機またはスキップされる。Wi-Fi が既定ネットワークになった後に WorkManager / JobScheduler の制約を満たすと処理を再開する。

共通ポリシーを `core:background` に置くことで、今後バックグラウンド取得を追加する際にも同じ設定を再利用できる。

一方、Wi-Fi 限定を解除した直後でも、解除前に Wi-Fi 制約で登録済みだった一部の one-shot ジョブは次の再登録まで元の制約を保持する可能性がある。安全側に倒し、Wi-Fi 限定を有効化した際にモバイル通信が発生しないことを優先する。

## Related ADRs

- ADR-0006: 長時間の同期は再開可能な WorkManager ジョブへ分割する
- ADR-0021: AI モデルのバックグラウンドダウンロード
- ADR-0036: Kindle 表紙補完
- ADR-0037: Audible 表紙補完
