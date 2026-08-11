# ADR-0019: Google Play Books の読書画面は実行時に解決する

- Status: Accepted
- Date: 2026-08-11
- Updated: 2026-08-11

## Context

蔵書機能では Google Books API の `accessInfo.webReaderLink` を Google Play Books の対象書籍へ直接遷移するために利用したい。

`webReaderLink` は Google Books サイト上で対象 Volume を読むための URL であり、Google Play Books Android アプリ向けの公開 deep-link API ではない。Android で package を `com.google.android.apps.books` に限定しても、Reader ではなく Google Play の書籍詳細へ解決される場合がある。

一度は Google Play Books の内部実装クラス `com.google.android.apps.play.books.ebook.activity.ReadingActivity` を固定して明示起動する方針を採用したが、実端末ではこの経路が利用できず、その後の URL フォールバックによって Google Play の書籍ページへ遷移した。

また ADR-0013 で同期する `My eBooks` は可変 bookshelf であり、Google Books API 上の Volume が常に Google Play ストアの商品や直接読書可能な購入本であるとは限らない。Google Books の情報 URL に含まれる Volume ID だけを根拠に Reader URL を合成すると、ユーザーライブラリ項目で Google Play の「見つかりません」へ遷移し得る。

## Decision

### Reader URL と情報 URL を区別する

Google Books / Google Play Books の URL に `id` が含まれるという理由だけで Reader URL を合成しない。

直接読書経路の対象は、Google Books API から取得して保存済みの URL が次の Reader URL である場合に限定する。

```text
http(s)://play.google.com/books/reader?... 
```

HTTP の Reader URL は HTTPS に正規化するが、`id`、`hl`、`source` など Google が返したクエリパラメータは削除しない。

`books.google.com/books?id=...` や `play.google.com/store/books/...` など Reader ではない URL は Reader URL へ変換せず、その URL 本来の意味のまま扱う。

### Google Play Books の Reader Activity を実行時に解決する

Android 11 以降の package visibility を考慮し、アプリ manifest の `<queries>` に次を宣言する。

```xml
<package android:name="com.google.android.apps.books" />
```

Reader URL を開く際は、端末にインストールされている Google Play Books に対して `PackageManager` から次の候補を取得する。

1. Reader URL の package 指定 `ACTION_VIEW` に一致する exported Activity
2. Google Play Books package に宣言された exported Activity のうち、Activity 名が reading / reader / ebook 系のもの

候補は reader 系の名前を優先し、store / shop / catalog / detail / preview 系の Activity は直接読書候補から除外する。候補 Activity は `ComponentName` を指定して明示起動する。

旧バージョンとの互換経路として、従来の `ReadingActivity` 固定名も最後の direct-reader 候補として残す。ただし公開 API ではないため `ActivityNotFoundException` と `SecurityException` を処理する。

### ストアページを direct-reader のフォールバックにしない

Reader Activity を起動できなかった場合、package 指定 Reader URL や通常の Reader URL をそのまま起動して Google Play の書籍詳細へ流すことはしない。

Google Play Books がインストール済みなら front-door Activity を開く。Google Play Books 自体がインストールされていない場合だけ、最後のフォールバックとして Web の Reader URL を通常の `ACTION_VIEW` で開く。

Reader ではない Google Books URL は Google Play Books 向け URL に変換せず、通常の `ACTION_VIEW` で開く。これにより My eBooks 内の非購入・非ストア項目を Google Play の存在しない商品 ID として扱わない。

Reader Intent には API が返した公開 URL 以外の認証情報を追加しない。OAuth access token、Google アカウント情報、Cookie、端末識別子は Play Books へ渡さない。

## Consequences

### Positive

- Play Books の内部 Activity 名が更新されても、端末上の exported reader Activity を検出できる可能性がある
- Google が返した Reader URL のパラメータを保持できる
- Reader URL と Google Books の情報 URLを混同しない
- direct-reader 起動失敗時に Google Play ストアの商品ページへ誤遷移しない
- My eBooks の非購入項目を存在しない Play Store 商品として開かない
- 既存 DB は URL 自体を保持しているため再同期を必須としない

### Negative

- Google Play Books は Android 向けの公開 reader deep-link API を提供していないため、直接遷移は依然として内部 Activity の構成に依存する
- Activity 名のヒューリスティックは Google Play Books の実装変更により追従できなくなる可能性がある
- direct-reader を解決できない端末では Play Books のホームからユーザーが対象書籍を選ぶ必要がある
- Reader URL を持たない Google Books のユーザライブラリ項目は、直接読書ではなく情報ページへ遷移する

## Relationship to existing ADRs

- ADR-0013 の Google Books API 同期方式と source-specific ID 方針は維持する
- ADR-0013 の Purchased / My eBooks は取得対象を表すだけで、すべての項目が Google Play ストア商品であるとは仮定しない
- 本 ADR は同期後の Google Books URL を Android で開く際の外部アプリ連携方針を定義する
