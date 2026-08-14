# ADR-0048: Google Books の読書 URL と情報 URL を分離する

- Status: Accepted
- Date: 2026-08-11
- Updated: 2026-08-14

## Context

蔵書機能では Google Books API の URL を利用して対象書籍を開く。

Google Books API は用途の異なる URL を返す。

- `accessInfo.webReaderLink`: Google Books サイトで対象 Volume を読むための URL
- `volumeInfo.infoLink`: Google Books サイトで対象 Volume の情報を見るための URL
- `userInfo.isPurchased`: 認証済みユーザーが対象 Volume を購入済みかを示す情報

当初の実装では `webReaderLink` がない場合に `infoLink` を `LibraryBook.infoUrl` へ保存していた。この値を Android の `ACTION_VIEW` へ渡すと、Google Play の書籍詳細として解決される場合がある。特に `My eBooks` の項目は Google Play ストアの商品や直接読書可能な購入本であるとは限らないため、存在しない商品として Google Play Store の「アイテムは見つかりませんでした」画面へ遷移することがあった。

一方、Google Play Books で購入した Volume でも `webReaderLink` が返らない場合がある。その場合に何も開けないのは、購入済み蔵書としての操作性が低い。

また `webReaderLink` 自体も Google Play Books Android アプリ向けの公開 deep-link API ではない。Reader Activity の内部クラス名を固定する方式も、Play Books の更新によって利用できなくなるため安定した公開契約とは扱えない。

## Decision

### 読書 URL と購入済みフォールバックを分離する

Google Play Books / Google Books から同期した Volume について、次の優先順位で `LibraryBook.infoUrl` を決定する。

1. `accessInfo.webReaderLink` がある場合は、その URL を読書 URL として保存する
2. `webReaderLink` がなく、購入済みと判定できる場合は `https://play.google.com/books` を Play Books アプリ起動用の内部フォールバックとして保存する
3. それ以外は `null` とする

`volumeInfo.infoLink` は書籍情報ページであり、読書開始 URL のフォールバックとして利用しない。

購入済み判定は、認証済み Google Books API レスポンスの `userInfo.isPurchased` を利用する。また Purchased bookshelf (`1`) に含まれる Volume は bookshelf 自体の意味から購入済みとして扱う。同期時は My eBooks (`7`) を先に、Purchased (`1`) を後に処理し、両方に存在する同一 Volume では Purchased 側の判定を最終状態として残す。

`https://play.google.com/books` は一般ブラウザへ開く URL としては扱わない。アプリ内部で Play Books ホームのフォールバックを表す識別子として扱い、`com.google.android.apps.books` の launch intent を起動する。対象書籍への直接遷移は保証しない。

### 既存キャッシュの `infoLink` を外部へ渡さない

既存 DB には以前の同期で `infoLink` が保存されている可能性があるため、再同期を修正適用の前提にしない。

`LibraryUriHandler` は URL を次の 4 種類に分類する。

1. `http(s)://play.google.com/books/reader/...`: Reader URL
2. `https://play.google.com/books`: 購入済み書籍用の Play Books ホームフォールバック
3. `books.google.*` または `play.google.com/store/books/...` 等: Google Books / Google Play の情報 URL
4. その他の URL

Reader URL だけを Google Play Books の読書画面起動対象とする。Play Books ホームフォールバックは Google Play Books アプリの front-door Activity を起動する。Google Books / Google Play の情報 URL は外部 `ACTION_VIEW` へ渡さず、「Google Books API の読書リンクがないため直接開けない」ことをユーザーへ表示する。

これにより、旧キャッシュに残った `infoLink` から Google Play Store の存在しない商品へ遷移する経路も遮断する。旧キャッシュには購入済み判定が保存されていないため、購入済みフォールバックを有効にするには Google Play Books を一度再同期する必要がある。

### Reader URL の起動

Reader URL は HTTP の場合だけ HTTPS に正規化し、`id`、`hl`、`source` など Google が返したクエリパラメータは維持する。

Android 11 以降の package visibility を考慮し、manifest の `<queries>` で `com.google.android.apps.books` を宣言する。

Reader URL を開く際は、端末にインストールされている Google Play Books に対して `PackageManager` から exported reader 系 Activity を探索し、候補を `ComponentName` で明示起動する。内部 Activity の固定クラス名は使用しない。固定名の Activity が実際に存在して外部起動可能であれば同じ探索結果に含まれ、exported でない Activity は固定 `ComponentName` を指定しても外部アプリから安定して起動できないため、固定名による追加フォールバックは冗長と判断する。

Reader Activity を起動できない場合は Google Play Books の front-door Activity を開く。そこまで失敗した場合でも Reader URL を通常の `ACTION_VIEW` へ渡して Google Play Store に解決させず、起動できなかったことを表示する。

Reader Intent と Play Books ホームフォールバックには API が返した公開 URL 以外の認証情報を追加しない。OAuth access token、Google アカウント情報、Cookie、端末識別子は Play Books へ渡さない。

## Consequences

### Positive

- `infoLink` を読書 URL と誤認して Google Play Store の存在しない商品へ遷移しない
- 既存 DB に過去の `infoLink` が残っていても再同期なしで誤遷移を防げる
- Google Books API が明示的に返した Reader URL だけを直接読書対象として扱える
- 購入済み書籍で Reader URL がない場合でも Google Play Books アプリまでは開ける
- `My eBooks` に手動追加された未購入 Volume を購入済み書籍として扱わない
- Google が返した Reader URL のパラメータを保持できる
- Play Books の内部 Activity 名が更新されても、端末上の exported reader Activity を検出できる可能性がある
- 特定の内部 Reader Activity クラス名への重複した依存を持たない
- OAuth token やアカウント情報を外部 Intent に含めない

### Negative

- 購入済みフォールバックは Play Books のホームを開くため、対象書籍が自動的に選択されるとは限らない
- 旧キャッシュで購入済みフォールバックを利用するには一度 Google Play Books の再同期が必要になる
- `webReaderLink` が返らず、購入済みとも判定できない Google Books / My eBooks 項目はアプリから直接読書開始できない
- Google Play Books は Android 向けの公開 reader deep-link API を提供していないため、Reader URL から Play Books 内の対象書籍へ直接遷移する経路は依然として内部 Activity の構成に依存する
- Activity 名のヒューリスティックは Google Play Books の実装変更により追従できなくなる可能性がある

## Relationship to existing ADRs

- ADR-0013 の Google Books API 同期方式と source-specific ID 方針は維持する
- ADR-0013 の Purchased / My eBooks は取得対象を表す。Purchased bookshelf と `userInfo.isPurchased` は購入済み判定に利用するが、My eBooks の全項目を購入済みとは仮定しない
- 本 ADR は Google Books API が返す URL の意味と Android での外部遷移方針を定義する
