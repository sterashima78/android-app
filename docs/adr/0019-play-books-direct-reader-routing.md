# ADR-0019: Google Play Books の読書画面を段階的フォールバックで直接開く

- Status: Accepted
- Date: 2026-08-11

## Context

蔵書機能では Google Books API から取得した書籍 URL を Android の `ACTION_VIEW` で開いていた。

Google Books API の `accessInfo.webReaderLink` は Google Books サイト上で対象 Volume を読むための URL だが、Android で同じ URL を処理した場合に Google Play の書籍ページとして解決され、Google Play Books の対象書籍の読書画面へ直接遷移しない場合がある。

package を `com.google.android.apps.books` に限定した `ACTION_VIEW` だけでも、URL の解決結果が期待する読書画面にならない端末・バージョンがある。

## Decision

Google Books / Google Play Books の URL に含まれる `id` パラメータを Google Books Volume ID として利用し、開く直前に次の Reader URL を生成する。

```text
https://play.google.com/books/reader?id=<volume-id>
```

Google Books API の Volume ID は Volume を識別する ID であり、同期済みの URL に含まれる ID をそのまま利用する。DB の再同期は要求しない。

Android では次の順序で遷移を試す。

1. Google Play Books の `com.google.android.apps.play.books.ebook.activity.ReadingActivity` を明示して Reader URL を渡す
2. 明示 Activity が存在しない、または起動権限がない場合は `com.google.android.apps.books` package 指定の `ACTION_VIEW` へフォールバックする
3. package 指定でも開けない場合は Reader URL を通常の `ACTION_VIEW` で開く

明示 Activity 名は Google の公開 API 契約ではなく Play Books 実装詳細として扱う。そのため、ActivityNotFoundException と SecurityException を処理して必ず下位のフォールバックへ移る。

Reader Intent には Volume ID と公開 URL 以外を含めない。OAuth access token、Google アカウント情報、その他の認証情報は渡さない。

## Consequences

### Positive

- Google Play Books が対応している場合、対象書籍の読書画面へ直接遷移できる
- Google Books の情報 URL、Reader URL、Google Play の書籍詳細 URLのいずれを保存済みでも Volume ID から同じ Reader URL を生成できる
- 既存 DB の再同期を必要としない
- Play Books の内部 Activity が変更された場合も package 指定または通常 URL へフォールバックできる

### Negative

- 最優先経路は Google Play Books の内部 Activity 名に依存する
- Google Play Books 側が Reader URL や Activity 構成を変更した場合、直接遷移できずフォールバック経路になる可能性がある
- 通常 URL までフォールバックした場合は対象書籍の Web/Google Play ページに遷移する可能性がある

## Relationship to existing ADRs

- ADR-0013 の Google Play Books 同期方式と `LibraryBook` の source-specific ID 方針は変更しない
- 本 ADR は同期後の Google Play Books 書籍を Android で開く際の外部アプリ連携方針のみを定義する
