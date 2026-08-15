# ADR-0065: SMB 蔵書と組み込み Book Reader を分離して提供する

- Status: Accepted
- Date: 2026-08-15
- Updated: 2026-08-15
- Refines: ADR-0013

## Context

既存の蔵書は Google Play Books、Kindle、Audible の所有情報を `LibraryBook` / `LibrarySource` に正規化し、サービス固有の取得処理を `feature:library:data` に閉じ込めている。一方、ユーザーが管理するファイルサーバ上には ZIP / CBZ / PDF の書籍があり、これらも既存蔵書と同じ一覧で扱い、タップ時には外部アプリではなくアプリ内で直接読みたい。

ファイルサーバは SMB のみを対象とする。SMB 接続設定にはパスワードが含まれるため、公開リポジトリへ実接続情報を混入させず、端末保存時も平文 credential を避ける必要がある。

また ZIP と PDF の表示は蔵書同期とは異なる変更理由を持つ。ビューアには少なくとも左右ページ送り、右から左 / 左から右の読み方向、上下連続スクロール、表示モード切り替え時の読書位置維持が必要である。

蔵書一覧を見ながらファイル名の不備や不要ファイルを発見することもある。別のファイル管理アプリで修正すると、パス由来の `sourceId` と蔵書メタデータの対応が崩れるため、SMB由来の書籍については蔵書一覧から実ファイルを安全に管理できる必要がある。

## Decision

### LibrarySource に SMB を追加する

`LibrarySource.SMB` を追加し、SMB 上の `.zip` / `.cbz` / `.pdf` を1ファイル1冊の `LibraryBook` として既存 `library_items` に保存する。

`sourceId` はサーバ設定 ID と正規化された共有内パスから SHA-256 で導出する。実ホスト名や認証情報は ID に含めない。

同期は設定済みの全 SMB サーバを再帰走査し、すべて成功した後にだけ SMB source の `library_items` をトランザクションで置換する。接続失敗時は既存の SMB 蔵書を保持する。

### SMB 固有処理は library:data が所有する

SMB 接続、ディレクトリ走査、ファイル取得、キャッシュ管理、ファイル名変更、ファイル削除は `feature:library:data` が所有する。初期実装では SMBJ を使用し、SMB 2 / SMB 3 を対象とする。SMB 1 はサポートしない。

非機密の接続設定は Library feature 所有の SQLite table に保存する。`smb_library_servers` は ADR-0047 に従って `libraryDatabaseSchema` の `DatabaseSchemaContribution` として app-level schema に登録し、既存DBにも schema version 更新時に追加する。

パスワードは Android Keystore で生成した非エクスポート AES/GCM 鍵を用いて暗号化し、暗号文だけを app-private SharedPreferences に保存する。パスワードは UI に再表示しない。

実サーバのホスト名、IP アドレス、ユーザー名、パスワード、共有パスをソースコード、fixture、ADR、ログへ記録しない。

現時点の targetSdk 36 では LAN 通信は `INTERNET` 権限による従来互換のアクセスを利用する。将来 targetSdk を 37 以上へ更新するときは Android 17 の Local Network Protection に従い `ACCESS_LOCAL_NETWORK` の宣言・実行時許可を同時に実装する。targetSdk 36 以下の間はこの権限を先行して要求しない。

### 蔵書一覧から SMB 実ファイルを管理する

書籍の長押しで既存の操作メニューを開く。`LibrarySource.SMB` の書籍だけ、通常の「シリーズを編集」「非表示」に加えて「ファイル名を変更」「ファイルを削除」を表示する。

ファイル名変更は同一ディレクトリ内の rename とし、ディレクトリ移動には利用しない。ユーザーが拡張子を省略した場合は現在の拡張子を維持し、異なる形式への変更をこの操作では許可しない。変更先に同名ファイルが存在する場合は失敗させる。

`sourceId` はパス由来なので rename により変化する。アプリ内 rename では SMB 上の rename が成功した後、`library_items` の ID・タイトル・内部 URI と、`hidden_library_items`、`library_item_series`、`library_item_series_exclusions` の参照 ID を同じ変更として移行する。DB 側の移行に失敗した場合は可能な限り SMB 上の rename を元へ戻す。端末内の書籍キャッシュも新しい `sourceId` へ移動する。

表紙キャッシュも `sourceId` をキーに含むため、rename 時は旧表紙を破棄して新しい `sourceId` で再生成する。表紙再生成の失敗は rename 自体を失敗させず、次回同期や読書時に再試行できる。

ファイル削除は不可逆操作のため確認ダイアログを必須とする。SMB 上の削除が成功した後、蔵書項目、非表示・シリーズ関連メタデータ、読書用キャッシュ、表紙キャッシュを削除する。「非表示」は引き続き実ファイルを変更しない別操作として残す。

Book Reader の読書位置は Book Reader feature が `sourceId` をキーに所有しているため、Library feature からそのストレージへ依存して移行しない。このためファイル名変更後の読書位置は新規書籍として開始する。この境界を変更する場合は Book Reader 側に明示的な ID 移行契約を追加する。

### 読書前にローカルキャッシュ境界を置く

同期時は書籍本体をダウンロードしない。SMB 本を初めて開く際にファイル全体を app cache へ取得し、以降はキャッシュを優先する。

キャッシュの版は remote file size と last modified time で識別する。初期上限は 2 GiB とし、古いファイルから削除する LRU 相当の運用を行う。

