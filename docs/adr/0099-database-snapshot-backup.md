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
├── database/
│   └── yomitori-rss.db
└── preferences/
    └── user-preferences.json
```

`manifest.json` は次を含む。

- backup format / version
- export timestamp
- database file name
- database schema version
- database byte size / SHA-256 checksum
- user preferences byte size / SHA-256 checksum

snapshot 作成時は WAL を無効化して main database へ反映した後、rollback journal mode の EXCLUSIVE transaction を保持した状態で database file をコピーする。コピー完了後は WAL を再度有効化する。これにより minSdk 29 でも `VACUUM INTO` の availability に依存せず、一貫した snapshot を生成する。

snapshot には SQLite `application_id` として `YOMI` (`0x594F4D49`) を付与する。復元前に次を全て検証する。

- archive format / version
- entry 名と重複
- schema version が現在のアプリ以下であること
- database / preferences のfile sizeとSHA-256 checksum
- preference file / value type / restricted key
- SQLite `application_id`
- SQLite `PRAGMA quick_check`

復元時は snapshot を database directory に staging し、現在の database を退避してから置換する。置換後は通常の `SQLiteOpenHelper` open path を通すため、古い schema version の snapshot は既存 migration により現在 schema へ更新する。open / integrity check に失敗した場合は退避した database を戻す。preferences はdatabase置換前に現在値を退避し、database復元が失敗した場合は元のpreferencesへ戻す。

旧 JSON backup version 1–8 は import のみ維持し、新規 export には使用しない。自動バックアップの世代管理では旧 `v1.json` と新 `v2.zip` を合わせて最新10世代を保持する。

scheduled backup は app composition root が保持する共有 `YomitoriDatabase` instance を使用する。backup worker が独自 `SQLiteOpenHelper` を生成して、foreground process と別の database lifecycle を持たないようにする。

## Data scope

SQLite snapshot には統合DB内の全ユーザーデータが含まれる。これにはRSS、記事、ブックマーク、要約、メール本文とローカル状態、蔵書、ナレッジ、資産、Task、Chat、YouTubeなどが含まれる。

DB外でユーザーが明示的に作成・選択した設定は、SharedPreferences file / key のallowlistで `preferences/user-preferences.json` に保存する。現在の対象は次のとおり。

- `background_data_fetch`: background data取得設定
- `book_reader_position`: 読書位置・reader mode
- `local_ai_background_execution`: local AI background実行設定
- `local_summary_models`: `selected_model_id`、`inference_backend`、`thinking_enabled`、`speculative_decoding_enabled`、`context_size_mode` のみ
- `summary_preferences`: custom要約prompt
- `workout`: workout履歴・設定
- `x_viewer_preferences`: X custom CSS

allowlist方式とし、将来追加されるSharedPreferencesを暗黙には含めない。credentialや端末固有値が後から自動的にbackupへ混入することを防ぐためである。`local_summary_models` のようにユーザー設定と端末・artifact依存stateが同居するfileはkey単位で制限し、復元時も許可keyだけを置換して他のkeyは維持する。

次の値は明示的にbackup対象外とする。

- Gmail OAuth access token: アプリでは永続化しない
- `smb_library_credentials`: Android Keystore keyで暗号化されたSMB password
- `google_drive_backup`: persisted URI permissionに依存する保存先設定
- `local_context_benchmarks`: 端末memory / performance依存のbenchmark
- `local_summary_models` 内のmodel revision marker、download / inference時間推定など端末・artifact依存state
- local model file / cache（アプリ独自backup）
- transient queue state / download state / crash diagnostics

Android Auto Backup / device transfer はSharedPreferencesのkey単位除外ができないため、`local_summary_models.xml` はfile全体を対象外にする。device transferではlocal model artifact自体は従来どおり `local-summary-models/` を移行し、artifact revision markerは移行先で現行artifactを検証して再生成する。アプリ独自backupでは上記の安全なuser settingだけを移行する。

新端末ではGmailやSMBなど必要なcredentialを再認証・再入力する。

## Consequences

### Positive

- `DatabaseSchemaContribution` で追加された永続tableは追加実装なしでバックアップ対象になる。
- Task / Chat / YouTubeを含む統合DB全体を一貫して移行できる。
- backup固有のtable / column serializerの追従漏れをなくせる。
- database schema migrationをbackup復元にも再利用できる。
- checksum、application id、SQLite integrity checkにより破損や別DBの誤復元を検出できる。
- workout履歴、custom prompt、CSS、読書位置などSQLite外のユーザーデータも移行できる。
- preference allowlistによりcredentialやdevice-specific stateを意図せず含めにくい。
- user settingとdevice-specific stateが同居するpreferencesもkey単位で分離できる。

### Negative

- JSONよりbackup sizeが増え、メール本文など従来含まれなかった個人データもユーザが選択したGoogle Drive folderへ保存される。
- backup作成中は短時間WALを停止し、snapshot copy中はwriterを待たせる。
- 新しいSharedPreferences user dataを追加した場合はfile / key allowlistへの追加判断が必要になる。
- Android標準backupはkey単位policyを表現できないため、mixed-state preferencesはfile全体を除外する必要がある。
- database file置換のため、復元は通常のrow-level importよりdatabase lifecycleへの影響が大きい。

## Alternatives considered

### JSON exportを拡張する

各featureを追加するたびにbackup schemaへの追従が必要で、今回の取りこぼしを構造的に防げないため採用しない。

### SharedPreferencesを全件backupする

将来credentialや端末固有stateがSharedPreferencesに追加されたとき、自動的に外部backupへ含まれる危険があるため採用しない。user-ownedで移行可能な設定だけをallowlistする。

### `VACUUM INTO` を必須にする

live database backupとして有効だが、Android API 29相当のSQLite versionでは利用できないためminSdk 29の共通実装には採用しない。将来minimum SQLite versionを引き上げた場合はsnapshot作成手段として再検討できる。

### `.db` fileを稼働中に直接copyする

WALにのみ存在するcommitted dataを欠落させ得るため採用しない。
