# ADR-0186: Bookmark から Library への移動を一方向の Bookmark use case として所有する

- Status: Accepted
- Date: 2026-08-26
- Supersedes: [ADR-0143](0143-web-library-source-and-bookmark-transfer.md) の Bookmark ↔ Library 相互移動判断
- Refines: [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md), [ADR-0125](0125-application-service-and-capability-segregation.md)

## Context

ADR-0143 では Bookmark と Web Library の双方向移動を `:app` の `BookmarkLibraryTransferService` が調停していた。

しかし利用者に必要な操作を見直すと、必要なのは Bookmark を Web 蔵書へ移動する方向だけである。Library から Bookmark へ戻す機能を維持するためだけに、両 feature を対称な cross-context operation として扱い続ける必要はない。

また、現行の architecture principles は feature 間依存そのものを禁止していない。ownership と layer rule に従い、依存方向が一方向であれば、操作を実際に所有する feature が他 feature の narrow capability を利用できる。

## Decision

### 移動は Bookmark → Library の一方向だけを提供する

ユーザー操作としての移動は Bookmark から Web Library への方向だけを提供する。

Library → Bookmark の移動はサポートしない。Library UI から「ブックマークへ移動」の導線を提供せず、app composition から逆方向 callback を公開しない。

### Bookmark が移動 use case を所有する

`feature:bookmark:domain` は `feature:library:domain` に依存し、Library が公開する `WebLibraryAdder` capability を利用する。

`MoveBookmarkToLibraryUseCase` は Bookmark domain に置き、次の順序を所有する。

1. `WebLibraryAdder.addWebBook` で移動先を作成する
2. 追加成功後に `BookmarkMutator.unsaveArticle` で元 Bookmark を解除する
3. Bookmark の変更通知を発行する

Library 追加に失敗した場合は Bookmark を解除しない。Context を跨ぐ単一 DB transaction は作らない。

### Library は追加 capability を narrow に公開する

Web Library の追加だけを必要とする consumer のために `WebLibraryAdder` を公開する。既存の `WebLibraryMutator` は `WebLibraryAdder` を拡張し、Library 自身の追加・metadata 再取得・削除用途は従来どおり `WebLibraryMutator` を利用できる。

Bookmark は Library の削除・再取得 capability に依存しない。

### `:app` は composition のみに戻す

`:app` の `BookmarkLibraryTransferService` は削除する。

app composition は `MoveBookmarkToLibraryUseCase` に `WebLibraryAdder`、`BookmarkMutator`、変更通知 callback を接続し、Bookmark UI に操作 callback を渡すだけとする。移動順序や失敗時の保持ルールを `:app` に実装しない。

## Consequences

- cross-feature dependency は `Bookmark -> Library domain` の一方向になる。
- Library は Bookmark の保存 capability や Bookmark implementation を必要としない。
- 双方向 transfer を抽象化する app-level service が不要になる。
- Bookmark → Library の安全な更新順序は Bookmark domain の unit test で固定できる。
- Library → Bookmark を利用していた操作導線はなくなる。必要になった場合は、再び対称な transfer abstraction を戻すのではなく、その時点の利用要件と ownership を再評価する。

## Verification

- `MoveBookmarkToLibraryUseCaseTest` で Library 追加後に Bookmark を解除する順序を検証する。
- Library 追加失敗時に Bookmark を解除しないことを検証する。
- Library route dependency から逆方向 callback がなくなっていることを確認する。
- `BookmarkLibraryTransferService` が production source に残っていないことを確認する。
- architecture verification、unit test、lint、public repository verification を CI で実行する。
