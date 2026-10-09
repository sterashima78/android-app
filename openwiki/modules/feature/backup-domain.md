---
type: module
title: バックアップ Domain：保存・復元と時刻設定の契約
description: バックアップの入口、設定済み判定、初回保存の部分失敗、時刻値の検証を説明する。
tags:
  - backup
  - domain
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-5fcf5d78754c5b405cc9f57e
    resource: repo://feature/backup/data/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/data/BackupRepository.kt
  - id: openwiki-source-42df42f401b724126e24b9fa
    resource: repo://feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt
  - id: openwiki-source-7a2828780a18c24fd4131084
    resource: repo://feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupScheduleTime.kt
  - id: openwiki-source-50f29c0bade04c1db78295b2
    resource: repo://feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupStatus.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# バックアップ Domain：保存・復元と時刻設定の契約

`:feature:backup:domain` は端末内データの退避・復元と、Google Drive保存先の設定を利用する側の契約を所有する。JVMモジュールであり、AndroidのURI権限、SQLite操作、WorkManagerの具体的な処理はDataへ委ねる。UIは文字列のdocument URIを渡して操作を要求し、保存方式を直接扱わない。

## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [BackupRepository](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt) | interface。SAF URIによるexport/restore、Driveフォルダ設定、即時backup、Wi-Fi/時刻設定、無効化を公開する。 |
| [ConfigureGoogleDriveResult](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt) / [Enabled](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt) / [EnabledWithInitialBackupFailure](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt) | sealed interfaceと結果型。保存先設定の成立と初回保存失敗を別々に表す。 |
| [BackupScheduleTime](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupScheduleTime.kt) | data class。時・分の範囲を構築時に検査し、encoded、compareTo、parseで保存・並べ替え・復号を統一する。 |
| [GoogleDriveBackupStatus](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupStatus.kt) | data class。フォルダ、直近成功/ファイル/失敗、Wi-Fi限定、予定時刻を表示用snapshotとして返す。configuredはURIの有無。 |

## 主要なAPI・構成要素の接続

`exportTo(documentUri)`と`restoreFrom(documentUri)`の入力は文字列のSAF document URI。`configureGoogleDrive(folderUri)`はtree URIを受け、設定成功後に初回backupが失敗しても`EnabledWithInitialBackupFailure`を返せる。`backupToGoogleDriveNow()`は作成したファイル名を返す。`status/ensureScheduled`、`setGoogleDriveWifiOnly/setGoogleDriveScheduleTimes/disableGoogleDrive`は同期設定capabilityである。

`BackupScheduleTime.parse`は不正形式や範囲外にnullを返す。構築は範囲外を例外にする。実装は[DefaultBackupRepository](backup-data.md)で、[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)がDBと変更通知を注入し、[BackupViewModel](backup-ui.md)へ渡す。


## 操作と結果の意味

`BackupRepository` は文書へのexport/restore、Drive保存先設定、今すぐの保存、Wi-Fi制限、複数時刻の更新、無効化を分けている。`ensureScheduled()` は既存設定に合わせた予約調整の入口である。保存先設定の結果は単純な成功・失敗ではなく、設定成功後の初回バックアップだけ失敗した状態も返す。そのため呼び出し側は部分成功を設定解除として扱わず、再実行可能な状態として示せる。



## 設定値と検証

`GoogleDriveBackupStatus.configured` はfolder URIの有無で判定し、直近成功日時・ファイル名・エラーを表示用metadataとして持つ。時刻は`BackupScheduleTime`が0〜23時、0〜59分に制限し、`HH:mm`へ符号化して時刻順に比較する。不正文字列のparseはnullとなる。実際の予約や永続保存はこの値を受け取るDataが担当する。

`GoogleDriveBackupStatusTest` はURIによる設定済み判定を確認する。時刻や結果型を変更するときはUIの表示・時刻追加とDataのpreferences/schedulerの解釈を同時に確認する。

## 調査・変更の入口

[BackupRepository](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt)、[BackupScheduleTime](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupScheduleTime.kt)、[GoogleDriveBackupStatus](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupStatus.kt)、[GoogleDriveBackupStatusTest](../../../feature/backup/domain/src/test/kotlin/dev/terashima/yomitorirss/backup/GoogleDriveBackupStatusTest.kt) を起点に責務と呼び出し側を確認する。

関連: [backup data](backup-data.md) / [backup ui](backup-ui.md) / [全体構成](../../architecture/system.md)。
