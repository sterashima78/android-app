# ADR-0014: 蔵書の非表示状態を同期キャッシュから分離して保存する

- Status: Accepted
- Date: 2026-08-10

## Context

ADR-0013 では、外部サービスから取得した蔵書を `library_items` に保存し、Google Play Books の同期時にはサービス単位で行を置換する方針を採用している。`library_items` は外部サービスから再構築できるキャッシュであり、ユーザーがアプリ内で行った編集状態を保持する用途ではない。

蔵書一覧には、所有はしているが通常の一覧では見たくない書籍を除外したいという要求がある。この操作は削除ではなく可逆な「非表示」とし、後から一覧へ戻せる必要がある。また、Google Play Books を再同期しても非表示指定が解除されてはならない。

`library_items` に `hidden` 列を追加すると、同期時の全件置換によってユーザーの非表示指定が失われる。同期処理で旧レコードからフラグを引き継ぐ方法もあるが、外部データのキャッシュ更新とユーザー設定の保存という異なる責務が結合する。

## Decision

ユーザーによる非表示指定を `hidden_library_items` として `library_items` から分離して保存する。

```text
hidden_library_items
- source
- source_id
- hidden_at
- PRIMARY KEY(source, source_id)
```

識別子は ADR-0013 と同じく `LibrarySource` と source-specific ID の組を使用する。

`hidden_library_items` には `library_items` への外部キーを設定しない。同期時に対象書籍が一時的に取得できなかった場合でも非表示指定を維持し、同じ source/source ID の書籍が再び同期されたときに自動的に非表示へ戻すためである。

通常の蔵書一覧は `hidden_library_items` に一致するキーが存在しない書籍だけを返す。非表示一覧は、現在 `library_items` に存在し、かつ `hidden_library_items` に一致する書籍だけを返す。これにより、同期元から消えた書籍の非表示指定は保持しつつ UI には不要な孤立エントリを表示しない。

Domain では `LibrarySnapshot` に表示中と非表示の書籍を分けて公開し、`LibraryRepository` に `hideBook` / `restoreBook` を定義する。UI は「蔵書」と「非表示」を切り替え、非表示操作と再表示操作を可逆な状態変更として提供する。

## Consequences

### Positive

- Google Play Books の再同期後も非表示指定を維持できる
- 外部サービス由来のキャッシュとユーザーのローカル設定の責務を分離できる
- 非表示は削除ではないため、ユーザーが後から確実に復元できる
- 将来 Kindle / Audible を追加しても同じ source/source ID モデルを利用できる
- 同期元から一時的に消えた書籍が戻った場合も非表示指定を再適用できる

### Negative

- `library_items` に存在しない非表示キーが DB に残る場合がある
- 非表示一覧を得るために `library_items` と非表示指定の照合が必要になる
- 将来 source-specific ID の意味が変わるサービスを追加する場合はキー移行を検討する必要がある

## Relationship to existing ADRs

- ADR-0013 の「`library_items` は再構築可能な同期キャッシュ」という判断を維持する
- ADR-0003 / ADR-0004 に従い、非表示状態も `library` feature の domain/data/ui 内で完結させる
- `core:database` は引き続き汎用 capability とし、蔵書固有スキーマを持たせない
