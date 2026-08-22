# ADR-0141: SMB 書籍の読書開始を明示的な application callback で接続する

- Status: Accepted
- Date: 2026-08-23
- Refines: ADR-0065
- Related: ADR-0136

## Context

ADR-0065 では SMB 書籍を `yomitori://smb-book/open` の内部 URI で識別し、application composition layer から組み込み Book Reader へ接続し、SMB file path 等を外部 Intent へ渡さない方針を定めた。

実装では Library UI の `LocalUriHandler` を `LibraryUriHandler` に差し替え、SMB 内部 URI を横取りして `onOpenSmbBook` へ変換していた。しかし、書籍サムネイル側は内部・外部を区別せず `UriHandler.openUri()` を呼ぶ構造だった。このため composition 上の handler 差し替えが効かない実行経路やバイナリ差異があると、Compose の `AndroidUriHandler` が内部 URI を `ACTION_VIEW` として Android へ渡し、対応 Activity が存在しない場合にクラッシュする。また例外文字列へ内部 URI の query が含まれると、SMB 内パスや内部 ID がクラッシュ診断へ残る。

内部ナビゲーションを URI handler の暗黙的差し替えへ依存させる必要はない。Library はすでに `LibraryBook` を保持し、application composition layer は `onOpenSmbBook(LibraryBook)` から Book Reader を開く契約を持っている。

## Decision

### SMB の読書開始は明示 callback にする

Library UI は書籍タップを次の3種類へ分類する。

- SMB: `OpenSmbBook` として `onOpenSmbBook(LibraryBook)` を直接呼ぶ
- 外部サービス: `OpenExternalUri` として `UriHandler` を使う
- 開く先なし: 操作メニューを表示する

`onOpenSmbBook` は `LibraryScreen` から一覧・シリーズ・非表示表示を経由して書籍サムネイルまで明示的に渡す。application composition layer の `LibraryRoute` は従来どおり callback を受けて full-screen Dialog の Book Reader を開く。

`LibraryUriHandler` は SMB 内部 URI を解釈しない。SMB の読書開始に `CompositionLocal` の handler 差し替えを使用しない。

### SMB の `infoUrl` は内部 locator として扱う

SMB の `LibraryBook.infoUrl` は repository が server、path、remote version、format を復元するための内部 locator として維持する。ただし外部 open target ではない。

`LibraryBook.openUrl()` は `LibrarySource.SMB` に対して常に `null` を返す。これにより別 UI が `openUrl()` を利用しても SMB locator を外部 URI として扱えないよう防御を重ねる。

AndroidManifest に `yomitori://smb-book` の intent-filter は追加しない。内部 locator を OS の implicit Intent routing へ公開しない。

### クラッシュ診断から SMB locator の query を除去する

`StartupCrashStore` は stack trace を保存する前に `yomitori://smb-book/open?...` の query 全体を `[redacted]` へ置換する。実 SMB パス、server ID、source ID 等を診断情報へ残さない。

クラッシュした APK を特定できるよう、診断ヘッダへ version、versionCode、Git commit SHA を追加する。GitHub Actions で作る APK は `GITHUB_SHA` を BuildConfig へ埋め込む。ローカルビルドで SHA が得られない場合は `local` と記録する。

### 回帰テストを追加する

次を unit test で固定する。

- SMB `infoUrl` が `openUrl()` から公開されない
- SMB 書籍タップが `OpenSmbBook` に分類される
- 外部 URL は `OpenExternalUri`、URL なしは操作メニューになる
- クラッシュ診断から SMB locator の query が除去される

テストデータには実環境の host、path、ID、credential を使用しない。

## Consequences

### Positive

- SMB 書籍タップが Android の implicit Intent routing に到達しなくなる
- `LocalUriHandler` の提供状態に依存せず組み込み Book Reader を開ける
- SMB 内部 locator が `openUrl()` 経由で外部へ漏れる経路を閉じられる
- クラッシュレポートから実 SMB 内パスや内部 ID を除去できる
- versionCode と commit SHA によりインストール済み APK と repository revision の対応を判定しやすくなる

### Negative

- Library UI の callback 引数が各表示 component を通って増える
- SMB `infoUrl` は内部 locator と外部 URL が同じ field に保存される既存 persistence 形式を当面維持するため、意味の違いは source と API 契約で守る必要がある

## Follow-up

将来 `LibraryBook.infoUrl` の用途を persistence schema 上でも分離する場合は、SMB locator を専用の内部 location model へ移す。その変更は migration と backup 形式への影響を伴うため、別判断として扱う。
