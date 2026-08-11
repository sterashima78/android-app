# ADR-0019: Google Books の読書 URL と情報 URL を分離する

- Status: Accepted
- Date: 2026-08-11
- Updated: 2026-08-11

## Context

蔵書機能では Google Books API の URL を利用して対象書籍を開く。

Google Books API は用途の異なる URL を返す。

- `accessInfo.webReaderLink`: Google Books サイトで対象 Volume を読むための URL
- `volumeInfo.infoLink`: Google Books サイトで対象 Volume の情報を見るための URL

当初の実装では `webReaderLink` がない場合に `infoLink` を `LibraryBook.infoUrl` へ保存していた。この値を Android の `ACTION_VIEW` へ渡すと、Google Play の書籍詳細として解決される場合がある。特に `My eBooks` の項目は Google Play ストアの商品や直接読書可能な購入本であるとは限らないため、存在しない商品として Google Play Store の「アイテムは見つかりませんでした」画面へ遷移することがあった。

また `webReaderLink` 自体も Google Play Books Android アプリ向けの公開 deep-link API ではない。Reader Activity の内部クラス名を固定する方式も、Play Books の更新によって利用できなくなるため安定した公開契約とは扱えない。

## Decision

### `webReaderLink` だけを読書 URL として保存する

Google Play Books / Google Books から同期した `LibraryBook.infoUrl` には `accessInfo.webReaderLink` だけを保存する。

`volumeInfo.infoLink` は書籍情報ページであり、読書開始 URL のフォールバックとして利用しない。`webReaderLink` が返らない Volume は `LibraryBook.infoUrl = null` とする。

これにより、次回同期以降は直接読書 URL がない項目をクリック可能な外部 URL として扱わない。

### 既存キャッシュの `infoLink` を外部へ渡さない

既存 DB には以前の同期で `infoLink` が保存されている可能性があるため、再同期を修正適用の前提にしない。

`LibraryUriHandler` は URL を次の 3 種類に分類する。

1. `http(s)://play.google.com/books/reader/...`: Reader URL
2. `books.google.*` または `play.google.com/store/books/...` 等: Google Books / Google Play の情報 URL
3. その他の URL

Reader URL だけを Google Play Books の読書画面起動対象とする。Google Books / Google Play の情報 URL は外部 `ACTION_VIEW` へ渡さず、「Google Books API の読書リンクがないため直接開けない」ことをユーザーへ表示する。

これにより、旧キャッシュに残った `infoLink` から Google Play Store の存在しない商品へ遷移する経路も遮断する。

### Reader URL の起動

Reader URL は HTTP の場合だけ HTTPS に正規化し、`id`、`hl`、`source` など Google が返したクエリパラメータは維持する。

Android 11 以降の package visibility を考慮し、manifest の `<queries>` で `com.google.android.apps.books` を宣言する。

Reader URL を開く際は、端末にインストールされている Google Play Books に対して `PackageManager` から exported reader 系 Activity を探索し、候補を `ComponentName` で明示起動する。旧バージョンとの互換経路として従来の `ReadingActivity` 固定名も最後の direct-reader 候補として残す。

Reader Activity を起動できない場合は Google Play Books の front-door Activity を開く。そこまで失敗した場合でも Reader URL を通常の `ACTION_VIEW` へ渡して Google Play Store に解決させず、起動できなかったことを表示する。

Reader Intent には API が返した公開 URL 以外の認証情報を追加しない。OAuth access token、Google アカウント情報、Cookie、端末識別子は Play Books へ渡さない。

## Consequences

### Positive

- `infoLink` を読書 URL と誤認して Google Play Store の存在しない商品へ遷移しない
- 既存 DB に過去の `infoLink` が残っていても再同期なしで誤遷移を防げる
- Google Books API が明示的に返した Reader URL だけを直接読書対象として扱える
- Google が返した Reader URL のパラメータを保持できる
- Play Books の内部 Activity 名が更新されても、端末上の exported reader Activity を検出できる可能性がある
- OAuth token やアカウント情報を外部 Intent に含めない

### Negative

- `webReaderLink` が返らない Google Books / My eBooks 項目はアプリから直接読書開始できない
- Google Play Books は Android 向けの公開 reader deep-link API を提供していないため、Reader URL から Play Books 内の対象書籍へ直接遷移する経路は依然として内部 Activity の構成に依存する
- Activity 名のヒューリスティックは Google Play Books の実装変更により追従できなくなる可能性がある
- direct-reader を解決できない端末では Play Books のホームからユーザーが対象書籍を選ぶ必要がある

## Relationship to existing ADRs

- ADR-0013 の Google Books API 同期方式と source-specific ID 方針は維持する
- ADR-0013 の Purchased / My eBooks は取得対象を表すだけで、すべての項目が Google Play ストア商品であるとは仮定しない
- 本 ADR は Google Books API が返す URL の意味と Android での外部遷移方針を定義する
