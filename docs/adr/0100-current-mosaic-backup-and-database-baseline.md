# ADR-0100: 統合DBとMosaicバックアップを現行互換性ベースラインとする

- Status: Accepted
- Date: 2026-08-18
- Amends: ADR-0059, ADR-0060, ADR-0084, ADR-0098, ADR-0099
- Amended by: [ADR-0138](0138-database-v27-compatibility-baseline.md)

## Context

ADR-0098 で Task、Chat、YouTube を含む durable relational user data を共有 `yomitori-rss.db` へ統合し、ADR-0099 でアプリ独自バックアップを共有DBの整合した snapshot を含む ZIP archive へ変更した。

これらの変更を含む版について、実端末で既存データのDB統合とバックアップ・復元が完了したことを確認した。利用者は現在の最新版を利用しており、旧独立DBや旧JSONバックアップへ直接戻る必要はない。

一方、実装には移行期間だけ必要だった次の互換処理が残っている。

- `yomitori-tasks.db`、`yomitori-chat.db`、`youtube.db` から共有DBへデータをコピーする起動時処理
- JSONバックアップ version 1–8 のserializer / restore処理
- Google Drive上の旧 `yomitori-auto-...` 世代を現行バックアップと合わせて管理する処理
- バックアップarchive内部の旧アプリ名由来のformat名・database entry名・SQLite application id

また、ADR-0084 でユーザー向けアプリ名は `Mosaic` へ変更済みだが、バックアップのファイル名やarchive内部の識別子は旧名称のままだった。

## Decision

### 統合DB

DB統合済みの共有DBを永続データの現行ベースラインとする。

起動時に旧独立DBを検出して共有DBへコピーする `LegacyDatabaseMigration` と、その互換性テストを削除する。旧独立DBだけにデータが存在する状態から現行版へ直接更新することは保証しない。

共有DBの実ファイル名 `yomitori-rss.db`、`applicationId`、package、namespace は変更しない。これらは既存インストール上の現行データやAndroidのアプリ識別に関わる内部識別子であり、ブランド名に合わせるためだけに新しいデータ移行を発生させない。これはADR-0084の内部識別子維持方針を継続する。

### アプリ独自バックアップ

アプリ独自バックアップは、統合DB snapshotを含む現行ZIP archiveだけを読み書きする。旧JSONバックアップ version 1–8 のimport互換性とserializer / restore実装を削除する。

バックアップDBの最低schema versionは、DB統合が完了したversion 23とする。version 23以降のsnapshotは、復元時に通常のdatabase migrationを利用して現行schemaへ更新できる。version 22以前のsnapshotを直接復元することは保証しない。

ユーザーに見えるバックアップ名とarchive内部の識別子を `Mosaic` に統一する。

```text
mosaic-auto-<timestamp>.zip
├── manifest.json
├── database/
│   └── mosaic.db
└── preferences/
    └── user-preferences.json
```

- 自動バックアップ: `mosaic-auto-<timestamp>.zip`
- 手動バックアップ: `mosaic-backup-<date>.zip`
- manifest format: `mosaic-database-backup`
- manifest database name: `mosaic.db`
- database entry: `database/mosaic.db`
- snapshot SQLite `application_id`: `MOSA` (`0x4D4F5341`)

archive内の `mosaic.db` はバックアップ上の論理名であり、復元先の実ファイル名 `yomitori-rss.db` を変更するものではない。

Google Driveの世代管理は `mosaic-auto-` で始まる現行ZIPだけを対象とする。旧名称のバックアップを削除するためだけの互換コードは保持しないため、既存の旧バックアップファイルはアプリからは管理対象外となる。

## Consequences

### Positive

- 起動時に旧Task / Chat / YouTube databaseの存在やschema差分を考慮する必要がなくなる。
- バックアップ実装は現行のDB snapshot archiveだけを理解すればよくなり、旧JSON serializerとdatabase schemaの二重管理を解消できる。
- バックアップの外部表現が現在のアプリ名 `Mosaic` と一致する。
- 現行端末の共有DB実ファイルを移動・改名しないため、整理作業そのものによる現行データ移行リスクを増やさない。
- 今後のdatabase schema migration機構は維持されるため、version 23以降のMosaicバックアップを将来版で復元できる。

### Negative

- 旧独立DBだけに残っているデータは自動統合されない。
- 旧JSONバックアップ version 1–8 と、ADR-0099導入直後の旧名称ZIPバックアップは現行版では復元しない。
- Google Drive上に旧名称の自動バックアップが残っていても、自動世代削除の対象にはならない。

## Security / privacy

この変更はバックアップ対象データの範囲を増やさない。ADR-0099のpreference allowlistとcredential / device-specific state除外方針を維持する。

公開リポジトリには実バックアップ、実ユーザーデータ、credential、token、端末固有の保存先URIを含めない。テストでは人工データだけを使用する。

## Relationship to previous ADRs

- ADR-0059 / ADR-0060 の「移行完了後は一時互換処理を現行形式へ収束させる」方針を、ADR-0098 / ADR-0099導入後の状態に適用する。
- ADR-0084 のMosaicブランドをバックアップの外部表現へ拡張する。一方、現行インストールの継続性に関わる内部DB実ファイル名やpackageは変更しない。
- ADR-0098 の旧独立DBからのone-time migrationは本ADRで役目を終え、削除する。
- ADR-0099 の旧JSON import互換、旧バックアップ世代との混在管理、旧名称のarchive識別子は本ADRで置き換える。SQLite snapshot方式、checksum、integrity check、preference allowlistは維持する。