この境界により ZIP の central directory と Android `PdfRenderer` が必要とするローカル random access を単純に扱える。完全な SMB range streaming は初期実装の対象外とする。

### 表紙は派生キャッシュとして段階的に生成する

SMB 蔵書も既存の蔵書グリッドで `thumbnailUrl` を利用する。表紙は原本ではなく再生成可能な派生キャッシュとして app cache に保存し、remote file size と last modified time をキャッシュキーへ含める。

ZIP / CBZ は同期時に書籍本体を全体ダウンロードせず、SMB の入力ストリームを先頭から最大 32 MiB まで走査し、最初に現れる JPEG / PNG / WebP を表紙候補として保存する。すでに読書用ローカルキャッシュがある場合は、Book Reader と同じ自然順で最初の画像を選ぶ。

PDF は同期のためだけに全ファイルをダウンロードしない。すでに読書用ローカルキャッシュがある場合、または初回読書でローカルキャッシュを作成した後に `PdfRenderer` で1ページ目を表紙へ変換する。Book Reader を閉じる際は Library snapshot を再読込し、生成済み表紙を反映する。

表紙抽出の失敗は蔵書同期・読書開始を失敗させない。表紙が取得できない場合は従来どおり「表紙なし」を表示する。サーバ設定削除時は対象書籍の読書キャッシュと表紙キャッシュの両方を削除する。

### Book Reader を独立 feature とする

表示ロジックを Library UI に埋め込まず、次の concept-oriented module を追加する。

```text
:feature:book-reader:domain
:feature:book-reader:data
:feature:book-reader:ui
```

`domain` は `BookDocument`、`BookPageSource`、`ReadingPosition`、`ReaderMode`、`ReadingDirection` を所有する。

`data` は ZIP / CBZ と PDF のページ供給を実装する。ZIP / CBZ は内部ディレクトリを含めて JPEG / PNG / WebP を自然順に並べる。PDF は Android `PdfRenderer` を使い、ページ単位で描画する。

`ui` は次の2表示方式を同じ論理ページ位置の上に実装する。

- `PAGED`: 左右スワイプによる1ページ送り。右から左 / 左から右を切り替え可能
- `VERTICAL`: 上下への連続スクロール

モード切り替え時は現在の論理ページを維持する。読書位置、表示モード、読み方向は書籍ごとに app-private storage へ保存する。

### Library から Book Reader への遷移

外部サービスの本は既存 URI routing を維持する。SMB 本だけは `yomitori://smb-book/open` の内部 URI として識別し、application composition/navigation layer が Library から Book Reader へ接続する。

Book Reader は Library の通常コンテンツと置換せず、application composition layer から full-screen `Dialog` として表示する。これにより親 `Scaffold` のトップバーや content padding に Reader の操作 UI が依存せず、Android の戻る操作は Dialog の dismiss として蔵書一覧へ戻る。Library 一覧自体は背後で composition を維持する。

SMB file path 等は外部 Intent へ渡さない。

## Consequences

### Positive

- SMB 本を Kindle / Play Books / Audible と同じ蔵書一覧・source filter で扱える
- 蔵書一覧から不要ファイルの削除やファイル名の修正を完結できる
- アプリ内 rename では非表示・シリーズ設定と読書用キャッシュを新しい `sourceId` へ引き継げる
- ZIP / CBZ は原本を全体取得せずに表紙を表示できる
- PDF は読書用キャッシュを再利用して表紙を生成できる
- NAS が一時的に利用できなくても同期失敗で既存蔵書を失わない
- 一度取得した本は SMB 接続なしでもキャッシュから読める
- ZIP と PDF で左右ページ送り / 上下スクロールを共通 UI として利用できる
- Library の取得責務と Book Reader の表示責務を分離できる
- Reader の操作 UI と戻る処理を親画面のグローバル chrome から分離できる
- SMB credential を公開リポジトリや平文 DB に置かない

### Negative

- 初回読書時はファイル全体のダウンロード完了まで待つ必要がある
- 大きな PDF / ZIP は端末キャッシュ容量を消費する
- 未読の PDF はローカルキャッシュがないため、同期直後には実ページ表紙を表示できない場合がある
- ZIP / CBZ の同期時表紙抽出は 32 MiB 以内に最初の画像へ到達できない場合は表紙なしになる
- `PdfRenderer` ベースの初期実装では PDF のテキスト検索・選択を提供しない
- ファイル名変更では `sourceId` が変わるため Book Reader の読書位置は引き継がない
- ファイル削除は SMB 上の実ファイルを削除する不可逆操作である
- 初期実装では RAR / CBR / 7z / EPUB、暗号化 ZIP、パスワード付き PDF を扱わない
- targetSdk 37 以上へ更新する際は Local Network Protection 対応が追加で必要になる

## Relationship to existing ADRs

- ADR-0003: `app` は composition / navigation、feature 固有の実処理は feature module が所有する原則に従う
- ADR-0004: Book Reader を独立した concept-oriented feature とする
- ADR-0013: サービス非依存の Library model を維持したまま `LibrarySource.SMB` を追加し、本 ADR が SMB 固有取得方式を定める
- ADR-0034: source filter は `LibrarySource` の追加に追従し、SMB を識別可能にする
- ADR-0047: SMB 接続設定 table は Library feature の `DatabaseSchemaContribution` が所有し、`core:database` に feature schema を流出させない
- ADR-0054: Superseded 後も残る `app` を composition/navigation に限定する一般原則に従う
- ADR-0055: 現在の最大番号より大きい一意番号を採番する
