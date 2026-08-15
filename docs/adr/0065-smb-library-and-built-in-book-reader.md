# ADR-0065: SMB 蔵書と組み込み Book Reader を分離して提供する

- Status: Accepted
- Date: 2026-08-15
- Updated: 2026-08-15
- Refines: ADR-0013

## Context

既存の蔵書は Google Play Books、Kindle、Audible の所有情報を `LibraryBook` / `LibrarySource` に正規化し、サービス固有の取得処理を `feature:library:data` に閉じ込めている。一方、ユーザーが管理するファイルサーバ上には ZIP / CBZ / PDF の書籍があり、これらも既存蔵書と同じ一覧で扱い、タップ時には外部アプリではなくアプリ内で直接読みたい。

ファイルサーバは SMB のみを対象とする。SMB 接続設定にはパスワードが含まれるため、公開リポジトリへ実接続情報を混入させず、端末保存時も平文 credential を避ける必要がある。

また ZIP と PDF の表示は蔵書同期とは異なる変更理由を持つ。ビューアには少なくとも左右ページ送り、右から左 / 左から右の読み方向、上下連続スクロール、表示モード切り替え時の読書位置維持が必要である。

## Decision

### LibrarySource に SMB を追加する

`LibrarySource.SMB` を追加し、SMB 上の `.zip` / `.cbz` / `.pdf` を1ファイル1冊の `LibraryBook` として既存 `library_items` に保存する。

`sourceId` はサーバ設定 ID と正規化された共有内パスから SHA-256 で導出する。実ホスト名や認証情報は ID に含めない。

同期は設定済みの全 SMB サーバを再帰走査し、すべて成功した後にだけ SMB source の `library_items` をトランザクションで置換する。接続失敗時は既存の SMB 蔵書を保持する。

### SMB 固有処理は library:data が所有する

SMB 接続、ディレクトリ走査、ファイル取得、キャッシュ管理は `feature:library:data` が所有する。初期実装では SMBJ を使用し、SMB 2 / SMB 3 を対象とする。SMB 1 はサポートしない。

非機密の接続設定は Library feature 所有の SQLite table に保存する。`smb_library_servers` は ADR-0047 に従って `libraryDatabaseSchema` の `DatabaseSchemaContribution` として app-level schema に登録し、既存DBにも schema version 更新時に追加する。

パスワードは Android Keystore で生成した非エクスポート AES/GCM 鍵を用いて暗号化し、暗号文だけを app-private SharedPreferences に保存する。パスワードは UI に再表示しない。

実サーバのホスト名、IP アドレス、ユーザー名、パスワード、共有パスをソースコード、fixture、ADR、ログへ記録しない。

現時点の targetSdk 36 では LAN 通信は `INTERNET` 権限による従来互換のアクセスを利用する。将来 targetSdk を 37 以上へ更新するときは Android 17 の Local Network Protection に従い `ACCESS_LOCAL_NETWORK` の宣言・実行時許可を同時に実装する。targetSdk 36 以下の間はこの権限を先行して要求しない。

### 読書前にローカルキャッシュ境界を置く

同期時は書籍本体をダウンロードしない。SMB 本を初めて開く際にファイル全体を app cache へ取得し、以降はキャッシュを優先する。

キャッシュの版は remote file size と last modified time で識別する。初期上限は 2 GiB とし、古いファイルから削除する LRU 相当の運用を行う。

この境界により ZIP の central directory と Android `PdfRenderer` が必要とするローカル random access を単純に扱える。完全な SMB range streaming は初期実装の対象外とする。

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
- NAS が一時的に利用できなくても同期失敗で既存蔵書を失わない
- 一度取得した本は SMB 接続なしでもキャッシュから読める
- ZIP と PDF で左右ページ送り / 上下スクロールを共通 UI として利用できる
- Library の取得責務と Book Reader の表示責務を分離できる
- Reader の操作 UI と戻る処理を親画面のグローバル chrome から分離できる
- SMB credential を公開リポジトリや平文 DB に置かない

### Negative

- 初回読書時はファイル全体のダウンロード完了まで待つ必要がある
- 大きな PDF / ZIP は端末キャッシュ容量を消費する
- `PdfRenderer` ベースの初期実装では PDF のテキスト検索・選択を提供しない
- ファイルのリネームは sourceId の変更として扱われ、読書位置を引き継がない
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
