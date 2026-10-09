---
type: module
title: バックアップ Data：ZIP整合性と時刻予約
description: 整合したdatabase archive、復元時の検証、Driveの時刻予約とWorker失敗処理を説明する。
tags:
  - backup
  - data
  - modules
sources:
  - id: openwiki-source-e21606996fcee06eb4693cb9
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupPreferences.kt
  - id: openwiki-source-5fcf5d78754c5b405cc9f57e
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupRepository.kt
  - id: openwiki-source-a800a59b02ddddbc2830709c
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/DatabaseBackupArchive.kt
  - id: openwiki-source-1c370f53fd1a0ac9d4d77508
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt
  - id: openwiki-source-690c2005eab08a4bf1e338b6
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupStore.kt
  - id: openwiki-source-79179474ad844222bd5f9a98
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupWorker.kt
  - id: openwiki-source-805655882ce1ba786f47e791
    resource: repo://feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt
generated: { by: "codex", at: "2026-10-09T08:22:11.354Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T08:22:11.354Z
---

# バックアップ Data：ZIP整合性と時刻予約

`:feature:backup:data` は`DefaultBackupRepository`を通じて、文書URIへの入出力、database snapshotと設定のZIP化、Drive保存先の権限、WorkManager予約を実装する。core databaseに加え、復元後の整合性を戻すためBookmark・Library・RSSのData initializerを利用する。

## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [DefaultBackupRepository](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupRepository.kt) | 公開class。BackupRepository実装。SAF stream、archive、初回Drive保存、権限移行、restore後のowner初期化と通知を協調させる。 |
| [DatabaseBackupArchive](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/DatabaseBackupArchive.kt) | internal class。writeTo/validate/restoreでDB snapshotとallowlist設定をZIPに格納し、形式・schema・size・hash・entryを検査する。 |
| [BackupPreferences](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupPreferences.kt) / [PreferenceBackupRule](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupPreferences.kt) / [PreferenceValue](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupPreferences.kt) | internal classとルール・内部sealed型。encode/validate/restoreが許可ファイル・キーだけを型付きJSONとして扱う。 |
| [GoogleDriveBackupPreferences](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupPreferences.kt) | 公開class。status/isConfigured/isWifiOnly/scheduleTimesと設定変更、recordSuccess/recordFailureでDrive運用状態を保存する。 |
| [GoogleDriveBackupService](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupService.kt) / [GoogleDriveBackupStore](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupStore.kt) | 公開class群。Service.backupがIOでarchiveをStoreへ渡して成功・失敗を記録。StoreはSAF書込み後に再読検証し、古い管理対象backupを削除する。 |
| [GoogleDriveBackupScheduler](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt) / [GoogleDriveBackupScheduleWorker](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt) | 公開objectとWorker。ensureScheduled/reschedule/cancel、時刻ごとの単発予約、doWorkによる次回予約とbackup enqueueを担う。 |
| [GoogleDriveBackupWorker](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupWorker.kt) / [BackupWorkerFactory](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupWorker.kt) | 公開Workerとfactory。doWorkがDomain Repositoryへ委譲し、factoryはbackup・schedule Workerに依存を注入する。 |
| [autoBackupFileName](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupFiles.kt) / [obsoleteAutoBackupNames](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupFiles.kt) | internal関数。日時名と保持対象を計算し、手動ファイルは自動削除対象から外す。 |
| [isValidatedWifiForGoogleDriveBackup](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupNetworkPolicy.kt) / [googleDriveBackupNetworkConstraints](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt) | internal拡張・関数。手動実行と予約実行のvalidated Wi-Fi条件をそれぞれ検査・設定する。 |

## 主要なAPI・構成要素の接続

`restoreFrom`はSAF入力を一時ファイルに写し、archive検証・復元後にLibrary、RSS推薦、Bookmarkのinitializerを呼び、persistence/data変更通知と予約再設定を行う。`DatabaseBackupArchive.restore`は設定を先に復元してDBを置換し、例外時は元設定への復元を試みて例外を返す。

archive 内の置換処理で失敗した場合は、`DatabaseBackupArchive.restore` が復元前の設定への復帰を試みる。一方、archive の復元が戻った後に `DefaultBackupRepository.restoreFrom` が行う Library・RSS推薦・Bookmark initializer、変更通知、予約再設定はその catch 範囲外である。後続処理が失敗すると例外は呼び出し元へ伝わり、置換済み database は戻らない。[BackupViewModel.importBackup](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt) は成功時だけ完了通知を立て、失敗時はエラーメッセージを表示するため、復元済み database と画面の失敗表示が併存し得る。

`GoogleDriveBackupStore.write`（internal）は永続read/write権限を検査し、document作成→書込み→再読検証→保持整理の順に進む。途中失敗は作成documentの削除を試みる。`GoogleDriveBackupWorker.doWork`は権限・引数例外や再試行上限ではfailure、その他の一時障害はretry。時刻Workerは設定解除や削除済み時刻ならsuccessで終える。

[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)がDefaultBackupRepositoryを作り、[AppBackgroundRuntime](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/background/AppBackgroundRuntime.kt)が起動時予約を復元する。archive内部の設定allowlistはcredential・URI権限・端末ベンチマーク・一時状態を持ち込まない。


## exportとrestore

`DatabaseBackupArchive.writeTo()` はsnapshotを作成・識別し、databaseとallowlist設定のサイズ・SHA-256、schema versionをmanifestへ記録する。restoreはarchive形式、サイズ、checksum、現在schemaとの一致、設定内容とsnapshotを検証した後に置換する。置換で失敗した場合は変更前の設定への復帰を試みる。Repositoryは一時import fileをfinallyで削除し、復元後にfeature initializer、変更通知、予約の再調整を実施する。



## Drive予約と失敗

保存先は永続URI権限を取得して保持し、設定後の初回保存に失敗しても設定済み結果を維持する。時刻schedulerは旧periodic/変更後workを停止し、登録された各ローカル時刻の次回発火をone-shotで予約する。発火Workerがnetwork制約付きの保存workをenqueueして次の時刻を予約するため、厳密なalarmではない。Wi-Fi限定では検証済みWi-Fiを要求する。保存Workerは権限・引数エラーを失敗とし、その他も試行回数に上限を設ける。

`DatabaseBackupArchiveTest`のchecksum破損・schema不一致拒否、`BackupPreferencesTest`のallowlist、`GoogleDriveBackupSchedulerTest`の日付繰越とnetwork制約が主要な確認先である。

## 調査・変更の入口

[BackupRepository](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupRepository.kt)、[DatabaseBackupArchive](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/DatabaseBackupArchive.kt)、[GoogleDriveBackupScheduler](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupScheduler.kt)、[GoogleDriveBackupWorker](../../../feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/GoogleDriveBackupWorker.kt)、[DatabaseBackupArchiveTest](../../../feature/backup/data/src/test/kotlin/dev/terashima/yomitorirss/backup/DatabaseBackupArchiveTest.kt) を起点に責務と呼び出し側を確認する。

関連: [backup domain](backup-domain.md) / [backup ui](backup-ui.md) / [全体構成](../../architecture/system.md)。
