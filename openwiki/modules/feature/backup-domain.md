---
type: module
title: "バックアップ Domain：保存・復元と時刻設定の契約"
description: "バックアップの入口、設定済み判定、初回保存の部分失敗、時刻値の検証を説明する。"
tags: [backup, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-42df42f401b724126e24b9fa
    resource: repo://feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt
  - id: openwiki-source-7a2828780a18c24fd4131084
    resource: repo://feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupScheduleTime.kt
  - id: openwiki-source-50f29c0bade04c1db78295b2
    resource: repo://feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupStatus.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# バックアップ Domain：保存・復元と時刻設定の契約

`:feature:backup:domain` は端末内データの退避・復元と、Google Drive保存先の設定を利用する側の契約を所有する。JVMモジュールであり、AndroidのURI権限、SQLite操作、WorkManagerの具体的な処理はDataへ委ねる。UIは文字列のdocument URIを渡して操作を要求し、保存方式を直接扱わない。

## 操作と結果の意味

`BackupRepository` は文書へのexport/restore、Drive保存先設定、今すぐの保存、Wi-Fi制限、複数時刻の更新、無効化を分けている。`ensureScheduled()` は既存設定に合わせた予約調整の入口である。保存先設定の結果は単純な成功・失敗ではなく、設定成功後の初回バックアップだけ失敗した状態も返す。そのため呼び出し側は部分成功を設定解除として扱わず、再実行可能な状態として示せる。

## 設定値と検証

`GoogleDriveBackupStatus.configured` はfolder URIの有無で判定し、直近成功日時・ファイル名・エラーを表示用metadataとして持つ。時刻は`BackupScheduleTime`が0〜23時、0〜59分に制限し、`HH:mm`へ符号化して時刻順に比較する。不正文字列のparseはnullとなる。実際の予約や永続保存はこの値を受け取るDataが担当する。

`GoogleDriveBackupStatusTest` はURIによる設定済み判定を確認する。時刻や結果型を変更するときはUIの表示・時刻追加とDataのpreferences/schedulerの解釈を同時に確認する。

## 調査・変更の入口

[BackupRepository](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupRepository.kt)、[BackupScheduleTime](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupScheduleTime.kt)、[GoogleDriveBackupStatus](../../../feature/backup/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupStatus.kt)、[GoogleDriveBackupStatusTest](../../../feature/backup/domain/src/test/kotlin/dev/terashima/yomitorirss/backup/GoogleDriveBackupStatusTest.kt) を起点に責務と呼び出し側を確認する。

関連: [backup data](backup-data.md) / [backup ui](backup-ui.md) / [全体構成](../../architecture/system.md)。
