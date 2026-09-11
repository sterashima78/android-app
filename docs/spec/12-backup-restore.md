# 12. バックアップと復元

- アプリ独自backupは、統合SQLite databaseの整合したsnapshotを含むMosaic形式のZIP archiveとする。
- backupにはmanifest、database snapshot、allowlistされたuser preferencesを含む。
- checksum、SQLite application id、integrity check等を利用して復元前にarchiveを検証する。
- database snapshotは現在のapplication schema versionと一致する場合だけ復元対象とし、異なるschema versionのbackupは復元前に拒否する。
- Google Driveでは、保存先設定後にバックアップ対象変更から15分後と1日1回の自動バックアップを行い、手動実行も提供する。
- 「Wi-Fi接続時のみバックアップ」を有効にした場合、Google Driveへの自動・手動・初回バックアップはインターネット接続可能なWi-Fiが利用できる場合だけ実行する。既定はOFFとする。
- Wi-Fi限定設定はallowlistされたuser preferenceとしてbackup対象とするが、Google Drive保存先URI・表示名・実行履歴はbackup対象外とする。
- credential、token、SMB password、Google Drive保存先、端末依存benchmark、model cache等はbackup対象外とする。
- SMB表紙cacheやSMB動画thumbnail cacheのように再生成可能な派生ファイルはbackup本体へ含めず、復元後にowner featureの経路で再生成・再取得する。

詳細は ADR-0099、ADR-0100、ADR-0135、ADR-0138、ADR-0195、ADR-0217 と `docs/architecture/persistence.md` を参照する。
