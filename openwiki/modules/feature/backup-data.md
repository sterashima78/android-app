---
type: module
title: "バックアップ Data：ZIP整合性と時刻予約"
description: "整合したdatabase archive、復元時の検証、Driveの時刻予約とWorker失敗処理を説明する。"
tags: [backup, data, modules]
sources:
  - id: openwiki-source-a800a59b02ddddbc2830709c
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/DatabaseBackupArchive.kt
  - id: openwiki-source-1c370f53fd1a0ac9d4d77508
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt
  - id: openwiki-source-79179474ad844222bd5f9a98
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupWorker.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T14:01:52.039Z
---

# バックアップ Data：ZIP整合性と時刻予約

`:feature:backup:data` は`DefaultBackupRepository`を通じて、文書URIへの入出力、database snapshotと設定のZIP化、Drive保存先の権限、WorkManager予約を実装する。core databaseに加え、復元後の整合性を戻すためBookmark・Library・RSSのData initializerを利用する。

## exportとrestore

`DatabaseBackupArchive.writeTo()` はsnapshotを作成・識別し、databaseとallowlist設定のサイズ・SHA-256、schema versionをmanifestへ記録する。restoreはarchive形式、サイズ、checksum、現在schemaとの一致、設定内容とsnapshotを検証した後に置換する。置換で失敗した場合は変更前の設定への復帰を試みる。Repositoryは一時import fileをfinallyで削除し、復元後にfeature initializer、変更通知、予約の再調整を実施する。

## Drive予約と失敗

保存先は永続URI権限を取得して保持し、設定後の初回保存に失敗しても設定済み結果を維持する。時刻schedulerは旧periodic/変更後workを停止し、登録された各ローカル時刻の次回発火をone-shotで予約する。発火Workerがnetwork制約付きの保存workをenqueueして次の時刻を予約するため、厳密なalarmではない。Wi-Fi限定では検証済みWi-Fiを要求する。保存Workerは権限・引数エラーを失敗とし、その他も試行回数に上限を設ける。

`DatabaseBackupArchiveTest`のchecksum破損・schema不一致拒否、`BackupPreferencesTest`のallowlist、`GoogleDriveBackupSchedulerTest`の日付繰越とnetwork制約が主要な確認先である。

## 調査・変更の入口

[BackupRepository](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupRepository.kt)、[DatabaseBackupArchive](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/DatabaseBackupArchive.kt)、[GoogleDriveBackupScheduler](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt)、[GoogleDriveBackupWorker](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupWorker.kt)、[DatabaseBackupArchiveTest](../../../feature/backup/data/src/test/kotlin/dev/terashima/yomitorirss/backup/DatabaseBackupArchiveTest.kt) を起点に責務と呼び出し側を確認する。

関連: [backup domain](backup-domain.md) / [backup ui](backup-ui.md) / [全体構成](../../architecture/system.md)。
