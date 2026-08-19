# ADR-0120: Bookmark 内部責務・import application service・framework provider 境界を明示する

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0002, ADR-0101, ADR-0106, ADR-0107, ADR-0117, ADR-0119

## Context

ADR-0106 / ADR-0117 / ADR-0119 により Content / Curation 周辺の cross-context persistence は段階的に公開契約へ移された。一方、Curation 内部には次の責務の曖昧さが残っていた。

- `BookmarkStore` が Bookmark read model、Tag catalog、Folder catalog、article-tag/folder association、import 用 tag 作成を同時に所有していた。
- `DefaultBookmarkImportRepository` が Android document I/O、CSV/HTML parse、Content への import/save、Curation tag 永続化、結果集計まで一つの data class で orchestration していた。
- X CSS では ADR-0107 により Application cast を使う service locator を廃止したが、コードベース全体として `RepositoryProvider` 等の lookup を許可する条件は機械的に固定されていなかった。
- 棚卸し中、`BookmarkAutoEnrichmentBackfillWorker` が `YomitoriApplication` に直接 cast し、`AppContainer` から複数 Repository を取得する未監査の service locator を持つことが判明した。
- 境界テスト導入後、`MainActivity` と `AiTaskQueueRoute` にも既存の `YomitoriApplication` 直接 cast が残っていることが検出された。

Provider lookup 自体を一律禁止すると、Android が constructor を所有する `Activity` / `Worker` / `Service` / `AppWidgetProvider` の entry point に不自然な依存解決を強いる。一方、通常の Route / UI / application composition で Provider lookup を許すと、ADR-0107 で解消した hidden dependency が再発する。

## Decision

### 1. Bookmark persistence helper を変更理由ごとに分割する

従来の `BookmarkStore` を次の4責務へ分割する。

- `BookmarkReadStore`: Bookmark 画面向け read model の組み立てだけを担当する。
- `BookmarkTagStore`: Tag catalog の list/create/rename/delete を担当する。
- `BookmarkFolderStore`: Folder catalog と system folder invariant を担当する。
- `BookmarkAssociationStore`: article-tag、article-folder、Read Later membership の変更を担当する。

`DefaultBookmarkRepository` は Curation repository としてこれらを調停するが、SQL helper 自身の変更理由は分離する。

`BookmarkReadStore` に残る `articles` / `feeds` / `feed_folders` access は ADR-0117 / ADR-0119 の transitional read model であり、foreign-table allowlist の path を新ファイル名へ更新する。この変更では物理 schema migration は行わない。

### 2. Bookmark import の orchestration は Application Service とする

`ImportBookmarksUseCase` を Curation domain module に置き、import workflow を次の port に分ける。

- `BookmarkImportSource`: Android document を import entry へ変換する。
- `BookmarkArticleGateway`: Content の作成・保存・重複判定を行う既存 transitional port。
- `BookmarkImportTagWriter`: import された entry の Curation-owned tag association を書く。

UseCase は entry の反復、added/duplicate/skipped 集計、各 port の呼び出し順だけを所有する。

`DefaultBookmarkImportRepository` は UI 互換の入口として残すが、CSV/HTML format を選択して `ImportBookmarksUseCase` を呼ぶ薄い adapter とする。Android `ContentResolver` と parser は `AndroidBookmarkImportSource` に閉じ込める。

### 3. Provider lookup は framework-owned entry point に限定する

`config/architecture/framework-provider-lookups.tsv` を追加し、production code に存在する `as? XxxProvider` lookup を path・contract・理由で列挙する。

許可対象は Android / WorkManager が constructor を所有する entry point に限る。

- Android Activity の composition root
- WorkManager Worker
- Android Service
- AppWidgetProvider / RemoteViewsService から到達する repository access
- background entry point が共有 database を Context から生成するための schema provider

通常の Route、Screen、ViewModel、application service、data object で Provider lookup を新設してはならず、composition root から明示注入する。

`MainActivity` は Android が生成するため `MainActivityDependenciesProvider` だけを Application から取得し、`AppContainer` 自体は参照しない。共有ブックマーク保存と backup change notification は `MainActivityDependencies` が限定された契約として公開する。

`FrameworkProviderBoundaryTest` は production Kotlin source の Provider cast 集合と manifest を完全一致させる。これにより新規 lookup だけでなく不要になった stale manifest entry も検出する。

### 4. YomitoriApplication 直接 cast は禁止する

`BookmarkAutoEnrichmentBackfillWorker` の `YomitoriApplication` cast と `AppContainer` lookup を削除する。

backfill の cross-context orchestration は `BookmarkAutoEnrichmentBackfillUseCase` に移し、Worker は `BookmarkAutoEnrichmentBackfillProvider` という framework entry point contract だけを取得する。`YomitoriApplication` は composition root としてその contract を実装する。

`AiTaskQueueRoute` は Application を参照せず、`AppRouteDependencies` から `SettingsRoute` を通して `AiTaskQueueRepository` を明示注入する。

`MainActivity` も `YomitoriApplication` implementation type へ cast せず、framework entry point 用の `MainActivityDependenciesProvider` のみに依存する。

production code の `as` / `as? YomitoriApplication` は architecture test で禁止する。

## Consequences

### Positive

- Bookmark data helper の変更理由が read model、catalog、association に分かれる。
- import の workflow を Android I/O や SQLite から独立して unit test できる。
- cross-context import が data implementation の暗黙 orchestration ではなく Application Service として表現される。
- framework provider の例外範囲が監査可能になり、通常経路への service locator 再導入を検出できる。
- Bookmark backfill Worker、MainActivity、AI task queue Route が `YomitoriApplication` implementation type を知らなくなる。
- Compose Route の AI task queue dependency は他の Route dependency と同様に composition root から追跡できる。

### Negative

- Curation data module 内の小さな Store / adapter class が増える。
- framework entry point では Provider contract 自体は残る。
- Provider manifest は framework entry point の追加・削除時に意図的な更新が必要になる。
- `BookmarkReadStore` の transitional foreign table access は今回では解消しない。

## Test strategy

- `ImportBookmarksUseCaseTest`: parse 済み entry の Content import、tag association、added/duplicate/skipped 集計、変更通知を純粋 unit test で検証する。
- 既存 `BookmarkCsvTest` / HTML import parser test で document format の挙動を維持する。
- `FrameworkProviderBoundaryTest`: Provider cast と manifest の完全一致、および `YomitoriApplication` 直接 cast 不在を検証する。
- 既存 Bookmark / Article gateway tests により保存・重複・unsave の挙動を継続検証する。
- CI で `verifyArchitecture`、table ownership verification、全 unit test、lint を実行する。

## Public repository safety

追加する manifest、ADR、unit test は repository 内の型名と `example.com` のテスト URL のみを使用し、token、credential、OAuth secret、実ユーザー URL、メールアドレス、個人データを含めない。

## Remaining follow-up

- ADR-0117 / ADR-0119 に従い `articles.saved_at` を Curation-owned persistence へ移す。
- `BookmarkReadStore` から `feeds` / `feed_folders` JOIN を除去し、Content / Source の公開 read contract を利用する。
- Provider contract が constructor injection 可能な runtime infrastructure に置き換わった場合は manifest entry と contract を削除する。
