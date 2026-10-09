---
type: module
title: バックアップ UI：操作状態と部分成功の表示
description: BackupViewModelによる非同期保存・復元、Drive初回保存の部分成功と時刻一覧を説明する。
tags:
  - backup
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T04:30:12.060Z
sources:
  - id: openwiki-source-805655882ce1ba786f47e791
    resource: repo://feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt
generated: { by: "codex", at: "2026-10-09T04:30:12.060Z" }
---

# バックアップ UI：操作状態と部分成功の表示

`:feature:backup:ui` は`BackupViewModel`と`GoogleDriveBackupDialog`で保存・復元設定の操作を提供する。依存するのはBackup Domainで、URI権限やZIP検証をUIに持ち込まず、外側で選択されたURIをRepositoryへ渡す。

## 主要な構成要素

| 構成要素・種類 | 役割・主要メソッドと関係 |
| --- | --- |
| [BackupScheduleTimeUi](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt) / [BackupUiState](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt) | data class群。時刻label、Drive status、実行中、通知、復元完了イベントを表示状態にまとめる。 |
| [BackupViewModel](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt) / [Factory](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt) | 公開class群。refreshStatus、exportBackup/importBackup、Drive設定・即時実行・予定編集・無効化とイベント消費を公開する。 |
| [GoogleDriveBackupDialog](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupDialog.kt) | 公開Composable。stateと各callbackを受け、保存先選択、即時保存、Wi-Fi、時刻追加/削除、解除を表示する。内部BackupTimePickerDialogが時刻入力を担う。 |

## 主要なAPI・構成要素の接続

`exportBackup/importBackup(documentUri)`はIO coroutineでRepositoryを呼ぶ。復元成功は`restoreCompleted=true`と再起動を促すmessageを公開し、`consumeRestoreCompleted()`でイベントを消費する。`configureGoogleDrive`は設定失敗と初回保存のみの失敗を別messageにする。`backupToGoogleDriveNow()`は未設定ならcommandを送らず案内する。

`addGoogleDriveScheduleTime`はDomain時刻を構築して既存一覧へ追加、重複除去・昇順化して保存し、`removeGoogleDriveScheduleTime`は一致時刻だけを除く。`setGoogleDriveWifiOnly`、`disableGoogleDrive`、`refreshStatus`はRepository statusを再読する。`dismissMessage`は通知のみを消す。

利用者は[SettingsFeatureScreen](settings-ui.md)。フォルダpickerやdocument作成/選択はapp側callbackへ委譲し、本moduleはAndroid権限やDBを直接変更しない。


## 状態と操作フロー

初期化では`ensureScheduled()`を呼んで既存予約を調整し、Repositoryのstatusを`BackupUiState`へ変換する。export/importとDrive保存は`viewModelScope`のIO dispatcherで実行し、messageやrunningを更新する。Drive保存先設定では「設定できなかった」と「設定できたが初回保存に失敗した」を別の文言で示す。今すぐ保存は未設定ならRepositoryを呼ばず、保存先選択を促す。

復元成功時には再起動案内と`restoreCompleted`を立て、利用側が`consumeRestoreCompleted()`で消費できる。これは表示側への完了通知であり、database変更はDataが行う。通常のエラーはrunningを解除してmessageへ反映する。

## 時刻設定と確認先

時刻追加はDomainの範囲検証を通し、重複を除いてソートした一覧を保存する。削除やWi-Fi制限更新後もstatusを再取得するため、表示と永続設定を合わせる。`BackupUiStateTest`は未設定・非実行・未復元の初期状態を確認する。操作の多重実行や復元後通知の扱いを変更する場合は、ViewModelの状態更新と画面の有効条件を合わせて確認する。

## 調査・変更の入口

[BackupViewModel](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/BackupViewModel.kt)、[GoogleDriveBackupDialog](../../../feature/backup/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/backup/GoogleDriveBackupDialog.kt)、[BackupUiStateTest](../../../feature/backup/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/settings/BackupUiStateTest.kt) を起点に責務と呼び出し側を確認する。

関連: [backup domain](backup-domain.md) / [backup data](backup-data.md) / [全体構成](../../architecture/system.md)。
