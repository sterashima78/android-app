# ADR-0217: Google DriveバックアップのWi-Fi実行ポリシーをユーザー設定にする

- Status: Accepted
- Date: 2026-08-29
- Refines: [ADR-0099](0099-database-snapshot-backup.md), [ADR-0195](0195-trigger-backup-from-persistence-commit-boundary.md)

## Context

Google Driveバックアップは、変更から15分後のone-time Workと1日1回のperiodic WorkをWorkManagerで実行し、従来は `NetworkType.CONNECTED` のみを制約としていた。このため、Wi-Fiだけでなくモバイルデータ通信でもバックアップが実行される。

バックアップarchiveはdatabase snapshotとallowlistされたuser preferencesを含み、サイズや実行頻度によってはモバイルデータ通信量を消費する。ユーザーがGoogle DriveバックアップをWi-Fi接続時だけに制限できる必要がある。

`NetworkType.UNMETERED` は「Wi-Fi」を意味しない。Androidのnetwork capabilityではtransport種別とmetered状態は別概念であり、Wi-Fi transportそのものを要求する場合は `NetworkRequest` の `TRANSPORT_WIFI` を利用する必要がある。現在のminSdk 34とWorkManager 2.11系では `Constraints.Builder.setRequiredNetworkRequest` を利用できる。

また、Google Drive保存先URIは端末上のpersisted permissionに依存するためADR-0099に従いbackup対象外である。一方、「Wi-Fi接続時のみ」という実行方針はユーザー設定であり、端末を復元した場合にも引き継ぐ価値がある。

## Decision

### 1. Wi-Fi限定をGoogle Driveバックアップのユーザー設定として持つ

Backup Contextの `google_drive_backup` SharedPreferencesに `wifi_only` を保存する。

既存利用者の振る舞いを変えないため既定値はOFFとし、OFFでは従来どおり接続済みnetworkを許可する。

### 2. ONの場合はWi-Fi transportをWorkManager制約として要求する

変更後バックアップとperiodicバックアップの双方で、Wi-Fi限定ONの場合は次のnetwork条件を持つ `NetworkRequest` をWorkManagerの `Constraints` に設定する。

- `TRANSPORT_WIFI`
- `NET_CAPABILITY_INTERNET`
- `NET_CAPABILITY_VALIDATED`

これにより、単にnetworkへ接続しているだけのWi-Fiではなく、インターネット到達性が検証済みのWi-FiでGoogle Driveバックアップを実行する。

Wi-Fi限定OFFの場合は従来どおり `NetworkType.CONNECTED` を利用する。

### 3. 手動実行と初回バックアップにも同じpolicyを適用する

「今すぐバックアップ」と保存先設定直後の初回バックアップも、Wi-Fi限定ONの場合は同じWi-Fi条件を要求する。

WorkManager制約だけに限定すると、自動実行はWi-Fi限定でも手動実行だけモバイル通信を利用でき、設定名と実際の通信policyが一致しないためである。

Wi-Fi限定ONで利用可能なWi-Fiがない場合はアップロードを開始せず、Wi-Fi接続を求めるエラーをユーザーへ返す。

### 4. `wifi_only` だけをbackup allowlistへ追加する

ADR-0099とADR-0195のallowlist方針に従い、`google_drive_backup` preference file全体ではなく `wifi_only` keyだけを `BackupPreferences.BACKUP_RULES` に追加する。

次の値は引き続きbackup対象外とする。

- Google Drive保存先URI / 表示名
- 最終成功時刻 / 最終ファイル名
- 直近エラー

これにより、端末依存のpersisted URIや実行履歴をarchiveへ混入させず、ユーザーの通信方針だけを復元できる。

`BackupPreferenceChangeObserver` も同じallowlistを使用するため、`wifi_only` の変更だけがdurable preference changeとして既存の `PersistenceChangeNotifier` へ流れる。保存先URI等の更新をバックアップ対象変更として誤検知しない。

### 5. scheduling ownershipはBackup Contextに維持する

設定変更時はBackup Contextが既存periodic Workを `ExistingPeriodicWorkPolicy.UPDATE` で更新する。

変更後のone-time WorkはADR-0195の既存経路で再予約され、通常featureやSettings UIから `BackupChangeScheduler` を直接呼ばない。UIはBackup Repositoryの設定APIだけを利用する。

## Consequences

### Positive

- ユーザーがGoogle Driveバックアップによるモバイルデータ通信を明示的に避けられる。
- `UNMETERED` ではなくWi-Fi transportを指定するため、設定名とAndroid network条件が一致する。
- 自動、手動、初回バックアップで同じ通信policyを適用できる。
- Wi-Fi限定設定はbackupから復元できる一方、Google Drive保存先や実行履歴は引き続きarchiveへ含まれない。
- ADR-0195のpersistence change境界とBackup Context ownershipを維持できる。

### Negative

- Wi-Fi限定ONでは、Wi-Fiが接続されていてもインターネット到達性が検証されるまでバックアップを実行しない。
- Wi-Fiを長期間利用しない端末では自動バックアップが遅延する。
- 手動実行時にもnetwork状態の確認が必要になる。

## Verification

- Wi-Fi限定ONのWorkManager制約が `TRANSPORT_WIFI`、`INTERNET`、`VALIDATED` を要求することをunit testする。
- Wi-Fi限定OFFでは従来どおり `NetworkType.CONNECTED` になることをunit testする。
- backup archiveに `wifi_only` が含まれ、Google Drive保存先URIは含まれないことをunit testする。
- restoreで `wifi_only` は復元されるが、現在端末のGoogle Drive保存先URIは上書きされないことをunit testする。
- `BackupPreferenceChangeObserver` が `wifi_only` の変更だけを通知し、保存先URI変更を通知しないことをunit testする。
- PR quality checks、architecture verification、lint、public repository verificationを実行する。

## Public repository review

本変更はnetwork policy、設定key、UI文言、synthetic test dataだけを追加する。credential、token、OAuth secret、実ユーザーのGoogle Drive URI、メールアドレス、個人データ、database、backup archive、diagnostic artifactをrepositoryへ追加しない。

## References

- [ADR-0099](0099-database-snapshot-backup.md)
- [ADR-0195](0195-trigger-backup-from-persistence-commit-boundary.md)
- Android Developers: `NetworkRequest.Builder`
- AndroidX WorkManager: `Constraints.Builder.setRequiredNetworkRequest`
