# ADR-0195: 自動バックアップの変更検知を persistence commit boundary に置く

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0099](0099-database-snapshot-backup.md)

## Context

ADR-0099 によりアプリ独自バックアップの正本は統合 SQLite database の snapshot になり、database に追加された durable user data は feature ごとの backup serializer を追加せずバックアップ対象にできるようになった。

一方、自動バックアップを予約する契機は feature / UI / mutator 側に分散していた。各 ViewModel や write path が `BackupChangeScheduler` を知り、永続化後に個別に `scheduleAfterChange()` 相当の処理を呼ぶ方式では、新しい write path、Worker、import、background task を追加した際に予約呼び出しを忘れる可能性がある。また通常 feature が Backup Context に依存し、バックアップという cross-cutting concern が persistence ownership と無関係な層へ漏れていた。

既存の `DataChangeNotifier` は RSS 等の画面更新を目的とした通知でもあるため、これを自動バックアップの汎用変更通知として再利用すると、Task / Chat 等の変更まで UI refresh に波及する。表示更新通知と durable persistence change は別の意味を持つ。

さらに統合DBには、backup archiveへ含まれていても復元先で正本として扱わない transient queue / download state や、ADR-0135 の SMB `file://` 表紙参照のような端末local cache metadataも存在する。SQLiteへの全writeを機械的にバックアップ契機にすると、queue progressやcache再生成だけで15分後のbackupを繰り返し再予約してしまう。

ADR-0099 の archive は SQLite snapshot だけでなく明示的に allowlist した SharedPreferences も含む。したがって database commit だけを監視しても、要約prompt、reader position、workout設定等のバックアップ対象設定だけを変更した場合は自動バックアップ契機を失う。

したがって境界は単なる「SQLite write」ではなく、「backup対象の durable user data が正常に永続化されたか」で定義する必要がある。

## Decision

### 1. durable database mutation の成功 commit をバックアップ変更通知の境界とする

`:core:database` の `DatabaseConnection.write` / `DatabaseConnection.transaction` を、通常 runtime における durable database mutation の共通境界とする。

mutation が正常に完了し、database への変更が commit された場合に `PersistenceChangeNotifier` へ変更を通知する。transaction が失敗・rollback した場合は通知しない。

`write` も内部では transaction として実行する。Android の WAL 利用時は複数 SQLite connection が使われ得るため、変更件数の前後値は transaction を開始した同一 connection 上で取得する。transaction 外で connection-local な変更件数を比較して通知判定しない。

feature / Repository / Worker / import 処理は、通常の durable write をこの mutation API 経由に揃える。バックアップのためだけに各 caller が追加処理を呼ばない。

### 2. backup対象外のlocal / cache / transient stateは明示的な非通知境界を使う

ADR-0099 がbackup対象外として扱う transient queue、download state、device/cache-only state等には `DatabaseConnection.localWrite` / `DatabaseConnection.localTransaction` を使用できる。

これらは通常のtransactionと同様にatomicにcommit / rollbackするが、local stateだけが変更された場合は `PersistenceChangeNotifier` を発火させない。

ただし `localTransaction` の内部から durable `write` / `transaction` を呼んだ場合は、外側transactionを persistence changeへ昇格させる。これにより呼び出し階層の都合でdurable mutationを非通知transactionの中へ組み込んでも、バックアップ通知を隠せない。

`DatabaseConnection.writable` の直接利用はschema初期化・migration等のmaintenance writeに限定する。既存の明示的なbackup対象外runtime stateは段階的に `localWrite` / `localTransaction` へ寄せる。

### 3. `PersistenceChangeNotifier` と UI 用 `DataChangeNotifier` を分離する

`PersistenceChangeNotifier` は backup対象の durable persistence change の発生を表す persistence-level signal とする。SQLite の durable commitだけでなく、ADR-0099のallowlist対象SharedPreferences変更も同じsignalへ合流できる。

既存の `DataChangeNotifier` は画面や read model の再読込等、表示更新に必要な domain/application signal として維持する。バックアップ予約のために `DataChangeNotifier` を発火させたり、`DataChangeNotifier` の全イベントを永続化変更として扱ったりしない。

