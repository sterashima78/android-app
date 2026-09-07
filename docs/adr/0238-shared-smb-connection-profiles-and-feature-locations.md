# ADR-0238: SMB接続プロファイルと機能別同期場所を分離する

- Status: Accepted
- Date: 2026-09-07
- Refines: [ADR-0065](0065-smb-library-and-built-in-book-reader.md), [ADR-0237](0237-video-library-and-web-extraction.md)
- Amends: [ADR-0138](0138-database-v27-compatibility-baseline.md)

## Context

ADR-0065 では Library が SMB server settings と credential を所有し、server設定に host / port / share / root path / username / domain / password をまとめて扱うことにした。ADR-0237 では Video がこの Library-owned SMB設定を `SmbMediaFileAccess` 経由で再利用し、credentialを複製しない方針を採用した。

しかし、同じSMBサーバへ接続するときでも、蔵書と動画では異なるshareやroot pathを同期対象にしたい。host、port、username、domain、passwordは接続先に対する共通設定であり、share / root pathは各機能が「何を同期するか」を決める設定である。この2種類を一つのserver設定として扱うと、VideoがLibraryの同期範囲に従属してしまう。

一方、passwordをVideo側へ複製したり、Libraryとは別の暗号化credential storeを追加すると、同一credentialの第二のsource of truthが生まれる。既存インストールには `smb_library_servers` と、server IDをkeyにした暗号化済みpasswordが既に存在するため、接続設定を全面的に別Contextへ移動すると不必要なcredential migrationと削除コストも増える。

## Decision

### SMB設定を接続プロファイルと同期場所へ意味的に分離する

SMB設定を次の2種類として扱う。

- 接続プロファイル: name、host、port、username、domain、password
- 機能別同期場所: connection profile ID、share、root path

接続プロファイルはアプリの全体設定画面から登録・編集する。passwordは再表示せず、従来と同じAndroid Keystore + AES/GCMで保護されたapp-private credential storeへ保存する。

接続プロファイルを利用する機能はpasswordを受け取らない。接続や認証は既存のSMB access boundary内で行い、consumerへcredential値を公開しない。

### credential storageのownershipは今回移動しない

今回の目的は設定の意味とUI配置を分けることであり、credential trust boundaryそのものを変更することではない。

そのため、既存のLibrary-owned `smb_library_servers` と暗号化credential storeを接続プロファイルの保存基盤として継続利用する。`share_name` / `root_path` columnも既存Library設定との互換性のため維持するが、これらはLibraryの同期場所として解釈し、全体設定画面では編集しない。

将来、Library / Video以外の複数ContextがSMB接続機構を広く利用し、credential ownership自体をLibraryから移動する合理性が生じた場合は、独立したSMB Contextまたはtechnical capabilityへの移動を別ADRで判断する。今回そのsystem-wide ownership migrationは行わない。

### LibraryはLibrary用share / root pathを所有する

LibraryのSMB設定画面では、登録済み接続プロファイルごとにLibrary用のshareとroot pathを設定する。Library同期はLibrary用shareが設定されている接続先だけを対象にする。

既存の `smb_library_servers.share_name` / `root_path` はそのままLibrary同期場所として扱う。このため既存インストールではLibraryのSMB設定とpasswordを移行せず保持できる。

接続プロファイルを新規作成した直後はLibrary用shareを未設定にできる。Library同期は未設定profileを無視し、少なくとも1つLibrary同期場所が設定されていない場合は同期を開始しない。

### VideoはVideo用SMB同期場所を所有する

Video Dataは新しい `video_smb_sources` tableを所有する。

各recordは次を保持する。

- id
- connection profile ID (`server_id`)
- share name
- root path
- updated timestamp

同じ接続プロファイルに複数のVideo同期場所を登録できる。Video設定画面から接続プロファイルを選び、share / root pathを登録・編集・削除して、VideoのSMB同期を明示的に実行できる。

Videoはconnection profile tableやcredential storeを直接read/writeしない。profile一覧やSMB file accessはLibrary Domainが公開するnarrow capabilityを利用する。

### SMB media accessはcaller指定の同期場所を扱う

`SmbMediaFileAccess` はLibraryのshare / root pathを暗黙に走査するAPIをやめ、consumerから connection profile ID、share、root pathを受けて列挙・random-access readを行う。

credentialとhost / username等の接続詳細はaccess implementation内部で解決する。Videoへ公開するのはprofile ID、share/pathに基づくfile identityとfile metadataだけとする。

random-access readでは、指定されたfile pathが指定root path配下にあることを検証する。SMB動画のdurable source identityはshareが異なる同名pathを区別できるよう、connection profile ID、share、pathを含める。

### 接続プロファイル削除はforeign stateを直接削除しない

全体設定で接続プロファイルを削除した場合、credentialとLibrary側のprofile rowを削除する。Video-owned `video_smb_sources` はLibrary / Settingsから直接削除しない。

Video設定は参照先profileが存在しないsourceを無効な設定として表示・削除できるものとし、同期対象から除外する。これによりcross-context foreign table writeを導入しない。

### database versionを29へ進める

Video-owned `video_smb_sources` を追加するためapplication database versionを29へ更新する。

version 28の現在インストールからversion 29への更新を保証する。既存Library SMB設定は同じtable / credential keyを継続利用するためmigrationなしで維持する。

version 28でVideoがLibraryのSMB同期場所を暗黙利用していた挙動は、version 29ではVideo設定として明示的な同期場所を持つ形へ収束させる。upgrade時には既存Library用share / root pathをVideo用初期sourceとして1回だけ取り込める移行経路を用意し、既存のVideo SMB同期対象を失わない。

backup restoreはADR-0138 / ADR-0237のexact-version policyを維持する。version 29アプリはversion 29 snapshotを復元対象とし、version 28 snapshotを直接復元しない。

## Consequences

- 同じSMB接続情報を使いながら、LibraryとVideoで異なるshare / root pathを設定できる。
- passwordを全体設定から管理でき、Video側にcredentialの第二保存先を作らない。
- 既存Library SMB設定と暗号化passwordをそのまま維持できる。
- Videoに `video_smb_sources` という新しいdurable user stateが追加される。
- global settings UIはLibrary Domainのconnection-profile capabilityを利用するが、credential storageのownership自体はLibraryに残る。
- `SmbMediaFileAccess` のcontractはLibraryの同期範囲を暗黙利用する形から、callerが明示した同期場所を利用する形へ変更される。
- 接続プロファイル削除後にVideo側の参照が一時的にdanglingになる可能性があるが、foreign writeで自動削除せずVideo自身が無効設定として扱う。

## Verification

- 既存 `smb_library_servers` rowと暗号化passwordをversion 29でも利用できることをtestする。
- Library用share / root pathが未設定のprofileをLibrary同期が対象外にすることをtestする。
- `video_smb_sources` のfresh schema / version 28 -> 29 upgradeをtestする。
- Video SMB sourceの追加・更新・削除と、同一profileへの複数path登録をrepository testする。
- SMB Video source IDがserver / share / pathの組み合わせを区別して往復できることをtestする。
- `SmbMediaFileAccess` が指定root外のpathを開かないこと、credentialをcontractへ公開しないことをtestする。
- Settings UIが接続プロファイルを編集し、Library / Video UIがそれぞれshare / root pathを編集することを確認する。
- architecture verificationで `video_smb_sources` のownershipとfeature dependency directionを検証する。
- public repository reviewで実host、username、password、share/pathをfixture・log・documentへ含めていないことを確認する。
