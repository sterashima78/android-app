# ADR-0098: ユーザーデータを単一 SQLite database に統合する

- Status: Accepted
- Date: 2026-08-18
- Amends: ADR-0010, ADR-0047

## Context

ADR-0047 は `yomitori-rss.db` を単一 database とし、各 feature が `DatabaseSchemaContribution` を通じて自身の table と migration を所有する方針を採用した。

その後に追加された Task、Chat、YouTube はそれぞれ `yomitori-tasks.db`、`yomitori-chat.db`、`youtube.db` という独立した SQLite database を作成していた。この分散には次の問題がある。

- Android の backup / device transfer 設定が `yomitori-rss.db` だけを明示対象としていたため、新しい database が自動的に移行対象にならない。
- feature 追加時に database file 単位の backup 設定を追従させる必要があり、永続データの取りこぼしが発生しやすい。
- app 全体の transactional user data が複数 file に分散し、schema version、移行、diagnostics の管理点が増える。
- Task / Chat / YouTube を物理的に別 database とするための isolation、security、lifecycle 上の要件は存在しない。

一方で、database file を統合しても feature ownership を統合する必要はない。table 定義と query の責務は各 feature に残せる。

## Decision

永続的なユーザーデータを格納する SQLite database は原則 `yomitori-rss.db` に統一する。

- Task、Chat、YouTube の table をそれぞれ `:feature:task:data`、`:feature:chat:data`、`:feature:youtube:data` の `DatabaseSchemaContribution` として公開する。
- `:app` はそれらを既存 contribution と同様に composition する。
- database version を 23 に更新する。
- Task / Chat / YouTube repository は `DatabaseConnection` を注入され、独自 `SQLiteOpenHelper` を生成しない。
- table / query / repository の concept ownership は引き続き各 feature に置く。単一 database は persistence infrastructure の共有であり、feature 間の domain coupling を意味しない。

新しい feature が durable relational user data を保存する場合も、原則として `DatabaseSchemaContribution` を追加する。別の物理 database が必要な場合は、transaction boundary、security boundary、独立 lifecycle など具体的な理由を ADR で記録する。

## Existing-device migration

既存端末では version 23 の共有 schema を作成した後、app composition root が旧 database file を検出して共有 database へ移行する。

対象は次の3 file とする。

```text
yomitori-tasks.db
yomitori-chat.db
youtube.db
```

移行は次の条件を満たす。

- 旧 database を `ATTACH DATABASE` で読み込み、共有 database の transaction 内で copy する。
- primary key を維持し、再実行時は `INSERT OR IGNORE` により idempotent にする。
- Task の旧 version 1 に `description` がない場合は空文字を補う。
- YouTube の旧 version 1 に `is_watch_later` がない場合は `0` を補う。
- copy transaction が成功した database だけ旧 file を削除する。
- migration が失敗した場合は旧 file を残し、silent data loss を避ける。

## Backup implications

Android Auto Backup / device transfer は引き続き `yomitori-rss.db` を database 対象として指定する。Task / Chat / YouTube がこの file に統合されるため、feature ごとに database file の include を追加する必要がなくなる。

SharedPreferences、local model、credential など SQLite 外のデータはこの決定の対象外であり、それぞれの backup policy を維持する。

アプリ独自backupの方式は ADR-0099 で決定し、Google Drive / 手動バックアップを統合DBの整合したSQLite snapshotへ変更する。本 ADR で保留していた独自backupの取りこぼし対策は ADR-0099 に引き継ぐ。

## Consequences

### Positive

- Task、Chat、YouTube が Android backup / device transfer から漏れなくなる。
- 新しい feature table は既存の schema contribution mechanism に乗せれば同じ physical database に含まれる。
- schema version と database lifecycle が1か所に集約される。
- feature ownership と physical persistence unit を分離して考えられる。
- 旧端末のデータを保持したまま移行できる。

### Negative

- app-level database version は feature をまたいで共有されるため、schema 変更時に `:app` の version 更新が必要になる。
- physical database 障害の影響範囲は広くなる。
- 初回 version 23 起動時に旧 database からの one-time copy が必要になる。

## Relationship to existing ADRs

ADR-0047 の「単一 database + feature-owned schema contribution」という決定を Task / Chat / YouTube にも適用し、例外的に残っていた独立 database を解消する。

ADR-0010 の YouTube concept ownership と RSS からの domain/data/UI 分離は維持する。ただし ADR-0010 section 5 の「専用 `youtube.db` を持つ」という physical persistence decision は本 ADR で置き換える。YouTube table の ownership は `:feature:youtube:data` に残る。
