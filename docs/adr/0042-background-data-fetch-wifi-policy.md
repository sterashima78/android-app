# ADR-0042: バックグラウンド取得の Wi-Fi 制約

- Status: Accepted
- Date: 2026-08-14

設定に「Wi-Fi 接続中のみ取得」を追加し、初期値は OFF とする。対象は Gmail 定期同期、RSS ウィジェット更新、Kindle/Audible 表紙補完、AI モデルのバックグラウンドダウンロード。ON 時は WorkManager / JobScheduler で Wi-Fi transport を要求し、実行直前にも既定ネットワークが Wi-Fi か確認する。手動取得、Gmail 初回同期、Google Drive バックアップは対象外とする。