これにより Task / Chat / Library 等の永続化が RSS 画面の不要な再読込を誘発しない。

### 4. Backup scheduling の ownership は app composition root に置く

`:app` が `PersistenceChangeNotifier` を1か所で購読し、変更を `BackupChangeScheduler` に接続する。

```text
feature / worker / import
        |
        v
repository / store
        |
        +--> durable DB user data
        |      DatabaseConnection.write / transaction
        |                 |
        |                 v
        |      PersistenceChangeNotifier
        |
        +--> backup-excluded local state
        |      DatabaseConnection.localWrite / localTransaction
        |                 |
        |                 +-- no persistence notification
        |
        +--> backed-up SharedPreferences
               BackupPreferenceChangeObserver
                         |
                         v
              PersistenceChangeNotifier

PersistenceChangeNotifier
        |
        v
:app composition root
        |
        v
PersistenceBackupChangeObserver
        |
        v
BackupChangeScheduler
        |
        v
scheduled Google Drive backup
```

通常 feature の ViewModel / Repository / mutator は `BackupChangeScheduler` に依存しない。Backup Context の scheduling API を通常 mutation の公開契約にしない。

既存のバックアップ側 debounce / delay policy は Backup Context が引き続き所有し、persistence layer は「backup対象変更が永続化された」という事実だけを通知する。

SQLite snapshot restore は row-level mutation API を通らず database file 自体を置換する特殊な durable mutationである。restore後のLibrary cache / queue cleanupは `localTransaction` とし、restoreと全initializerが成功した時点で Backup Context の persistence adapter が `PersistenceChangeNotifier` を1回明示的に通知する。restore callerから `BackupChangeScheduler` を直接呼ばない。

### 5. ADR-0099のSharedPreferences allowlist変更も同じsignalへ合流させる

Backup Context は `BackupPreferences.BACKUP_RULES` を backup archive と変更検知の単一allowlistとして所有する。

`BackupPreferenceChangeObserver` はこのallowlistに含まれるSharedPreferencesだけへ listener を登録する。ruleがkey allowlistを持つ場合は対象keyだけを `PersistenceChangeNotifier` へ通知し、model revision、device benchmark、credential等の除外key/fileは通知しない。

backup restoreは複数SharedPreferencesを連続更新するため、restore処理中は `BackupPreferenceChangeSuppression` でlistener通知を抑制する。database snapshot、allowlist preferences、restore initializerの全処理が成功した後に `BackupRepository` が既存の `PersistenceChangeNotifier` を1回通知する。これによりrestore途中の状態を複数回backup予約せず、restore完了状態だけをdirtyとして扱う。

新しいSharedPreferencesをbackup対象へ追加する場合は `BACKUP_RULES` へ追加することでarchive内容と自動変更検知を同時に更新する。通常featureへ `BackupChangeScheduler` を注入しない。

### 6. Mail / SMB / Summary の既知legacy pathを整理する

本 ADR 導入時に残っていた Mail Context の account / sync checkpoint / local mail state と、Library Context の SMB server設定は `DatabaseConnection.write` / `transaction` へ移行する。

SMB表紙については ADR-0135 に従い、`library_items.thumbnail_url` に保存する `file://` URLは再生成可能な端末local cache参照として扱う。表紙生成、cache eviction、`smb_cover_prefetch_queue` の状態・progress、restore後のcache cleanupは `localWrite` / `localTransaction` を利用し、バックアップ契機にしない。SMB credentialも引き続きbackup対象外とする。

Summary Context では `article_summaries` の要約結果は ADR-0099 が明示するdurable user dataなので `DatabaseConnection.write` を通す。一方 `summary_tasks`、prepared content、retry / progress等のqueue実行状態はtransient processing stateとしてバックアップ契機にしない。

新しい durable user-data write に caller-specific backup bridge や未通知の raw writable mutation を追加してはならない。

### 7. Architecture verification で境界の逆流を防ぐ

