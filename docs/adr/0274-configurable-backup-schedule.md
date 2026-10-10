# ADR-0274: 自動バックアップをユーザー設定の時刻スケジュールで実行する

- Status: Accepted
- Date: 2026-09-29
- Supersedes: [ADR-0195](0195-trigger-backup-from-persistence-commit-boundary.md)
- Refines: [ADR-0099](0099-database-snapshot-backup.md), [ADR-0217](0217-google-drive-backup-wifi-network-policy.md)

## Context

従来の自動バックアップは、backup対象のdurable user data変更を `PersistenceChangeNotifier` へ集約し、変更から15分後のone-shot workと1日1回のperiodic workを併用していた。

この方式は変更頻度が高い利用ではバックアップ予約が過剰になりやすく、ユーザーが「いつバックアップするか」を制御できない。バックアップの目的は各mutation直後の複製ではなく、端末内のdurable user dataを適切な頻度で外部保存先へ退避できることである。

## Decision

### 1. persistence changeを自動バックアップのtriggerにしない

`PersistenceChangeNotifier`、backup対象SharedPreferencesの変更通知、feature mutationは自動バックアップworkを予約しない。

databaseのdurable/local mutation境界そのものはこのADRでは変更しない。既存の `DatabaseConnection.write` / `transaction` / `localWrite` / `localTransaction` の意味は維持する。

### 2. 自動バックアップはユーザー設定のローカル時刻で実行する

Backup Contextは、1日の中で実行したいローカル時刻を複数保持できる。

各時刻について次回のローカル日時を計算し、その時刻までのinitial delayを持つone-shot WorkManager workを予約する。workの終了後は同じ時刻の次回実行を再予約する。

WorkManagerの性質上、指定時刻は厳密なalarmではなく実行可能になる目標時刻であり、Doze、OS scheduler、network constraint等により遅延し得る。

### 3. scheduleはBackup Contextのuser preferenceとして保持する

登録時刻はBackup Contextが所有するSharedPreferencesへ保存する。保存形式は `HH:mm` の集合とし、重複を持たず時刻順に解釈する。

scheduleはcredentialや保存先URIではないため、既存のbackup preference allowlistへ追加し、通常のbackup archiveに含める。保存先URI・表示名・実行履歴は従来どおりbackup対象外とする。

scheduleが空の場合、自動バックアップは行わない。手動バックアップと保存先設定直後の初回バックアップは維持する。

### 4. network policyは既存契約を維持する

自動バックアップworkはADR-0217のnetwork constraintをそのまま利用する。Wi-Fi限定が有効なら検証済みWi-Fi、無効なら接続済みnetworkを要求する。

設定変更時は既存のscheduled workを置き換え、次回実行から新しいschedule / network policyを反映する。

### 5. legacy scheduleを停止する

従来の「変更から15分後」と「1日1回」のunique workは新方式のschedule適用時にcancelする。upgrade後に新しいscheduleが1件もない場合は自動バックアップを開始しない。

## Consequences

- backup頻度はデータ変更回数ではなくユーザーが登録した時刻数で決まる。
- Backup Context以外のfeatureはbackup schedulingを意識しない。
- scheduleは複数登録でき、重複時刻は1件に正規化される。
- exact alarm permissionは追加しない。
- 時刻指定はbest-effortであり、OS制約により遅延し得る。
- persistence change notification infrastructureはこの変更だけを理由に全systemから削除しないが、自動バックアップtriggerとしては使用しない。

## Compatibility retirement (2026-10-10)

現行の更新互換性baselineでは旧変更時・旧周期backup workへの移行は完了しているため、毎回の再設定・取消時に実行していた旧unique workのcancel処理を退役させた。現行の時刻scheduleの取消と再登録、network constraintは維持する。
