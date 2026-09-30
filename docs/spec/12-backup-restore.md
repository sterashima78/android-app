# 12. バックアップと復元

- アプリ独自backupは、統合SQLite databaseの整合したsnapshotを含むMosaic形式のZIP archiveとする。
- backupにはmanifest、database snapshot、allowlistされたuser preferencesを含む。
- checksum、SQLite application id、integrity check等を利用して復元前にarchiveを検証する。
- database snapshotは現在のapplication schema versionと一致する場合だけ復元対象とし、異なるschema versionのbackupは復元前に拒否する。
- Google Driveでは、保存先設定後に自動バックアップ時刻を複数登録できる。
<!-- formal-requirement
id: BACKUP-SCHEDULE-001
revision: 1
models:
  - spec-models/quint/backup_schedule.qnt
  - spec-models/alloy/backup_schedule.als
-->
- 登録した各ローカル時刻にバックアップjobを1つ予約する。時刻未登録では自動バックアップ用のscheduled workを持たず、手動実行はscheduleなしでも利用できる。
<!-- /formal-requirement -->
- 指定時刻はWorkManagerのbackground制約により遅延し得る。
- 「Wi-Fi接続時のみバックアップ」を有効にした場合、Google Driveへの自動・手動・初回バックアップはインターネット接続可能なWi-Fiが利用できる場合だけ実行する。既定はOFFとする。
- Wi-Fi限定設定と自動バックアップ時刻はallowlistされたuser preferenceとしてbackup対象とするが、Google Drive保存先URI・表示名・実行履歴はbackup対象外とする。
- credential、token、SMB password、Google Drive保存先、端末依存benchmark、model cache等はbackup対象外とする。
- SMB表紙cacheやSMB動画thumbnail cacheのように再生成可能な派生ファイルはbackup本体へ含めず、復元後にowner featureの経路で再生成・再取得する。

詳細は ADR-0099、ADR-0100、ADR-0135、ADR-0138、ADR-0217、ADR-0274 と `docs/architecture/persistence.md` を参照する。


## 形式モデル

- [Quint: `backup_schedule.qnt`](../../spec-models/quint/backup_schedule.qnt) — 自動バックアップ時刻の追加・削除・発火・次回再予約を有限state machineとして検査する。時刻未登録ではautomatic workが存在しないこともsafety invariantに含む。
- [Alloy: `backup_schedule.als`](../../spec-models/alloy/backup_schedule.als) — 設定済みローカル時刻とscheduled workが一意対応し、automatic backup jobが設定済みscheduleに由来する構造を検査する。manual backupはscheduleなしでも成立する。
