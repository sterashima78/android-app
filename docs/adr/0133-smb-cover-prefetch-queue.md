# ADR-0133: SMB 蔵書の表紙取得を永続バックグラウンドキューへ分離する

- Status: Accepted
- Date: 2026-08-21
- Refines: ADR-0006, ADR-0047, ADR-0065, ADR-0101
- Supersedes: ADR-0065 の「表紙は派生キャッシュとして段階的に生成する」にある同期時の remote ZIP / CBZ 表紙走査方針

## Context

ADR-0065 では SMB 蔵書の同期時に ZIP / CBZ の入力ストリームを最大 32 MiB まで走査して表紙を生成し、PDF は読書用ローカルキャッシュが存在する場合だけ表紙を生成するとした。

この方式では ZIP / CBZ の冊数が増えるほど通常の蔵書同期に remote read が混ざり、一覧の同期完了まで待ち時間が増える。一方 PDF は未読の本ほど表紙が生成されない。表紙は蔵書そのものではなく再生成可能な派生データなので、蔵書同期の成功条件から分離し、ネットワークと端末ストレージを制限したバックグラウンド処理として扱う方が責務に合う。

また大量の表紙取得では、実行中・待機・失敗・対象外をユーザーが確認でき、PDF の一時転送量も把握できる必要がある。アプリ終了や process death 後もキュー状態を失わないことが望ましい。

初期実装では ZIP / CBZ の走査を 32 MiB、PDF の一時取得を 64 MiB に制限したが、実際の蔵書ではこの制限による `SKIPPED` が多い。表紙生成後に PDF 一時ファイルを必ず削除すること、処理は Wi-Fi 接続かつ battery-not-low 条件で直列実行することから、永続ストレージ消費を増やさずに転送上限を緩和できる。

## Decision

### SMB 同期は remote 表紙取得を待たない

通常の SMB 蔵書同期はファイル一覧とメタデータの同期を優先する。既存の表紙キャッシュ、またはすでに存在する読書用ローカルキャッシュから生成できる表紙は再利用してよいが、同期処理そのものから新しい SMB remote read を行わない。

同期完了後、`thumbnail_url` が未取得の SMB 書籍を表紙先読みキューへ追加し、WorkManager の unique work を起動する。ユーザーは設定画面から未取得分を再度キューへ投入できる。

### Library が永続キューを所有する

表紙先読みは Library Context の派生データ生成であるため、`feature:library:data` が `smb_cover_prefetch_queue` を所有する。table は `libraryDatabaseSchema` の `DatabaseSchemaContribution` に登録し、app-level database version を 26 へ更新する。

状態は次を持つ。

- `PENDING`: 待機中
- `RUNNING`: 実行中
- `FAILED`: 通信や認証等で失敗し、明示的な再試行対象
- `COMPLETED`: 表紙生成済み
- `SKIPPED`: サイズ制限や表紙候補なし等により自動取得対象外

process death 等で `RUNNING` のまま残った項目は次回 Worker 開始時に `PENDING` へ戻す。完了・対象外の履歴は最大 200 件へ制限する。

WorkManager は `NetworkRequest` で Wi-Fi transport と battery-not-low を要求する。SMB は LAN 上のサーバへ接続する機能なので、モバイル回線を避ける条件は「非従量制」ではなく「Wi-Fi 接続」とする。Android が Wi-Fi を従量制として扱っている場合も表紙先読みを許可する。1冊ずつ直列処理し、通常は unique work の `APPEND_OR_REPLACE` を使って Worker 終了直前に新規ジョブが追加されても後続実行要求を失わない。

旧実装の `NetworkType.UNMETERED` で待機中の WorkRequest が残っている端末では、新しい Wi-Fi 制約の WorkRequest を単純に append すると旧 WorkRequest の後ろで待ち続ける。そのため Wi-Fi 制約への初回移行時だけ `REPLACE` で既存 unique work を置き換え、以後は `APPEND_OR_REPLACE` に戻す。durable queue は Library DB 側にあるため、置換で Worker がキャンセルされても実行中項目を `PENDING` へ戻して再開できる。

Wi-Fi・battery-not-low 条件を満たしているにもかかわらず WorkManager が `ENQUEUED` のまま OS scheduler 待ちになっている場合、設定画面から「実行を再要求」を明示できる。この操作は durable queue を作り直さず、既存 unique work を `REPLACE` して同じ制約の WorkRequest を再登録する。通常のキュー追加と明示的な再要求は Scheduler API でも分離し、再要求を通常の `APPEND_OR_REPLACE` と混同しない。WorkManager は即時実行を保証しないため、UI 上も「今すぐ実行」ではなく再要求として扱う。

### ZIP / CBZ は最大 64 MiB の streaming 走査とする

ZIP / CBZ は SMB stream の先頭から最大 64 MiB だけを読み、最初に到達した JPEG / PNG / WebP を表紙候補とする。書籍本体を読書用キャッシュへ保存しない。

64 MiB 以内で画像へ到達できない場合は `SKIPPED` とし、無制限に原本を転送しない。

### PDF は 128 MiB 以下だけ一時取得する

Android `PdfRenderer` は local random access を必要とするため、PDF の表紙生成だけは原本全体の一時取得を許可する。ただし対象を 128 MiB 以下に限定する。

