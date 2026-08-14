# ADR-0042: バックグラウンド取得の Wi-Fi 制約

- Status: Accepted
- Date: 2026-08-14
- Amended: 2026-08-14

設定に「Wi-Fi 接続中のみ取得」を追加し、初期値は OFF とする。

対象は Gmail 定期同期、RSS ウィジェット更新、AI モデルのバックグラウンドダウンロードとする。ON 時は WorkManager / JobScheduler で Wi-Fi transport を要求し、実行直前にも既定ネットワークが Wi-Fi か確認する。

手動取得、Gmail 初回同期、Google Drive バックアップは対象外とする。

Kindle / Audible の表紙は ADR-0057 / ADR-0058 により Web Library エクスポート JSON を正規データとし、アプリ内のバックグラウンド表紙補完を廃止したため、本 Wi-Fi 制約の対象からも除外する。
