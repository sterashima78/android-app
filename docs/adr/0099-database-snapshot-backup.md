# ADR-0099: アプリ独自バックアップを SQLite database snapshot にする

- Status: Accepted
- Date: 2026-08-18
- Extends: ADR-0098

## Context

ADR-0098 により durable relational user data は原則 `yomitori-rss.db` に統合された。一方、アプリ独自の手動 / Google Drive バックアップは、各 table から選択した row を JSON に変換する実装のままだった。

この方式では feature や column を追加するたびに backup export / restore の両方を更新する必要があり、実際に未保存記事、要約、メール、蔵書、Task、Chat、YouTube などがバックアップ対象から漏れる状態が発生した。database schema migration と backup JSON version migration も二重に維持する必要がある。

また共有 database は WAL を使用するため、稼働中の `.db` file だけを単純コピーすると、WAL にのみ存在する committed data を失う可能性がある。

## Decision

アプリ独自バックアップの正本を `yomitori-rss.db` の整合した snapshot に変更する。

バックアップ形式は ZIP archive とし、現在の version は `v2` とする。

```text
yomitori-auto-<timestamp>-v2.zip
├── manifest.json
└── database/
    └── yomitori-rss.db
```

`manifest.json` は次を含む。

- backup format / version
- export timestamp
- database file name
- database schema version
- database byte size
- SHA-256 checksum

snapshot 作成時は WAL を無効化して main database へ反映した後、rollback journal mode の EXCLUSIVE transaction を保持した状態で database file をコピーする。コピー完了後は WAL を再度有効化する。これにより minSdk 29 でも `VACUUM INTO` の availability に依存せず、一貫した snapshot を生成する。

snapshot には SQLite `application_id` として `YOMI` (`0x594F4D49`) を付与する。復元前に次を全て検証する。

- archive format / version
- entry 名と重複
- schema version が現在のアプリ以下であること
- file size
- SHA-256 checksum
- SQLite `application_id`
- SQLite `PRAGMA quick_check`

復元時は snapshot を database directory に staging し、現在の database を退避してから置換する。置換後は通常の `SQLiteOpenHelper` open path を通すため、古い schema version の snapshot は既存 migration により現在 schema へ更新する。open / integrity check に失敗した場合は退避した database を戻す。

旧 JSON backup version 1–8 は import のみ維持し、新規 export には使用しない。自動バックアップの世代管理では旧 `v1.json` と新 `v2.zip` を合わせて最新10世代を保持する。

scheduled backup は app composition root が保持する共有 `YomitoriDatabase` instance を使用する。backup worker が独自 `SQLiteOpenHelper` を生成して、foreground process と別の database lifecycle を持たないようにする。

## Data scope

SQLite snapshot には統合DB内の全ユーザーデータが含まれる。これにはRSS、記事、ブックマーク、要約、メール本文とローカル状態、蔵書、ナレッジ、資産、Task、Chat、YouTubeなどが含まれる。

OAuth credential、SMB password、Android Keystore key は統合DB外に保存されているため、このarchiveには含めない。新端末では必要に応じて再認証・credential再入力を行う。

SharedPreferences、local model file、cache、device-specific benchmark は本決定のbackup対象外とする。これらのうち端末間移行すべき設定をアプリ独自backupへ追加する場合は、secretやdevice-specific stateを除外できる明示的なpolicyを別途定義する。

## Consequences

### Positive

- `DatabaseSchemaContribution` で追加された永続tableは追加実装なしでバックアップ対象になる。
- Task / Chat / YouTubeを含む統合DB全体を一貫して移行できる。
- backup固有のtable / column serializerの追従漏れをなくせる。
- database schema migrationをbackup復元にも再利用できる。
- checksum、application id、SQLite integrity checkにより破損や別DBの誤復元を検出できる。

### Negative

- JSONよりbackup sizeが増え、メール本文など従来含まれなかった個人データもユーザが選択したGoogle Drive folderへ保存される。
- backup作成中は短時間WALを停止し、snapshot copy中はwriterを待たせる。
- SQLite外の設定はこのarchiveだけでは移行されない。
- database file置換のため、復元は通常のrow-level importよりdatabase lifecycleへの影響が大きい。

## Alternatives considered

### JSON exportを拡張する

各featureを追加するたびにbackup schemaへの追従が必要で、今回の取りこぼしを構造的に防げないため採用しない。

### `VACUUM INTO` を必須にする

live database backupとして有効だが、Android API 29相当のSQLite versionでは利用できないためminSdk 29の共通実装には採用しない。将来minimum SQLite versionを引き上げた場合はsnapshot作成手段として再検討できる。

### `.db` fileを稼働中に直接copyする

WALにのみ存在するcommitted dataを欠落させ得るため採用しない。