一時ファイルは app cache の専用ディレクトリに置き、1ページ目を表紙へ変換した後、成功・失敗を問わず `finally` で削除する。読書用の `smb-books` キャッシュへ移動しない。128 MiB を超える PDF は `SKIPPED` とし、通常の読書時に書籍本体がキャッシュされた場合のみ既存経路で表紙を生成できる。

PDF の転送中は `downloaded_bytes` / `total_bytes` をキューへ保存し、UI から進捗率と転送量を確認できる。DB 書き込みは概ね 1 MiB ごとに間引く。

### 表紙キャッシュ自体も小容量化する

一覧表示用表紙は最大辺 640 px の JPEG quality 85 とし、原本画像や PDF page bitmap をそのまま永続保存しない。remote file size と modified time をキャッシュキーへ含める既存方針を維持する。

表紙キャッシュ全体は 200 MiB を上限とする。表紙を利用した時刻を file last-modified として更新し、上限超過時は現在生成した表紙を保護した上で古い表紙から LRU 相当で削除する。削除された表紙は `library_items.thumbnail_url` を null に戻し、キュー履歴へ `SKIPPED` として理由を残す。通常の自動同期では直ちに再取得せず、ユーザーが「未取得表紙を先読み」を明示実行した場合に再評価できる。

以前の PNG 表紙キャッシュは再生成可能な派生キャッシュなので migration 対象とせず、必要に応じて新形式へ再生成する。

### キュー画面を Library 設定に置く

SMB 設定画面に「表紙先読みキュー」を追加し、次を表示する。

- 実行中 / 待機 / 完了 / 失敗 / 対象外の件数
- WorkManager の実行状態と、Wi-Fi・バッテリー・OS scheduler のどこで待っているか
- Wi-Fi・バッテリー条件を満たした `ENQUEUED` 状態では、通常は自動開始する旨と OS により遅延し得る旨、および「実行を再要求」操作
- 最新ジョブの書籍名と状態
- PDF 実行中の転送済み bytes / total bytes と progress indicator
- 失敗理由または対象外理由
- 未取得表紙のキュー投入操作
- 失敗ジョブの一括再試行

表示中は active job がある間だけ ViewModel がキュー snapshot を定期再読込し、処理完了時に Library snapshot も再読込して新しい表紙を一覧へ反映する。WorkManager の状態は永続キューの業務状態へ混ぜず、観測用 runtime snapshot として UI に投影する。

### Credential の公開境界は変更しない

SMB Worker は既存の app-private encrypted credential を読み取って接続する。平文 password、実 host、username、share path を source code、test fixture、ADR、log へ追加しない。WorkManager input data に credential を渡さない。

## Consequences

### Positive

- 蔵書同期が ZIP / CBZ の表紙読み込み待ちから分離される。
- 未読 PDF でも 128 MiB 以下なら表紙を事前生成できる。
- PDF 本体は表紙生成後に残らず、永続的な端末容量増加を抑えられる。
- ZIP / CBZ の remote read は1冊最大 64 MiB、PDF は1冊最大 128 MiBに上限がある。
- 表紙キャッシュは最大 200 MiB に制限される。
- モバイル回線とバッテリー低下中はバックグラウンド転送を行わず、従量制設定の Wi-Fi では実行できる。
- process death 後も待機・失敗状態を確認して再開できる。
- ユーザーが処理件数、進捗、失敗理由に加えて WorkManager の待機状態を確認できる。
- OS scheduler 待ちが長い場合に、永続キューを失わず実行要求だけを明示的に再登録できる。

### Negative

- PDF は表紙だけが必要でも最大 128 MiB の原本全体を一時転送する場合がある。
- 128 MiB 超の PDF は未読状態では自動表紙生成されない。
- ZIP / CBZ の表紙画像が archive の先頭 64 MiB より後にある場合は自動取得できない。
- 200 MiB 超過時は古い表紙が一覧から消え、必要なら明示的な再取得が必要になる。
- Library DB に派生処理のキューtableが1つ増える。
- active queue 表示中は定期的な DB snapshot read と WorkManager state read が発生する。
- 「実行を再要求」は OS scheduler の判断を迂回するものではなく、押しても即時開始しない場合がある。

## Alternatives considered

### `NetworkType.UNMETERED` を維持する

通信量の制御としては単純だが、ユーザーが Wi-Fi に接続していても Android 側の従量制判定だけで SMB の LAN 内処理が無期限に止まる可能性がある。SMB の到達経路を表す条件としては Wi-Fi transport の方が直接的なので採用しない。

### すべての書籍本体をバックグラウンドでキャッシュする

表紙生成は単純になるが、大量の書籍本体が端末へ保存され、表紙先読みの目的に対してストレージ消費が大きすぎるため採用しない。

### PDF の任意 range だけを取得して描画する

PDF の cross-reference や object 配置はファイルごとに異なり、先頭数 MiB だけで1ページ目を確実に描画できない。独自 PDF parser / random-access SMB abstraction の追加に対して効果が小さいため採用しない。

### 表紙取得を通常の SMB 同期内に残す

実装は単純だが、メタデータ同期と派生画像生成の失敗・性能特性が結合するため採用しない。

## Sources

- [ADR-0006](0006-durable-background-sync.md)
- [ADR-0047](0047-feature-owned-database-schema-contributions.md)
- [ADR-0065](0065-smb-library-and-built-in-book-reader.md)
- [ADR-0101](0101-feature-route-and-background-runtime-ownership.md)
