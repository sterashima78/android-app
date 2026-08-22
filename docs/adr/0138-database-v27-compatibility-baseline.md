# ADR-0138: database version 27 を更新・バックアップ互換性の基準とする

- Status: Accepted
- Date: 2026-08-22
- Amends: [ADR-0059](0059-current-version-compatibility-baseline.md), [ADR-0100](0100-current-mosaic-backup-and-database-baseline.md), [ADR-0123](0123-content-curation-persistence-phase2.md)

## Context

現在利用中のアプリと、現在保持している Mosaic バックアップはいずれも database version 27 へ到達している。

ADR-0059 では現在配布中の最新版を次版への更新互換性 baseline とし、役目を終えた一度限り migration を保持し続けない方針を採用した。ADR-0100 では統合 DB 導入後の version 23 をバックアップ復元の最低 version としていたが、その後 version 24〜27 の schema migration が配布され、現行環境は version 27 へ収束している。

production code には version 13〜27 到達のためだけの migration と migration 専用 test が残り、Curation ownership transfer のための v24 -> v25 foreign-table allowlist も維持されていた。これらを保持すると、現在利用しない schema と transitional ownership exception を今後の変更でも理解し続ける必要がある。

## Decision

- database version 27 を現在の更新互換性 baseline とする。
- version 27 より前の database から現在版へ直接更新することは保証しない。
- version 27 より前の Mosaic database snapshot / backup を現在版へ直接復元することも保証しない。
- backup restore は現在の application schema version と snapshot schema version が一致する場合だけ受理する。今後 database version を上げ、直前 version の backup を引き続き復元対象にする場合は、その schema change と同時に restore baseline を明示的に更新する。
- fresh install の schema は各 feature の `DatabaseSchemaContribution.createSchema` を正本とする。
- `DatabaseMigration` / migration runner の汎用機構は今後の version 27 以降の schema change に利用するため維持する。
- version 27 到達のためだけに存在する feature migration と専用 regression test を削除する。
- v24 -> v25 の `articles.saved_at` -> `bookmarks` ownership transfer migration を削除し、対応する foreign-table allowlist entry も削除する。
- `articles.saved_at` は現行 schema に存在せず、Curation の bookmark state は `bookmarks` table のみを source of truth とする。

## Consequences

### Positive

- production schema code が現在の schema と今後追加される migration だけを扱えばよくなる。
- migration 専用 helper、legacy schema fixture、migration-only test を削除できる。
- Content/Curation 間の transitional foreign-table access がなくなり、allowlist を空にできる。
- backup restore の対応範囲が実際に保持している現行バックアップと一致し、古い snapshot を受理した後に migration 不足で失敗する曖昧さをなくせる。

### Negative

- database version 26 以下のアプリ状態から現在版への直接更新は保証しない。
- database version 26 以下のバックアップは現在版では復元できない。
- 今後 schema version を更新する際は、その直前 baseline からの migration と backup restore 対応範囲を同時に判断する必要がある。

## Verification

- fresh database が version 27 の全 feature schema を生成する test を維持する。
- current version の snapshot round-trip test を維持する。
- current version と異なる snapshot schema version を拒否する test を追加する。
- `foreign-table-access-allowlist.tsv` に runtime/migration exception が残っていないことを architecture verification で確認する。
- 全 unit tests、architecture verification、release lint を CI で実行する。

## Public repository note

この変更では実 database、実バックアップ、credential、token、個人データを repository に追加しない。test data は架空の値だけを使用する。
