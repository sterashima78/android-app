# ADR-0124: Application Service と capability interface を責務境界として使う

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0002, ADR-0101, ADR-0106, ADR-0120

## Context

ADR-0002 は変更理由の異なる責務を class / interface で分け、広すぎる interface は capability ごとに分割する方針を定めている。ADR-0106 は複数 Context / Aggregate にまたがる command orchestration を Application Service とする。ADR-0120 では Bookmark import と framework provider の境界を具体化した。

一方、実装には次の責務集中が残っていた。

- `AppContainer` が Bookmark 追加後の Content 判定と Summary enrichment 要求を実行していた。
- `MainActivity` が共有 Intent の受理後、Bookmark 保存と backup scheduling を個別に orchestration していた。
- `KnowledgeRepository` / `DefaultKnowledgeRepository` が read、永続化、AI生成、source収集、ページ生成・編集を一つの契約と実装に集約していた。
- `BookmarkRepository` と `SummaryRepository` が、利用者によっては不要な command / query capability まで一括で公開していた。

この状態では composition root、framework entry point、Repository の変更理由が増え、必要以上に大きな依存を各 consumer へ渡すことになる。

## Decision

### 1. Composition root は組み立てに限定する

`:app` の `AppContainer` は concrete implementation と Application Service / capability の組み立てだけを行う。

Content 種別や source を使った業務判定、複数 Repository の呼び分け、保存後の副作用判断などの業務 orchestration は `AppContainer` に記述しない。

### 2. Framework entry point は application capability に委譲する

Activity / Worker は Android framework input の受理と結果の presentation に限定する。

共有 Bookmark 保存では `MainActivity` は共有 Intent を解析して保存 capability を呼び、保存・backup scheduling の一連の処理は `SaveSharedBookmarkUseCase` が担う。

Knowledge build Worker は Repository の汎用契約ではなく `KnowledgeBuilder` capability を呼ぶ。

### 3. Repository contract は capability ごとに分割する

consumer が必要な最小契約へ依存できるよう、次の capability を公開する。

- Bookmark: `BookmarkReader`, `BookmarkCatalog`, `BookmarkMutator`, `SharedBookmarkSaver`
- Summary: `SummaryRequester`, `BookmarkEnrichmentRequester`, `SummaryReader`
- Knowledge: `KnowledgeReader`, `KnowledgePageManager`, `KnowledgeBuilder`, `KnowledgePageCreator`, `KnowledgePageEditor`

既存 consumer の移行を一度に強制しないため、`BookmarkRepository` / `SummaryRepository` / `KnowledgeRepository` の aggregate interface は必要な capability を合成する facade として利用できる。ただし新しい依存では、利用目的が一つに限定できる場合は narrow capability を優先する。

### 4. Knowledge の persistence と generation を分ける

Knowledge は次の責務へ物理的に分離する。

- `DefaultKnowledgeRepository`: read contract の adapter
- `SqlKnowledgePageStore`: Knowledge-owned table の SQL 永続化
- `ManagingKnowledgeRepository`: delete / split / merge のユーザー管理 command
- `DefaultKnowledgeGenerationService`: Bookmark / Summary source の収集、AI生成、build / create / edit orchestration

Generation Service は SQL を直接操作せず `SqlKnowledgePageStore` を利用する。Repository は AI runtime や Bookmark / Summary source collection を所有しない。

### 5. Bookmark enrichment policy は Summary application capability が所有する

Bookmark 追加後の自動 AI enrichment と既存 Bookmark の backfill は、同じ `shouldRequestBookmarkEnrichment` policy を利用する。

`AppContainer` や Worker に source 判定を複製しない。通常追加は `BookmarkAutoEnrichmentUseCase`、backfill は `BackfillBookmarkAutoEnrichmentUseCase` が orchestration する。

## Consequences

- `:app` は composition / platform adapter に近づき、業務ルールの変更理由を持ちにくくなる。
- Activity / Worker のテスト対象が framework input と application capability の接続へ限定される。
- consumer は read-only 等の必要な capability だけに依存でき、interface の変更波及を抑えられる。
- Knowledge の AI生成と SQL 永続化を独立して変更・テストしやすくなる。
- capability 数は増えるが、新しい Gradle module は増やさず既存 feature/domain/data 内で ownership を維持する。
- 単一メソッドを転送するだけの wrapper UseCase は作らない。実際の orchestration がない場合は capability interface を直接注入する。

## Verification

- Bookmark enrichment policy / UseCase の unit test を `feature:summary:domain` で実行する。
- 共有 Bookmark 保存と変更通知の unit test を `feature:bookmark:domain` で実行する。
- architecture test で `AppContainer` に enrichment policy が戻らないこと、`MainActivity` に backup orchestration が戻らないことを検査する。
- architecture test で Knowledge Repository が AI / cross-context source collection を所有せず、Generation Service が SQL を直接操作しないことを検査する。
- 通常の `verifyArchitecture` と対象 module の unit test を CI で実行する。

## References

- [ADR-0002](0002-function-class-interface-boundaries.md)
- [ADR-0101](0101-feature-route-and-background-runtime-ownership.md)
- [ADR-0106](0106-domain-context-aggregate-and-persistence-ownership.md)
- [ADR-0120](0120-bookmark-application-service-and-framework-provider-boundary.md)
