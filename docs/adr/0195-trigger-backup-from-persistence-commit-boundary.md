# ADR-0195: 自動バックアップの変更検知を persistence commit boundary に置く

- Status: Accepted
- Date: 2026-08-27
- Refines: [ADR-0099](0099-database-snapshot-backup.md)

## Context

ADR-0099 によりアプリ独自バックアップの正本は統合 SQLite database の snapshot になり、database に追加された durable user data は feature ごとの backup serializer を追加せずバックアップ対象にできるようになった。

一方、自動バックアップを予約する契機は feature / UI / mutator 側に分散していた。各 ViewModel や write path が `BackupChangeScheduler` を知り、永続化後に個別に `scheduleAfterChange()` 相当の処理を呼ぶ方式では、新しい write path、Worker、import、background task を追加した際に予約呼び出しを忘れる可能性がある。また通常 feature が Backup Context に依存し、バックアップという cross-cutting concern が persistence ownership と無関係な層へ漏れていた。

既存の `DataChangeNotifier` は RSS 等の画面更新を目的とした通知でもあるため、これを自動バックアップの汎用変更通知として再利用すると、Task / Chat 等の変更まで UI refresh に波及する。表示更新通知と durable persistence change は別の意味を持つ。

バックアップの正本が database snapshot である以上、バックアップ予約の変更検知も「どの feature が操作したか」ではなく「durable database mutation が正常に commit されたか」に合わせる必要がある。

## Decision

### 1. durable database mutation の成功 commit をバックアップ変更通知の境界とする

`:core:database` の `DatabaseConnection.write` / `DatabaseConnection.transaction` を、通常 runtime における durable database mutation の共通境界とする。

mutation が正常に完了し、database への変更が commit された場合に `PersistenceChangeNotifier` へ変更を通知する。transaction が失敗・rollback した場合は通知しない。

`write` も内部では transaction として実行する。Android の WAL 利用時は複数 SQLite connection が使われ得るため、変更件数の前後値は transaction を開始した同一 connection 上で取得する。transaction 外で connection-local な変更件数を比較して通知判定しない。

feature / Repository / Worker / import 処理は、通常の durable write をこの mutation API 経由に揃える。バックアップのためだけに各 caller が追加処理を呼ばない。

### 2. `PersistenceChangeNotifier` と UI 用 `DataChangeNotifier` を分離する

`PersistenceChangeNotifier` は durable database change の発生だけを表す persistence-level signal とする。

既存の `DataChangeNotifier` は画面や read model の再読込等、表示更新に必要な domain/application signal として維持する。バックアップ予約のために `DataChangeNotifier` を発火させたり、`DataChangeNotifier` の全イベントを永続化変更として扱ったりしない。

これにより Task / Chat / Library 等の永続化が RSS 画面の不要な再読込を誘発しない。

### 3. Backup scheduling の ownership は app composition root に置く

`:app` が `PersistenceChangeNotifier` を1か所で購読し、変更を `BackupChangeScheduler` に接続する。

```text
feature / worker / import
        |
        v
repository / store
        |
        v
DatabaseConnection.write / transaction
        |
        v
PersistenceChangeNotifier
        |
        v
:app composition root
        |
        v
BackupChangeScheduler
        |
        v
scheduled Google Drive backup
```

通常 feature の ViewModel / Repository / mutator は `BackupChangeScheduler` に依存しない。Backup Context の scheduling API を通常 mutation の公開契約にしない。

既存のバックアップ側 debounce / delay policy は Backup Context が引き続き所有し、persistence layer は「変更が commit された」という事実だけを通知する。

SQLite snapshot restore は row-level mutation API を通らず database file 自体を置換する特殊な durable mutation である。restore と restore 後の schema 初期化が成功した時点で Backup Context の persistence adapter が `PersistenceChangeNotifier` を明示的に通知する。restore caller から `BackupChangeScheduler` を直接呼ばない。

### 4. 移行中の legacy write path は明示的に縮退させる

共通 mutation API を通らない既存 write path が残る期間は、取りこぼし防止の互換 bridge を限定的に利用できる。ただし恒久的な二重通知モデルにはせず、対象 write を `DatabaseConnection.write` / `transaction` へ移した時点で caller-specific backup scheduling を削除する。

本ADR導入時点で、主要なRSS / Article / Bookmark / Asset / Library / Task / YouTubeの対象writeは共通mutation APIへ移行する。一方、次の既存pathには未移行のraw writable mutationが残るため、後続のpersistence整理対象とする。

- Mail Context: account / sync checkpoint と `read_later_locally` / `archived_locally` 等のメールローカル状態。
- Library Context の SMB adapter: SMB server設定と一部のderived cover metadata。

ADR-0099でbackup対象外としたSMB credential、transient queue state、download state等は、バックアップ通知のためだけに共通mutation APIへ移行する対象とはしない。schema初期化・migration等のmaintenance writeもraw writableを利用できる。

新しい durable write に legacy bridge や未通知のraw writable mutationを追加してはならない。

### 5. Architecture verification で feature への逆流を防ぐ

architecture test で、通常 feature/UI が `BackupChangeScheduler` や caller-driven backup scheduling API を所有・参照しないことを検証する。

また直接 database mutation を追加する場合は、persistence commit notification を迂回しないことを review / test で確認する。共通境界へ移行済みの主要pathはsource architecture testでraw writable mutationの再導入を防ぐ。

## Consequences

### Positive

- 新しい table や write path の追加時に feature ごとのバックアップ予約実装が不要になる。
- foreground UI、Worker、import 等、caller の種類に依存せず database change を同じ境界で扱える。
- rollback した transaction をバックアップ対象変更として誤通知しにくくなる。
- RSS / Bookmark / Reddit / YouTube 等の feature から Backup Context への直接依存を削除できる。
- UI refresh の `DataChangeNotifier` と backup trigger の意味を分離できる。
- ADR-0099 の database snapshot を正本とする方針と、変更検知の境界が一致する。

### Negative

- 通常 runtime の write path は `DatabaseConnection.write` / `transaction` を経由する規律が必要になる。
- persistence layer に durable change signal という cross-cutting mechanism が追加される。
- SQLite 外の SharedPreferences 等のユーザー所有データは database commit だけでは変更検知できないため、それらの自動バックアップ契機は各 persistence mechanism の境界で別途扱う必要がある。
- 移行期間中は legacy write path が残り得るため、既知の未移行箇所を明示して段階的に縮退させる必要がある。

## Verification

- `DatabaseConnection.write` の成功 mutation 後に persistence change が通知されることを unit test する。
- `DatabaseConnection.write` が失敗した場合は rollback され、通知されないことを unit test する。
- `DatabaseConnection.transaction` の成功 commit 後に通知され、失敗 / rollback 時には通知されないことを unit test する。
- WAL利用時も変更件数の前後値をtransaction内の同一SQLite connectionで評価する。
- `PersistenceChangeNotifier` の通知が app composition で `BackupChangeScheduler` に接続されることを test する。
- SQLite snapshot restore成功後も `PersistenceChangeNotifier` が通知されることを確認する。
- `DataChangeNotifier` を backup trigger として利用しないことを確認する。
- 通常 feature/UI が `BackupChangeScheduler` または caller-driven scheduling API に依存しないことを architecture test する。
- 共通境界へ移行した主要な direct database write がraw writable mutationへ戻らないことをarchitecture testする。
- Architecture / Test / Lint / public repository verification を実行する。

## References

- [ADR-0098](0098-unified-user-database.md)
- [ADR-0099](0099-database-snapshot-backup.md)
- [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0136](0136-public-repository-content-verification.md)