architecture test で、通常 feature/UI が `BackupChangeScheduler` や caller-driven backup scheduling API を所有・参照しないことを検証する。

また共通境界へ移行済みの主要durable pathはraw writable mutationへ戻らないことを検証する。backup対象外stateについては、SMB cover queue / cache / restore cleanupがdurable `transaction` を使用しないことを固定する。

`article_summaries` の保存についても `YomitoriDatabase.writableDatabase` への直接mutationへ戻らないことを検証する。

app compositionでは `PersistenceBackupChangeObserver` と `BackupPreferenceChangeObserver` の両方が起動し、DBとallowlist preferencesの変更が同じ `PersistenceChangeNotifier` を経由することを検証する。

## Consequences

### Positive

- 新しい durable table や write path の追加時に feature ごとのバックアップ予約実装が不要になる。
- foreground UI、Worker、import 等、caller の種類に依存せず durable database change を同じ境界で扱える。
- rollback したtransactionをバックアップ対象変更として誤通知しにくくなる。
- transient queue progressやdevice-local cache更新による不要なバックアップ再予約を避けられる。
- RSS / Bookmark / Reddit / YouTube 等の feature から Backup Context への直接依存を削除できる。
- UI refresh の `DataChangeNotifier` と backup trigger の意味を分離できる。
- ADR-0099 の database snapshotとSharedPreferences allowlistの双方で、archive scopeと変更検知scopeが一致する。
- Mailのローカル状態、SMB server設定、article summary、バックアップ対象settingsがfeature-specific scheduling hookなしで同じ自動バックアップ契機を持つ。

### Negative

- runtime write pathはdurable / localの意味を判断して適切なmutation APIを選ぶ必要がある。
- persistence layerにdurable change signalとlocal transactionというcross-cutting mechanismが追加される。
- Backup ContextはSharedPreferences allowlist listenerとrestore中の通知抑制を維持する必要がある。
- DB snapshot自体にはtransient tableやlocal cache参照columnも含まれ得るため、restore initializerやscope ADRを維持する必要がある。

## Verification

- `DatabaseConnection.write` の成功mutation後にpersistence changeが通知されることをunit testする。
- `DatabaseConnection.write` が失敗した場合はrollbackされ、通知されないことをunit testする。
- `DatabaseConnection.transaction` の成功commit後に通知され、失敗 / rollback時には通知されないことをunit testする。
- `localWrite` / `localTransaction` はlocal stateだけのcommitでは通知しないことをunit testする。
- `localTransaction` 内にdurable `write` がnestedした場合は外側commit後に1回通知することをunit testする。
- WAL利用時も変更件数の前後値をtransaction内の同一SQLite connectionで評価する。
- allowlist対象SharedPreferencesの変更が `PersistenceChangeNotifier` を発火することをunit testする。
- key allowlist外のSharedPreferences変更は通知しないことをunit testする。
- backup restore中のSharedPreferences変更は個別通知せず、restore成功後の明示通知だけを利用することをtestする。
- `PersistenceChangeNotifier` の通知がapp compositionで `BackupChangeScheduler` に接続されることをtestする。
- SQLite snapshot restore成功後も `PersistenceChangeNotifier` が通知され、restore cache cleanup単体では通知しないことを確認する。
- `DataChangeNotifier` をbackup triggerとして利用しないことを確認する。
- 通常 feature/UI が `BackupChangeScheduler` または caller-driven scheduling API に依存しないことをarchitecture testする。
- RSS / Article / Bookmark / Asset / Library / Mail / Summary / Task / YouTube の移行済みdurable database writeがraw writable mutationへ戻らないことをarchitecture testする。
- SMB credential、cover cache、cover prefetch queue、schema maintenance等はbackup対象外として維持する。
- Architecture / Test / Lint / public repository verificationを実行する。

## References

- [ADR-0098](0098-unified-user-database.md)
- [ADR-0099](0099-database-snapshot-backup.md)
- [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0135](0135-smb-cover-cache-backup-restore.md)
- [ADR-0136](0136-public-repository-content-verification.md)
- [Issue #329](https://github.com/sterashima78/android-app/issues/329)
