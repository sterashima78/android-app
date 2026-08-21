# ADR-0135: SMB 表紙キャッシュをバックアップ復元後に再関連付けする

- Status: Accepted
- Date: 2026-08-21
- Amends: ADR-0099, ADR-0100, ADR-0133

## Context

ADR-0099 / ADR-0100 によりアプリ独自バックアップは共有 SQLite database の snapshot を正本とし、再生成可能な local cache はバックアップ対象外としている。ADR-0133 では SMB 書籍の表紙画像を app cache に最大 200 MiB 保持し、`library_items.thumbnail_url` には生成済み画像の `file://` URLを保存する。

この組み合わせでは、バックアップには `thumbnail_url` が含まれる一方、参照先の表紙ファイルは含まれない。別端末への復元や cache 消去後の復元では、存在しない local file を指す URL が DB に残り、`thumbnail_url IS NULL` を対象とする表紙先読みキューにも再投入されない。

また `smb_cover_prefetch_queue` 自体も DB snapshot に含まれるが、表紙 cache と対になる派生処理状態であるため、復元先で `COMPLETED` 等の状態をそのまま正本として扱うべきではない。SMB password は既存方針どおりバックアップ対象外なので、新端末で復元直後に全表紙を自動実行すると認証失敗を大量発生させる。

## Decision

SMB 表紙画像は引き続き再生成可能な cache とし、バックアップ archive へ含めない。

バックアップ復元完了後、Backup Context は Library Context が公開する復元後初期化 API を呼ぶ。Library Context は自身が所有する table に対して次を行う。

1. `library_items` のうち `source = SMB` かつ `thumbnail_url` が `file:` scheme の項目だけ `thumbnail_url = NULL` に戻す。
2. `smb_cover_prefetch_queue` を空にし、復元前端末の待機・実行・失敗・完了・対象外状態を引き継がない。
3. Kindle / Audible 等の remote cover URL、および将来 SMB が remote URL を持つ場合の非 `file:` URL は変更しない。

app cache 内に既存の SMB 表紙ファイルが残っていても、復元処理では削除しない。後から同一 revision の表紙取得が走った場合は既存 cache file を再利用できるためである。別端末や cache 消去済みの場合は通常の表紙生成経路へフォールバックする。

復元直後には表紙先読みを自動投入しない。SMB credential はバックアップ対象外だからである。ユーザーが SMB サーバ設定を保存して credential が利用可能になった時、Library Repository は `thumbnail_url` が未取得の項目を通常の表紙先読みキューへ追加する。Library UI は既存の scheduler 契約に従って active queue の実行を要求する。

Backup Context が Library table を直接更新することは禁止する。復元固有の意味を持つ `LibraryBackupRestoreInitializer` を `feature:library:data` に置き、table owner が invalidation を実施する。Backup はこの owner API に依存する。

## Consequences

### Positive

- バックアップ容量を表紙 cache 分だけ増やさずに済む。
- 復元後に存在しない `file://` 表紙を正規データとして保持しない。
- 復元前端末の表紙先読みキュー状態を誤って継承しない。
- Kindle / Audible の remote cover URL はそのまま復元できる。
- 同一端末に cache が残っている場合は再取得時に再利用できる。
- 新端末では credential 再設定後に通常のキューへ復帰でき、復元直後の大量認証失敗を避けられる。
- foreign table write を追加せず、Library の persistence ownership を維持できる。

### Negative

- 復元直後は SMB 表紙が一時的に未取得表示になる。
- 新端末では SMB credential の再入力が必要で、表紙再生成にはネットワークアクセスが必要になる。
- 復元後初期化 API という Backup → Library の feature 間依存が1つ増える。

## Alternatives considered

### SMB 表紙画像を backup ZIP に含める

最大 200 MiB の再生成可能 cache により backup size と upload / restore 時間が増えるため採用しない。

### `file://` URL をそのまま復元する

参照先ファイルの存在が保証されず、未取得判定にも入らなくなるため採用しない。

### 復元直後に全件を PENDING へ戻して自動実行する

SMB credential がバックアップされない新端末で大量の認証失敗を発生させるため採用しない。

### Backup module から Library table を直接 UPDATE / DELETE する

ADR-0106 の persistence ownership に反するため採用しない。
